package com.example.data.remote

import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.ui.components.CurrencyFormatter
import com.example.ui.viewmodel.ScopeSummary
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class LocalQueryReply(
    val scope: FinanceScope,
    val replyText: String,
    val badge: String,
)

object LocalQueryResponder {

    fun tryAnswer(
        rawInput: String,
        activeScope: FinanceScope,
        personalSummary: ScopeSummary,
        businessSummary: ScopeSummary,
        allTransactions: List<FinanceTransaction>,
        selectedMonthKey: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): LocalQueryReply? {
        val normalized = normalize(rawInput)
        if (normalized.isBlank()) return null

        val targetScope = resolveRequestedScope(normalized, activeScope)
        val summary = if (targetScope == FinanceScope.BUSINESS) businessSummary else personalSummary
        val scopeLabel = if (targetScope == FinanceScope.BUSINESS) "caixa da empresa" else "suas finanças pessoais"

        // 1) "minhas metas" / "metas" / "reservas"
        if (isGoalsQuery(normalized)) {
            val goals = summary.scopeGoals
            if (goals.isEmpty()) {
                return LocalQueryReply(
                    scope = targetScope,
                    replyText = "Senhor, ainda não há metas cadastradas em $scopeLabel. Posso criar uma agora se me disser o objetivo e o valor.",
                    badge = "${targetScope.labelPt} • Consulta instantânea",
                )
            }
            val details = goals.take(4).joinToString("; ") { goal ->
                val pct = if (goal.targetAmount > 0.0) {
                    ((goal.currentAmount / goal.targetAmount) * 100).toInt().coerceIn(0, 999)
                } else 0
                "${goal.title}: ${CurrencyFormatter.formatBrl(goal.currentAmount)} de ${CurrencyFormatter.formatBrl(goal.targetAmount)} ($pct%)"
            }
            return LocalQueryReply(
                scope = targetScope,
                replyText = "Suas metas em $scopeLabel estão assim, senhor: $details.",
                badge = "${targetScope.labelPt} • ${goals.size} meta(s)",
            )
        }

        // 2) "o que tenho a pagar" / contas pendentes / lembretes
        if (isPayablesQuery(normalized)) {
            val unpaidTx = summary.monthTransactions.filter {
                it.type == TransactionType.EXPENSE.wireValue && !it.isPaid
            }
            val openReminders = summary.scopeReminders.filter { !it.isCompleted }

            if (unpaidTx.isEmpty() && openReminders.isEmpty()) {
                return LocalQueryReply(
                    scope = targetScope,
                    replyText = "Tudo em dia em $scopeLabel, senhor. Não há despesas pendentes nem lembretes em aberto.",
                    badge = "${targetScope.labelPt} • Sem pendências",
                )
            }

            val parts = mutableListOf<String>()
            if (unpaidTx.isNotEmpty()) {
                val txTotal = unpaidTx.sumOf { it.amount }
                val list = unpaidTx.take(3).joinToString(", ") {
                    "${it.title} (${CurrencyFormatter.formatBrl(it.amount)})"
                }
                parts.add("despesas pendentes somando ${CurrencyFormatter.formatBrl(txTotal)} ($list)")
            }
            if (openReminders.isNotEmpty()) {
                val remList = openReminders.take(3).joinToString(", ") {
                    val amt = if (it.amount > 0.0) " • ${CurrencyFormatter.formatBrl(it.amount)}" else ""
                    "${it.title} (${it.dueDateText}$amt)"
                }
                parts.add("lembretes abertos: $remList")
            }
            return LocalQueryReply(
                scope = targetScope,
                replyText = "Senhor, em $scopeLabel você tem ${parts.joinToString(" e ")}.",
                badge = "${targetScope.labelPt} • Pendências",
            )
        }

        // 3) "quanto gastei hoje / esta semana / este mês"
        if (isExpensesQuery(normalized)) {
            val scopeExpenses = allTransactions.filter {
                it.scope == targetScope.wireValue && it.type == TransactionType.EXPENSE.wireValue
            }
            val zone = now.zone
            return when {
                containsAny(normalized, "hoje", "neste dia") -> {
                    val today = now.toLocalDate()
                    val todayExpenses = scopeExpenses.filter { tx ->
                        txLocalDate(tx, zone, selectedMonthKey) == today
                    }
                    val total = todayExpenses.sumOf { it.amount }
                    val reply = if (total <= 0.0) {
                        "Nenhum gasto registrado hoje em $scopeLabel, senhor."
                    } else {
                        "Hoje você gastou ${CurrencyFormatter.formatBrl(total)} em $scopeLabel (${todayExpenses.size} lançamento(s))."
                    }
                    LocalQueryReply(
                        scope = targetScope,
                        replyText = reply,
                        badge = "${targetScope.labelPt} • Gastos hoje",
                    )
                }

                containsAny(normalized, "esta semana", "essa semana", "semana") -> {
                    val startOfWeek = now.toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val endOfWeek = startOfWeek.plusDays(6)
                    val weekExpenses = scopeExpenses.filter { tx ->
                        val date = txLocalDate(tx, zone, selectedMonthKey)
                        date != null && !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
                    }
                    val total = weekExpenses.sumOf { it.amount }
                    val reply = if (total <= 0.0) {
                        "Não encontrei gastos registrados nesta semana em $scopeLabel, senhor."
                    } else {
                        "Nesta semana você acumulou ${CurrencyFormatter.formatBrl(total)} em gastos em $scopeLabel (${weekExpenses.size} lançamento(s))."
                    }
                    LocalQueryReply(
                        scope = targetScope,
                        replyText = reply,
                        badge = "${targetScope.labelPt} • Gastos da semana",
                    )
                }

                else -> {
                    val monthTotal = summary.paidExpenses + summary.pendingExpenses
                    val topCategory = summary.expenseSlices.firstOrNull()
                    val topNote = if (topCategory != null) {
                        " O maior peso foi ${topCategory.category} (${CurrencyFormatter.formatBrl(topCategory.amount)})."
                    } else ""
                    val reply = if (monthTotal <= 0.0) {
                        "Ainda não há gastos registrados neste mês em $scopeLabel, senhor."
                    } else {
                        "Neste mês você teve ${CurrencyFormatter.formatBrl(monthTotal)} em gastos em $scopeLabel (${CurrencyFormatter.formatBrl(summary.paidExpenses)} pagos e ${CurrencyFormatter.formatBrl(summary.pendingExpenses)} a pagar).$topNote"
                    }
                    LocalQueryReply(
                        scope = targetScope,
                        replyText = reply,
                        badge = "${targetScope.labelPt} • Gastos do mês",
                    )
                }
            }
        }

        // 4) "quanto recebi" / "quanto faturei"
        if (isIncomeQuery(normalized)) {
            val totalIncome = summary.monthIncome + summary.pendingIncome
            val reply = if (totalIncome <= 0.0) {
                "Ainda não há entradas registradas neste mês em $scopeLabel, senhor."
            } else {
                "Neste mês entraram ${CurrencyFormatter.formatBrl(summary.monthIncome)} em $scopeLabel, com ${CurrencyFormatter.formatBrl(summary.pendingIncome)} ainda a receber."
            }
            return LocalQueryReply(
                scope = targetScope,
                replyText = reply,
                badge = "${targetScope.labelPt} • Receitas do mês",
            )
        }

        // 5) "saldo"
        if (isBalanceQuery(normalized)) {
            val balanceText = CurrencyFormatter.formatBrl(summary.totalBalance)
            val incomeText = CurrencyFormatter.formatBrl(summary.monthIncome)
            val paidText = CurrencyFormatter.formatBrl(summary.paidExpenses)
            return LocalQueryReply(
                scope = targetScope,
                replyText = "Senhor, seu saldo atual em $scopeLabel é de $balanceText. No mês foram $incomeText em receitas recebidas e $paidText em gastos pagos.",
                badge = "${targetScope.labelPt} • Saldo atual",
            )
        }

        return null
    }

