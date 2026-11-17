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
import com.example.data.model.ReminderRecurrence
import com.example.data.model.ReminderScheduleHelper
import com.example.data.model.TransactionType
import com.example.data.remote.AlfredAiService
import com.example.data.remote.AlfredParsedAction
import com.example.data.remote.GoalMatcher
import com.example.data.remote.LocalCommandParser
import com.example.data.remote.LocalQueryResponder
import com.example.data.remote.ManualEntryDraft
import com.example.data.remote.WeeklySummaryBuilder
import com.example.data.repository.FinanceRepository
import com.example.notifications.AlfredNotificationBridge
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.MonthHelper
import com.google.firebase.Timestamp
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

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
    data class Streaming(
        val partialText: String,
        val ttftMs: Long? = null,
    ) : ChatStatus
    data class Error(
        val message: String,
        val failedPrompt: String? = null,
        val manualDraft: ManualEntryDraft? = null,
    ) : ChatStatus
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
    private val notificationBridge: AlfredNotificationBridge? = null,
) : ViewModel() {

    private val _selectedMonthKey = MutableStateFlow(MonthHelper.currentMonthKey())
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
    private val _debugTtftByContent = MutableStateFlow<Map<String, Long>>(emptyMap())

    private data class RetryCommandState(
        val txIds: MutableList<String> = mutableListOf(),
        val reminderIds: MutableList<String> = mutableListOf(),
        val goalIds: MutableList<String> = mutableListOf(),
        val completedGoalKeys: MutableSet<String> = mutableSetOf(),
    )

    private val retryStates = mutableMapOf<String, RetryCommandState>()

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
        combine(
            remoteChatMessagesState,
            _optimisticMessages,
            _debugTtftByContent,
        ) { remoteState, pending, ttftMap ->
            fun attachTtft(items: List<AlfredChatMessage>): List<AlfredChatMessage> =
                if (ttftMap.isEmpty()) {
                    items
                } else {
                    items.map { msg ->
                        if (msg.debugTtftMs != null) {
                            msg
                        } else {
                            val tracked = ttftMap[msg.text]
                            if (tracked != null && msg.sender == ChatSender.ALFRED.wireValue) {
                                msg.copy(debugTtftMs = tracked)
                            } else {
                                msg
                            }
                        }
                    }
                }

            when (remoteState) {
                is UiState.Success -> {
                    val remote = remoteState.data
                    val unmatchedPending = pending.filter { local ->
                        remote.none { r -> r.sender == local.sender && r.text == local.text }
                    }
                    UiState.Success(attachTtft(remote + unmatchedPending))
                }
                is UiState.Loading -> {
                    if (pending.isNotEmpty()) UiState.Success(attachTtft(pending)) else UiState.Loading
                }
                is UiState.Error -> {
                    if (pending.isNotEmpty()) UiState.Success(attachTtft(pending)) else remoteState
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val personalSummary: StateFlow<ScopeSummary> = buildScopeSummary(FinanceScope.PERSONAL)
    val businessSummary: StateFlow<ScopeSummary> = buildScopeSummary(FinanceScope.BUSINESS)

    private var hasSeededGoals = false

    init {
        if (notificationBridge != null) {
            viewModelScope.launch {
                remindersState.collect { state ->
                    if (state is UiState.Success) {
                        runCatching { notificationBridge.syncReminders(state.data) }
                            .onFailure { Log.w(TAG, "Falha ao sincronizar alarmes de lembretes", it) }
                    }
                }
            }
            viewModelScope.launch {
                goalsState.collect { state ->
                    if (state is UiState.Success) {
                        runCatching {
                            if (!hasSeededGoals) {
                                hasSeededGoals = true
                                notificationBridge.seedGoalMilestonesIfAbsent(state.data)
                            } else {
                                notificationBridge.checkGoalMilestones(state.data)
                            }
                        }.onFailure { Log.w(TAG, "Falha ao checar marcos de metas", it) }
                    }
                }
            }
        }
    }

    fun resyncNotifications() {
        val bridge = notificationBridge ?: return
        val reminders = (remindersState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            runCatching { bridge.syncReminders(reminders) }
                .onFailure { Log.w(TAG, "Falha ao reagendar notificações", it) }
        }
    }

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
                        dayOfMonth = LocalDate.now().dayOfMonth,
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
        dueAt: Timestamp? = null,
        recurrence: ReminderRecurrence = ReminderRecurrence.NONE,
        monthKey: String = _selectedMonthKey.value,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                val anchorDay = dueAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.dayOfMonth ?: 0
                repository.addReminder(
                    FinanceReminder(
                        scope = scope.wireValue,
                        title = title.trim(),
                        amount = amount.coerceAtLeast(0.0),
                        dueDateText = dueDateText.ifBlank { "Em breve" },
                        dueAt = dueAt,
                        recurrence = recurrence.wireValue,
                        monthKey = monthKey,
                        anchorDay = anchorDay,
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

    fun updateReminder(
        reminderId: String,
        title: String,
        amount: Double,
        dueDateText: String,
        dueAt: Timestamp?,
        recurrence: ReminderRecurrence,
        monthKey: String = _selectedMonthKey.value,
    ) {
        if (reminderId.isBlank() || title.isBlank()) return
        viewModelScope.launch {
            try {
                val anchorDay = dueAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.dayOfMonth ?: 0
                repository.updateReminder(
                    reminderId = reminderId,
                    title = title.trim(),
                    amount = amount.coerceAtLeast(0.0),
                    dueDateText = dueDateText.ifBlank { "Em breve" },
                    dueAt = dueAt,
                    recurrence = recurrence.wireValue,
                    monthKey = monthKey,
                    anchorDay = anchorDay,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Falha ao atualizar lembrete."
            }
        }
    }

    fun toggleReminderCompleted(reminder: FinanceReminder) {
        viewModelScope.launch {
            try {
                val markingComplete = !reminder.isCompleted
                val recurrence = ReminderRecurrence.fromWire(reminder.recurrence)
                if (markingComplete && recurrence != ReminderRecurrence.NONE) {
                    val zone = ZoneId.systemDefault()
                    val nowMillis = System.currentTimeMillis()
                    val baseMillis = reminder.dueAt?.toDate()?.time ?: nowMillis
                    val anchorDay = reminder.anchorDay.takeIf { it in 1..31 }
                        ?: Instant.ofEpochMilli(baseMillis).atZone(zone).dayOfMonth
                    val nextMillis = ReminderScheduleHelper.computeNextOccurrenceMillis(
                        currentDueAtMillis = baseMillis,
                        recurrence = recurrence,
                        zoneId = zone,
                        nowMillis = nowMillis,
                        anchorDayOfMonth = anchorDay,
                    )
                    if (nextMillis != null) {
                        val nextId = ReminderScheduleHelper.deterministicNextOccurrenceId(reminder.id, nextMillis, zone)
                        val nextDueAt = Timestamp(Date(nextMillis))
                        val nextDueDateText = ReminderScheduleHelper.formatDueDateText(nextMillis, zoneId = zone)
                        val nextMonthKey = Instant.ofEpochMilli(nextMillis)
                            .atZone(zone)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.US))
                        repository.completeRecurringReminder(
                            reminder = reminder,
                            nextOccurrenceId = nextId,
                            nextDueAt = nextDueAt,
                            nextDueDateText = nextDueDateText,
                            nextMonthKey = nextMonthKey,
                            nextAnchorDay = anchorDay,
                        )
                        return@launch
                    }
                }
                if (!markingComplete && recurrence != ReminderRecurrence.NONE && reminder.generatedNextId.isNotBlank()) {
                    repository.reopenRecurringReminder(
                        reminderId = reminder.id,
                        generatedNextId = reminder.generatedNextId,
                    )
                    return@launch
                }
                repository.toggleReminderCompleted(reminder.id, markingComplete)
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
                val updatedAmount = goal.currentAmount + delta
                repository.updateGoalAmount(goal.id, updatedAmount)
                notificationBridge?.let { bridge ->
                    runCatching {
                        bridge.checkGoalMilestones(listOf(goal.copy(currentAmount = updatedAmount)))
                    }
                }
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
                targetFlow.value = if (latestText.isNotBlank()) {
                    InsightUiState.Success(latestText)
                } else {
                    InsightUiState.Error("Alfred não retornou o insight. Toque para tentar novamente.")
                }
            } catch (e: TimeoutCancellationException) {
                targetFlow.value = InsightUiState.Error("Tempo limite de 20s excedido ao consultar o Alfred.")
            } catch (e: CancellationException) {
                if (targetFlow.value is InsightUiState.Loading || targetFlow.value is InsightUiState.Streaming) {
                    targetFlow.value = InsightUiState.Idle
                }
                throw e
            } catch (e: Exception) {
                targetFlow.value = InsightUiState.Error(
                    e.localizedMessage ?: "Não foi possível gerar o insight do Alfred."
                )
            }
        }
    }

    fun requestWeeklySummary(scope: FinanceScope) {
        val allTx = (transactionsState.value as? UiState.Success)?.data.orEmpty()
        val allRem = (remindersState.value as? UiState.Success)?.data.orEmpty()
        val allGoals = (goalsState.value as? UiState.Success)?.data.orEmpty()
        val report = WeeklySummaryBuilder.build(
            scope = scope,
            transactions = allTx,
            reminders = allRem,
            goals = allGoals,
        )
        val alfredMessage = AlfredChatMessage(
            id = "local_alfred_${System.currentTimeMillis()}",
            userId = currentUserId,
            sender = ChatSender.ALFRED.wireValue,
            text = report.text,
            scope = scope.wireValue,
            actionSummary = report.badge,
        )
        _optimisticMessages.value = _optimisticMessages.value + alfredMessage
        persistAlfredReplySafely(alfredMessage)
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
        val retryKey = "${activeScope.wireValue}|$effectivePrompt"
        val retryState = retryStates.getOrPut(retryKey) { RetryCommandState() }

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

        // Persist user message without blocking command processing or AI latency
        viewModelScope.launch {
            try {
                repository.addChatMessage(userMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao salvar mensagem do usuário no Firestore: ${e.message}", e)
            }
        }

        viewModelScope.launch {
            val draftHint = LocalCommandParser.extractManualDraft(effectivePrompt, activeScope)
            try {
                val knownAccounts = (accountsState.value as? UiState.Success)?.data.orEmpty()
                val allGoals = (goalsState.value as? UiState.Success)?.data.orEmpty()
                val localBadges = mutableListOf<String>()
                val localReplies = mutableListOf<String>()
                var primaryOutputScope = activeScope
                var txIdx = 0
                var remIdx = 0
                var goalIdx = 0

                fun nextId(bucket: MutableList<String>, index: Int, collection: String): String {
                    while (bucket.size <= index) {
                        bucket.add(repository.generateDocumentId(collection))
                    }
                    return bucket[index]
                }

                // 1) Compound local parser (no AI) for transactions & goal contributions when no receipt bitmap
                val splitResult = if (receiptBitmap == null) {
                    LocalCommandParser.splitAndResolve(effectivePrompt, activeScope, knownAccounts)
                } else {
                    null
                }

                if (splitResult != null) {
                    for (localParsed in splitResult.localTransactions) {
                        if (localParsed.amount <= 0.0 || localParsed.title.isBlank()) continue
                        val matchedAccount = knownAccounts.firstOrNull { acc ->
                            acc.scope == localParsed.scope.wireValue &&
                                localParsed.accountName.isNotBlank() &&
                                acc.name.equals(localParsed.accountName, ignoreCase = true)
                        }
                        val resolvedAccountName = matchedAccount?.name
                            ?: localParsed.accountName.ifBlank {
                                if (localParsed.scope == FinanceScope.BUSINESS) "Caixa PJ" else "Carteira"
                            }
                        val presetTxId = nextId(retryState.txIds, txIdx++, "transactions")
                        val txDate = localParsed.date
                        repository.addTransaction(
                            tx = FinanceTransaction(
                                scope = localParsed.scope.wireValue,
                                type = localParsed.type.wireValue,
                                title = localParsed.title,
                                category = localParsed.category,
                                amount = localParsed.amount,
                                accountId = matchedAccount?.id.orEmpty(),
                                accountName = resolvedAccountName,
                                monthKey = MonthHelper.currentMonthKey(txDate),
                                dayOfMonth = txDate.dayOfMonth,
                                isPaid = localParsed.isPaid,
                                isRecurring = false,
                            ),
                            presetId = presetTxId,
                        )
                        val tipo = if (localParsed.type == TransactionType.INCOME) "Receita" else "Despesa"
                        localBadges.add("${localParsed.scope.labelPt} • $tipo ${CurrencyFormatter.formatBrl(localParsed.amount)}")
                        localReplies.add(localParsed.toAlfredConfirmation())
                        primaryOutputScope = localParsed.scope
                    }

                    for ((gIdx, contrib) in splitResult.localGoalContributions.withIndex()) {
                        val matchedGoal = GoalMatcher.findBestMatch(contrib.goalQuery, allGoals, contrib.scope)
                        if (matchedGoal != null) {
                            val contribKey = "local:$gIdx:${matchedGoal.id}:${contrib.amount}"
                            if (contribKey !in retryState.completedGoalKeys) {
                                val updatedGoalAmount = matchedGoal.currentAmount + contrib.amount
                                repository.updateGoalAmount(matchedGoal.id, updatedGoalAmount)
                                retryState.completedGoalKeys.add(contribKey)
                                notificationBridge?.let { bridge ->
                                    runCatching {
                                        bridge.checkGoalMilestones(
                                            listOf(matchedGoal.copy(currentAmount = updatedGoalAmount))
                                        )
                                    }
                                }
                            }
                            val goalScope = FinanceScope.fromWire(matchedGoal.scope)
                            val updatedTotal = matchedGoal.currentAmount + contrib.amount
                            localBadges.add("${goalScope.labelPt} • +${CurrencyFormatter.formatBrl(contrib.amount)} em ${matchedGoal.title}")
                            localReplies.add(
                                "Aporte de ${CurrencyFormatter.formatBrl(contrib.amount)} adicionado à meta \"${matchedGoal.title}\" " +
                                    "(agora em ${CurrencyFormatter.formatBrl(updatedTotal)} de ${CurrencyFormatter.formatBrl(matchedGoal.targetAmount)})."
                            )
                            primaryOutputScope = goalScope
                        } else {
                            localReplies.add(
                                "Senhor, não encontrei uma meta correspondente a \"${contrib.goalQuery}\". " +
                                    "Pode informar o nome exato da meta ou pedir para eu criar uma nova?"
                            )
                        }
                    }
                }

                val remainingPrompt = if (receiptBitmap != null) {
                    effectivePrompt
                } else {
                    splitResult?.unresolvedPrompt.orEmpty()
                }

                // Pure local resolution when nothing unresolved remains
                if (receiptBitmap == null && remainingPrompt.isBlank() && (localReplies.isNotEmpty() || localBadges.isNotEmpty())) {
                    val alfredMessage = AlfredChatMessage(
                        id = "local_alfred_${System.currentTimeMillis()}",
                        userId = currentUserId,
                        sender = ChatSender.ALFRED.wireValue,
                        text = localReplies.joinToString("\n"),
                        scope = primaryOutputScope.wireValue,
                        actionSummary = localBadges.joinToString("\n"),
                    )
                    _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                    _chatStatus.value = ChatStatus.Idle
                    retryStates.remove(retryKey)
                    persistAlfredReplySafely(alfredMessage)
                    return@launch
                }

                // 2) Local instant query path (no AI) when no local writes happened
                if (receiptBitmap == null && localReplies.isEmpty()) {
                    val allTx = (transactionsState.value as? UiState.Success)?.data.orEmpty()
                    val queryReply = LocalQueryResponder.tryAnswer(
                        rawInput = effectivePrompt,
                        activeScope = activeScope,
                        personalSummary = personalSummary.value,
                        businessSummary = businessSummary.value,
                        allTransactions = allTx,
                        selectedMonthKey = _selectedMonthKey.value,
                    )
                    if (queryReply != null) {
                        val alfredMessage = AlfredChatMessage(
                            id = "local_alfred_${System.currentTimeMillis()}",
                            userId = currentUserId,
                            sender = ChatSender.ALFRED.wireValue,
                            text = queryReply.replyText,
                            scope = queryReply.scope.wireValue,
                            actionSummary = queryReply.badge,
                        )
                        _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                        _chatStatus.value = ChatStatus.Idle
                        retryStates.remove(retryKey)
                        persistAlfredReplySafely(alfredMessage)
                        return@launch
                    }
                }

                // 3) Gemini path (reduced context: active scope only, +1 line if other scope is mentioned)
                val aiInput = remainingPrompt.ifBlank { effectivePrompt }
                val scopedContext = buildChatContextForAi(activeScope, aiInput)

                if (aiService.shouldExtractStructuredAction(aiInput, receiptBitmap)) {
                    val parsed = aiService.interpretUserCommand(
                        userMessage = aiInput,
                        receiptBitmap = receiptBitmap,
                        activeScope = activeScope,
                        recentHistory = historySnapshot,
                        financialContextSummary = scopedContext,
                    )

                    val aiBadges = mutableListOf<String>()
                    val aiNotes = mutableListOf<String>()
                    var aiPrimaryScope: FinanceScope? = null

                    suspend fun executeAiAction(actionIdx: Int, action: AlfredParsedAction) {
                        if (aiPrimaryScope == null) aiPrimaryScope = action.scope
                        when (action.intent) {
                            "CREATE_TRANSACTION" -> {
                                val cleanTitle = action.title.trim()
                                if (action.amount > 0.0 && cleanTitle.isNotBlank()) {
                                    val matchedAccount = knownAccounts.firstOrNull { acc ->
                                        acc.scope == action.scope.wireValue &&
                                            action.accountName.isNotBlank() &&
                                            acc.name.equals(action.accountName, ignoreCase = true)
                                    }
                                    val resolvedAccount = matchedAccount?.name
                                        ?: action.accountName.ifBlank {
                                            if (action.scope == FinanceScope.BUSINESS) "Caixa PJ" else "Carteira"
                                        }
                                    val txDate = action.transactionDateIso.trim()
                                        .takeIf { it.isNotBlank() }
                                        ?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
                                        ?: LocalDate.now()
                                    val presetId = nextId(retryState.txIds, txIdx++, "transactions")
                                    repository.addTransaction(
                                        tx = FinanceTransaction(
                                            scope = action.scope.wireValue,
                                            type = action.transactionType.wireValue,
                                            title = cleanTitle,
                                            category = action.category,
                                            amount = action.amount,
                                            accountId = matchedAccount?.id.orEmpty(),
                                            accountName = resolvedAccount,
                                            monthKey = MonthHelper.currentMonthKey(txDate),
                                            dayOfMonth = txDate.dayOfMonth,
                                            isPaid = action.isPaid,
                                            isRecurring = false,
                                        ),
                                        presetId = presetId,
                                    )
                                    val tipo = if (action.transactionType == TransactionType.INCOME) "Receita" else "Despesa"
                                    aiBadges.add("${action.scope.labelPt} • $tipo ${CurrencyFormatter.formatBrl(action.amount)}")
                                } else {
                                    aiNotes.add("Senhor, preciso de um valor positivo e de uma descrição para registrar o lançamento.")
                                }
                            }

                            "CREATE_REMINDER" -> {
                                val cleanTitle = action.title.trim()
                                if (cleanTitle.isNotBlank()) {
                                    val parsedDue = ReminderScheduleHelper.parseDueAtIso(action.dueAtIso)
                                    val dueText = parsedDue?.displayText
                                        ?: action.dueDateText.ifBlank { "Em breve" }
                                    val rec = ReminderRecurrence.fromWire(action.recurrence)
                                    val monthKey = parsedDue?.monthKey ?: _selectedMonthKey.value
                                    val anchorDay = parsedDue?.epochMillis
                                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).dayOfMonth }
                                        ?: 0
                                    val presetId = nextId(retryState.reminderIds, remIdx++, "reminders")
                                    repository.addReminder(
                                        reminder = FinanceReminder(
                                            scope = action.scope.wireValue,
                                            title = cleanTitle,
                                            amount = action.amount.coerceAtLeast(0.0),
                                            dueDateText = dueText,
                                            dueAt = parsedDue?.timestamp,
                                            recurrence = rec.wireValue,
                                            monthKey = monthKey,
                                            anchorDay = anchorDay,
                                            isCompleted = false,
                                        ),
                                        presetId = presetId,
                                    )
                                    val recTag = if (rec != ReminderRecurrence.NONE) " (${rec.labelPt})" else ""
                                    aiBadges.add("${action.scope.labelPt} • Lembrete: $dueText$recTag")
                                } else {
                                    aiNotes.add("Senhor, qual título devo colocar neste lembrete?")
                                }
                            }

                            "CREATE_GOAL" -> {
                                val cleanTitle = action.title.trim()
                                if (action.amount > 0.0 && cleanTitle.isNotBlank()) {
                                    val presetId = nextId(retryState.goalIds, goalIdx++, "goals")
                                    repository.addGoal(
                                        goal = FinanceGoal(
                                            scope = action.scope.wireValue,
                                            title = cleanTitle,
                                            targetAmount = action.amount,
                                            currentAmount = 0.0,
                                            deadlineText = action.dueDateText.take(40),
                                        ),
                                        presetId = presetId,
                                    )
                                    aiBadges.add("${action.scope.labelPt} • Meta ${CurrencyFormatter.formatBrl(action.amount)}")
                                } else {
                                    aiNotes.add(
                                        if (cleanTitle.isNotBlank()) {
                                            "Senhor, qual valor alvo em reais você gostaria de definir para a meta \"$cleanTitle\"?"
                                        } else {
                                            "Senhor, informe o objetivo e o valor em reais para que eu crie a meta."
                                        }
                                    )
                                }
                            }

                            "CONTRIBUTE_GOAL" -> {
                                val queryTitle = action.title.trim()
                                val delta = action.amount
                                if (delta <= 0.0) {
                                    aiNotes.add("Senhor, qual valor você deseja aportar na meta?")
                                } else {
                                    val matchedGoal = GoalMatcher.findBestMatch(queryTitle, allGoals, action.scope)
                                    if (matchedGoal != null) {
                                        val contribKey = "ai:$actionIdx:${matchedGoal.id}:$delta"
                                        if (contribKey !in retryState.completedGoalKeys) {
                                            val updatedGoalAmount = matchedGoal.currentAmount + delta
                                            repository.updateGoalAmount(matchedGoal.id, updatedGoalAmount)
                                            retryState.completedGoalKeys.add(contribKey)
                                            notificationBridge?.let { bridge ->
                                                runCatching {
                                                    bridge.checkGoalMilestones(
                                                        listOf(matchedGoal.copy(currentAmount = updatedGoalAmount))
                                                    )
                                                }
                                            }
                                        }
                                        val goalScope = FinanceScope.fromWire(matchedGoal.scope)
                                        aiPrimaryScope = goalScope
                                        aiBadges.add("${goalScope.labelPt} • +${CurrencyFormatter.formatBrl(delta)} em ${matchedGoal.title}")
                                    } else {
                                        aiNotes.add(
                                            "Senhor, não encontrei uma meta correspondente a \"${queryTitle.ifBlank { "informada" }}\". " +
                                                "Pode dizer o nome exato ou pedir para eu criar essa meta?"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    parsed.actions.forEachIndexed { idx, action ->
                        executeAiAction(idx, action)
                    }

                    val combinedReplies = buildList {
                        addAll(localReplies)
                        if (parsed.replyText.isNotBlank() && aiNotes.isEmpty()) {
                            add(parsed.replyText.trim())
                        } else if (aiNotes.isNotEmpty()) {
                            if (parsed.actions.any { it.intent != "CONTRIBUTE_GOAL" } && parsed.replyText.isNotBlank()) {
                                add(parsed.replyText.trim())
                            }
                            addAll(aiNotes)
                        }
                    }
                    val allBadges = localBadges + aiBadges
                    val finalReplyText = combinedReplies.joinToString("\n").ifBlank { parsed.replyText }
                    val resolvedScope = aiPrimaryScope ?: primaryOutputScope

                    val alfredMessage = AlfredChatMessage(
                        id = "local_alfred_${System.currentTimeMillis()}",
                        userId = currentUserId,
                        sender = ChatSender.ALFRED.wireValue,
                        text = finalReplyText,
                        scope = resolvedScope.wireValue,
                        actionSummary = allBadges.joinToString("\n"),
                    )
                    _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                    _chatStatus.value = ChatStatus.Idle
                    retryStates.remove(retryKey)
                    persistAlfredReplySafely(alfredMessage)
                } else {
                    var streamedText = ""
                    var firstTokenLatencyMs: Long? = null
                    aiService.streamConversation(
                        userMessage = aiInput,
                        activeScope = activeScope,
                        recentHistory = historySnapshot,
                        financialContextSummary = scopedContext,
                    ).collect { chunk ->
                        streamedText = chunk.accumulatedText
                        if (firstTokenLatencyMs == null && chunk.firstTokenLatencyMs > 0L) {
                            firstTokenLatencyMs = chunk.firstTokenLatencyMs
                        }
                        val previewText = if (localReplies.isNotEmpty()) {
                            (localReplies + chunk.accumulatedText).joinToString("\n")
                        } else {
                            chunk.accumulatedText
                        }
                        _chatStatus.value = ChatStatus.Streaming(
                            partialText = previewText,
                            ttftMs = firstTokenLatencyMs,
                        )
                    }

                    val finalStream = streamedText.trim()
                    if (finalStream.isBlank() && localReplies.isEmpty()) {
                        _chatStatus.value = ChatStatus.Error(
                            message = "Alfred não retornou conteúdo. Tente novamente ou registre manualmente.",
                            failedPrompt = effectivePrompt,
                            manualDraft = draftHint,
                        )
                    } else {
                        val combinedFinal = (localReplies + listOfNotNull(finalStream.takeIf { it.isNotBlank() }))
                            .joinToString("\n")
                        if (firstTokenLatencyMs != null) {
                            _debugTtftByContent.value = _debugTtftByContent.value + (combinedFinal to firstTokenLatencyMs!!)
                        }
                        val alfredMessage = AlfredChatMessage(
                            id = "local_alfred_${System.currentTimeMillis()}",
                            userId = currentUserId,
                            sender = ChatSender.ALFRED.wireValue,
                            text = combinedFinal,
                            scope = primaryOutputScope.wireValue,
                            actionSummary = localBadges.joinToString("\n"),
                            debugTtftMs = firstTokenLatencyMs,
                        )
                        _optimisticMessages.value = _optimisticMessages.value + alfredMessage
                        _chatStatus.value = ChatStatus.Idle
                        retryStates.remove(retryKey)
                        persistAlfredReplySafely(alfredMessage)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("AlfredAI", "Timeout no chat [${e::class.java.simpleName}]", e)
                _chatStatus.value = ChatStatus.Error(
                    message = "Tempo limite excedido ao aguardar o modelo. Tente novamente ou registre manualmente.",
                    failedPrompt = effectivePrompt,
                    manualDraft = draftHint,
                )
            } catch (e: CancellationException) {
                if (_chatStatus.value is ChatStatus.Thinking || _chatStatus.value is ChatStatus.Streaming) {
                    _chatStatus.value = ChatStatus.Idle
                }
                throw e
            } catch (e: Exception) {
                Log.e("AlfredAI", "Erro no chat [${e::class.java.simpleName}]: ${e.message}", e)
                _chatStatus.value = ChatStatus.Error(
                    message = e.localizedMessage ?: "Falha ao falar com o Alfred.",
                    failedPrompt = effectivePrompt,
                    manualDraft = draftHint,
                )
            } finally {
                if (_chatStatus.value is ChatStatus.Thinking || _chatStatus.value is ChatStatus.Streaming) {
                    _chatStatus.value = ChatStatus.Idle
                }
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

    private fun buildChatContextForAi(activeScope: FinanceScope, userPrompt: String): String {
        val primarySummary = buildContextSummaryForAi(activeScope)
        val otherScope = if (activeScope == FinanceScope.PERSONAL) {
            FinanceScope.BUSINESS
        } else {
            FinanceScope.PERSONAL
        }
        return if (mentionsScope(userPrompt, otherScope)) {
            val other = if (otherScope == FinanceScope.PERSONAL) personalSummary.value else businessSummary.value
            val oneLiner = "${otherScope.labelPt}: Saldo R$ ${"%.2f".format(other.totalBalance)}, Receitas R$ ${"%.2f".format(other.monthIncome)}, Gastos R$ ${"%.2f".format(other.paidExpenses)}"
            "$primarySummary\n$oneLiner"
        } else {
            primarySummary
        }
    }

    private fun mentionsScope(prompt: String, scope: FinanceScope): Boolean {
        val lower = java.text.Normalizer.normalize(prompt.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val keywords = if (scope == FinanceScope.BUSINESS) {
            listOf("empresa", "pj", "negocio", "caixa", "venda", "vendas", "faturamento", "cliente", "fornecedor")
        } else {
            listOf("pessoal", "pessoais", "pf", "casa", "familia", "salario")
        }
        return keywords.any { kw -> Regex("""(?<!\w)${Regex.escape(kw)}(?!\w)""").containsMatchIn(lower) }
    }

    private fun buildContextSummaryForAi(scope: FinanceScope): String {
        val summary = if (scope == FinanceScope.PERSONAL) personalSummary.value else businessSummary.value
        val monthLabel = MonthHelper.labelFor(_selectedMonthKey.value)
        val topCategories = summary.expenseSlices.take(4)
            .joinToString(", ") { "${it.category}: R$ ${"%.2f".format(it.amount)}" }
            .ifBlank { "sem gastos" }
        val accountsList = summary.scopeAccounts.take(5)
            .joinToString(", ") { "${it.name} (R$ ${"%.2f".format(it.balance)})" }
            .ifBlank { "nenhuma" }
        val recentTx = summary.monthTransactions.take(6)
            .joinToString("; ") {
                val sign = if (it.type == TransactionType.INCOME.wireValue) "+" else "-"
                "${it.title} ($sign R$ ${"%.2f".format(it.amount)}, ${it.category})"
            }
            .ifBlank { "nenhum" }
        val openReminders = summary.scopeReminders.filter { !it.isCompleted }.take(4)
            .joinToString(", ") { "${it.title} (${it.dueDateText}, R$ ${"%.2f".format(it.amount)})" }
            .ifBlank { "nenhum" }
        val goalsList = summary.scopeGoals.take(4)
            .joinToString(", ") { "${it.title}: R$ ${"%.2f".format(it.currentAmount)} / R$ ${"%.2f".format(it.targetAmount)}" }
            .ifBlank { "nenhuma" }

        return """
            [${scope.labelPt} — $monthLabel]
            Saldo: R$ ${"%.2f".format(summary.totalBalance)} | Receitas: R$ ${"%.2f".format(summary.monthIncome)} (a receber R$ ${"%.2f".format(summary.pendingIncome)})
            Gastos pagos: R$ ${"%.2f".format(summary.paidExpenses)} (a pagar R$ ${"%.2f".format(summary.pendingExpenses)}) | Cartão: R$ ${"%.2f".format(summary.creditCardTotal)}
            Contas: $accountsList
            Top categorias: $topCategories
            Recentes: $recentTx
            Lembretes: $openReminders
            Metas: $goalsList
        """.trimIndent()
    }

    private companion object {
        const val TAG = "FinanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
