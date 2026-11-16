package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AlfredChatMessage
import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceTransaction
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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

    suspend fun addTransaction(tx: FinanceTransaction): String {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document()
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
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleTransactionPaid(transactionId: String, isPaid: Boolean) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document(transactionId)
        try {
            docRef.update(
                mapOf(
                    "isPaid" to isPaid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTransaction(transactionId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("transactions").document(transactionId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
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
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateAccountBalance(accountId: String, newBalance: Double) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("accounts").document(accountId)
        try {
            docRef.update(
                mapOf(
                    "balance" to newBalance,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("accounts").document(accountId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addReminder(reminder: FinanceReminder): String {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document()
        val payload = mapOf(
            "userId" to uid,
            "scope" to reminder.scope,
            "title" to reminder.title.trim().take(120),
            "amount" to reminder.amount.coerceAtLeast(0.0),
            "dueDateText" to reminder.dueDateText.trim().ifBlank { "Hoje" }.take(40),
            "monthKey" to reminder.monthKey,
            "isCompleted" to reminder.isCompleted,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleReminderCompleted(reminderId: String, isCompleted: Boolean) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document(reminderId)
        try {
            docRef.update(
                mapOf(
                    "isCompleted" to isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteReminder(reminderId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("reminders").document(reminderId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addGoal(goal: FinanceGoal): String {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("goals").document()
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
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGoalAmount(goalId: String, newCurrentAmount: Double) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("goals").document(goalId)
        try {
            docRef.update(
                mapOf(
                    "currentAmount" to newCurrentAmount.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGoal(goalId: String) {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("goals").document(goalId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addChatMessage(message: AlfredChatMessage): String {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("chatMessages").document()
        val payload = mapOf(
            "userId" to uid,
            "sender" to message.sender,
            "text" to message.text.trim().take(4000),
            "scope" to message.scope,
            "actionSummary" to message.actionSummary.trim().take(240),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
