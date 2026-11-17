package com.example.data.remote

import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceScope
import com.example.data.model.TransactionType
import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale

data class LocalParsedTransaction(
    val scope: FinanceScope,
    val type: TransactionType,
    val title: String,
    val category: String,
    val amount: Double,
    val isPaid: Boolean,
    val accountName: String = "",
    val date: LocalDate = LocalDate.now(),
) {
    fun toAlfredConfirmation(): String {
        val formattedValue = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", amount)
        val typeLabel = if (type == TransactionType.INCOME) "Receita" else "Despesa"
        val scopeLabel = if (scope == FinanceScope.BUSINESS) {
            "na sua empresa"
        } else {
            "nas suas finanças pessoais"
        }
        val statusLabel = when {
            isPaid && type == TransactionType.INCOME -> "recebida"
            isPaid -> "paga"
            type == TransactionType.INCOME -> "a receber"
            else -> "a pagar"
        }
        val accountPart = if (accountName.isNotBlank()) " (conta: $accountName)" else ""
        return "Anotado, senhor: $typeLabel $statusLabel \"$title\" ($formattedValue) em $category$accountPart, registrada $scopeLabel."
    }
}

data class ManualEntryDraft(
    val scope: FinanceScope,
    val type: TransactionType = TransactionType.EXPENSE,
    val title: String = "",
    val category: String = "Outros",
    val amount: Double = 0.0,
    val isPaid: Boolean = true,
)

data class LocalParsedGoalContribution(
    val scope: FinanceScope,
    val goalQuery: String,
    val amount: Double,
)

data class CompoundSplitResult(
    val localTransactions: List<LocalParsedTransaction>,
    val localGoalContributions: List<LocalParsedGoalContribution>,
    val unresolvedPrompt: String,
)

object LocalCommandParser {

    private val AMOUNT_REGEX = Regex(
        """(?i)(?:r\$\s*)?(\d{1,3}(?:\.\d{3})+,\d{1,2}|\d+[.,]\d{1,2}|\d{1,3}(?:\.\d{3})+|\d+)(?:\s*reais?)?"""
    )

    private val EXPENSE_VERBS = setOf(
        "gastei", "paguei", "comprei", "gastar", "pagar", "comprar", "transferi", "mandei", "debitei"
    )
    private val INCOME_VERBS = setOf(
        "recebi", "vendi", "faturei", "ganhei", "receber", "vender", "faturar", "ganhar", "entrou", "caiu"
    )
    private val PAST_TENSE_VERBS = setOf(
        "gastei", "paguei", "comprei", "transferi", "mandei", "debitei",
        "recebi", "vendi", "faturei", "ganhei", "entrou", "caiu"
    )

    private val BUSINESS_KEYWORDS = listOf(
        "empresa", "cliente", "clientes", "venda", "vendas", "vendi",
        "faturei", "faturamento", "fornecedor", "fornecedores", "pj",
        "conta pj", "imposto", "impostos", "cnpj", "negocio", "caixa da empresa"
    )

    private val PERSONAL_EXPLICIT_KEYWORDS = listOf(
        "conta pessoal", "financas pessoais", "pessoal", "conta pf"
    )

    private val ACCOUNT_PHRASES = listOf(
        "conta pessoal", "conta pj", "conta empresarial", "conta corrente", "conta poupanca",
        "conta pf", "nubank", "inter", "itau", "bradesco", "santander", "banco do brasil",
        "c6", "btg", "xp", "picpay", "mercado pago", "caixa", "carteira",
        "cartao de credito", "cartao"
    )

    private val QUESTION_PREFIXES = listOf(
        "como ", "qual ", "quais ", "quanto ", "quantos ", "quantas ",
        "por que ", "porque ", "o que ", "sera ", "devo ", "posso ",
        "vale a pena", "compensa ", "me explica", "me ajuda", "me ajude"
    )

    private val NON_TRANSACTION_KEYWORDS = listOf(
        "lembra", "lembrete", "me avisa", "avisar", "meta", "objetivo",
        "economizar", "investir", "dica", "dicas", "estrategia", "conselho"
    )

