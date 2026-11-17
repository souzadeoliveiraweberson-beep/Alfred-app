package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationChannels {
    const val CHANNEL_REMINDERS = "alfred_reminders"
    const val CHANNEL_SUMMARIES = "alfred_summaries"
    const val CHANNEL_GOALS = "alfred_goals"

    const val EXTRA_NAV_ROUTE = "alfred_nav_route"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Lembretes",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisos no horário e 1 hora antes dos seus lembretes."
            enableVibration(true)
        }

        val summariesChannel = NotificationChannel(
            CHANNEL_SUMMARIES,
            "Resumo diário",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Resumos diários e semanais no estilo Alfred."
        }

        val goalsChannel = NotificationChannel(
            CHANNEL_GOALS,
            "Metas",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Marcos de progresso (50%, 80% e 100%) das suas metas."
        }

        manager.createNotificationChannels(
            listOf(remindersChannel, summariesChannel, goalsChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val managerEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!managerEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    fun buildExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun buildBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    fun buildBatteryOptimizationFallbackIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    @SuppressLint("MissingPermission")
    fun postNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        targetRoute: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureCreated(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_ROUTE, targetRoute)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_alfred)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }
}
