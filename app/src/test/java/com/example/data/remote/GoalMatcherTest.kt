package com.example.data.remote

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoalMatcherTest {

    private val sampleGoals = listOf(
        FinanceGoal(id = "g1", scope = FinanceScope.PERSONAL.wireValue, title = "iPhone 17 Pro", targetAmount = 9000.0),
        FinanceGoal(id = "g2", scope = FinanceScope.PERSONAL.wireValue, title = "Reserva de Emergência", targetAmount = 20000.0),
        FinanceGoal(id = "g3", scope = FinanceScope.BUSINESS.wireValue, title = "Capital de Giro PJ", targetAmount = 50000.0),
    )

    @Test
    fun findBestMatch_matchesPortugueseAccentsAndPartialQuery() {
        val match = GoalMatcher.findBestMatch("meta do iPhone", sampleGoals, FinanceScope.PERSONAL)
        assertNotNull(match)
        assertEquals("g1", match!!.id)

        val emergencyMatch = GoalMatcher.findBestMatch("emergencia", sampleGoals, FinanceScope.PERSONAL)
        assertNotNull(emergencyMatch)
        assertEquals("g2", emergencyMatch!!.id)
    }

    @Test
    fun findBestMatch_findsAcrossScopeWhenUnambiguousAndReturnsNullWhenUnknown() {
        val bizMatch = GoalMatcher.findBestMatch("capital de giro", sampleGoals, FinanceScope.PERSONAL)
        assertNotNull(bizMatch)
        assertEquals("g3", bizMatch!!.id)

        val unknown = GoalMatcher.findBestMatch("compra de barco", sampleGoals, FinanceScope.PERSONAL)
        assertNull(unknown)
    }
}
