package com.example.notifications

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notificationDataStore by preferencesDataStore(name = "alfred_notification_prefs")

data class NotificationSettings(
    val remindersEnabled: Boolean = true,
    val oneHourBeforeEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = true,
    val weeklySummaryEnabled: Boolean = true,
    val goalsEnabled: Boolean = true,
    val dailySummaryHour: Int = 8,
    val dailySummaryMinute: Int = 0,
    val nicknames: List<String> = DEFAULT_NICKNAMES,
    val lastNickname: String = "",
    val lastPhraseIndices: Map<String, Int> = emptyMap(),
    val notifiedGoalMilestones: Map<String, Int> = emptyMap(),
    val onboardingPromptShown: Boolean = false,
) {
    companion object {
        val DEFAULT_NICKNAMES = listOf("chefe", "mestre", "patrão Nascimento", "Nascimento")
    }
}

data class ReminderAlarmSnapshot(
    val id: String,
    val title: String,
    val amount: Double,
    val dueAtMillis: Long,
    val scope: String,
)

class NotificationPreferences(private val context: Context) {

    val settingsFlow: Flow<NotificationSettings> = context.notificationDataStore.data.map { prefs ->
        mapPreferences(prefs)
    }

    suspend fun getSettings(): NotificationSettings {
        return mapPreferences(context.notificationDataStore.data.first())
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setOneHourBeforeEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_ONE_HOUR_BEFORE_ENABLED] = enabled }
    }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_DAILY_SUMMARY_ENABLED] = enabled }
    }

    suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    suspend fun setGoalsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_GOALS_ENABLED] = enabled }
    }

    suspend fun setDailySummaryTime(hour: Int, minute: Int) {
        context.notificationDataStore.edit {
            it[KEY_DAILY_SUMMARY_HOUR] = hour.coerceIn(0, 23)
            it[KEY_DAILY_SUMMARY_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setNicknames(nicknames: List<String>) {
        val cleaned = nicknames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .ifEmpty { NotificationSettings.DEFAULT_NICKNAMES }
        context.notificationDataStore.edit {
            it[KEY_NICKNAMES_JSON] = JSONArray(cleaned).toString()
        }
    }

    suspend fun addNickname(nickname: String) {
        val clean = nickname.trim().take(36)
        if (clean.isBlank()) return
        val current = getSettings().nicknames
        setNicknames(current + clean)
    }

    suspend fun removeNickname(nickname: String) {
        val current = getSettings().nicknames
        val remaining = current.filterNot { it.equals(nickname, ignoreCase = true) }
        if (remaining.isEmpty()) return
        setNicknames(remaining)
    }

    suspend fun resetDefaultNicknames() {
        setNicknames(NotificationSettings.DEFAULT_NICKNAMES)
    }

    suspend fun recordNotificationDispatch(categoryKey: String, nickname: String, phraseIndex: Int) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_LAST_NICKNAME] = nickname
            val currentMap = decodeIntMap(prefs[KEY_LAST_PHRASE_INDICES_JSON]).toMutableMap()
            currentMap[categoryKey] = phraseIndex
            prefs[KEY_LAST_PHRASE_INDICES_JSON] = encodeIntMap(currentMap)
        }
    }

    suspend fun markGoalMilestoneNotified(goalId: String, milestone: Int) {
        context.notificationDataStore.edit { prefs ->
            val currentMap = decodeIntMap(prefs[KEY_GOAL_MILESTONES_JSON]).toMutableMap()
            val prev = currentMap[goalId] ?: 0
            if (milestone > prev) {
                currentMap[goalId] = milestone
                prefs[KEY_GOAL_MILESTONES_JSON] = encodeIntMap(currentMap)
            }
        }
    }

    suspend fun seedGoalMilestonesIfAbsent(milestones: Map<String, Int>) {
        if (milestones.isEmpty()) return
        context.notificationDataStore.edit { prefs ->
            val currentMap = decodeIntMap(prefs[KEY_GOAL_MILESTONES_JSON]).toMutableMap()
            var changed = false
            milestones.forEach { (goalId, level) ->
                if (!currentMap.containsKey(goalId)) {
                    currentMap[goalId] = level
                    changed = true
                }
            }
            if (changed) {
                prefs[KEY_GOAL_MILESTONES_JSON] = encodeIntMap(currentMap)
            }
        }
    }

    suspend fun setOnboardingPromptShown(shown: Boolean) {
        context.notificationDataStore.edit { it[KEY_ONBOARDING_PROMPT_SHOWN] = shown }
    }

    suspend fun getCachedReminderSnapshots(): List<ReminderAlarmSnapshot> {
        val raw = context.notificationDataStore.data.first()[KEY_CACHED_ALARMS_JSON].orEmpty()
        return decodeSnapshots(raw)
    }

    suspend fun saveCachedReminderSnapshots(snapshots: List<ReminderAlarmSnapshot>) {
        context.notificationDataStore.edit { prefs ->
            prefs[KEY_CACHED_ALARMS_JSON] = encodeSnapshots(snapshots)
        }
    }

    private fun mapPreferences(prefs: Preferences): NotificationSettings {
        val rawNicknames = prefs[KEY_NICKNAMES_JSON]
        val nicknames = decodeStringList(rawNicknames)
            .ifEmpty { NotificationSettings.DEFAULT_NICKNAMES }
        return NotificationSettings(
            remindersEnabled = prefs[KEY_REMINDERS_ENABLED] ?: true,
            oneHourBeforeEnabled = prefs[KEY_ONE_HOUR_BEFORE_ENABLED] ?: true,
            dailySummaryEnabled = prefs[KEY_DAILY_SUMMARY_ENABLED] ?: true,
            weeklySummaryEnabled = prefs[KEY_WEEKLY_SUMMARY_ENABLED] ?: true,
            goalsEnabled = prefs[KEY_GOALS_ENABLED] ?: true,
            dailySummaryHour = (prefs[KEY_DAILY_SUMMARY_HOUR] ?: 8).coerceIn(0, 23),
            dailySummaryMinute = (prefs[KEY_DAILY_SUMMARY_MINUTE] ?: 0).coerceIn(0, 59),
            nicknames = nicknames,
            lastNickname = prefs[KEY_LAST_NICKNAME].orEmpty(),
            lastPhraseIndices = decodeIntMap(prefs[KEY_LAST_PHRASE_INDICES_JSON]),
            notifiedGoalMilestones = decodeIntMap(prefs[KEY_GOAL_MILESTONES_JSON]),
            onboardingPromptShown = prefs[KEY_ONBOARDING_PROMPT_SHOWN] ?: false,
        )
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.optInt(key, 0))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeIntMap(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeSnapshots(raw: String): List<ReminderAlarmSnapshot> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val title = o.optString("title")
                    val dueAtMillis = o.optLong("dueAtMillis", 0L)
                    if (id.isNotBlank() && title.isNotBlank() && dueAtMillis > 0L) {
                        add(
                            ReminderAlarmSnapshot(
                                id = id,
                                title = title,
                                amount = o.optDouble("amount", 0.0),
                                dueAtMillis = dueAtMillis,
                                scope = o.optString("scope", "PERSONAL"),
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSnapshots(items: List<ReminderAlarmSnapshot>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("amount", item.amount)
                    put("dueAtMillis", item.dueAtMillis)
                    put("scope", item.scope)
                }
            )
        }
        return arr.toString()
    }

    private companion object {
        val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val KEY_ONE_HOUR_BEFORE_ENABLED = booleanPreferencesKey("one_hour_before_enabled")
        val KEY_DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
        val KEY_WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        val KEY_GOALS_ENABLED = booleanPreferencesKey("goals_enabled")
        val KEY_DAILY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
        val KEY_DAILY_SUMMARY_MINUTE = intPreferencesKey("daily_summary_minute")
        val KEY_NICKNAMES_JSON = stringPreferencesKey("nicknames_json")
        val KEY_LAST_NICKNAME = stringPreferencesKey("last_nickname")
        val KEY_LAST_PHRASE_INDICES_JSON = stringPreferencesKey("last_phrase_indices_json")
        val KEY_GOAL_MILESTONES_JSON = stringPreferencesKey("goal_milestones_json")
        val KEY_ONBOARDING_PROMPT_SHOWN = booleanPreferencesKey("onboarding_prompt_shown")
        val KEY_CACHED_ALARMS_JSON = stringPreferencesKey("cached_reminder_alarms_v1")
    }
}
