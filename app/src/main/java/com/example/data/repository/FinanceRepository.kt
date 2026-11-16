package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AlfredChatMessage
import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceTransaction
import com.example.data.model.ReminderRecurrence
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário não autenticado com Google.")
    }

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    fun generateDocumentId(collectionName: String): String {
        val uid = requireUserId()
        return userRoot(uid).collection(collectionName).document().id
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        userId: String,
        operationType: OperationType,
        path: String,
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeTransactions(userId: String): Flow<List<FinanceTransaction>> {
        val path = "users/$userId/transactions"
        return userRoot(userId).collection("transactions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FinanceTransaction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(userId, OperationType.LIST, path)
    }

    fun observeAccounts(userId: String): Flow<List<FinanceAccount>> {
        val path = "users/$userId/accounts"
        return userRoot(userId).collection("accounts")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FinanceAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(userId, OperationType.LIST, path)
    }

    fun observeReminders(userId: String): Flow<List<FinanceReminder>> {
        val path = "users/$userId/reminders"
        return userRoot(userId).collection("reminders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FinanceReminder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(userId, OperationType.LIST, path)
    }

    fun observeGoals(userId: String): Flow<List<FinanceGoal>> {
        val path = "users/$userId/goals"
        return userRoot(userId).collection("goals")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FinanceGoal::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(userId, OperationType.LIST, path)
    }

    fun observeChatMessages(userId: String): Flow<List<AlfredChatMessage>> {
        val path = "users/$userId/chatMessages"
        return userRoot(userId).collection("chatMessages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AlfredChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(userId, OperationType.LIST, path)
    }

    suspend fun getTransactionById(transactionId: String): FinanceTransaction? {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document(transactionId)
        return try {
            docRef.get().await().toObject(
                FinanceTransaction::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    private suspend inline fun <T> executeWrite(
        operationType: OperationType,
        path: String,
        crossinline block: suspend () -> T,
    ): T {
        return try {
            withTimeout(WRITE_TIMEOUT_MS) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Tempo limite de 15s ao sincronizar escrita no Firestore ($path).",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            throw e
        }
    }

    suspend fun addTransaction(tx: FinanceTransaction, presetId: String? = null): String {
        val uid = requireUserId()
        val effectiveId = presetId?.takeIf { it.isNotBlank() } ?: tx.id.takeIf { it.isNotBlank() }
        val docRef = if (effectiveId != null) {
            userRoot(uid).collection("transactions").document(effectiveId)
        } else {
            userRoot(uid).collection("transactions").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "scope" to tx.scope,
            "type" to tx.type,
            "title" to tx.title.trim().take(120),
            "category" to tx.category.trim().ifBlank { "Outros" }.take(60),
            "amount" to tx.amount,
            "accountId" to tx.accountId.take(128),
            "accountName" to tx.accountName.trim().ifBlank { "Carteira" }.take(80),
            "monthKey" to tx.monthKey,
            "dayOfMonth" to tx.dayOfMonth.coerceIn(1, 31),
            "isPaid" to tx.isPaid,
            "isRecurring" to tx.isRecurring,
            "notes" to tx.notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
            docRef.id
        }
    }

    suspend fun toggleTransactionPaid(transactionId: String, isPaid: Boolean) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document(transactionId)
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.update(
                mapOf(
                    "isPaid" to isPaid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteTransaction(transactionId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document(transactionId)
        executeWrite(OperationType.DELETE, docRef.path) {
            docRef.delete().await()
        }
    }

    suspend fun addAccount(account: FinanceAccount): String {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("accounts").document()
        val payload = mapOf(
            "userId" to uid,
            "scope" to account.scope,
            "name" to account.name.trim().take(60),
            "kind" to account.kind,
            "balance" to account.balance,
            "creditLimit" to account.creditLimit.coerceAtLeast(0.0),
            "closingDay" to account.closingDay.coerceIn(1, 31),
            "dueDay" to account.dueDay.coerceIn(1, 31),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
            docRef.id
        }
    }

    suspend fun updateAccountBalance(accountId: String, newBalance: Double) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("accounts").document(accountId)
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.update(
                mapOf(
                    "balance" to newBalance,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("accounts").document(accountId)
        executeWrite(OperationType.DELETE, docRef.path) {
            docRef.delete().await()
        }
    }

    suspend fun addReminder(reminder: FinanceReminder, presetId: String? = null): String {
        val uid = requireUserId()
        val effectiveId = presetId?.takeIf { it.isNotBlank() } ?: reminder.id.takeIf { it.isNotBlank() }
        val docRef = if (effectiveId != null) {
            userRoot(uid).collection("reminders").document(effectiveId)
        } else {
            userRoot(uid).collection("reminders").document()
        }
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("scope", reminder.scope)
            put("title", reminder.title.trim().take(120))
            put("amount", reminder.amount.coerceAtLeast(0.0))
            put("dueDateText", reminder.dueDateText.trim().ifBlank { "Em breve" }.take(40))
            put("monthKey", reminder.monthKey)
            put("isCompleted", reminder.isCompleted)
            put("recurrence", ReminderRecurrence.fromWire(reminder.recurrence).wireValue)
            if (reminder.dueAt != null) {
                put("dueAt", reminder.dueAt)
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
            docRef.id
        }
    }

    suspend fun updateReminder(
        reminderId: String,
        title: String,
        amount: Double,
        dueDateText: String,
        dueAt: Timestamp?,
        recurrence: String,
        monthKey: String,
    ) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document(reminderId)
        val updates = mutableMapOf<String, Any?>(
            "title" to title.trim().take(120),
            "amount" to amount.coerceAtLeast(0.0),
            "dueDateText" to dueDateText.trim().ifBlank { "Em breve" }.take(40),
            "recurrence" to ReminderRecurrence.fromWire(recurrence).wireValue,
            "monthKey" to monthKey,
            "dueAt" to dueAt,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.update(updates).await()
        }
    }

    suspend fun toggleReminderCompleted(reminderId: String, isCompleted: Boolean) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document(reminderId)
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.update(
                mapOf(
                    "isCompleted" to isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun completeRecurringReminder(
        reminder: FinanceReminder,
        nextOccurrenceId: String,
        nextDueAt: Timestamp,
        nextDueDateText: String,
        nextMonthKey: String,
    ) {
        val uid = requireUserId()
        val currentRef = userRoot(uid).collection("reminders").document(reminder.id)
        val nextRef = userRoot(uid).collection("reminders").document(nextOccurrenceId)
        val nextPayload = mapOf(
            "userId" to uid,
            "scope" to reminder.scope,
            "title" to reminder.title.trim().take(120),
            "amount" to reminder.amount.coerceAtLeast(0.0),
            "dueDateText" to nextDueDateText.trim().ifBlank { "Em breve" }.take(40),
            "dueAt" to nextDueAt,
            "recurrence" to ReminderRecurrence.fromWire(reminder.recurrence).wireValue,
            "monthKey" to nextMonthKey,
            "isCompleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        executeWrite(OperationType.UPDATE, currentRef.path) {
            db.runBatch { batch ->
                batch.update(
                    currentRef,
                    mapOf(
                        "isCompleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                batch.set(nextRef, nextPayload)
            }.await()
        }
    }

    suspend fun deleteReminder(reminderId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document(reminderId)
        executeWrite(OperationType.DELETE, docRef.path) {
            docRef.delete().await()
        }
    }

    suspend fun addGoal(goal: FinanceGoal, presetId: String? = null): String {
        val uid = requireUserId()
        val effectiveId = presetId?.takeIf { it.isNotBlank() } ?: goal.id.takeIf { it.isNotBlank() }
        val docRef = if (effectiveId != null) {
            userRoot(uid).collection("goals").document(effectiveId)
        } else {
            userRoot(uid).collection("goals").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "scope" to goal.scope,
            "title" to goal.title.trim().take(80),
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount.coerceAtLeast(0.0),
            "deadlineText" to goal.deadlineText.trim().take(40),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
            docRef.id
        }
    }

    suspend fun updateGoalAmount(goalId: String, newCurrentAmount: Double) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("goals").document(goalId)
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.update(
                mapOf(
                    "currentAmount" to newCurrentAmount.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun deleteGoal(goalId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("goals").document(goalId)
        executeWrite(OperationType.DELETE, docRef.path) {
            docRef.delete().await()
        }
    }

    suspend fun addChatMessage(message: AlfredChatMessage, presetId: String? = null): String {
        val uid = requireUserId()
        val effectiveId = presetId?.takeIf { it.isNotBlank() }
        val docRef = if (effectiveId != null) {
            userRoot(uid).collection("chatMessages").document(effectiveId)
        } else {
            userRoot(uid).collection("chatMessages").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "sender" to message.sender,
            "text" to message.text.trim().take(4000),
            "scope" to message.scope,
            "actionSummary" to message.actionSummary.trim().take(600),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
            docRef.id
        }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 15_000L
    }
}
