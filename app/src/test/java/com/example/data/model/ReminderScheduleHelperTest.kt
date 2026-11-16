package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleHelperTest {

    private val spZone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val referenceNow: ZonedDateTime = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, spZone)

    @Test
    fun parseDueAtIso_withFullOffset_preservesDateAndTime() {
        val parsed = ReminderScheduleHelper.parseDueAtIso(
            dueAtIso = "2026-11-17T14:00:00-03:00",
            now = referenceNow,
            zoneId = spZone,
        )
        assertNotNull(parsed)
        parsed!!
        assertFalse(parsed.defaultedToNineAm)
        assertEquals("2026-11", parsed.monthKey)
        assertEquals("Amanhã, 14:00", parsed.displayText)
        val zoned = Instant.ofEpochMilli(parsed.epochMillis).atZone(spZone)
        assertEquals(14, zoned.hour)
        assertEquals(0, zoned.minute)
    }

    @Test
    fun parseDueAtIso_dateOnly_defaultsToNineAmWithFlag() {
        val parsed = ReminderScheduleHelper.parseDueAtIso(
            dueAtIso = "2026-11-16",
            now = referenceNow,
            zoneId = spZone,
        )
        assertNotNull(parsed)
        parsed!!
        assertTrue(parsed.defaultedToNineAm)
        assertEquals("Hoje, 09:00", parsed.displayText)
        val zoned = Instant.ofEpochMilli(parsed.epochMillis).atZone(spZone)
        assertEquals(9, zoned.hour)
    }

    @Test
    fun computeNextOccurrenceMillis_handlesDailyWeeklyMonthlyAndNone() {
        val baseMillis = ZonedDateTime.of(2026, 11, 16, 14, 30, 0, 0, spZone).toInstant().toEpochMilli()

        assertNull(ReminderScheduleHelper.computeNextOccurrenceMillis(baseMillis, ReminderRecurrence.NONE, spZone))

        val daily = Instant.ofEpochMilli(
            ReminderScheduleHelper.computeNextOccurrenceMillis(baseMillis, ReminderRecurrence.DAILY, spZone)!!
        ).atZone(spZone)
        assertEquals(LocalDate.of(2026, 11, 17), daily.toLocalDate())
        assertEquals(LocalTime.of(14, 30), daily.toLocalTime())

        val weekly = Instant.ofEpochMilli(
            ReminderScheduleHelper.computeNextOccurrenceMillis(baseMillis, ReminderRecurrence.WEEKLY, spZone)!!
        ).atZone(spZone)
        assertEquals(LocalDate.of(2026, 11, 23), weekly.toLocalDate())

        val monthly = Instant.ofEpochMilli(
            ReminderScheduleHelper.computeNextOccurrenceMillis(baseMillis, ReminderRecurrence.MONTHLY, spZone)!!
        ).atZone(spZone)
        assertEquals(LocalDate.of(2026, 12, 16), monthly.toLocalDate())
    }

    @Test
    fun deterministicNextOccurrenceId_doesNotGrowUnboundedAcrossCycles() {
        val nextMillis = ZonedDateTime.of(2026, 12, 16, 14, 0, 0, 0, spZone).toInstant().toEpochMilli()
        val id1 = ReminderScheduleHelper.deterministicNextOccurrenceId("rem_abc", nextMillis, spZone)
        val id2 = ReminderScheduleHelper.deterministicNextOccurrenceId(id1, nextMillis, spZone)
        assertEquals(id1, id2)
        assertEquals("rem_abc_next_202612161400", id1)
    }
}