    private val GOAL_CONTRIBUTION_VERBS = setOf(
        "guardei", "depositei", "aportei", "coloquei", "juntei", "transferi", "separei", "poupando"
    )

    private val SEGMENT_TRIGGER_VERBS = EXPENSE_VERBS + INCOME_VERBS + GOAL_CONTRIBUTION_VERBS + setOf(
        "lembra", "lembrar", "lembrete", "avisa", "avisar", "agenda", "agendar", "criar", "cria"
    )

    fun splitCompoundSegments(rawInput: String): List<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return emptyList()

        // Step 1: split on ';' and commas not inside decimal numbers (e.g. preserve "48,50")
        val primaryParts = trimmed.split(Regex("""\s*;\s*|,(?!\d)\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Step 2: selectively split on " e " ONLY when the right side starts a new clause
        val result = mutableListOf<String>()
        val andRegex = Regex("""(?i)\s+e\s+""")
        for (part in primaryParts) {
            val subParts = part.split(andRegex).map { it.trim() }.filter { it.isNotBlank() }
            if (subParts.size <= 1) {
                result.add(part)
                continue
            }
            val buffer = StringBuilder(subParts.first())
            for (idx in 1 until subParts.size) {
                val candidate = subParts[idx]
                val normCandidate = normalize(candidate)
                val tokens = normCandidate.split(Regex("""[\s,;.!?]+""")).filter { it.isNotBlank() }
                val looksLikeStandaloneClause =
                    tokens.any { it in SEGMENT_TRIGGER_VERBS } ||
                        extractAmount(candidate) != null ||
                        containsWordOrPhrase(normCandidate, "me lembra") ||
                        containsWordOrPhrase(normCandidate, "lembrete")
                if (looksLikeStandaloneClause) {
                    result.add(buffer.toString().trim())
                    buffer.clear()
                    buffer.append(candidate)
                } else {
                    buffer.append(" e ").append(candidate)
                }
            }
            if (buffer.isNotEmpty()) {
                result.add(buffer.toString().trim())
            }
        }
        return result.filter { it.isNotBlank() }
    }

    fun splitAndResolve(
        rawInput: String,
        activeScope: FinanceScope,
        knownAccounts: List<FinanceAccount> = emptyList(),
        referenceDate: LocalDate = LocalDate.now(),
    ): CompoundSplitResult {
        val segments = splitCompoundSegments(rawInput)
        if (segments.isEmpty()) {
            return CompoundSplitResult(emptyList(), emptyList(), "")
        }

        val resolvedTx = mutableListOf<LocalParsedTransaction>()
        val resolvedGoals = mutableListOf<LocalParsedGoalContribution>()
        val unresolved = mutableListOf<String>()

        for (seg in segments) {
            val tx = parseTransaction(seg, activeScope, knownAccounts, referenceDate)
            if (tx != null && tx.amount > 0.0 && tx.title.isNotBlank()) {
                resolvedTx.add(tx)
                continue
            }
            val goalContrib = parseGoalContribution(seg, activeScope)
            if (goalContrib != null) {
                resolvedGoals.add(goalContrib)
                continue
            }
            unresolved.add(seg)
        }

        return CompoundSplitResult(
            localTransactions = resolvedTx,
            localGoalContributions = resolvedGoals,
            unresolvedPrompt = unresolved.joinToString(", "),
        )
    }

    fun parseGoalContribution(
        rawInput: String,
        activeScope: FinanceScope,
    ): LocalParsedGoalContribution? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null
        val normalized = normalize(trimmed)
        if (trimmed.endsWith("?") || QUESTION_PREFIXES.any { normalized.startsWith(it) }) return null

        val words = normalized.split(Regex("""[\s,;.!?]+""")).filter { it.isNotBlank() }
        val hasContribVerb = words.any { it in GOAL_CONTRIBUTION_VERBS }
        val mentionsGoalOrReserve = containsWordOrPhrase(normalized, "meta") ||
            containsWordOrPhrase(normalized, "reserva") ||
            containsWordOrPhrase(normalized, "objetivo")
        if (!hasContribVerb || !mentionsGoalOrReserve) return null

        val amount = extractAmount(trimmed) ?: return null
        if (amount <= 0.0) return null

        val goalRegex = Regex(
            """(?i)\b(?:na|no|para|pra|pro|em)\s+(?:a\s+|o\s+)?(?:meta|reserva|objetivo)\s*(?:do|da|de|dos|das)?\s*(.+)$"""
        )
        val query = goalRegex.find(trimmed)?.groupValues?.getOrNull(1)
            ?.trim(' ', '.', ',', '!', '?')
            .orEmpty()
            .ifBlank {
                AMOUNT_REGEX.replace(trimmed, " ")
                    .replace(Regex("""(?i)\b(?:guardei|depositei|aportei|coloquei|juntei|transferi|separei|na|no|meta|do|da|de)\b"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            }

        if (query.isBlank()) return null

        return LocalParsedGoalContribution(
            scope = detectScope(normalized, activeScope),
            goalQuery = query,
            amount = amount,
        )
    }

    fun parseTransaction(
        rawInput: String,
        activeScope: FinanceScope,
        knownAccounts: List<FinanceAccount> = emptyList(),
        referenceDate: LocalDate = LocalDate.now(),
    ): LocalParsedTransaction? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val normalized = normalize(trimmed)
        if (trimmed.endsWith("?") || QUESTION_PREFIXES.any { normalized.startsWith(it) }) {
            return null
        }
        if (NON_TRANSACTION_KEYWORDS.any { containsWordOrPhrase(normalized, it) }) {
            return null
        }

        val words = normalized.split(Regex("""[\s,;.!?]+""")).filter { it.isNotBlank() }
        val hasExpense = words.any { it in EXPENSE_VERBS }
        val hasIncome = words.any { it in INCOME_VERBS }

        // Ambiguous if both or neither appear
        if (hasExpense == hasIncome) return null
        val type = if (hasIncome) TransactionType.INCOME else TransactionType.EXPENSE

        val amount = extractAmount(trimmed) ?: return null
        if (amount <= 0.0) return null

        val scope = detectScope(normalized, activeScope)
        val extracted = extractTitleAndAccount(trimmed, normalized, type, knownAccounts)
        if (extracted.title.isBlank()) return null

        val category = detectCategory(
            normalized = normalized,
            title = extracted.title,
            type = type,
            scope = scope,
        )
        val isPaid = detectIsPaid(normalized, words)
        val parsedDate = extractTransactionDate(normalized, referenceDate)

        return LocalParsedTransaction(
            scope = scope,
            type = type,
            title = extracted.title,
            category = category,
            amount = amount,
            isPaid = isPaid,
            accountName = extracted.accountName,
            date = parsedDate,
        )
    }

