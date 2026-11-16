package com.example.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.ui.navigation.ROUTE_ANALYTICS

interface AlfredNotificationBridge {
    suspend fun syncReminders(reminders: List<FinanceReminder>)
    suspend fun seedGoalMilestonesIfAbsent(goals: List<FinanceGoal>)
    suspend fun checkGoalMilestones(goals: List<FinanceGoal>)
}

class AndroidNotificationBridge(
    context: Context,
    private val preferences: NotificationPreferences = NotificationPreferences(context.applicationContext),
    private val scheduler: ReminderAlarmScheduler = ReminderAlarmScheduler(context.applicationContext, preferences),
) : AlfredNotificationBridge {

    private val appContext = context.applicationContext

    override suspend fun syncReminders(reminders: List<FinanceReminder>) {
        NotificationChannels.ensureCreated(appContext)
        scheduler.syncReminders(reminders)
    }

    override suspend fun seedGoalMilestonesIfAbsent(goals: List<FinanceGoal>) {
        if (goals.isEmpty()) return
        val initialLevels = goals.associate { goal ->
            goal.id to (AlfredNotificationFormatter.highestCrossedMilestone(goal) ?: 0)
        }
        preferences.seedGoalMilestonesIfAbsent(initialLevels)
    }

    override suspend fun checkGoalMilestones(goals: List<FinanceGoal>) {
        if (goals.isEmpty()) return
        NotificationChannels.ensureCreated(appContext)
        var settings = preferences.getSettings()
        if (!settings.goalsEnabled) return

        for (goal in goals) {
            val prevLevel = settings.notifiedGoalMilestones[goal.id] ?: 0
            val result = AlfredNotificationFormatter.formatGoalMilestone(
                goal = goal,
                previousNotifiedMilestone = prevLevel,
                settings = settings,
            ) ?: continue

            val (reached, formatted) = result
            preferences.markGoalMilestoneNotified(goal.id, reached)
            val posted = NotificationChannels.postNotification(
                context = appContext,
                notificationId = ("goal:${goal.id}:$reached").hashCode(),
                channelId = NotificationChannels.CHANNEL_GOALS,
                title = formatted.title,
                body = formatted.body,
                targetRoute = ROUTE_ANALYTICS,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
            if (posted) {
                preferences.recordNotificationDispatch(
                    categoryKey = formatted.category.key,
                    nickname = formatted.chosenNickname,
                    phraseIndex = formatted.chosenPhraseIndex,
                )
            }
            settings = preferences.getSettings()
        }
    }
}
