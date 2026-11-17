package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SummaryAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val kind = intent.getStringExtra(SummaryNotificationWorker.KEY_KIND)
            ?: SummaryNotificationWorker.KIND_DAILY

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SummaryWorkScheduler.enqueueImmediateOneTime(appContext, kind)
                val settings = NotificationPreferences(appContext).getSettings()
                SummaryWorkScheduler.syncWork(appContext, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