    fun extractManualDraft(rawInput: String, activeScope: FinanceScope): ManualEntryDraft {
        val parsed = parseTransaction(rawInput, activeScope)
        if (parsed != null) {
            return ManualEntryDraft(
                scope = parsed.scope,
                type = parsed.type,
                title = parsed.title,
                category = parsed.category,
                amount = parsed.amount,
                isPaid = parsed.isPaid,
            )
        }
        val normalized = normalize(rawInput)
        val words = normalized.split(Regex("""[\s,;.!?]+""")).filter { it.isNotBlank() }
        val type = if (words.any { it in INCOME_VERBS }) TransactionType.INCOME else TransactionType.EXPENSE
        val amount = extractAmount(rawInput) ?: 0.0
        val scope = detectScope(normalized, activeScope)
        val extracted = extractTitleAndAccount(rawInput, normalized, type, emptyList())
        val category = detectCategory(normalized, extracted.title, type, scope)
        return ManualEntryDraft(
            scope = scope,
            type = type,
            title = extracted.title,
            category = category,
            amount = amount,
            isPaid = detectIsPaid(normalized, words),
        )
    }

    internal fun extractAmount(rawInput: String): Double? {
        val sanitized = stripDateTokensForAmountScan(rawInput)
        val match = AMOUNT_REGEX.find(sanitized) ?: return null
        val token = match.groupValues.getOrNull(1)?.trim() ?: return null
        return parseBrazilianNumber(token)
    }

