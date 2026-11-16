package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountKind
import com.example.data.model.AlfredChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.data.remote.AlfredAiService
import com.example.data.repository.FinanceRepository
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.MonthHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface InsightUiState {
    data object Idle : InsightUiState
    data object Loading : InsightUiState
    data class Streaming(val text: String) : InsightUiState
    data class Success(val text: String) : InsightUiState
    data class Error(val message: String) : InsightUiState
}

sealed interface ChatStatus {
    data object Idle : ChatStatus
    data object Thinking : ChatStatus
    data class Streaming(val partialText: String) : ChatStatus
    data class Error(val message: String, val failedPrompt: String? = null) : ChatStatus
}

data class CategorySlice(
    val category: String,
    val amount: Double,
    val percentage: Float,
)

data class ScopeSummary(
    val scope: FinanceScope,
    val totalBalance: Double,
    val monthIncome: Double,
    val paidExpenses: Double,
    val pendingExpenses: Double,
    val pendingIncome: Double,
    val creditCardTotal: Double,
    val netMonthResult: Double,
    val monthTransactions: List<FinanceTransaction>,
    val scopeAccounts: List<FinanceAccount>,
    val scopeReminders: List<FinanceReminder>,
    val scopeGoals: List<FinanceGoal>,
    val expenseSlices: List<CategorySlice>,
    val incomeSlices: List<CategorySlice>,
)

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val aiService: AlfredAiService,
    private val currentUserId: String,
) : ViewModel() {

    private val _selectedMonthKey = MutableStateFlow(MonthHelper.DEFAULT_MONTH_KEY)
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _hideValues = MutableStateFlow(false)
    val hideValues: StateFlow<Boolean> = _hideValues.asStateFlow()

    private val _personalInsight = MutableStateFlow<InsightUiState>(InsightUiState.Idle)
    val personalInsight: StateFlow<InsightUiState> = _personalInsight.asStateFlow()

    private val _businessInsight = MutableStateFlow<InsightUiState>(InsightUiState.Idle)
    val businessInsight: StateFlow<InsightUiState> = _businessInsight.asStateFlow()

    private val _chatStatus = MutableStateFlow<ChatStatus>(ChatStatus.Idle)
    val chatStatus: StateFlow<ChatStatus> = _chatStatus.asStateFlow()

    private val _optimisticMessages = MutableStateFlow<List<AlfredChatMessage>>(emptyList())

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val transactionsState: StateFlow<UiState<List<FinanceTransaction>>> =
        repository.observeTransactions(currentUserId)
            .toUiState("Erro ao carregar transações")

    val accountsState: StateFlow<UiState<List<FinanceAccount>>> =
        repository.observeAccounts(currentUserId)
            .toUiState("Erro ao carregar contas e cartões")

    val remindersState: StateFlow<UiState<List<FinanceReminder>>> =
        repository.observeReminders(currentUserId)
            .toUiState("Erro ao carregar lembretes")

    val goalsState: StateFlow<UiState<List<FinanceGoal>>> =
        repository.observeGoals(currentUserId)
            .toUiState("Erro ao carregar metas")

    private val remoteChatMessagesState: StateFlow<UiState<List<AlfredChatMessage>>> =
        repository.observeChatMessages(currentUserId)
            .toUiState("Erro ao carregar conversa com o Alfred")

    val chatMessagesState: StateFlow<UiState<List<AlfredChatMessage>>> =
        combine(remoteChatMessagesState, _optimisticMessages) { remoteState, pending ->
            when (remoteState) {
                is UiState.Success -> {
                    val remote = remoteState.data
                    val unmatchedPending = pending.filter { local ->
                        remote.none { r -> r.sender == local.sender && r.text == local.text }
                    }
                    UiState.Success(remote + unmatchedPending)
                }
                is UiState.Loading -> {
                    if (pending.isNotEmpty()) UiState.Success(pending) else UiState.Loading
                }
                is UiState.Error -> {
                    if (pending.isNotEmpty()) UiState.Success(pending) else remoteState
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val personalSummary: StateFlow<ScopeSummary> = buildScopeSummary(FinanceScope.PERSONAL)
    val businessSummary: StateFlow<ScopeSummary> = buildScopeSummary(FinanceScope.BUSINESS)

    private fun buildScopeSummary(scope: FinanceScope): StateFlow<ScopeSummary> =
        combine(
            transactionsState,
            accountsState,
            remindersState,
            goalsState,
            selectedMonthKey,
        ) { txState, accState, remState, goalState, monthKey ->
            val allTx = (txState as? UiState.Success)?.data.orEmpty()
            val allAcc = (accState as? UiState.Success)?.data.orEmpty()
            val allRem = (remState as? UiState.Success)?.data.orEmpty()
            val allGoals = (goalState as? UiState.Success)?.data.orEmpty()

            val scopeTx = allTx.filter {
                it.scope == scope.wireValue && it.monthKey == monthKey
            }
            val scopeAccounts = allAcc.filter { it.scope == scope.wireValue }
            val scopeReminders = allRem.filter { it.scope == scope.wireValue }
            val scopeGoals = allGoals.filter { it.scope == scope.wireValue }

            val incomeTx = scopeTx.filter { it.type == TransactionType.INCOME.wireValue }
            val expenseTx = scopeTx.filter { it.type == TransactionType.EXPENSE.wireValue }

            val monthIncome = incomeTx.filter { it.isPaid }.sumOf { it.amount }
            val pendingIncome = incomeTx.filter { !it.isPaid }.sumOf { it.amount }
            val paidExpenses = expenseTx.filter { it.isPaid }.sumOf { it.amount }
            val pendingExpenses = expenseTx.filter { !it.isPaid }.sumOf { it.amount }

            val creditCardIds = scopeAccounts
                .filter { it.kind == AccountKind.CREDIT_CARD.wireValue }
                .map { it.id }
                .toSet()
            val creditCardTotal = expenseTx.filter {
                it.accountId in creditCardIds ||
                    it.accountName.contains("cartão", ignoreCase = true) ||
                    it.accountName.contains("crédito", ignoreCase = true)
            }.sumOf { it.amount }

            val accountsBalance = scopeAccounts
                .filter { it.kind != AccountKind.CREDIT_CARD.wireValue }
                .sumOf { it.balance }
            val netMonthResult = monthIncome - paidExpenses
            val totalBalance = accountsBalance + netMonthResult

            ScopeSummary(
                scope = scope,
                totalBalance = totalBalance,
                monthIncome = monthIncome,
                paidExpenses = paidExpenses,
                pendingExpenses = pendingExpenses,
                pendingIncome = pendingIncome,
                creditCardTotal = creditCardTotal,
                netMonthResult = netMonthResult,
                monthTransactions = scopeTx,
                scopeAccounts = scopeAccounts,
                scopeReminders = scopeReminders,
                scopeGoals = scopeGoals,
                expenseSlices = computeSlices(expenseTx),
                incomeSlices = computeSlices(incomeTx),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySummary(scope),
        )

    private fun computeSlices(items: List<FinanceTransaction>): List<CategorySlice> {
        val total = items.sumOf { it.amount }
        if (total <= 0.0) return emptyList()
        return items
            .groupBy { it.category.ifBlank { "Outros" } }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategorySlice(
                    category = cat,
                    amount = sum,
                    percentage = (sum / total).toFloat().coerceIn(0f, 1f),
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun emptySummary(scope: FinanceScope) = ScopeSummary(
        scope = scope,
        totalBalance = 0.0,
        monthIncome = 0.0,
        paidExpenses = 0.0,
        pendingExpenses = 0.0,
        pendingIncome = 0.0,
        creditCardTotal = 0.0,
        netMonthResult = 0.0,
        monthTransactions = emptyList(),
        scopeAccounts = emptyList(),
        scopeReminders = emptyList(),
        scopeGoals = emptyList(),
        expenseSlices = emptyList(),
        incomeSlices = emptyList(),
    )

    private fun <T> Flow<List<T>>.toUiState(defaultError: String): StateFlow<UiState<List<T>>> =
        map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, defaultError, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: defaultError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    fun selectMonth(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun toggleHideValues() {
        _hideValues.value = !_hideValues.value
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun clearChatError() {
        _chatStatus.value = ChatStatus.Idle
    }

    fun addTransaction(
        scope: FinanceScope,
        type: TransactionType,
        title: String,
        category: String,
        amount: Double,
        account: FinanceAccount?,
        isPaid: Boolean,
        isRecurring: Boolean,
        notes: String = "",
    ) {
        if (amount <= 0.0 || title.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    FinanceTransaction(
                        scope = scope.wireValue,
                        type = type.wireValue,
                        title = title.trim(),
                        category = category.trim().ifBlank { "Outros" },
                        amount = amount,
                        accountId = account?.id.orEmpty(),
                        accountName = account?.name ?: if (scope == FinanceScope.BUSINESS) "Caixa PJ" else "Carteira",
                        monthKey = _selectedMonthKey.value,
                        dayOfMonth = 15,
                        isPaid = isPaid,
                        isRecurring = isRecurring,
                        notes = notes,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Não foi possível salvar o lançamento."
            }
        }
    }

    fun toggleTransactionPaid(tx: FinanceTransaction) {
        viewModelScope.launch {
            try {
                repository.toggleTransactionPaid(tx.id, !tx.isPaid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao atualizar status."
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transactionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao remover lançamento."
            }
        }
    }

    fun addAccount(
        scope: FinanceScope,
        name: String,
        kind: AccountKind,
        balance: Double,
        creditLimit: Double,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addAccount(
                    FinanceAccount(
                        scope = scope.wireValue,
                        name = name.trim(),
                        kind = kind.wireValue,
                        balance = balance,
                        creditLimit = creditLimit,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao adicionar conta."
            }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAccount(accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao remover conta."
            }
        }
    }

    fun addReminder(
        scope: FinanceScope,
        title: String,
        amount: Double,
        dueDateText: String,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addReminder(
                    FinanceReminder(
                        scope = scope.wireValue,
                        title = title.trim(),
                        amount = amount.coerceAtLeast(0.0),
                        dueDateText = dueDateText.ifBlank { "Em breve" },
                        monthKey = _selectedMonthKey.value,
                        isCompleted = false,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao criar lembrete."
            }
        }
    }

    fun toggleReminderCompleted(reminder: FinanceReminder) {
        viewModelScope.launch {
            try {
                repository.toggleReminderCompleted(reminder.id, !reminder.isCompleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao atualizar lembrete."
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                repository.deleteReminder(reminderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao remover lembrete."
            }
        }
    }

    fun addGoal(
        scope: FinanceScope,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        deadlineText: String,
    ) {
        if (title.isBlank() || targetAmount <= 0.0) return
        viewModelScope.launch {
            try {
                repository.addGoal(
                    FinanceGoal(
                        scope = scope.wireValue,
                        title = title.trim(),
                        targetAmount = targetAmount,
                        currentAmount = currentAmount.coerceAtLeast(0.0),
                        deadlineText = deadlineText.trim(),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao criar meta."
            }
        }
    }

    fun contributeToGoal(goal: FinanceGoal, delta: Double) {
        if (delta <= 0.0) return
        viewModelScope.launch {
            try {
                repository.updateGoalAmount(goal.id, goal.currentAmount + delta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao aportar na meta."
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            try {
                repository.deleteGoal(goalId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao excluir meta."
            }
        }
    }

    fun requestInsight(scope: FinanceScope) {
        val targetFlow = if (scope == FinanceScope.PERSONAL) _personalInsight else _businessInsight
        if (targetFlow.value is InsightUiState.Loading || targetFlow.value is InsightUiState.Streaming) {
            return
        }
        viewModelScope.launch {
            targetFlow.value = InsightUiState.Loading
            try {
                val contextText = buildContextSummaryForAi(scope)
                var latestText = ""
                aiService.streamInsight(scope, contextText).collect { partial ->
                    latestText = partial
                    targetFlow.value = InsightUiState.Streaming(partial)
                }
                targetFlow.value = InsightUiState.Success(latestText)
            } catch (e: TimeoutCancellationException) {
                targetFlow.value = InsightUiState.Error("Tempo limite excedido ao consultar o Alfred.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                targetFlow.value = InsightUiState.Error(
                    e.localizedMessage ?: "Não foi possível gerar o insight do Alfred."
                )
            }
        }
    }

    fun sendChatMessage(
        text: String,
        receiptBitmap: Bitmap? = null,
        activeScope: FinanceScope = FinanceScope.PERSONAL,
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && receiptBitmap == null) return
        if (_chatStatus.value is ChatStatus.Thinking || _chatStatus.value is ChatStatus.Streaming) {
            return
        }
        val effectivePrompt = trimmed.ifBlank { "Analise este comprovante e registre o gasto ou receita." }

        val historySnapshot = (chatMessagesState.value as? UiState.Success)?.data.orEmpty()

        val userMessage = AlfredChatMessage(
            id = "local_user_${System.currentTimeMillis()}",
            userId = currentUserId,
            sender = ChatSender.USER.wireValue,
            text = effectivePrompt,
            scope = activeScope.wireValue,
            actionSummary = if (receiptBitmap != null) "Imagem anexada" else "",
        )
        _optimisticMessages.value = _optimisticMessages.value + userMessage
        _chatStatus.value = ChatStatus.Thinking

        // Persist user message without blocking AI response latency
        viewModelScope.launch {
            try {
                repository.addChatMessage(userMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist user chat message to Firestore", e)
            }
        }

        viewModelScope.launch {
            try {
                val fullContext = buildString {
                    appendLine(buildContextSummaryForAi(FinanceScope.PERSONAL))
                    appendLine()
                    appendLine(buildContextSummaryForAi(FinanceScope.BUSINESS))
                }

                if (aiService.shouldExtractStructuredAction(effectivePrompt, receiptBitmap)) {
                    val parsed = aiService.interpretUserCommand(
                        userMessage = effectivePrompt,
                        receiptBitmap = receiptBitmap,
                        activeScope = activeScope,
                        recentHistory = historySnapshot,
                        financialContextSummary = fullContext,
                    )

                    var recordedBadge = parsed.actionSummary
                    when (parsed.intent) {
                        "CREATE_TRANSACTION" -> {
                            if (parsed.amount > 0.0) {
                                repository.addTransaction(
                                    FinanceTransaction(
                                        scope = parsed.scope.wireValue,
                                        type = parsed.transactionType.wireValue,
                                        title = parsed.title,
                                        category = parsed.category,
                                        amount = parsed.amount,
                                        accountName = parsed.accountName,
                                        monthKey = _selectedMonthKey.value,
                                        dayOfMonth = 15,
                                        isPaid = parsed.isPaid,
                                        isRecurring = false,
                                    )
                                )
                                val tipo = if (parsed.transactionType == TransactionType.INCOME) "Receita" else "Despesa"
                                recordedBadge = "${parsed.scope.labelPt} • $tipo ${CurrencyFormatter.formatBrl(parsed.amount)}"
                            }
                        }

                        "CREATE_REMINDER" -> {
                            repository.addReminder(
                                FinanceReminder(
                                    scope = parsed.scope.wireValue,
                                    title = parsed.title,
                                    amount = parsed.amount.coerceAtLeast(0.0),
                                    dueDateText = parsed.dueDateText.ifBlank { "Em breve" },
                                    monthKey = _selectedMonthKey.value,
                                    isCompleted = false,
                                )
                            )
                            recordedBadge = "${parsed.scope.labelPt} • Lembrete: ${parsed.dueDateText}"
                        }

                        "CREATE_GOAL" -> {
                            val target = if (parsed.amount > 0.0) parsed.amount else 1000.0
                            repository.addGoal(
                                FinanceGoal(
                                    scope = parsed.scope.wireValue,
                                    title = parsed.title,
                                    targetAmount = target,
                                    currentAmount = 0.0,
                                    deadlineText = parsed.dueDateText.take(40),
                                )
                            )
                            recordedBadge = "${parsed.scope.labelPt} • Meta ${CurrencyFormatter.formatBrl(target)}"
                        }
                    }

                    val alfredMessage = AlfredChatMessage(
                        id = "local_alfred_${System.currentTimeMillis()}",
                        userId = currentUserId,
                        sender = ChatSender.ALFRED.wireValue,
                        text = parsed.replyText,
                        scope = parsed.scope.wireValue,
                        actionSummary = recordedBadge,
                    )
                    _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                    _chatStatus.value = ChatStatus.Idle
                    persistAlfredReplySafely(alfredMessage)
                } else {
                    var streamedText = ""
                    aiService.streamConversation(
                        userMessage = effectivePrompt,
                        activeScope = activeScope,
                        recentHistory = historySnapshot,
                        financialContextSummary = fullContext,
                    ).collect { partial ->
                        streamedText = partial
                        _chatStatus.value = ChatStatus.Streaming(partial)
                    }

                    val finalText = streamedText.trim()
                    if (finalText.isBlank()) {
                        _chatStatus.value = ChatStatus.Error(
                            message = "Alfred não retornou conteúdo. Tente novamente.",
                            failedPrompt = effectivePrompt,
                        )
                    } else {
                        val alfredMessage = AlfredChatMessage(
                            id = "local_alfred_${System.currentTimeMillis()}",
                            userId = currentUserId,
                            sender = ChatSender.ALFRED.wireValue,
                            text = finalText,
                            scope = activeScope.wireValue,
                            actionSummary = "",
                        )
                        _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                        _chatStatus.value = ChatStatus.Idle
                        persistAlfredReplySafely(alfredMessage)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Alfred chat timed out", e)
                _chatStatus.value = ChatStatus.Error(
                    message = "Alfred demorou mais que o esperado para responder. Toque em tentar novamente.",
                    failedPrompt = effectivePrompt,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Alfred chat error", e)
                _chatStatus.value = ChatStatus.Error(
                    message = e.localizedMessage ?: "Falha ao falar com o Alfred.",
                    failedPrompt = effectivePrompt,
                )
            }
        }
    }

    private fun persistAlfredReplySafely(message: AlfredChatMessage) {
        viewModelScope.launch {
            try {
                repository.addChatMessage(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist Alfred reply to Firestore", e)
                _actionError.value = "Resposta exibida, mas houve falha ao salvar no backup: ${e.localizedMessage ?: "erro de rede"}"
            }
        }
    }

    private fun buildContextSummaryForAi(scope: FinanceScope): String {
        val summary = if (scope == FinanceScope.PERSONAL) personalSummary.value else businessSummary.value
        val monthLabel = MonthHelper.labelFor(_selectedMonthKey.value)
        val topCategories = summary.expenseSlices.take(4)
            .joinToString(", ") { "${it.category}: R$ ${"%.2f".format(it.amount)}" }
            .ifBlank { "sem gastos registrados" }
        val accountsList = summary.scopeAccounts.take(5)
            .joinToString(", ") { "${it.name} (R$ ${"%.2f".format(it.balance)})" }
            .ifBlank { "nenhuma conta cadastrada" }
        val recentTx = summary.monthTransactions.take(6)
            .joinToString("; ") {
                val sign = if (it.type == TransactionType.INCOME.wireValue) "+" else "-"
                "${it.title} ($sign R$ ${"%.2f".format(it.amount)}, ${it.category})"
            }
            .ifBlank { "nenhum lançamento neste mês" }
        val openReminders = summary.scopeReminders.filter { !it.isCompleted }.take(4)
            .joinToString(", ") { "${it.title} (${it.dueDateText}, R$ ${"%.2f".format(it.amount)})" }
            .ifBlank { "nenhum" }
        val goalsList = summary.scopeGoals.take(4)
            .joinToString(", ") { "${it.title}: R$ ${"%.2f".format(it.currentAmount)} / R$ ${"%.2f".format(it.targetAmount)}" }
            .ifBlank { "nenhuma" }

        return """
            [${scope.labelPt} — $monthLabel]
            Saldo/Caixa total: R$ ${"%.2f".format(summary.totalBalance)}
            Receitas pagas no mês: R$ ${"%.2f".format(summary.monthIncome)} | A receber: R$ ${"%.2f".format(summary.pendingIncome)}
            Gastos pagos no mês: R$ ${"%.2f".format(summary.paidExpenses)} | A pagar: R$ ${"%.2f".format(summary.pendingExpenses)}
            Cartão de crédito no mês: R$ ${"%.2f".format(summary.creditCardTotal)}
            Contas/Cartões: $accountsList
            Principais categorias de gasto: $topCategories
            Últimas movimentações: $recentTx
            Lembretes abertos: $openReminders
            Metas: $goalsList
        """.trimIndent()
    }

    private companion object {
        const val TAG = "FinanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
