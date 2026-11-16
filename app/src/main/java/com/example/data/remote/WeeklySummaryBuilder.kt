package com.example.data.remote

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.ui.components.CurrencyFormatter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeeklySummaryReport(
    val scope: FinanceScope,
    val text: String,
    val badge: String,
)

object WeeklySummaryBuilder {

    private val shortDayFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

    fun build(
        scope: FinanceScope,
        transactions: List<FinanceTransaction>,
        reminders: List<FinanceReminder>,
        goals: List<FinanceGoal>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): WeeklySummaryReport {
        val zone = now.zone
        val today = now.toLocalDate()
        val weekStart = today.minusDays(6)

        val scopeTx = transactions.filter { it.scope == scope.wireValue }
        val weekTx = scopeTx.filter { tx ->
            val txDate = txLocalDate(tx, zone)
            txDate != null && !txDate.isBefore(weekStart) && !txDate.isAfter(today)
        }

        val weekIncome = weekTx
            .filter { it.type == TransactionType.INCOME.wireValue }
            .sumOf { it.amount }
        val weekExpenses = weekTx
            .filter { it.type == TransactionType.EXPENSE.wireValue }
            .sumOf { it.amount }

        val topCategories = weekTx
            .filter { it.type == TransactionType.EXPENSE.wireValue }
            .groupBy { it.category.ifBlank { "Outros" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(3)

        val scopeGoals = goals.filter { it.scope == scope.wireValue }

        val nowMs = now.toInstant().toEpochMilli()
        val nextWeekMs = now.plusDays(7).toInstant().toEpochMilli()
        val upcomingReminders = reminders
            .filter { rem ->
                rem.scope == scope.wireValue &&
                    !rem.isCompleted &&
                    rem.dueAt != null &&
                    rem.dueAt.toDate().time in nowMs..nextWeekMs
            }
            .sortedBy { it.dueAt?.toDate()?.time ?: Long.MAX_VALUE }

        val scopeLabel = if (scope == FinanceScope.BUSINESS) "Caixa da Empresa" else "Finanças Pessoais"
        val rangeLabel = "${weekStart.format(shortDayFmt)} a ${today.format(shortDayFmt)}"

        val sb = StringBuilder()
        sb.appendLine("Resumo da semana ($scopeLabel • $rangeLabel):")
        sb.appendLine("• Entradas: ${CurrencyFormatter.formatBrl(weekIncome)}")
        sb.appendLine("• Saídas: ${CurrencyFormatter.formatBrl(weekExpenses)}")

        if (topCategories.isNotEmpty()) {
            val cats = topCategories.joinToString(", ") {
                "${it.key} (${CurrencyFormatter.formatBrl(it.value)})"
            }
            sb.appendLine("• Maiores gastos: $cats")
        } else {
            sb.appendLine("• Maiores gastos: nenhum gasto registrado nos últimos 7 dias.")
        }

        if (scopeGoals.isNotEmpty()) {
            val goalsText = scopeGoals.take(3).joinToString("; ") { g ->
                val pct = if (g.targetAmount > 0.0) {
                    ((g.currentAmount / g.targetAmount) * 100).toInt().coerceIn(0, 999)
                } else 0
                "${g.title}: ${CurrencyFormatter.formatBrl(g.currentAmount)}/${CurrencyFormatter.formatBrl(g.targetAmount)} ($pct%)"
            }
            sb.appendLine("• Metas: $goalsText")
        } else {
            sb.appendLine("• Metas: nenhuma meta cadastrada neste escopo.")
        }

        if (upcomingReminders.isNotEmpty()) {
            val remText = upcomingReminders.take(4).joinToString("; ") { rem ->
                val amt = if (rem.amount > 0.0) " (${CurrencyFormatter.formatBrl(rem.amount)})" else ""
                "${rem.title}$amt — ${rem.dueDateText}"
            }
            sb.append("• Próximos 7 dias: $remText")
        } else {
            val undatedOpen = reminders.count { it.scope == scope.wireValue && !it.isCompleted && it.dueAt == null }
            if (undatedOpen > 0) {
                sb.append("• Próximos 7 dias: nenhum lembrete com horário definido ($undatedOpen sem data).")
            } else {
                sb.append("• Próximos 7 dias: nenhum lembrete agendado.")
            }
        }

        return WeeklySummaryReport(
            scope = scope,
            text = sb.toString().trim(),
            badge = "${scope.labelPt} • Resumo semanal ($rangeLabel)",
        )
    }

    private fun txLocalDate(tx: FinanceTransaction, zone: ZoneId): java.time.LocalDate? {
        return tx.createdAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
    }
}
