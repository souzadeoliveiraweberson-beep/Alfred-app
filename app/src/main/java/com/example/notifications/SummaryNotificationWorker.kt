package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceTransaction
import com.example.ui.navigation.ROUTE_ANALYTICS
import com.example.ui.navigation.ROUTE_FINANCES
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class SummaryNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: KIND_DAILY
        val context = applicationContext
        val prefs = NotificationPreferences(context)
        val settings = prefs.getSettings()

        try {
            if (!NotificationChannels.hasNotificationPermission(context)) {
                return Result.success()
            }
            if (kind == KIND_DAILY && !settings.dailySummaryEnabled) return Result.success()
            if (kind == KIND_WEEKLY && !settings.weeklySummaryEnabled) return Result.success()

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val userRoot = db.collection("users").document(uid)

            val transactions = fetchCollection(
                query = userRoot.collection("transactions"),
                clazz = FinanceTransaction::class.java,
            )
            val reminders = fetchCollection(
                query = userRoot.collection("reminders"),
                clazz = FinanceReminder::class.java,
            )
            val goals = if (kind == KIND_WEEKLY) {
                fetchCollection(
                    query = userRoot.collection("goals"),
                    clazz = FinanceGoal::class.java,
                )
            } else {
                emptyList()
            }

            val formatted = if (kind == KIND_WEEKLY) {
                AlfredNotificationFormatter.formatWeeklySummary(
                    transactions = transactions,
                    reminders = reminders,
                    goals = goals,
                    settings = settings,
                )
            } else {
                AlfredNotificationFormatter.formatDailySummary(
                    transactions = transactions,
                    reminders = reminders,
                    settings = settings,
                )
            } ?: return Result.success()

            val targetRoute = if (kind == KIND_WEEKLY) ROUTE_ANALYTICS else ROUTE_FINANCES
            val notifId = if (kind == KIND_WEEKLY) WEEKLY_NOTIFICATION_ID else DAILY_NOTIFICATION_ID
            val posted = NotificationChannels.postNotification(
                context = context,
                notificationId = notifId,
                channelId = NotificationChannels.CHANNEL_SUMMARIES,
                title = formatted.title,
                body = formatted.body,
                targetRoute = targetRoute,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
            if (posted) {
                prefs.recordNotificationDispatch(
                    categoryKey = formatted.category.key,
                    nickname = formatted.chosenNickname,
                    phraseIndex = formatted.chosenPhraseIndex,
                )
            }
            return Result.success()
        } finally {
            SummaryWorkScheduler.syncWork(context, settings)
        }
    }

    private suspend fun <T> fetchCollection(
        query: com.google.firebase.firestore.CollectionReference,
        clazz: Class<T>,
    ): List<T> {
        return try {
            withTimeout(FETCH_TIMEOUT_MS) {
                query.get(Source.DEFAULT)
                    .await()
                    .toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        } catch (_: Exception) {
            runCatching {
                query.get(Source.CACHE)
                    .await()
                    .toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrDefault(emptyList())
        }
    }

    companion object {
        const val KEY_KIND = "summary_kind"
        const val KIND_DAILY = "daily"
        const val KIND_WEEKLY = "weekly"
        private const val FETCH_TIMEOUT_MS = 10_000L
        private const val DAILY_NOTIFICATION_ID = 90_101
        private const val WEEKLY_NOTIFICATION_ID = 90_102
    }
}

object SummaryWorkScheduler {
    private const val LEGACY_UNIQUE_DAILY_WORK = "alfred_daily_summary_work"
    private const val LEGACY_UNIQUE_WEEKLY_WORK = "alfred_weekly_summary_work"
    private const val ONE_TIME_DAILY_WORK = "alfred_daily_summary_onetime"
    private const val ONE_TIME_WEEKLY_WORK = "alfred_weekly_summary_onetime"
    private const val REQ_DAILY_ALARM = 91_101
    private const val REQ_WEEKLY_ALARM = 91_102

    fun syncWork(
        context: Context,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        // Remove legacy PeriodicWork entries so only exact AlarmManager -> OneTimeWork runs
        workManager.cancelUniqueWork(LEGACY_UNIQUE_DAILY_WORK)
        workManager.cancelUniqueWork(LEGACY_UNIQUE_WEEKLY_WORK)

        if (settings.dailySummaryEnabled) {
            val triggerAt = nextDailyTriggerAtMillis(
                hour = settings.dailySummaryHour,
                minute = settings.dailySummaryMinute,
                now = now,
            )
            scheduleExactAlarm(
                context = appContext,
                requestCode = REQ_DAILY_ALARM,
                kind = SummaryNotificationWorker.KIND_DAILY,
                triggerAtMillis = triggerAt,
            )
        } else {
            cancelSummaryAlarm(appContext, REQ_DAILY_ALARM, SummaryNotificationWorker.KIND_DAILY)
            workManager.cancelUniqueWork(ONE_TIME_DAILY_WORK)
        }

        if (settings.weeklySummaryEnabled) {
            val triggerAt = nextWeeklySundayTriggerAtMillis(
                hour = 9,
                minute = 0,
                now = now,
            )
            scheduleExactAlarm(
                context = appContext,
                requestCode = REQ_WEEKLY_ALARM,
                kind = SummaryNotificationWorker.KIND_WEEKLY,
                triggerAtMillis = triggerAt,
            )
        } else {
            cancelSummaryAlarm(appContext, REQ_WEEKLY_ALARM, SummaryNotificationWorker.KIND_WEEKLY)
            workManager.cancelUniqueWork(ONE_TIME_WEEKLY_WORK)
        }
    }

    fun enqueueImmediateOneTime(context: Context, kind: String) {
        val appContext = context.applicationContext
        val uniqueName = if (kind == SummaryNotificationWorker.KIND_WEEKLY) {
            ONE_TIME_WEEKLY_WORK
        } else {
            ONE_TIME_DAILY_WORK
        }
        val request = OneTimeWorkRequestBuilder<SummaryNotificationWorker>()
            .setInputData(Data.Builder().putString(SummaryNotificationWorker.KEY_KIND, kind).build())
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleExactAlarm(
        context: Context,
        requestCode: Int,
        kind: String,
        triggerAtMillis: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildSummaryPendingIntent(
            context = context,
            requestCode = requestCode,
            kind = kind,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            try {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
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
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun cancelSummaryAlarm(context: Context, requestCode: Int, kind: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildSummaryPendingIntent(
            context = context,
            requestCode = requestCode,
            kind = kind,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildSummaryPendingIntent(
        context: Context,
        requestCode: Int,
        kind: String,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra(SummaryNotificationWorker.KEY_KIND, kind)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    internal fun nextDailyTriggerAtMillis(
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        var next = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    internal fun nextWeeklySundayTriggerAtMillis(
        hour: Int = 9,
        minute: Int = 0,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        var next = now
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1)
        }
        return next.toInstant().toEpochMilli()
    }

    internal fun millisUntilNextDaily(
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        val nextMillis = nextDailyTriggerAtMillis(hour, minute, now)
        return (nextMillis - now.toInstant().toEpochMilli()).coerceAtLeast(1L)
    }

    internal fun millisUntilNextWeeklySunday(
        hour: Int = 9,
        minute: Int = 0,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        val nextMillis = nextWeeklySundayTriggerAtMillis(hour, minute, now)
        return (nextMillis - now.toInstant().toEpochMilli()).coerceAtLeast(1L)
    }
}
