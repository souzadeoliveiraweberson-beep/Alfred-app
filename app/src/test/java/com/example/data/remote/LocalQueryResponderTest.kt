package com.example.data.remote

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.ui.viewmodel.CategorySlice
import com.example.ui.viewmodel.ScopeSummary
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

class LocalQueryResponderTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = ZonedDateTime.of(2026, 11, 18, 15, 0, 0, 0, zone) // Quarta-feira

    private fun summaryFor(scope: FinanceScope, txList: List<FinanceTransaction>): ScopeSummary {
        val filtered = txList.filter { it.scope == scope.wireValue }
        return ScopeSummary(
            scope = scope,
            totalBalance = 2450.0,
            monthIncome = 4000.0,
            paidExpenses = 1550.0,
            pendingExpenses = 300.0,
            pendingIncome = 500.0,
            creditCardTotal = 450.0,
            netMonthResult = 2450.0,
            monthTransactions = filtered,
            scopeAccounts = emptyList(),
            scopeReminders = listOf(
                FinanceReminder(
                    id = "r1",
                    scope = scope.wireValue,
                    title = "Conta de Luz",
                    amount = 180.0,
                    dueDateText = "Amanhã, 14:00",
                    isCompleted = false,
                )
            ),
            scopeGoals = listOf(
                FinanceGoal(
                    id = "g1",
                    scope = scope.wireValue,
                    title = "Reserva",
                    targetAmount = 10000.0,
                    currentAmount = 3500.0,
                )
            ),
            expenseSlices = listOf(CategorySlice("Alimentação", 500.0, 0.5f)),
            incomeSlices = emptyList(),
        )
    }

    private fun sampleTransactions(): List<FinanceTransaction> {
        val todayTs = Timestamp(Date.from(now.minusHours(2).toInstant()))
        val mondayTs = Timestamp(Date.from(now.minusDays(2).toInstant()))
        val lastWeekTs = Timestamp(Date.from(now.minusDays(8).toInstant()))
        return listOf(
            FinanceTransaction(
                id = "t1",
                scope = FinanceScope.PERSONAL.wireValue,
                type = TransactionType.EXPENSE.wireValue,
                title = "Almoço",
                category = "Alimentação",
                amount = 45.0,
                monthKey = "2026-11",
                isPaid = true,
                createdAt = todayTs,
            ),
            FinanceTransaction(
                id = "t2",
                scope = FinanceScope.PERSONAL.wireValue,
                type = TransactionType.EXPENSE.wireValue,
                title = "Uber",
                category = "Transporte",
                amount = 35.0,
                monthKey = "2026-11",
                isPaid = true,
                createdAt = mondayTs,
            ),
            FinanceTransaction(
                id = "t3",
                scope = FinanceScope.PERSONAL.wireValue,
                type = TransactionType.EXPENSE.wireValue,
                title = "Internet",
                category = "Moradia",
                amount = 120.0,
                monthKey = "2026-11",
                isPaid = false,
                createdAt = lastWeekTs,
            ),
        )
    }

    @Test
    fun answersBalancePayablesIncomeAndGoalsLocally() {
        val txs = sampleTransactions()
        val personal = summaryFor(FinanceScope.PERSONAL, txs)
        val business = summaryFor(FinanceScope.BUSINESS, txs)

        val saldo = LocalQueryResponder.tryAnswer("saldo", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(saldo)
        assertTrue(saldo!!.replyText.contains("2.450,00"))

        val income = LocalQueryResponder.tryAnswer("quanto recebi", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(income)
        assertTrue(income!!.replyText.contains("4.000,00"))

        val payables = LocalQueryResponder.tryAnswer("o que tenho a pagar", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(payables)
        assertTrue(payables!!.replyText.contains("Internet"))
        assertTrue(payables.replyText.contains("Conta de Luz"))

        val goals = LocalQueryResponder.tryAnswer("minhas metas", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(goals)
        assertTrue(goals!!.replyText.contains("Reserva"))
        assertTrue(goals.replyText.contains("35%"))
    }

    @Test
    fun answersExpensesTodayWeekAndMonthLocally() {
        val txs = sampleTransactions()
        val personal = summaryFor(FinanceScope.PERSONAL, txs)
        val business = summaryFor(FinanceScope.BUSINESS, txs)

        val today = LocalQueryResponder.tryAnswer("quanto gastei hoje", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(today)
        assertEquals(FinanceScope.PERSONAL, today!!.scope)
        assertTrue(today.replyText.contains("45,00"))

        val week = LocalQueryResponder.tryAnswer("quanto gastei esta semana", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(week)
        assertTrue(week!!.replyText.contains("80,00"))

        val month = LocalQueryResponder.tryAnswer("quanto gastei este mês", FinanceScope.PERSONAL, personal, business, txs, "2026-11", now)
        assertNotNull(month)
        assertTrue(month!!.replyText.contains("1.850,00"))
    }

    @Test
    fun openEndedAdvicePrompt_returnsNullToUseGemini() {
        val txs = sampleTransactions()
        val personal = summaryFor(FinanceScope.PERSONAL, txs)
        val business = summaryFor(FinanceScope.BUSINESS, txs)

        assertNull(
            LocalQueryResponder.tryAnswer(
                "Como posso melhorar o lucro da minha empresa?",
                FinanceScope.BUSINESS,
                personal,
                business,
                txs,
                "2026-11",
                now,
            )
        )
    }
}
