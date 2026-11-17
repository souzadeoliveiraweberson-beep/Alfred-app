package com.example.data.model

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

data class ParsedDueDate(
    val epochMillis: Long,
    val timestamp: Timestamp,
    val displayText: String,
    val monthKey: String,
    val defaultedToNineAm: Boolean,
)

object ReminderScheduleHelper {

    private val ptBrLocale = Locale.forLanguageTag("pt-BR")
    private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm", ptBrLocale)
    private val dayOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val idKeyFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.US)

    fun parseDueAtIso(
        dueAtIso: String?,
        now: ZonedDateTime = ZonedDateTime.now(),
        zoneId: ZoneId = now.zone,
    ): ParsedDueDate? {
        val raw = dueAtIso?.trim().orEmpty()
        if (raw.isBlank()) return null

        var defaultedToNineAm = false
        val zoned: ZonedDateTime? = runCatching {
            ZonedDateTime.parse(raw).withZoneSameInstant(zoneId)
        }.recoverCatching {
            OffsetDateTime.parse(raw).atZoneSameInstant(zoneId)
        }.recoverCatching {
            LocalDateTime.parse(raw).atZone(zoneId)
        }.recoverCatching {
            val date = LocalDate.parse(raw)
            defaultedToNineAm = true
            date.atTime(9, 0).atZone(zoneId)
        }.getOrNull()

        if (zoned == null) return null
        val millis = zoned.toInstant().toEpochMilli()
        return ParsedDueDate(
            epochMillis = millis,
            timestamp = Timestamp(Date(millis)),
            displayText = formatDueDateText(millis, now, zoneId),
            monthKey = zoned.format(monthKeyFormatter),
            defaultedToNineAm = defaultedToNineAm,
        )
    }

    fun fromDateAndTime(
        date: LocalDate,
        time: LocalTime,
        now: ZonedDateTime = ZonedDateTime.now(),
        zoneId: ZoneId = now.zone,
    ): ParsedDueDate {
        val zoned = date.atTime(time).atZone(zoneId)
        val millis = zoned.toInstant().toEpochMilli()
        return ParsedDueDate(
            epochMillis = millis,
            timestamp = Timestamp(Date(millis)),
            displayText = formatDueDateText(millis, now, zoneId),
            monthKey = zoned.format(monthKeyFormatter),
            defaultedToNineAm = false,
        )
    }

    fun formatDueDateText(
        epochMillis: Long,
        now: ZonedDateTime = ZonedDateTime.now(),
        zoneId: ZoneId = now.zone,
    ): String {
        val target = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val timeText = target.format(timeOnlyFormatter)
        return when (target.toLocalDate()) {
            today -> "Hoje, $timeText"
            today.plusDays(1) -> "Amanhã, $timeText"
            else -> target.format(shortDateFormatter).replace(".", "")
        }.take(40)
    }

    fun computeNextOccurrenceMillis(
        currentDueAtMillis: Long,
        recurrence: ReminderRecurrence,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = currentDueAtMillis,
        anchorDayOfMonth: Int? = null,
    ): Long? {
        if (recurrence == ReminderRecurrence.NONE) return null
        val current = Instant.ofEpochMilli(currentDueAtMillis).atZone(zoneId)
        val anchorDay = (anchorDayOfMonth?.takeIf { it in 1..31 } ?: current.dayOfMonth)
        val targetMinMillis = maxOf(currentDueAtMillis, nowMillis)

        var cycle = 1L
        while (cycle <= 2000L) {
            val candidate = when (recurrence) {
                ReminderRecurrence.DAILY -> current.plusDays(cycle)
                ReminderRecurrence.WEEKLY -> current.plusWeeks(cycle)
                ReminderRecurrence.MONTHLY -> {
                    val ym = YearMonth.from(current).plusMonths(cycle)
                    val clampedDay = anchorDay.coerceAtMost(ym.lengthOfMonth())
                    current
                        .withYear(ym.year)
                        .withMonth(ym.monthValue)
                        .withDayOfMonth(clampedDay)
                }
                ReminderRecurrence.NONE -> return null
            }
            val candidateMillis = candidate.toInstant().toEpochMilli()
            if (candidateMillis > targetMinMillis) {
                return candidateMillis
            }
            cycle++
        }
        return null
    }

    fun deterministicNextOccurrenceId(
        baseReminderId: String,
        nextDueAtMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val sanitizedBase = baseReminderId
            .replace(Regex("""_next_\d{8,14}$"""), "")
            .replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
            .take(96)
            .ifBlank { "rem" }
        val key = Instant.ofEpochMilli(nextDueAtMillis).atZone(zoneId).format(idKeyFormatter)
        return "${sanitizedBase}_next_$key".take(128)
    }

    fun extractDateInput(reminder: FinanceReminder, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        val due = reminder.dueAt?.toDate()?.toInstant()?.atZone(zoneId)?.toLocalDate()
        return due ?: LocalDate.now(zoneId).plusDays(1)
    }

    fun extractTimeInput(reminder: FinanceReminder, zoneId: ZoneId = ZoneId.systemDefault()): LocalTime {
        val due = reminder.dueAt?.toDate()?.toInstant()?.atZone(zoneId)?.toLocalTime()
        return due ?: LocalTime.of(9, 0)
    }

    fun formatDateForPicker(date: LocalDate): String = date.format(dayOnlyFormatter)

    fun formatTimeForPicker(time: LocalTime): String = time.format(timeOnlyFormatter)
}