    private fun stripDateTokensForAmountScan(rawInput: String): String {
        return rawInput
            .replace(Regex("""\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b"""), " ")
            .replace(Regex("""(?i)\bdia\s+\d{1,2}(?:\s+de\s+[a-zçãõáéíóú]+)?\b"""), " ")
            .replace(Regex("""(?i)\b(?:no\s+)?\d{1,2}\s+de\s+(?:janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\b"""), " ")
    }

    internal fun extractTransactionDate(
        normalized: String,
        referenceDate: LocalDate = LocalDate.now(),
    ): LocalDate {
        if (containsWordOrPhrase(normalized, "anteontem")) {
            return referenceDate.minusDays(2)
        }
        if (containsWordOrPhrase(normalized, "ontem")) {
            return referenceDate.minusDays(1)
        }
        if (containsWordOrPhrase(normalized, "hoje")) {
            return referenceDate
        }

        // dd/MM/yyyy or dd/MM
        val slashMatch = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(normalized)
        if (slashMatch != null) {
            val day = slashMatch.groupValues[1].toIntOrNull()
            val month = slashMatch.groupValues[2].toIntOrNull()
            val yearRaw = slashMatch.groupValues[3].toIntOrNull()
            if (day != null && month != null && month in 1..12) {
                val year = when {
                    yearRaw == null -> referenceDate.year
                    yearRaw < 100 -> 2000 + yearRaw
                    else -> yearRaw
                }
                val ym = YearMonth.of(year, month)
                if (day in 1..ym.lengthOfMonth()) {
                    return LocalDate.of(year, month, day)
                }
            }
        }

        // "dia N de mês" or "N de mês" or "dia N"
        val dayWithMonthMatch = Regex(
            """\b(?:dia\s+)?(\d{1,2})\s+de\s+(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\b"""
        ).find(normalized)
        if (dayWithMonthMatch != null) {
            val day = dayWithMonthMatch.groupValues[1].toIntOrNull()
            val month = parsePortugueseMonth(dayWithMonthMatch.groupValues[2])
            if (day != null && month != null) {
                val ym = YearMonth.of(referenceDate.year, month)
                if (day in 1..ym.lengthOfMonth()) {
                    return LocalDate.of(referenceDate.year, month, day)
                }
            }
        }

        val diaOnlyMatch = Regex("""\bdia\s+(\d{1,2})\b""").find(normalized)
        if (diaOnlyMatch != null) {
            val day = diaOnlyMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                val ym = YearMonth.from(referenceDate)
                return LocalDate.of(ym.year, ym.month, day.coerceAtMost(ym.lengthOfMonth()))
            }
        }

