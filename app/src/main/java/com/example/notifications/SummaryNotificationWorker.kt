package com.example.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
import java.util.concurrent.TimeUnit

class SummaryNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: KIND_DAILY
        val context = applicationContext
        if (!NotificationChannels.hasNotificationPermission(context)) {
            return Result.success()
        }

        val prefs = NotificationPreferences(context)
        val settings = prefs.getSettings()
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
    private const val UNIQUE_DAILY_WORK = "alfred_daily_summary_work"
    private const val UNIQUE_WEEKLY_WORK = "alfred_weekly_summary_work"

    fun syncWork(context: Context, settings: NotificationSettings) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (settings.dailySummaryEnabled) {
            val delayMillis = millisUntilNextDaily(settings.dailySummaryHour, settings.dailySummaryMinute)
            val request = PeriodicWorkRequestBuilder<SummaryNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(SummaryNotificationWorker.KEY_KIND, SummaryNotificationWorker.KIND_DAILY).build())
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_DAILY_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(UNIQUE_DAILY_WORK)
        }

        if (settings.weeklySummaryEnabled) {
            val delayMillis = millisUntilNextWeeklySunday(hour = 9, minute = 0)
            val request = PeriodicWorkRequestBuilder<SummaryNotificationWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(SummaryNotificationWorker.KEY_KIND, SummaryNotificationWorker.KIND_WEEKLY).build())
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WEEKLY_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(UNIQUE_WEEKLY_WORK)
        }
    }

    internal fun millisUntilNextDaily(
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
        return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
    }

    internal fun millisUntilNextWeeklySunday(
        hour: Int = 9,
        minute: Int = 0,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        var next = now
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
    }
}