    private fun resolveRequestedScope(normalized: String, activeScope: FinanceScope): FinanceScope {
        val mentionsBusiness = containsAny(
            normalized,
            "empresa", "pj", "negocio", "caixa da empresa", "faturei", "faturamento"
        )
        val mentionsPersonal = containsAny(normalized, "pessoal", "financas pessoais", "pf", "casa")
        return when {
            mentionsBusiness && !mentionsPersonal -> FinanceScope.BUSINESS
            mentionsPersonal && !mentionsBusiness -> FinanceScope.PERSONAL
            else -> activeScope
        }
    }

    private fun isGoalsQuery(normalized: String): Boolean {
        val hasCreateIntent = containsAny(normalized, "criar", "cria", "nova meta", "adicionar", "definir")
        if (hasCreateIntent) return false
        return containsAny(normalized, "minhas metas", "minha meta", "metas", "como estao minhas metas")
    }

    private fun isPayablesQuery(normalized: String): Boolean =
        containsAny(
            normalized,
            "o que tenho a pagar",
            "contas a pagar",
            "tenho a pagar",
            "meus lembretes",
            "lembretes abertos",
            "pendencias",
            "contas pendentes"
        )

    private fun isExpensesQuery(normalized: String): Boolean =
        containsAny(
            normalized,
            "quanto gastei",
            "quanto eu gastei",
            "meus gastos",
            "gastos de hoje",
            "gastos da semana",
            "gastos do mes"
        )

    private fun isIncomeQuery(normalized: String): Boolean =
        containsAny(
            normalized,
            "quanto recebi",
            "quanto eu recebi",
            "quanto faturei",
            "minhas receitas",
            "meu faturamento"
        )

    private fun isBalanceQuery(normalized: String): Boolean {
        if (normalized == "saldo" || normalized == "meu saldo" || normalized == "saldo atual") return true
        return containsAny(
            normalized,
            "qual meu saldo",
            "qual e o meu saldo",
            "qual o saldo",
            "qual e meu saldo",
            "quanto tenho de saldo",
            "como esta meu saldo",
            "qual o caixa",
            "saldo da empresa",
            "saldo pessoal"
        )
    }

    private fun txLocalDate(
        tx: FinanceTransaction,
        zone: ZoneId,
        fallbackMonthKey: String,
    ): java.time.LocalDate? {
        tx.createdAt?.toDate()?.let { date ->
            return date.toInstant().atZone(zone).toLocalDate()
        }
        val key = tx.monthKey.ifBlank { fallbackMonthKey }
        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val day = tx.dayOfMonth.coerceIn(1, java.time.YearMonth.of(year, month).lengthOfMonth())
        return java.time.LocalDate.of(year, month, day)
    }

    private fun containsAny(normalized: String, vararg phrases: String): Boolean =
        phrases.any { phrase ->
            val escaped = Regex.escape(phrase)
            Regex("""(?<!\w)$escaped(?!\w)""").containsMatchIn(normalized)
        }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[?!.,]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
