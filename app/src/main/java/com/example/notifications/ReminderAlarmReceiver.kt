package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.data.model.FinanceScope
import com.example.ui.navigation.ROUTE_BUSINESS
import com.example.ui.navigation.ROUTE_FINANCES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val dueAtMillis = intent.getLongExtra(EXTRA_DUE_AT_MILLIS, 0L)
        val scopeWire = intent.getStringExtra(EXTRA_SCOPE).orEmpty()
        val isOneHourEarly = intent.getBooleanExtra(EXTRA_IS_ONE_HOUR_EARLY, false)

        if (reminderId.isBlank() || title.isBlank() || dueAtMillis <= 0L) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!NotificationChannels.hasNotificationPermission(appContext)) return@launch
                val prefs = NotificationPreferences(appContext)
                val settings = prefs.getSettings()
                if (!settings.remindersEnabled) return@launch
                if (isOneHourEarly && !settings.oneHourBeforeEnabled) return@launch

                val scope = FinanceScope.fromWire(scopeWire)
                val formatted = AlfredNotificationFormatter.formatReminder(
                    title = title,
                    amount = amount,
                    dueAtMillis = dueAtMillis,
                    scope = scope,
                    isOneHourEarly = isOneHourEarly,
                    settings = settings,
                )

                val targetRoute = if (scope == FinanceScope.BUSINESS) {
                    ROUTE_BUSINESS
                } else {
                    ROUTE_FINANCES
                }
                val notificationId = (reminderId + if (isOneHourEarly) ":1h" else ":due").hashCode()
                val posted = NotificationChannels.postNotification(
                    context = appContext,
                    notificationId = notificationId,
                    channelId = NotificationChannels.CHANNEL_REMINDERS,
                    title = formatted.title,
                    body = formatted.body,
                    targetRoute = targetRoute,
                    priority = NotificationCompat.PRIORITY_HIGH,
                )
                if (posted) {
                    prefs.recordNotificationDispatch(
                        categoryKey = formatted.category.key,
                        nickname = formatted.chosenNickname,
                        phraseIndex = formatted.chosenPhraseIndex,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_DUE_AT_MILLIS = "extra_due_at_millis"
        const val EXTRA_SCOPE = "extra_scope"
        const val EXTRA_IS_ONE_HOUR_EARLY = "extra_is_one_hour_early"
    }
}
