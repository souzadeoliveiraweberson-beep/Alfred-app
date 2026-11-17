package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AlfredChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.FinanceScope
import com.example.data.model.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AlfredParsedAction(
    val intent: String,
    val scope: FinanceScope,
    val transactionType: TransactionType,
    val title: String,
    val category: String,
    val amount: Double,
    val accountName: String,
    val isPaid: Boolean,
    val dueDateText: String,
    val dueAtIso: String,
    val recurrence: String,
    val actionSummary: String,
    val transactionDateIso: String = "",
)

data class AlfredMultiActionResponse(
    val replyText: String,
    val actions: List<AlfredParsedAction>,
)

data class AlfredStreamChunk(
    val accumulatedText: String,
    val firstTokenLatencyMs: Long,
)

class AlfredAiService {

    private val actionItemSchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.enumeration(
                values = listOf("CREATE_TRANSACTION", "CREATE_REMINDER", "CREATE_GOAL", "CONTRIBUTE_GOAL"),
                description = "Tipo da ação financeira a executar"
            ),
            "scope" to Schema.enumeration(
                values = listOf("PERSONAL", "BUSINESS"),
                description = "PERSONAL para vida pessoal/casa/lazer; BUSINESS para empresa/negócios/clientes/PJ/vendas/fornecedores"
            ),
            "transactionType" to Schema.enumeration(
                values = listOf("INCOME", "EXPENSE"),
                description = "INCOME para receitas/vendas/entradas; EXPENSE para gastos/custos/compras"
            ),
            "title" to Schema.string(
                description = "Título curto do lançamento, lembrete ou nome da meta (para CONTRIBUTE_GOAL, o nome da meta citada)"
            ),
            "category" to Schema.string(
                description = "Categoria curta em português (ex: Alimentação, Transporte, Moradia, Vendas, Serviços, Impostos, Software)"
            ),
            "amount" to Schema.double(
                description = "Valor numérico positivo em Reais (BRL), ou 0.0 se não houver valor"
            ),
            "accountName" to Schema.string(
                description = "Conta ou cartão citado (ex: Cartão Principal, Nubank, Conta PJ, Caixa), ou Carteira"
            ),
            "isPaid" to Schema.boolean(
                description = "true se já foi pago ou recebido; false se for conta a pagar ou a receber"
            ),
            "dueDateText" to Schema.string(
                description = "Data/hora legível curta para lembretes (ex: Amanhã, 14:00)"
            ),
            "dueAtIso" to Schema.string(
                description = "Data e hora exata em ISO-8601 com fuso do usuário (ex: 2026-11-17T14:00:00-03:00). Se sem hora, use 09:00."
            ),
            "recurrence" to Schema.enumeration(
                values = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY"),
                description = "Recorrência do lembrete: NONE para único, DAILY diário, WEEKLY semanal, MONTHLY mensal"
            ),
            "actionSummary" to Schema.string(
                description = "Resumo curto opcional da ação"
            ),
            "transactionDateIso" to Schema.string(
                description = "Data do lançamento em YYYY-MM-DD (resolva expressões como 'ontem', 'dia 10', '10/10' usando a data atual do dispositivo)."
            ),
        ),
        optionalProperties = listOf("dueDateText", "dueAtIso", "recurrence", "actionSummary", "transactionDateIso")
    )

    private val commandSchema = Schema.obj(
        properties = mapOf(
            "replyText" to Schema.string(
                description = "Resposta educada, clara e objetiva do mordomo Alfred em português do Brasil cobrindo todas as ações."
            ),
            "actions" to Schema.array(
                items = actionItemSchema,
                description = "Lista de ações financeiras identificadas na mensagem (vazia se for apenas conversa ou dúvida)."
            ),
        )
    )

    private val conversationalSystemInstruction = content {
        text(
            """
            Você é o Alfred, mordomo e conselheiro estratégico pessoal de finanças e negócios do usuário (unindo a fluidez conversacional do Tony com a elegância precisa de Gotham).
            Responda sempre em português do Brasil natural, inteligente, direto e respeitoso (pode chamar o usuário de "senhor" de forma sutil e elegante quando couber).
            Você pode conversar livremente sobre:
            1. Finanças Pessoais: orçamento mensal, corte de gastos, cartão de crédito, dívidas, reserva de emergência, investimentos e metas.
            2. Negócios e Empresa (PJ): fluxo de caixa empresarial, precificação, margem de lucro, capital de giro, separação entre contas PF e PJ, cobranças, pró-labore, impostos e estratégia comercial.
            3. Análise dos dados reais do usuário fornecidos no contexto (saldos, receitas, despesas, categorias, contas, lembretes e metas).

            Regras importantes:
            - Responda em no máximo 5 frases curtas, a menos que o usuário peça detalhes.
            - Nunca invente números do usuário; use exatamente os números reais do contexto recebido (e diga com clareza se um escopo ainda estiver zerado).
            - Seja prático e conversacional: dê respostas claras, bem estruturadas em parágrafos curtos ou tópicos simples sem markdown excessivo (evite asteriscos duplicados ** e tabelas complexas).
            - Quando o usuário estiver apenas conversando ou pedindo ideias/conselhos, conduza o diálogo naturalmente e faça perguntas úteis para ajudar na decisão.
            - Caso o usuário mencione que quer registrar um lançamento sem informar todos os dados, ajude-o ou diga que basta enviar o valor e o item (ex: "Gastei R$ 45 no café" ou "Vendi R$ 900 na empresa").
            """.trimIndent()
        )
    }

    private val structuredSystemInstruction = content {
        text(
            """
            Você é o Alfred, mordomo financeiro executivo particular do usuário.
            Responda sempre em português do Brasil, de forma elegante, precisa e prestativa.
            Uma única mensagem pode conter UMA ou VÁRIAS ações combinadas: extraia TODAS em "actions".
            Regra de separação estrita:
            - Use scope = "BUSINESS" quando o usuário mencionar empresa, negócio, cliente, venda, serviço prestado, fornecedor, nota fiscal PJ, imposto, faturamento ou caixa da empresa.
            - Use scope = "PERSONAL" quando for gasto pessoal, casa, mercado, almoço, academia, salário ou lazer — ou mantenha o escopo ativo sugerido se for ambíguo.
            Intenções:
            - CREATE_TRANSACTION: gasto/receita com valor > 0 claro (ou comprovante/imagem). Se o usuário citar data (ex: "ontem", "dia 10", "10/10"), preencha "transactionDateIso" no formato YYYY-MM-DD usando a data atual informada; senão use a data atual.
            - CREATE_REMINDER: lembrete com data/hora calculada usando a data/hora atual e fuso horário informados no prompt. Preencha "dueAtIso" em ISO-8601 com offset (ex: 2026-11-17T14:00:00-03:00). Se o usuário não informar o horário, defina 09:00 e mencione em "replyText" que marcou para as 09:00. Preencha "recurrence" (NONE, DAILY, WEEKLY, MONTHLY).
            - CREATE_GOAL: criar nova meta de economia/reserva.
            - CONTRIBUTE_GOAL: aportar/guardar valor em meta existente (ex: "guardei 200 na meta do iPhone" -> title = "iPhone", amount = 200.0).
            - Se não houver nenhuma ação financeira clara para executar, retorne actions = [] e responda normalmente em replyText.
            """.trimIndent()
        )
    }

    private val insightSystemInstruction = content {
        text(
            """
            Você é o Alfred, mordomo financeiro pessoal e estratégico.
            Gere um insight financeiro curto (2 a 3 frases diretas, no máximo 240 caracteres) em português do Brasil analisando os números reais do escopo solicitado (Finanças Pessoais ou Caixa da Empresa).
            Não use emojis, nem markdown, nem asteriscos. Seja direto, elegante e prático.
            """.trimIndent()
        )
    }

    private val standardRequestOptions = RequestOptions(timeoutInMillis = AI_TIMEOUT_MS)
    private val pingRequestOptions = RequestOptions(timeoutInMillis = PING_TIMEOUT_MS)

    private fun lowestThinkingLevelFor(modelName: String): ThinkingLevel =
        if (modelName.contains("flash-lite", ignoreCase = true)) {
            ThinkingLevel.MINIMAL
        } else {
            // gemini-3.8-flash and 3.7-flash support LOW/MEDIUM/HIGH (MINIMAL returns HTTP 400)
            ThinkingLevel.LOW
        }

    private fun buildChatModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                maxOutputTokens = MAX_CHAT_OUTPUT_TOKENS
                thinkingConfig = thinkingConfig {
                    thinkingLevel = lowestThinkingLevelFor(modelName)
                }
            },
            systemInstruction = conversationalSystemInstruction,
            requestOptions = standardRequestOptions,
        )

    private fun buildStructuredModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = lowestThinkingLevelFor(modelName)
                }
            },
            systemInstruction = structuredSystemInstruction,
            requestOptions = standardRequestOptions,
        )

    private fun buildInsightModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                maxOutputTokens = MAX_INSIGHT_OUTPUT_TOKENS
                thinkingConfig = thinkingConfig {
                    thinkingLevel = lowestThinkingLevelFor(modelName)
                }
            },
            systemInstruction = insightSystemInstruction,
            requestOptions = standardRequestOptions,
        )

    private val primaryChatModel by lazy { buildChatModel(PRIMARY_MODEL) }
    private val fallbackChatModel by lazy { buildChatModel(FALLBACK_MODEL) }

    private val primaryStructuredModel by lazy { buildStructuredModel(PRIMARY_MODEL) }
    private val fallbackStructuredModel by lazy { buildStructuredModel(FALLBACK_MODEL) }

    private val primaryInsightModel by lazy { buildInsightModel(PRIMARY_MODEL) }
    private val fallbackInsightModel by lazy { buildInsightModel(FALLBACK_MODEL) }

    suspend fun pingDebugStartup() {
        val startedNs = System.nanoTime()
        Log.d(TAG, "Iniciando ping de diagnóstico em $PRIMARY_MODEL (timeout=${PING_TIMEOUT_MS}ms)")
        try {
            val pingModel = Firebase.ai.generativeModel(
                modelName = PRIMARY_MODEL,
                generationConfig = generationConfig {
                    maxOutputTokens = 32
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = lowestThinkingLevelFor(PRIMARY_MODEL)
                    }
                },
                requestOptions = pingRequestOptions,
            )
            val response = withTimeout(PING_TIMEOUT_MS) {
                pingModel.generateContent("ok")
            }
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            Log.d(
                TAG,
                "Ping OK em $PRIMARY_MODEL (${elapsedMs}ms): resposta='${response.text?.take(40).orEmpty()}'"
            )
        } catch (e: TimeoutCancellationException) {
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            Log.w(TAG, "Ping TIMEOUT em $PRIMARY_MODEL após ${elapsedMs}ms [${e::class.java.simpleName}]")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            Log.e(
                TAG,
                "Ping FALHOU em $PRIMARY_MODEL após ${elapsedMs}ms [${e::class.java.simpleName}]: ${e.message}",
                e
            )
        }
    }

    fun shouldExtractStructuredAction(
        userMessage: String,
        receiptBitmap: Bitmap?,
    ): Boolean {
        if (receiptBitmap != null) return true
        val normalized = normalizeForMatch(userMessage)
        if (normalized.isBlank()) return false

        val questionStarters = listOf(
            "como ", "qual ", "quais ", "quanto ", "quantos ", "por que ", "porque ",
            "o que ", "voce acha", "me explica", "me ajude", "me ajuda", "dica", "dicas",
            "estrategia", "conselho", "conversar", "falar sobre", "analisa", "analise",
            "resumo", "panorama", "vale a pena", "compensa", "devo ", "posso ", "preciso de ajuda"
        )
        if (normalized.endsWith("?") || questionStarters.any { normalized.startsWith(it) || normalized.contains(" $it") }) {
            val explicitCommandOverride = listOf(
                "me lembra", "lembra me", "cria um lembrete", "criar lembrete",
                "registra ", "registrar ", "lanca ", "lancar ", "anota ", "anotar "
            )
            if (explicitCommandOverride.none { normalized.contains(it) }) {
                return false
            }
        }

        val hasAmount = Regex("""\d+([.,]\d{1,2})?""").containsMatchIn(normalized) ||
            normalized.contains("r$") ||
            normalized.contains("reais")

        val actionVerbs = listOf(
            "gastei", "paguei", "comprei", "recebi", "vendi", "faturei", "ganhei",
            "registra", "registrar", "lanca", "lancar", "anota", "anotar",
            "adiciona", "adicionar", "transferi", "pix", "boleto", "fatura",
            "guardei", "depositei", "aportei", "coloquei", "juntei",
            "me lembra", "lembrete", "agendar", "agenda", "nova meta", "criar meta", "meta de", "na meta"
        )

        if (actionVerbs.any { normalized.contains(it) } &&
            (hasAmount || normalized.contains("lembra") || normalized.contains("lembrete") || normalized.contains("meta"))
        ) {
            return true
        }

        return false
    }

    private fun currentDateTimeContextLine(): String {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val formatted = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val weekday = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("pt-BR"))
        return "Data/hora atual do dispositivo: $formatted ($weekday) | Fuso horário: ${zone.id}"
    }

    fun streamConversation(
        userMessage: String,
        activeScope: FinanceScope,
        recentHistory: List<AlfredChatMessage>,
        financialContextSummary: String,
    ): Flow<AlfredStreamChunk> = flow {
        val sdkHistory = recentHistory
            .filter { it.text.isNotBlank() }
            .takeLast(8)
            .map { msg ->
                val role = if (msg.sender == ChatSender.USER.wireValue) "user" else "model"
                content(role) { text(msg.text) }
            }

        val contextualTurn = """
            [${currentDateTimeContextLine()}]
            [Contexto: ${activeScope.labelPt} (${activeScope.wireValue})]
            $financialContextSummary

            [Mensagem atual do usuário]
            $userMessage
        """.trimIndent()

        var emittedAny = false
        val accumulated = StringBuilder()

        suspend fun runStream(modelName: String, model: GenerativeModel) {
            val startedNs = System.nanoTime()
            var ttftMs = -1L
            Log.d(TAG, "streamConversation START model=$modelName")
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    val chat = model.startChat(history = sdkHistory)
                    chat.sendMessageStream(contextualTurn).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            if (ttftMs < 0L) {
                                ttftMs = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(1L)
                                Log.d(TAG, "streamConversation FIRST_TOKEN model=$modelName ttft=${ttftMs}ms")
                            }
                            emittedAny = true
                            accumulated.append(piece)
                            emit(
                                AlfredStreamChunk(
                                    accumulatedText = accumulated.toString(),
                                    firstTokenLatencyMs = ttftMs,
                                )
                            )
                        }
                    }
                }
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.d(TAG, "streamConversation END model=$modelName duration=${elapsedMs}ms ttft=${ttftMs}ms chars=${accumulated.length}")
            } catch (e: TimeoutCancellationException) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.w(TAG, "streamConversation TIMEOUT model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]")
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.e(TAG, "streamConversation FAIL model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]: ${e.message}", e)
                throw e
            }
        }

        try {
            runStream(PRIMARY_MODEL, primaryChatModel)
            if (accumulated.isBlank()) {
                throw IllegalStateException("O modelo $PRIMARY_MODEL retornou resposta vazia.")
            }
        } catch (primaryError: Throwable) {
            if (primaryError is CancellationException && primaryError !is TimeoutCancellationException) {
                throw primaryError
            }
            if (!emittedAny) {
                accumulated.clear()
                try {
                    runStream(FALLBACK_MODEL, fallbackChatModel)
                    if (accumulated.isBlank()) {
                        throw IllegalStateException("O modelo reserva $FALLBACK_MODEL retornou resposta vazia.")
                    }
                } catch (fallbackError: Throwable) {
                    if (fallbackError is CancellationException && fallbackError !is TimeoutCancellationException) {
                        throw fallbackError
                    }
                    throw AlfredAiException(formatAiError(fallbackError), fallbackError)
                }
            } else {
                throw AlfredAiException(formatAiError(primaryError), primaryError)
            }
        }
    }

    suspend fun interpretUserCommand(
        userMessage: String,
        receiptBitmap: Bitmap?,
        activeScope: FinanceScope,
        recentHistory: List<AlfredChatMessage>,
        financialContextSummary: String,
    ): AlfredMultiActionResponse {
        val recentDialogue = recentHistory
            .takeLast(6)
            .joinToString("\n") { msg ->
                val who = if (msg.sender == ChatSender.USER.wireValue) "Usuário" else "Alfred"
                "$who: ${msg.text.take(220)}"
            }

        val promptText = """
            ${currentDateTimeContextLine()}
            Escopo visual atual do usuário: ${activeScope.wireValue} (${activeScope.labelPt}).
            Contexto financeiro real atual:
            $financialContextSummary

            Histórico recente da conversa:
            ${recentDialogue.ifBlank { "(início da conversa)" }}

            Mensagem atual do usuário: "$userMessage"
        """.trimIndent()

        suspend fun executeOn(modelName: String, model: GenerativeModel): AlfredMultiActionResponse {
            val startedNs = System.nanoTime()
            Log.d(TAG, "interpretUserCommand START model=$modelName hasImage=${receiptBitmap != null}")
            try {
                val response = withTimeout(AI_TIMEOUT_MS) {
                    if (receiptBitmap != null) {
                        model.generateContent(
                            content {
                                image(receiptBitmap)
                                text(promptText)
                            }
                        )
                    } else {
                        model.generateContent(promptText)
                    }
                }

                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                val jsonText = response.text
                    ?: throw IllegalStateException("Alfred não recebeu resposta do modelo $modelName.")
                Log.d(TAG, "interpretUserCommand END model=$modelName duration=${elapsedMs}ms")

                val json = JSONObject(jsonText)
                val replyText = json.optString("replyText", "Às suas ordens, senhor.")
                val actionsJson = json.optJSONArray("actions") ?: JSONArray()
                val parsedActions = buildList {
                    for (i in 0 until actionsJson.length()) {
                        val obj = actionsJson.optJSONObject(i) ?: continue
                        val intent = obj.optString("intent", "").trim()
                        if (intent.isBlank()) continue
                        add(
                            AlfredParsedAction(
                                intent = intent,
                                scope = FinanceScope.fromWire(obj.optString("scope", activeScope.wireValue)),
                                transactionType = TransactionType.fromWire(obj.optString("transactionType", "EXPENSE")),
                                title = obj.optString("title", "").trim(),
                                category = obj.optString("category", "Outros").ifBlank { "Outros" },
                                amount = obj.optDouble("amount", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it },
                                accountName = obj.optString("accountName", "Carteira").ifBlank { "Carteira" },
                                isPaid = obj.optBoolean("isPaid", true),
                                dueDateText = obj.optString("dueDateText", ""),
                                dueAtIso = obj.optString("dueAtIso", ""),
                                recurrence = obj.optString("recurrence", "NONE").ifBlank { "NONE" },
                                actionSummary = obj.optString("actionSummary", ""),
                                transactionDateIso = obj.optString("transactionDateIso", ""),
                            )
                        )
                    }
                }

                return AlfredMultiActionResponse(
                    replyText = replyText,
                    actions = parsedActions,
                )
            } catch (e: TimeoutCancellationException) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.w(TAG, "interpretUserCommand TIMEOUT model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]")
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.e(TAG, "interpretUserCommand FAIL model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]: ${e.message}", e)
                throw e
            }
        }

        return try {
            executeOn(PRIMARY_MODEL, primaryStructuredModel)
        } catch (primaryError: Throwable) {
            if (primaryError is CancellationException && primaryError !is TimeoutCancellationException) {
                throw primaryError
            }
            Log.w(TAG, "Tentando modelo reserva $FALLBACK_MODEL após falha em $PRIMARY_MODEL [${primaryError::class.java.simpleName}]")
            try {
                executeOn(FALLBACK_MODEL, fallbackStructuredModel)
            } catch (fallbackError: Throwable) {
                if (fallbackError is CancellationException && fallbackError !is TimeoutCancellationException) {
                    throw fallbackError
                }
                throw AlfredAiException(formatAiError(fallbackError), fallbackError)
            }
        }
    }

    fun streamInsight(
        scope: FinanceScope,
        financialContextSummary: String,
    ): Flow<String> = flow {
        val prompt = """
            ${currentDateTimeContextLine()}
            Analise o panorama atual de ${scope.labelPt} (${scope.wireValue}):
            $financialContextSummary
            Escreva um diagnóstico rápido e uma recomendação prática.
        """.trimIndent()

        var emittedAny = false
        val accumulated = StringBuilder()

        suspend fun runStream(modelName: String, model: GenerativeModel) {
            val startedNs = System.nanoTime()
            Log.d(TAG, "streamInsight START model=$modelName scope=${scope.wireValue}")
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    model.generateContentStream(prompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            emittedAny = true
                            accumulated.append(piece)
                            emit(accumulated.toString())
                        }
                    }
                }
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.d(TAG, "streamInsight END model=$modelName duration=${elapsedMs}ms")
            } catch (e: TimeoutCancellationException) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.w(TAG, "streamInsight TIMEOUT model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]")
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                Log.e(TAG, "streamInsight FAIL model=$modelName duration=${elapsedMs}ms [${e::class.java.simpleName}]: ${e.message}", e)
                throw e
            }
        }

        try {
            runStream(PRIMARY_MODEL, primaryInsightModel)
            if (accumulated.isBlank()) {
                throw IllegalStateException("O modelo $PRIMARY_MODEL retornou insight vazio.")
            }
        } catch (primaryError: Throwable) {
            if (primaryError is CancellationException && primaryError !is TimeoutCancellationException) {
                throw primaryError
            }
            if (!emittedAny) {
                accumulated.clear()
                try {
                    runStream(FALLBACK_MODEL, fallbackInsightModel)
                    if (accumulated.isBlank()) {
                        throw IllegalStateException("O modelo reserva $FALLBACK_MODEL retornou insight vazio.")
                    }
                } catch (fallbackError: Throwable) {
                    if (fallbackError is CancellationException && fallbackError !is TimeoutCancellationException) {
                        throw fallbackError
                    }
                    throw AlfredAiException(formatAiError(fallbackError), fallbackError)
                }
            } else {
                throw AlfredAiException(formatAiError(primaryError), primaryError)
            }
        }
    }

    private fun normalizeForMatch(input: String): String {
        val noAccents = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents.trim()
    }

    private fun formatAiError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        val raw = "${error.localizedMessage.orEmpty()} ${root.localizedMessage.orEmpty()}".trim()
        val lower = raw.lowercase()
        val baseMessage = when {
            error is TimeoutCancellationException || root is TimeoutCancellationException || lower.contains("timeout") ->
                "Tempo limite de 20s excedido nos modelos de IA. Verifique sua conexão ou registre manualmente."

            lower.contains("has not been used") ||
                lower.contains("not enabled") ||
                lower.contains("service_disabled") ||
                lower.contains("firebaseai") && lower.contains("disabled") ||
                lower.contains("generative language") && lower.contains("disabled") ->
                "A API do Firebase AI Logic não está habilitada neste projeto Firebase."

            lower.contains("403") ||
                lower.contains("app check") ||
                lower.contains("attestation") ||
                lower.contains("appcheck") ->
                "Falha de verificação do App Check (403). Confirme o token de debug no Console do Firebase."

            lower.contains("unknownhost") ||
                lower.contains("unable to resolve") ||
                lower.contains("failed to connect") ||
                lower.contains("network") ||
                lower.contains("socket") ->
                "Sem conexão estável de rede para falar com o Alfred. Você pode registrar manualmente."

            lower.contains("quota") || lower.contains("429") ->
                "Cota temporária da IA atingida (429). Tente em instantes ou registre manualmente."

            raw.isNotBlank() ->
                "Não foi possível obter resposta do Alfred: $raw"

            else ->
                "Não foi possível obter resposta do Alfred no momento."
        }
        return if (BuildConfig.DEBUG) {
            "$baseMessage [${error::class.java.simpleName}]"
        } else {
            baseMessage
        }
    }

    companion object {
        const val TAG = "AlfredAI"
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.8-flash"
        const val AI_TIMEOUT_MS = 20_000L
        const val PING_TIMEOUT_MS = 10_000L
        const val MAX_CHAT_OUTPUT_TOKENS = 500
        const val MAX_INSIGHT_OUTPUT_TOKENS = 240
    }
}

class AlfredAiException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
