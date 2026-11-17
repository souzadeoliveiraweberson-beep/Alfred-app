package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.ui.navigation.ROUTE_FINANCES

class ReminderAlarmScheduler(
    private val context: Context,
    private val preferences: NotificationPreferences = NotificationPreferences(context),
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    suspend fun syncReminders(
        reminders: List<FinanceReminder>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val settings = preferences.getSettings()
        val previousSnapshots = preferences.getCachedReminderSnapshots()

        val activeSnapshots = reminders.mapNotNull { rem ->
            val dueMs = rem.dueAt?.toDate()?.time ?: return@mapNotNull null
            if (rem.isCompleted || dueMs <= nowMillis + MIN_FUTURE_LEAD_MS) return@mapNotNull null
            ReminderAlarmSnapshot(
                id = rem.id,
                title = rem.title,
                amount = rem.amount,
                dueAtMillis = dueMs,
                scope = rem.scope,
            )
        }

        val activeIds = activeSnapshots.map { it.id }.toSet()
        previousSnapshots
            .filter { it.id !in activeIds }
            .forEach { cancelSnapshotAlarms(it.id) }

        if (!settings.remindersEnabled) {
            activeSnapshots.forEach { cancelSnapshotAlarms(it.id) }
            preferences.saveCachedReminderSnapshots(activeSnapshots)
            return
        }

        activeSnapshots.forEach { snapshot ->
            scheduleSnapshot(snapshot, settings.oneHourBeforeEnabled, nowMillis)
        }
        preferences.saveCachedReminderSnapshots(activeSnapshots)
    }

    suspend fun rescheduleFromCache(nowMillis: Long = System.currentTimeMillis()) {
        val settings = preferences.getSettings()
        val snapshots = preferences.getCachedReminderSnapshots()
            .filter { it.dueAtMillis > nowMillis + MIN_FUTURE_LEAD_MS }

        preferences.saveCachedReminderSnapshots(snapshots)
        if (!settings.remindersEnabled) {
            snapshots.forEach { cancelSnapshotAlarms(it.id) }
            return
        }
        snapshots.forEach { snapshot ->
            scheduleSnapshot(snapshot, settings.oneHourBeforeEnabled, nowMillis)
        }
    }

    private fun scheduleSnapshot(
        snapshot: ReminderAlarmSnapshot,
        oneHourBeforeEnabled: Boolean,
        nowMillis: Long,
    ) {
        if (snapshot.dueAtMillis <= nowMillis + MIN_FUTURE_LEAD_MS) {
            cancelSnapshotAlarms(snapshot.id)
            return
        }
        scheduleAlarm(
            snapshot = snapshot,
            triggerAtMillis = snapshot.dueAtMillis,
            isOneHourEarly = false,
        )

        val oneHourBeforeMillis = snapshot.dueAtMillis - ONE_HOUR_MS
        if (oneHourBeforeEnabled && oneHourBeforeMillis > nowMillis + MIN_FUTURE_LEAD_MS) {
            scheduleAlarm(
                snapshot = snapshot,
                triggerAtMillis = oneHourBeforeMillis,
                isOneHourEarly = true,
            )
        } else {
            cancelAlarm(snapshot.id, isOneHourEarly = true)
        }
    }

    private fun scheduleAlarm(
        snapshot: ReminderAlarmSnapshot,
        triggerAtMillis: Long,
        isOneHourEarly: Boolean,
    ) {
        val manager = alarmManager ?: return
        val pendingIntent = buildPendingIntent(
            snapshot = snapshot,
            isOneHourEarly = isOneHourEarly,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            try {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
                return
            } catch (_: SecurityException) {
                // Fallback gracefully below if exact alarm permission was revoked
            }
        }
        AlarmManagerCompat.setAndAllowWhileIdle(
            manager,
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun cancelSnapshotAlarms(reminderId: String) {
        cancelAlarm(reminderId, isOneHourEarly = false)
        cancelAlarm(reminderId, isOneHourEarly = true)
    }

    private fun cancelAlarm(reminderId: String, isOneHourEarly: Boolean) {
        val manager = alarmManager ?: return
        val pendingIntent = buildPendingIntent(
            snapshot = ReminderAlarmSnapshot(
                id = reminderId,
                title = "",
                amount = 0.0,
                dueAtMillis = 0L,
                scope = FinanceScope.PERSONAL.wireValue,
            ),
            isOneHourEarly = isOneHourEarly,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        snapshot: ReminderAlarmSnapshot,
        isOneHourEarly: Boolean,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, snapshot.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, snapshot.title)
            putExtra(ReminderAlarmReceiver.EXTRA_AMOUNT, snapshot.amount)
            putExtra(ReminderAlarmReceiver.EXTRA_DUE_AT_MILLIS, snapshot.dueAtMillis)
            putExtra(ReminderAlarmReceiver.EXTRA_SCOPE, snapshot.scope)
            putExtra(ReminderAlarmReceiver.EXTRA_IS_ONE_HOUR_EARLY, isOneHourEarly)
        }
        val requestCode = requestCodeFor(snapshot.id, isOneHourEarly)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun requestCodeFor(reminderId: String, isOneHourEarly: Boolean): Int {
        val suffix = if (isOneHourEarly) ":1h" else ":due"
        return (reminderId + suffix).hashCode()
    }

    suspend fun fireTestNotificationNow(): Boolean {
        val settings = preferences.getSettings()
        val formatted = AlfredNotificationFormatter.formatTestNotification(settings)
        val posted = NotificationChannels.postNotification(
            context = context,
            notificationId = TEST_NOTIFICATION_ID,
            channelId = NotificationChannels.CHANNEL_REMINDERS,
            title = formatted.title,
            body = formatted.body,
            targetRoute = ROUTE_FINANCES,
            priority = NotificationCompat.PRIORITY_HIGH,
        )
        if (posted) {
            preferences.recordNotificationDispatch(
                categoryKey = formatted.category.key,
                nickname = formatted.chosenNickname,
                phraseIndex = formatted.chosenPhraseIndex,
            )
        }
        return posted
    }

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
        const val MIN_FUTURE_LEAD_MS = 1_000L
        const val TEST_NOTIFICATION_ID = 90_901
    }
}
