package com.example.notifications

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.ui.components.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

enum class NotificationCategory(val key: String) {
    REMINDER_ON_TIME("reminder_on_time"),
    REMINDER_ONE_HOUR_BEFORE("reminder_one_hour_before"),
    DAILY_SUMMARY("daily_summary"),
    WEEKLY_SUMMARY("weekly_summary"),
    GOAL_MILESTONE("goal_milestone"),
    TEST("test"),
}

data class FormattedNotification(
    val title: String,
    val body: String,
    val category: NotificationCategory,
    val chosenNickname: String,
    val chosenPhraseIndex: Int,
)

object AlfredNotificationFormatter {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))
    private val dayMonthFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

    fun greetingForHour(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return when (h) {
            in 0..5 -> "Boa madrugada"
            in 6..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
    }

    fun pickNickname(
        nicknames: List<String>,
        lastNickname: String,
        random: Random = Random.Default,
    ): String {
        val pool = nicknames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { NotificationSettings.DEFAULT_NICKNAMES }
        if (pool.size == 1) return pool.first()
        val candidates = pool.filterNot { it.equals(lastNickname.trim(), ignoreCase = true) }
            .ifEmpty { pool }
        return candidates[random.nextInt(candidates.size)]
    }

    fun pickTemplateIndex(
        size: Int,
        lastIndex: Int?,
        random: Random = Random.Default,
    ): Int {
        require(size > 0) { "Template list must not be empty" }
        if (size == 1) return 0
        val candidates = (0 until size).filter { it != lastIndex }
        return candidates[random.nextInt(candidates.size)]
    }

    fun formatReminder(
        title: String,
        amount: Double,
        dueAtMillis: Long,
        scope: FinanceScope,
        isOneHourEarly: Boolean,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): FormattedNotification {
        val zone = now.zone
        val dueZoned = Instant.ofEpochMilli(dueAtMillis).atZone(zone)
        val timeText = dueZoned.format(timeFmt)
        val dayText = relativeDayLabel(dueZoned.toLocalDate(), now.toLocalDate())
        val cleanTitle = title.trim().ifBlank { "compromisso financeiro" }
        val amountSuffix = if (amount > 0.0) " (${CurrencyFormatter.formatBrl(amount)})" else ""
        val scopeHint = if (scope == FinanceScope.BUSINESS) " na empresa" else ""

        val category = if (isOneHourEarly) {
            NotificationCategory.REMINDER_ONE_HOUR_BEFORE
        } else {
            NotificationCategory.REMINDER_ON_TIME
        }

        val templates = if (isOneHourEarly) {
            listOf(
                "Falta 1 hora! $dayText, às $timeText, você tem: $cleanTitle$amountSuffix$scopeHint.",
                "Aviso antecipado: daqui a 1 hora ($dayText às $timeText) vence $cleanTitle$amountSuffix$scopeHint.",
                "De olho no relógio — em 1 hora ($timeText) está marcado: $cleanTitle$amountSuffix$scopeHint.",
                "Só passando antes: às $timeText ($dayText) você tem $cleanTitle$amountSuffix$scopeHint.",
                "Organizei aqui: falta 1 hora para $cleanTitle$amountSuffix às $timeText$scopeHint.",
            )
        } else {
            listOf(
                "Lembrando que agora, às $timeText, você tem: $cleanTitle$amountSuffix$scopeHint.",
                "Chegou a hora ($timeText): $cleanTitle$amountSuffix$scopeHint. Já deixei no radar para não passar.",
                "Às $timeText em ponto: $cleanTitle$amountSuffix$scopeHint. Se já resolveu, marque como concluído.",
                "Passando para avisar que agora, às $timeText, vence: $cleanTitle$amountSuffix$scopeHint.",
                "No seu radar das $timeText: $cleanTitle$amountSuffix$scopeHint. Tudo pronto por aí?",
            )
        }

        val chosenNickname = pickNickname(settings.nicknames, settings.lastNickname, random)
        val lastIdx = settings.lastPhraseIndices[category.key]
        val chosenIdx = pickTemplateIndex(templates.size, lastIdx, random)
        val greeting = greetingForHour(now.hour)

        return FormattedNotification(
            title = "$greeting, $chosenNickname",
            body = templates[chosenIdx],
            category = category,
            chosenNickname = chosenNickname,
            chosenPhraseIndex = chosenIdx,
        )
    }

    fun formatDailySummary(
        transactions: List<FinanceTransaction>,
        reminders: List<FinanceReminder>,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): FormattedNotification? {
        val zone = now.zone
        val today = now.toLocalDate()
        val monthKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.US))

        val todayTx = transactions.filter { tx ->
            tx.createdAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate() == today
        }
        val todayIncome = todayTx
            .filter { it.type == TransactionType.INCOME.wireValue }
            .sumOf { it.amount }
        val todayExpense = todayTx
            .filter { it.type == TransactionType.EXPENSE.wireValue }
            .sumOf { it.amount }

        val dueToday = reminders.filter { rem ->
            !rem.isCompleted &&
                rem.dueAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate() == today
        }.sortedBy { it.dueAt?.toDate()?.time ?: Long.MAX_VALUE }

        val overdue = reminders.filter { rem ->
            !rem.isCompleted &&
                rem.dueAt != null &&
                rem.dueAt.toDate().toInstant().atZone(zone).toLocalDate().isBefore(today)
        }

        val pendingPayablesMonth = transactions.filter { tx ->
            tx.monthKey == monthKey &&
                tx.type == TransactionType.EXPENSE.wireValue &&
                !tx.isPaid
        }
        val pendingTotal = pendingPayablesMonth.sumOf { it.amount }

        val hasRelevantData = todayTx.isNotEmpty() ||
            dueToday.isNotEmpty() ||
            overdue.isNotEmpty() ||
            pendingPayablesMonth.isNotEmpty()
        if (!hasRelevantData) return null

        val remindersPiece = when {
            dueToday.isNotEmpty() -> {
                val first = dueToday.first()
                val firstTime = first.dueAt?.toDate()?.toInstant()?.atZone(zone)?.format(timeFmt)
                val timePart = if (firstTime != null) " às $firstTime" else ""
                if (dueToday.size == 1) {
                    "1 lembrete hoje (${first.title}$timePart)"
                } else {
                    "${dueToday.size} lembretes hoje (próximo: ${first.title}$timePart)"
                }
            }
            overdue.isNotEmpty() -> "${overdue.size} lembrete(s) pendente(s) de dias anteriores"
            else -> "nenhum vencimento agendado para hoje"
        }

        val movementPiece = when {
            todayExpense > 0.0 && todayIncome > 0.0 ->
                "hoje já entraram ${CurrencyFormatter.formatBrl(todayIncome)} e saíram ${CurrencyFormatter.formatBrl(todayExpense)}"
            todayExpense > 0.0 ->
                "gastos registrados hoje somam ${CurrencyFormatter.formatBrl(todayExpense)}"
            todayIncome > 0.0 ->
                "entradas de hoje somam ${CurrencyFormatter.formatBrl(todayIncome)}"
            pendingTotal > 0.0 ->
                "há ${pendingPayablesMonth.size} conta(s) em aberto no mês (${CurrencyFormatter.formatBrl(pendingTotal)})"
            else -> "movimentação tranquila até agora"
        }

        val templates = listOf(
            "Panorama do dia: $remindersPiece; $movementPiece.",
            "Atualizando seu radar de hoje: $movementPiece, com $remindersPiece.",
            "Deixei tudo separado para hoje: $remindersPiece e $movementPiece.",
            "Resumo rápido no caixa: $movementPiece. Na agenda, $remindersPiece.",
            "Para você começar alinhado: $remindersPiece — e $movementPiece.",
        )

        val chosenNickname = pickNickname(settings.nicknames, settings.lastNickname, random)
        val lastIdx = settings.lastPhraseIndices[NotificationCategory.DAILY_SUMMARY.key]
        val chosenIdx = pickTemplateIndex(templates.size, lastIdx, random)
        val greeting = greetingForHour(now.hour)

        return FormattedNotification(
            title = "$greeting, $chosenNickname",
            body = templates[chosenIdx],
            category = NotificationCategory.DAILY_SUMMARY,
            chosenNickname = chosenNickname,
            chosenPhraseIndex = chosenIdx,
        )
    }

    fun formatWeeklySummary(
        transactions: List<FinanceTransaction>,
        reminders: List<FinanceReminder>,
        goals: List<FinanceGoal>,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): FormattedNotification? {
        val zone = now.zone
        val today = now.toLocalDate()
        val weekStart = today.minusDays(6)

        val weekTx = transactions.filter { tx ->
            val d = tx.createdAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
            d != null && !d.isBefore(weekStart) && !d.isAfter(today)
        }
        val weekIncome = weekTx
            .filter { it.type == TransactionType.INCOME.wireValue }
            .sumOf { it.amount }
        val weekExpense = weekTx
            .filter { it.type == TransactionType.EXPENSE.wireValue }
            .sumOf { it.amount }

        val topCategory = weekTx
            .filter { it.type == TransactionType.EXPENSE.wireValue }
            .groupBy { it.category.ifBlank { "Outros" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }

        val nowMs = now.toInstant().toEpochMilli()
        val nextWeekMs = now.plusDays(7).toInstant().toEpochMilli()
        val upcomingReminders = reminders.filter { rem ->
            !rem.isCompleted &&
                rem.dueAt != null &&
                rem.dueAt.toDate().time in nowMs..nextWeekMs
        }

        val hasData = weekTx.isNotEmpty() || upcomingReminders.isNotEmpty() || goals.isNotEmpty()
        if (!hasData) return null

        val inText = CurrencyFormatter.formatBrl(weekIncome)
        val outText = CurrencyFormatter.formatBrl(weekExpense)
        val topCatPiece = if (topCategory != null) {
            "maior gasto em ${topCategory.key} (${CurrencyFormatter.formatBrl(topCategory.value)})"
        } else {
            "sem categoria de gasto relevante nos últimos 7 dias"
        }
        val remPiece = if (upcomingReminders.isNotEmpty()) {
            "${upcomingReminders.size} lembrete(s) nos próximos 7 dias"
        } else {
            "agenda livre nos próximos 7 dias"
        }

        val templates = listOf(
            "Fechamento da semana: entraram $inText, saíram $outText, $topCatPiece e $remPiece.",
            "Balanço dos últimos 7 dias: receitas de $inText contra $outText em despesas; $topCatPiece.",
            "Seu domingo organizado: na semana entraram $inText e saíram $outText, com $remPiece.",
            "Resumo semanal no radar: $outText de saídas, $inText de entradas ($topCatPiece) e $remPiece.",
            "Passando o pente fino da semana: $inText em entradas, $outText em saídas e $remPiece.",
        )

        val chosenNickname = pickNickname(settings.nicknames, settings.lastNickname, random)
        val lastIdx = settings.lastPhraseIndices[NotificationCategory.WEEKLY_SUMMARY.key]
        val chosenIdx = pickTemplateIndex(templates.size, lastIdx, random)
        val greeting = greetingForHour(now.hour)

        return FormattedNotification(
            title = "$greeting, $chosenNickname",
            body = templates[chosenIdx],
            category = NotificationCategory.WEEKLY_SUMMARY,
            chosenNickname = chosenNickname,
            chosenPhraseIndex = chosenIdx,
        )
    }

    fun highestCrossedMilestone(goal: FinanceGoal): Int? {
        if (goal.targetAmount <= 0.0) return null
        val pct = (goal.currentAmount / goal.targetAmount) * 100.0
        return when {
            pct + 1e-6 >= 100.0 -> 100
            pct + 1e-6 >= 80.0 -> 80
            pct + 1e-6 >= 50.0 -> 50
            else -> null
        }
    }

    fun formatGoalMilestone(
        goal: FinanceGoal,
        previousNotifiedMilestone: Int,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): Pair<Int, FormattedNotification>? {
        val reached = highestCrossedMilestone(goal) ?: return null
        if (reached <= previousNotifiedMilestone) return null

        val currentText = CurrencyFormatter.formatBrl(goal.currentAmount)
        val targetText = CurrencyFormatter.formatBrl(goal.targetAmount)

        val templates = if (reached >= 100) {
            listOf(
                "Meta batida! \"${goal.title}\" chegou a 100% ($currentText de $targetText).",
                "Missão cumprida em \"${goal.title}\": você completou os 100% ($currentText).",
                "Excelente! A meta \"${goal.title}\" atingiu 100% do objetivo ($targetText).",
                "Tudo certo por aqui: \"${goal.title}\" alcançou 100% ($currentText acumulados).",
                "Vitória registrada: \"${goal.title}\" bateu a marca de 100% ($targetText).",
            )
        } else {
            listOf(
                "Você acaba de passar dos $reached% na meta \"${goal.title}\" ($currentText de $targetText).",
                "Avanço firme em \"${goal.title}\": já chegou a $reached% ($currentText acumulados).",
                "Bom ritmo! A meta \"${goal.title}\" atingiu o marco de $reached% ($currentText de $targetText).",
                "De olho no progresso: \"${goal.title}\" cruzou os $reached% ($currentText).",
                "Marco de $reached% conquistado em \"${goal.title}\" — faltam só ${CurrencyFormatter.formatBrl((goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0))}.",
            )
        }

        val chosenNickname = pickNickname(settings.nicknames, settings.lastNickname, random)
        val lastIdx = settings.lastPhraseIndices[NotificationCategory.GOAL_MILESTONE.key]
        val chosenIdx = pickTemplateIndex(templates.size, lastIdx, random)
        val greeting = greetingForHour(now.hour)

        return reached to FormattedNotification(
            title = "$greeting, $chosenNickname",
            body = templates[chosenIdx],
            category = NotificationCategory.GOAL_MILESTONE,
            chosenNickname = chosenNickname,
            chosenPhraseIndex = chosenIdx,
        )
    }

    fun formatTestNotification(
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): FormattedNotification {
        val timeText = now.format(timeFmt)
        val templates = listOf(
            "Tudo operando no padrão Gotham. Às $timeText, seus lembretes e resumos locais estão prontos.",
            "Teste confirmado às $timeText! Quando vencer uma conta ou meta avançar, eu aviso por aqui.",
            "Sinal verde às $timeText: notificações locais configuradas sem depender de servidor.",
            "Na escuta às $timeText. Vou lembrar no horário, 1h antes e nos seus resumos.",
            "Sistema de avisos testado às $timeText — você continua no controle total das finanças.",
        )
        val chosenNickname = pickNickname(settings.nicknames, settings.lastNickname, random)
        val lastIdx = settings.lastPhraseIndices[NotificationCategory.TEST.key]
        val chosenIdx = pickTemplateIndex(templates.size, lastIdx, random)
        val greeting = greetingForHour(now.hour)

        return FormattedNotification(
            title = "$greeting, $chosenNickname",
            body = templates[chosenIdx],
            category = NotificationCategory.TEST,
            chosenNickname = chosenNickname,
            chosenPhraseIndex = chosenIdx,
        )
    }

    private fun relativeDayLabel(target: LocalDate, today: LocalDate): String = when (target) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> "Em ${target.format(dayMonthFmt)}"
    }
}
