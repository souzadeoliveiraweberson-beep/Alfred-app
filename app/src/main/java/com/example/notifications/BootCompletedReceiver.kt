package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationChannels.ensureCreated(appContext)
                val prefs = NotificationPreferences(appContext)
                ReminderAlarmScheduler(appContext, prefs).rescheduleFromCache()
                val settings = prefs.getSettings()
                SummaryWorkScheduler.syncWork(appContext, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
