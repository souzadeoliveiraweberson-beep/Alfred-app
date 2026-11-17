package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthHelperTest {

    @Test
    fun availableMonthsFor_returns25DynamicMonthsAroundCenter() {
        val center = YearMonth.of(2026, 11)
        val months = MonthHelper.availableMonthsFor(center)
        assertEquals(25, months.size)
        assertEquals("2025-11", months.first().key)
        assertEquals("2026-11", months[12].key)
        assertEquals("2027-11", months.last().key)
        assertTrue(months.any { it.key == "2027-01" && it.fullLabel == "Janeiro 2027" })
    }

    @Test
    fun currentMonthKey_formatsYearAndTwoDigitMonth() {
        assertEquals("2027-02", MonthHelper.currentMonthKey(LocalDate.of(2027, 2, 14)))
        assertEquals("2026-12", MonthHelper.currentMonthKey(LocalDate.of(2026, 12, 1)))
    }
}
