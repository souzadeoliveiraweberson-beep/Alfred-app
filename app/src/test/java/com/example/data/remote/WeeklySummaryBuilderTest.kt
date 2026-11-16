package com.example.data.remote

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.google.firebase.Timestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

class WeeklySummaryBuilderTest {

    private val spZone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, spZone)

    private fun tsAtDaysOffset(days: Long): Timestamp {
        val instant = now.plusDays(days).toInstant()
        return Timestamp(Date.from(instant))
    }

    @Test
    fun build_computesRealSevenDayTotalsTopThreeCategoriesGoalsAndUpcomingReminders() {
        val transactions = listOf(
            FinanceTransaction(
                id = "t1", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.INCOME.wireValue,
                title = "Salário", category = "Salário", amount = 5000.0, createdAt = tsAtDaysOffset(-1)
            ),
            FinanceTransaction(
                id = "t2", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.EXPENSE.wireValue,
                title = "Supermercado", category = "Alimentação", amount = 600.0, createdAt = tsAtDaysOffset(-2)
            ),
            FinanceTransaction(
                id = "t3", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.EXPENSE.wireValue,
                title = "Uber", category = "Transporte", amount = 180.0, createdAt = tsAtDaysOffset(-3)
            ),
            FinanceTransaction(
                id = "t4", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.EXPENSE.wireValue,
                title = "Energia", category = "Moradia", amount = 220.0, createdAt = tsAtDaysOffset(0)
            ),
            FinanceTransaction(
                id = "t5", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.EXPENSE.wireValue,
                title = "Cinema", category = "Lazer", amount = 90.0, createdAt = tsAtDaysOffset(-4)
            ),
            // Outside 7 days window -> must be excluded
            FinanceTransaction(
                id = "t6", scope = FinanceScope.PERSONAL.wireValue, type = TransactionType.EXPENSE.wireValue,
                title = "Compra Antiga", category = "Outros", amount = 9999.0, createdAt = tsAtDaysOffset(-12)
            ),
            // Business scope -> must be excluded from Personal summary
            FinanceTransaction(
                id = "t7", scope = FinanceScope.BUSINESS.wireValue, type = TransactionType.INCOME.wireValue,
                title = "Venda PJ", category = "Vendas", amount = 8888.0, createdAt = tsAtDaysOffset(-1)
            ),
        )
        val reminders = listOf(
            FinanceReminder(
                id = "r1", scope = FinanceScope.PERSONAL.wireValue, title = "Pagar Internet",
                amount = 119.90, dueDateText = "Amanhã, 14:00", dueAt = tsAtDaysOffset(1), isCompleted = false
            ),
            FinanceReminder(
                id = "r2", scope = FinanceScope.PERSONAL.wireValue, title = "Lembrete Distante",
                amount = 50.0, dueDateText = "Em 15 dias", dueAt = tsAtDaysOffset(15), isCompleted = false
            ),
        )
        val goals = listOf(
            FinanceGoal(
                id = "g1", scope = FinanceScope.PERSONAL.wireValue, title = "Reserva",
                targetAmount = 1000.0, currentAmount = 500.0
            )
        )

        val report = WeeklySummaryBuilder.build(
            scope = FinanceScope.PERSONAL,
            transactions = transactions,
            reminders = reminders,
            goals = goals,
            now = now,
        )

        assertTrue(report.text.contains("5.000,00"))
        assertTrue(report.text.contains("1.090,00"))
        assertTrue(report.text.contains("Alimentação"))
        assertTrue(report.text.contains("Moradia"))
        assertTrue(report.text.contains("Transporte"))
        assertFalse(report.text.contains("Lazer")) // Top 3 only
        assertFalse(report.text.contains("9.999,00"))
        assertFalse(report.text.contains("8.888,00"))
        assertTrue(report.text.contains("Reserva"))
        assertTrue(report.text.contains("50%"))
        assertTrue(report.text.contains("Pagar Internet"))
        assertFalse(report.text.contains("Lembrete Distante"))
    }

    @Test
    fun build_emptyData_neverInventsValues() {
        val report = WeeklySummaryBuilder.build(
            scope = FinanceScope.BUSINESS,
            transactions = emptyList(),
            reminders = emptyList(),
            goals = emptyList(),
            now = now,
        )
        assertTrue(report.text.contains("Caixa da Empresa"))
        assertTrue(report.text.contains("nenhum gasto registrado"))
        assertTrue(report.text.contains("nenhuma meta cadastrada"))
        assertTrue(report.text.contains("nenhum lembrete agendado"))
    }
}