        return referenceDate
    }

    private fun parsePortugueseMonth(monthNorm: String): Month? = when (monthNorm) {
        "janeiro" -> Month.JANUARY
        "fevereiro" -> Month.FEBRUARY
        "marco" -> Month.MARCH
        "abril" -> Month.APRIL
        "maio" -> Month.MAY
        "junho" -> Month.JUNE
        "julho" -> Month.JULY
        "agosto" -> Month.AUGUST
        "setembro" -> Month.SEPTEMBER
        "outubro" -> Month.OCTOBER
        "novembro" -> Month.NOVEMBER
        "dezembro" -> Month.DECEMBER
        else -> null
    }

    private fun parseBrazilianNumber(token: String): Double? {
        val cleaned = token.replace(" ", "")
        val normalizedNum = when {
            cleaned.contains('.') && cleaned.contains(',') ->
                cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(',') ->
                cleaned.replace(",", ".")
            Regex("""^\d{1,3}(\.\d{3})+$""").matches(cleaned) ->
                cleaned.replace(".", "")
            else -> cleaned
        }
        return normalizedNum.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun detectScope(normalized: String, activeScope: FinanceScope): FinanceScope {
        if (PERSONAL_EXPLICIT_KEYWORDS.any { containsWordOrPhrase(normalized, it) } &&
            !containsWordOrPhrase(normalized, "empresa") &&
            !containsWordOrPhrase(normalized, "pj")
        ) {
            return FinanceScope.PERSONAL
        }
        return if (BUSINESS_KEYWORDS.any { containsWordOrPhrase(normalized, it) }) {
            FinanceScope.BUSINESS
        } else {
            activeScope
        }
    }

    private fun detectIsPaid(normalized: String, words: List<String>): Boolean {
        val futureMarkers = listOf("a pagar", "a receber", "vou pagar", "pendente", "vencimento", "amanha")
        if (futureMarkers.any { normalized.contains(it) }) return false
        return words.any { it in PAST_TENSE_VERBS }
    }

    private data class TitleAndAccount(
        val title: String,
        val accountName: String,
    )

    private fun extractTitleAndAccount(
        rawInput: String,
        normalized: String,
        type: TransactionType,
        knownAccounts: List<FinanceAccount>,
    ): TitleAndAccount {
        val prepRegex = Regex(
            """(?i)\b(?:no|na|nos|nas|do|da|dos|das|de|em|com|para|pra|pro)\s+(?:(?:um|uma|uns|umas|o|a|os|as)\s+)?"""
        )
        val trimmed = rawInput.trim()
        val matches = prepRegex.findAll(trimmed).toList()

        // Split all prepositional clauses in order
        val segments = mutableListOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            if (start <= end) {
                val seg = trimmed.substring(start, end)
                    .replace(Regex("""\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b"""), " ")
                    .replace(Regex("""(?i)\bdia\s+\d{1,2}\b"""), " ")
                    .replace(Regex("""(?i)\b(?:hoje|ontem|anteontem|agora|aqui)\b"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim(' ', '.', ',', '!', '?')
                if (seg.isNotBlank()) segments.add(seg)
            }
        }

        val knownNamesNorm = knownAccounts.associateBy { normalize(it.name) }

        fun matchAccountPhrase(segment: String): String? {
            val segNorm = normalize(segment)
            knownNamesNorm[segNorm]?.let { return it.name }
            // Strip leading "conta " for known account matching
            val withoutConta = segNorm.removePrefix("conta ").trim()
            knownNamesNorm[withoutConta]?.let { return it.name }

            val matchedPhrase = ACCOUNT_PHRASES.firstOrNull { phrase ->
                segNorm == phrase || segNorm.startsWith("$phrase ")
            } ?: return null
            return formatAccountLabel(segment, matchedPhrase)
        }

        var detectedAccount = ""
        val remainingSegments = mutableListOf<String>()
        for (seg in segments) {
            val matchedAccount = matchAccountPhrase(seg)
            if (matchedAccount != null && detectedAccount.isEmpty()) {
                detectedAccount = matchedAccount
            } else {
                remainingSegments.add(seg)
            }
        }

        var candidate = remainingSegments.firstOrNull().orEmpty()

        if (candidate.equals("empresa", ignoreCase = true) ||
            candidate.equals("minha empresa", ignoreCase = true)
        ) {
            candidate = if (type == TransactionType.INCOME) "Venda" else "Despesa da empresa"
        }

        if (candidate.isBlank() && detectedAccount.isNotEmpty()) {
            // Phrase after preposition was an account (ex.: "recebi 500 reais na conta pessoal")
            candidate = when {
                containsWordOrPhrase(normalized, "salario") -> "Salário"
                containsWordOrPhrase(normalized, "vendi") || containsWordOrPhrase(normalized, "venda") -> "Venda"
                type == TransactionType.INCOME -> "Receita"
                else -> "Despesa"
            }
        }

        if (candidate.isBlank()) {
            // Fallback: strip verb + date + amount from original text
            var stripped = stripDateTokensForAmountScan(rawInput)
            stripped = AMOUNT_REGEX.replace(stripped, " ")
            stripped = stripped.replace(
                Regex("""(?i)\b(?:gastei|paguei|comprei|recebi|vendi|faturei|ganhei|transferi|registra|lanca|lança|anota)\b"""),
                " "
            )
            stripped = stripped.replace(
                Regex("""(?i)\b(?:no|na|nos|nas|do|da|dos|das|de|em|com|um|uma|o|a|reais|real|hoje|ontem|anteontem)\b"""),
                " "
            ).replace(Regex("""\s+"""), " ").trim(' ', '.', ',', '!', '?')

            candidate = stripped
        }

        if (candidate.isBlank()) {
            candidate = when {
                containsWordOrPhrase(normalized, "vendi") || containsWordOrPhrase(normalized, "venda") -> "Venda"
                type == TransactionType.INCOME -> "Receita"
                else -> "Despesa"
            }
        }

        val cleanTitle = candidate.take(120).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pt-BR")) else ch.toString()
        }
        return TitleAndAccount(
            title = cleanTitle,
            accountName = detectedAccount,
        )
    }

    private fun formatAccountLabel(rawSegment: String, matchedPhrase: String): String {
        val cleaned = rawSegment.trim()
        return when (matchedPhrase) {
            "conta pj" -> "Conta PJ"
            "conta pf" -> "Conta PF"
            "nubank" -> "Nubank"
            "itau" -> "Itaú"
            "pix" -> "Pix"
            else -> cleaned.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pt-BR")) else ch.toString()
            }.take(60)
        }
    }

    private fun detectCategory(
        normalized: String,
        title: String,
        type: TransactionType,
        scope: FinanceScope,
    ): String {
        val titleNorm = normalize(title)
        val combined = "$normalized $titleNorm"
        return when {
            listOf(
                "almoco", "jantar", "cafe", "lanche", "mercado", "supermercado",
                "padaria", "ifood", "restaurante", "comida", "pizza", "acai", "feira"
            ).any { containsWordOrPhrase(combined, it) } -> "Alimentação"

            listOf(
                "uber", "99", "gasolina", "combustivel", "etanol", "diesel",
                "onibus", "metro", "estacionamento", "pedagio", "passagem", "carro", "moto"
            ).any { containsWordOrPhrase(combined, it) } -> "Transporte"

            listOf(
                "luz", "energia", "agua", "aluguel", "condominio",
                "internet", "gas", "casa", "moradia", "iptu", "telefone"
            ).any { containsWordOrPhrase(combined, it) } -> "Moradia"

            listOf(
                "farmacia", "remedio", "medico", "consulta", "exame",
                "academia", "dentista", "saude", "hospital"
            ).any { containsWordOrPhrase(combined, it) } -> "Saúde"

            listOf(
                "software", "saas", "servidor", "nuvem", "hospedagem", "sistema", "dominio"
            ).any { containsWordOrPhrase(combined, it) } ->
                if (scope == FinanceScope.BUSINESS) "Software" else "Assinaturas"

            listOf(
                "netflix", "spotify", "assinatura", "youtube", "prime", "icloud", "disney"
            ).any { containsWordOrPhrase(combined, it) } -> "Assinaturas"

            listOf(
                "cinema", "viagem", "bar", "show", "jogo", "lazer", "ingresso", "passeio"
            ).any { containsWordOrPhrase(combined, it) } -> "Lazer"

            listOf(
                "fornecedor", "fornecedores", "estoque", "mercadoria", "insumo", "materia"
            ).any { containsWordOrPhrase(combined, it) } -> "Fornecedores"

            listOf(
                "imposto", "impostos", "das", "mei", "darf", "taxa", "tributo", "inss"
            ).any { containsWordOrPhrase(combined, it) } -> "Impostos"

            listOf(
                "marketing", "anuncio", "trafego", "ads", "meta ads", "google ads"
            ).any { containsWordOrPhrase(combined, it) } -> "Marketing"

            listOf(
                "servico", "servicos", "consultoria", "freela", "freelance", "manutencao"
            ).any { containsWordOrPhrase(combined, it) } -> "Serviços"

            listOf(
                "venda", "vendas", "vendi", "faturei", "faturamento", "cliente"
            ).any { containsWordOrPhrase(combined, it) } -> "Vendas"

            listOf(
                "salario", "holerite", "pro-labore", "pro labore", "pagamento"
            ).any { containsWordOrPhrase(combined, it) } && type == TransactionType.INCOME ->
                if (scope == FinanceScope.BUSINESS) "Pró-labore" else "Salário"

            type == TransactionType.INCOME && scope == FinanceScope.BUSINESS -> "Vendas"

            else -> "Outros"
        }
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .trim()
    }

    private fun containsWordOrPhrase(normalizedText: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("""(?<!\w)$escaped(?!\w)""").containsMatchIn(normalizedText)
    }
}
