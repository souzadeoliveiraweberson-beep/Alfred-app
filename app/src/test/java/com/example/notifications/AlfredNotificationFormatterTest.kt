package com.example.notifications

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlin.random.Random

class AlfredNotificationFormatterTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun greetingForHour_coversAllFourDayPeriods() {
        assertEquals("Boa madrugada", AlfredNotificationFormatter.greetingForHour(0))
        assertEquals("Boa madrugada", AlfredNotificationFormatter.greetingForHour(5))
        assertEquals("Bom dia", AlfredNotificationFormatter.greetingForHour(6))
        assertEquals("Bom dia", AlfredNotificationFormatter.greetingForHour(11))
        assertEquals("Boa tarde", AlfredNotificationFormatter.greetingForHour(12))
        assertEquals("Boa tarde", AlfredNotificationFormatter.greetingForHour(17))
        assertEquals("Boa noite", AlfredNotificationFormatter.greetingForHour(18))
        assertEquals("Boa noite", AlfredNotificationFormatter.greetingForHour(23))
    }

    @Test
    fun pickNickname_neverRepeatsLastNicknameWhenPoolHasMultipleEntries() {
        val pool = listOf("chefe", "mestre", "patrão Nascimento", "Nascimento")
        var last = "chefe"
        val random = Random(42)
        repeat(25) {
            val picked = AlfredNotificationFormatter.pickNickname(pool, last, random)
            assertTrue(picked in pool)
            assertNotEquals(last, picked)
            last = picked
        }
    }

    @Test
    fun pickTemplateIndex_rotatesWithoutImmediateRepeatAcrossTemplates() {
        var lastIdx: Int? = null
        val random = Random(99)
        val seen = mutableSetOf<Int>()
        repeat(40) {
            val next = AlfredNotificationFormatter.pickTemplateIndex(size = 5, lastIndex = lastIdx, random = random)
            assertTrue(next in 0..4)
            if (lastIdx != null) {
                assertNotEquals(lastIdx, next)
            }
            seen.add(next)
            lastIdx = next
        }
        assertTrue(seen.size >= 4)
    }

    @Test
    fun formatReminder_formatsOnTimeAndOneHourBeforeWithoutRepeatingTemplateOrNickname() {
        val now = ZonedDateTime.of(2026, 5, 18, 13, 0, 0, 0, zone)
        val dueAtMillis = ZonedDateTime.of(2026, 5, 18, 14, 0, 0, 0, zone).toInstant().toEpochMilli()

        val initialSettings = NotificationSettings(
            nicknames = listOf("chefe", "mestre"),
            lastNickname = "mestre",
            lastPhraseIndices = mapOf(NotificationCategory.REMINDER_ON_TIME.key to 1),
        )

        val onTime = AlfredNotificationFormatter.formatReminder(
            title = "pagar a luz",
            amount = 150.0,
            dueAtMillis = dueAtMillis,
            scope = FinanceScope.PERSONAL,
            isOneHourEarly = false,
            settings = initialSettings,
            now = now,
            random = Random(7),
        )

        assertTrue(onTime.title.startsWith("Boa tarde, "))
        assertEquals("Boa tarde, chefe", onTime.title)
        assertTrue(onTime.body.contains("14:00"))
        assertTrue(onTime.body.contains("pagar a luz"))
        assertTrue(onTime.body.contains("150,00"))
        assertNotEquals(1, onTime.chosenPhraseIndex)

        val oneHourBefore = AlfredNotificationFormatter.formatReminder(
            title = "pagar DAS",
            amount = 0.0,
            dueAtMillis = dueAtMillis,
            scope = FinanceScope.BUSINESS,
            isOneHourEarly = true,
            settings = initialSettings.copy(lastNickname = onTime.chosenNickname),
            now = now,
            random = Random(3),
        )

        assertEquals("Boa tarde, mestre", oneHourBefore.title)
        assertTrue(oneHourBefore.body.contains("14:00"))
        assertTrue(oneHourBefore.body.contains("pagar DAS"))
        assertTrue(oneHourBefore.body.contains("empresa"))
        assertTrue(!oneHourBefore.body.contains("R$"))
    }

    @Test
    fun formatDailySummary_returnsNullWhenEmptyAndUsesRealDataWhenPresent() {
        val now = ZonedDateTime.of(2026, 5, 18, 8, 0, 0, 0, zone)
        val settings = NotificationSettings()

        val empty = AlfredNotificationFormatter.formatDailySummary(
            transactions = emptyList(),
            reminders = emptyList(),
            settings = settings,
            now = now,
        )
        assertNull(empty)

        val todayTs = Timestamp(Date.from(now.plusHours(2).toInstant()))
        val tx = FinanceTransaction(
            id = "tx1",
            scope = FinanceScope.PERSONAL.wireValue,
            type = TransactionType.EXPENSE.wireValue,
            title = "Almoço",
            category = "Alimentação",
            amount = 48.0,
            monthKey = "2026-05",
            isPaid = true,
            createdAt = todayTs,
        )
        val rem = FinanceReminder(
            id = "rem1",
            scope = FinanceScope.PERSONAL.wireValue,
            title = "Internet",
            amount = 110.0,
            dueDateText = "Hoje 10:00",
            dueAt = todayTs,
            isCompleted = false,
        )

        val formatted = AlfredNotificationFormatter.formatDailySummary(
            transactions = listOf(tx),
            reminders = listOf(rem),
            settings = settings,
            now = now,
            random = Random(5),
        )
        assertNotNull(formatted)
        assertTrue(formatted!!.title.startsWith("Bom dia, "))
        assertTrue(formatted.body.contains("48,00"))
        assertTrue(formatted.body.contains("Internet"))
    }

    @Test
    fun formatWeeklySummary_returnsNullWhenEmptyAndIncludesRealTotalsWhenPresent() {
        val now = ZonedDateTime.of(2026, 5, 24, 9, 0, 0, 0, zone)
        val settings = NotificationSettings()

        assertNull(
            AlfredNotificationFormatter.formatWeeklySummary(
                transactions = emptyList(),
                reminders = emptyList(),
                goals = emptyList(),
                settings = settings,
                now = now,
            )
        )

        val txTs = Timestamp(Date.from(now.minusDays(2).toInstant()))
        val txIn = FinanceTransaction(
            id = "in1",
            scope = FinanceScope.BUSINESS.wireValue,
            type = TransactionType.INCOME.wireValue,
            title = "Projeto",
            category = "Vendas",
            amount = 2400.0,
            createdAt = txTs,
        )
        val txOut = FinanceTransaction(
            id = "out1",
            scope = FinanceScope.PERSONAL.wireValue,
            type = TransactionType.EXPENSE.wireValue,
            title = "Mercado",
            category = "Alimentação",
            amount = 320.0,
            createdAt = txTs,
        )

        val weekly = AlfredNotificationFormatter.formatWeeklySummary(
            transactions = listOf(txIn, txOut),
            reminders = emptyList(),
            goals = emptyList(),
            settings = settings,
            now = now,
            random = Random(11),
        )
        assertNotNull(weekly)
        assertTrue(weekly!!.title.startsWith("Bom dia, "))
        assertTrue(weekly.body.contains("2.400,00"))
        assertTrue(weekly.body.contains("320,00"))
    }

    @Test
    fun formatGoalMilestone_detects50_80_100AndSkipsAlreadyNotifiedThresholds() {
        val now = ZonedDateTime.of(2026, 5, 18, 20, 0, 0, 0, zone)
        val settings = NotificationSettings()

        val goalBelow50 = FinanceGoal(id = "g1", title = "Reserva", targetAmount = 1000.0, currentAmount = 490.0)
        assertNull(AlfredNotificationFormatter.formatGoalMilestone(goalBelow50, 0, settings, now))

        val goal55 = goalBelow50.copy(currentAmount = 550.0)
        val hit50 = AlfredNotificationFormatter.formatGoalMilestone(goal55, 0, settings, now, Random(1))
        assertNotNull(hit50)
        assertEquals(50, hit50!!.first)
        assertTrue(hit50.second.title.startsWith("Boa noite, "))
        assertTrue(hit50.second.body.contains("50%"))
        assertTrue(hit50.second.body.contains("Reserva"))

        // Calling again with previousNotifiedMilestone = 50 should NOT refire
        assertNull(AlfredNotificationFormatter.formatGoalMilestone(goal55, 50, settings, now))

        val goal82 = goalBelow50.copy(currentAmount = 820.0)
        val hit80 = AlfredNotificationFormatter.formatGoalMilestone(goal82, 50, settings, now, Random(2))
        assertNotNull(hit80)
        assertEquals(80, hit80!!.first)
        assertTrue(hit80.second.body.contains("80%"))

        val goal100 = goalBelow50.copy(currentAmount = 1000.0)
        val hit100 = AlfredNotificationFormatter.formatGoalMilestone(goal100, 80, settings, now, Random(3))
        assertNotNull(hit100)
        assertEquals(100, hit100!!.first)
        assertTrue(hit100.second.body.contains("100%"))
    }

    @Test
    fun summaryWorkScheduler_computesNextDailyAndWeeklyDelaysCorrectly() {
        val morning = ZonedDateTime.of(2026, 5, 18, 7, 0, 0, 0, zone) // Monday 07:00
        val sameDayDelay = SummaryWorkScheduler.millisUntilNextDaily(hour = 8, minute = 0, now = morning)
        assertEquals(3_600_000L, sameDayDelay)

        val evening = ZonedDateTime.of(2026, 5, 18, 21, 0, 0, 0, zone) // Monday 21:00
        val nextDayDelay = SummaryWorkScheduler.millisUntilNextDaily(hour = 8, minute = 0, now = evening)
        assertEquals(11L * 3_600_000L, nextDayDelay)

        val sundayBefore9 = ZonedDateTime.of(2026, 5, 24, 8, 0, 0, 0, zone) // Sunday 08:00
        assertEquals(3_600_000L, SummaryWorkScheduler.millisUntilNextWeeklySunday(hour = 9, minute = 0, now = sundayBefore9))

        val sundayAfter9 = ZonedDateTime.of(2026, 5, 24, 10, 0, 0, 0, zone) // Sunday 10:00
        assertEquals((7L * 24L - 1L) * 3_600_000L, SummaryWorkScheduler.millisUntilNextWeeklySunday(hour = 9, minute = 0, now = sundayAfter9))
    }
}
