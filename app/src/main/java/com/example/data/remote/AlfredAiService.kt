package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AlfredChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.FinanceScope
import com.example.data.model.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.Normalizer

data class AlfredParsedResponse(
    val intent: String,
    val replyText: String,
    val scope: FinanceScope,
    val transactionType: TransactionType,
    val title: String,
    val category: String,
    val amount: Double,
    val accountName: String,
    val isPaid: Boolean,
    val dueDateText: String,
    val actionSummary: String,
)

class AlfredAiService {

    private val commandSchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.enumeration(
                values = listOf("CREATE_TRANSACTION", "CREATE_REMINDER", "CREATE_GOAL", "QUERY_OR_CHAT"),
                description = "Intenção financeira principal do comando do usuário"
            ),
            "replyText" to Schema.string(
                description = "Resposta educada, clara e objetiva do mordomo Alfred em português do Brasil"
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
                description = "Título curto do lançamento, lembrete ou meta"
            ),
            "category" to Schema.string(
                description = "Categoria curta em português (ex: Alimentação, Transporte, Moradia, Vendas, Serviços, Impostos, Software)"
            ),
            "amount" to Schema.double(
                description = "Valor numérico em Reais (BRL), 0.0 se não houver valor"
            ),
            "accountName" to Schema.string(
                description = "Conta ou cartão citado (ex: Cartão Principal, Nubank, Conta PJ, Caixa), ou Carteira"
            ),
            "isPaid" to Schema.boolean(
                description = "true se já foi pago ou recebido; false se for conta a pagar ou a receber"
            ),
            "dueDateText" to Schema.string(
                description = "Data/hora legível para lembretes (ex: Amanhã, 14:00)"
            ),
            "actionSummary" to Schema.string(
                description = "Resumo compacto da ação criada ou string vazia se for apenas conversa"
            ),
        ),
        optionalProperties = listOf("dueDateText", "actionSummary")
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
            Você é o Alfred, mordomo financeiro executivo particular do usuário (inspirado no Tony e no mordomo de Gotham).
            Responda sempre em português do Brasil, de forma elegante, precisa e prestativa.
            Analise mensagens de texto, transcrições de voz ou imagens de comprovantes/notas fiscais.
            Regra de separação estrita:
            - Use scope = "BUSINESS" quando o usuário mencionar empresa, negócio, cliente, venda, serviço prestado, fornecedor, nota fiscal PJ, imposto, faturamento ou caixa da empresa.
            - Use scope = "PERSONAL" quando for gasto pessoal, casa, mercado, almoço, academia, salário ou lazer — ou mantenha o escopo ativo sugerido se for ambíguo.
            - Só use intent = "CREATE_TRANSACTION" quando houver um valor > 0 claro (na mensagem ou na imagem).
            - Use intent = "CREATE_REMINDER" quando o usuário pedir para lembrar de pagar/cobrar algo.
            - Use intent = "CREATE_GOAL" quando pedir para criar uma meta de economia ou reserva.
            - Use intent = "QUERY_OR_CHAT" para perguntas sobre o saldo, resumos, conselhos de finanças/negócios ou conversas gerais.
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

    private fun buildChatModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = conversationalSystemInstruction,
        )

    private fun buildStructuredModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
            },
            systemInstruction = structuredSystemInstruction,
        )

    private fun buildInsightModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = insightSystemInstruction,
        )

    private val primaryChatModel by lazy { buildChatModel(PRIMARY_MODEL) }
    private val fallbackChatModel by lazy { buildChatModel(FALLBACK_MODEL) }

    private val primaryStructuredModel by lazy { buildStructuredModel(PRIMARY_MODEL) }
    private val fallbackStructuredModel by lazy { buildStructuredModel(FALLBACK_MODEL) }

    private val primaryInsightModel by lazy { buildInsightModel(PRIMARY_MODEL) }
    private val fallbackInsightModel by lazy { buildInsightModel(FALLBACK_MODEL) }

    fun shouldExtractStructuredAction(
        userMessage: String,
        receiptBitmap: Bitmap?,
    ): Boolean {
        if (receiptBitmap != null) return true
        val normalized = normalizeForMatch(userMessage)
        if (normalized.isBlank()) return false

        // Purely conversational / analytical questions should stream naturally
        val questionStarters = listOf(
            "como ", "qual ", "quais ", "quanto ", "quantos ", "por que ", "porque ",
            "o que ", "voce acha", "me explica", "me ajude", "me ajuda", "dica", "dicas",
            "estrategia", "conselho", "conversar", "falar sobre", "analisa", "analise",
            "resumo", "panorama", "vale a pena", "compensa", "devo ", "posso ", "preciso de ajuda"
        )
        if (normalized.endsWith("?") || questionStarters.any { normalized.startsWith(it) || normalized.contains(" $it") }) {
            // Still allow explicit "me lembra ..." or "pode registrar ..."
            val explicitCommandOverride = listOf(
                "me lembra", "lembra me", "cria um lembrete", "criar lembrete",
                "registra ", "registrar ", "lanca ", "lançar ", "lancar ", "anota ", "anotar "
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
            "me lembra", "lembrete", "agendar", "agenda", "nova meta", "criar meta", "meta de"
        )

        if (actionVerbs.any { normalized.contains(it) } &&
            (hasAmount || normalized.contains("lembra") || normalized.contains("lembrete") || normalized.contains("meta"))
        ) {
            return true
        }

        return false
    }

    fun streamConversation(
        userMessage: String,
        activeScope: FinanceScope,
        recentHistory: List<AlfredChatMessage>,
        financialContextSummary: String,
    ): Flow<String> = flow {
        val sdkHistory = recentHistory
            .filter { it.text.isNotBlank() }
            .takeLast(12)
            .map { msg ->
                val role = if (msg.sender == ChatSender.USER.wireValue) "user" else "model"
                content(role) { text(msg.text) }
            }

        val contextualTurn = """
            [Contexto financeiro atualizado — Escopo selecionado: ${activeScope.labelPt} (${activeScope.wireValue})]
            $financialContextSummary

            [Mensagem atual do usuário]
            $userMessage
        """.trimIndent()

        var emittedAny = false
        val accumulated = StringBuilder()

        suspend fun runStream(model: GenerativeModel) {
            withTimeout(AI_TIMEOUT_MS) {
                val chat = model.startChat(history = sdkHistory)
                chat.sendMessageStream(contextualTurn).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        emittedAny = true
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
        }

        try {
            runStream(primaryChatModel)
            if (accumulated.isBlank()) {
                throw IllegalStateException("O modelo retornou uma resposta vazia.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Conversational stream timed out on $PRIMARY_MODEL", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (primaryError: Exception) {
            Log.w(TAG, "Primary chat model $PRIMARY_MODEL failed; emittedAny=$emittedAny", primaryError)
            if (!emittedAny) {
                accumulated.clear()
                try {
                    runStream(fallbackChatModel)
                    if (accumulated.isBlank()) {
                        throw IllegalStateException("O modelo alternativo retornou uma resposta vazia.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Fallback chat model $FALLBACK_MODEL also failed", fallbackError)
                    throw IllegalStateException(formatAiError(fallbackError), fallbackError)
                }
            } else {
                throw IllegalStateException(formatAiError(primaryError), primaryError)
            }
        }
    }

    suspend fun interpretUserCommand(
        userMessage: String,
        receiptBitmap: Bitmap?,
        activeScope: FinanceScope,
        recentHistory: List<AlfredChatMessage>,
        financialContextSummary: String,
    ): AlfredParsedResponse {
        val recentDialogue = recentHistory
            .takeLast(6)
            .joinToString("\n") { msg ->
                val who = if (msg.sender == ChatSender.USER.wireValue) "Usuário" else "Alfred"
                "$who: ${msg.text.take(220)}"
            }

        val promptText = """
            Escopo visual atual do usuário: ${activeScope.wireValue} (${activeScope.labelPt}).
            Contexto financeiro real atual:
            $financialContextSummary

            Histórico recente da conversa:
            ${recentDialogue.ifBlank { "(início da conversa)" }}

            Mensagem atual do usuário: "$userMessage"
        """.trimIndent()

        suspend fun executeOn(model: GenerativeModel): AlfredParsedResponse {
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

            val jsonText = response.text
                ?: throw IllegalStateException("Alfred não recebeu resposta do modelo.")
            val json = JSONObject(jsonText)

            return AlfredParsedResponse(
                intent = json.optString("intent", "QUERY_OR_CHAT"),
                replyText = json.optString("replyText", "Às suas ordens, senhor."),
                scope = FinanceScope.fromWire(json.optString("scope", activeScope.wireValue)),
                transactionType = TransactionType.fromWire(json.optString("transactionType", "EXPENSE")),
                title = json.optString("title", "Lançamento").ifBlank { "Lançamento" },
                category = json.optString("category", "Outros").ifBlank { "Outros" },
                amount = json.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else it },
                accountName = json.optString("accountName", "Carteira").ifBlank { "Carteira" },
                isPaid = json.optBoolean("isPaid", true),
                dueDateText = json.optString("dueDateText", "Em breve"),
                actionSummary = json.optString("actionSummary", ""),
            )
        }

        return try {
            executeOn(primaryStructuredModel)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured command timed out on $PRIMARY_MODEL", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (primaryError: Exception) {
            Log.w(TAG, "Primary structured model $PRIMARY_MODEL failed, trying $FALLBACK_MODEL", primaryError)
            try {
                executeOn(fallbackStructuredModel)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Structured command timed out on $FALLBACK_MODEL", e)
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback structured model $FALLBACK_MODEL failed", fallbackError)
                throw IllegalStateException(formatAiError(fallbackError), fallbackError)
            }
        }
    }

    fun streamInsight(
        scope: FinanceScope,
        financialContextSummary: String,
    ): Flow<String> = flow {
        val prompt = """
            Analise o panorama atual de ${scope.labelPt} (${scope.wireValue}):
            $financialContextSummary
            Escreva um diagnóstico rápido e uma recomendação prática.
        """.trimIndent()

        var emittedAny = false
        val accumulated = StringBuilder()

        suspend fun runStream(model: GenerativeModel) {
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
        }

        try {
            runStream(primaryInsightModel)
            if (accumulated.isBlank()) {
                throw IllegalStateException("Resposta vazia ao gerar insight.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Insight stream timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (primaryError: Exception) {
            Log.w(TAG, "Primary insight model $PRIMARY_MODEL failed; emittedAny=$emittedAny", primaryError)
            if (!emittedAny) {
                accumulated.clear()
                try {
                    runStream(fallbackInsightModel)
                    if (accumulated.isBlank()) {
                        throw IllegalStateException("Resposta vazia ao gerar insight.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Fallback insight model $FALLBACK_MODEL failed", fallbackError)
                    throw IllegalStateException(formatAiError(fallbackError), fallbackError)
                }
            } else {
                throw IllegalStateException(formatAiError(primaryError), primaryError)
            }
        }
    }

    private fun normalizeForMatch(input: String): String {
        val noAccents = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents.trim()
    }

    private fun formatAiError(error: Throwable): String {
        val raw = error.localizedMessage ?: error.message ?: ""
        val lower = raw.lowercase()
        return when {
            lower.contains("403") || lower.contains("app check") || lower.contains("attestation") ->
                "Falha de verificação do App Check/Firebase AI (403). Reabra o app ou verifique a conexão."
            lower.contains("404") || lower.contains("not found") || lower.contains("model") ->
                "Modelo de IA indisponível no momento ($raw)."
            lower.contains("network") || lower.contains("unable to resolve") || lower.contains("connect") ->
                "Sem conexão estável para falar com o Alfred. Verifique sua internet."
            lower.contains("quota") || lower.contains("429") ->
                "Limite temporário de requisições atingido. Aguarde alguns segundos."
            raw.isNotBlank() ->
                "Não foi possível obter resposta do Alfred: $raw"
            else ->
                "Não foi possível obter resposta do Alfred no momento."
        }
    }

    private companion object {
        const val TAG = "AlfredAiService"
        const val PRIMARY_MODEL = "gemini-3.8-flash"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
