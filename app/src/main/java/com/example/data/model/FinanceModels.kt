package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class FinanceScope(val wireValue: String, val labelPt: String) {
    PERSONAL("PERSONAL", "Finanças"),
    BUSINESS("BUSINESS", "Empresa");

    companion object {
        fun fromWire(value: String): FinanceScope =
            entries.firstOrNull { it.wireValue == value } ?: PERSONAL
    }
}

enum class TransactionType(val wireValue: String, val labelPt: String) {
    INCOME("INCOME", "Receita"),
    EXPENSE("EXPENSE", "Despesa");

    companion object {
        fun fromWire(value: String): TransactionType =
            entries.firstOrNull { it.wireValue == value } ?: EXPENSE
    }
}

enum class AccountKind(val wireValue: String, val labelPt: String) {
    CHECKING("CHECKING", "Conta Corrente"),
    CREDIT_CARD("CREDIT_CARD", "Cartão de Crédito"),
    SAVINGS("SAVINGS", "Reserva"),
    BUSINESS_CASH("BUSINESS_CASH", "Caixa Empresarial");

    companion object {
        fun fromWire(value: String): AccountKind =
            entries.firstOrNull { it.wireValue == value } ?: CHECKING
    }
}

enum class ChatSender(val wireValue: String) {
    USER("USER"),
    ALFRED("ALFRED");

    companion object {
        fun fromWire(value: String): ChatSender =
            entries.firstOrNull { it.wireValue == value } ?: ALFRED
    }
}

enum class ReminderRecurrence(val wireValue: String, val labelPt: String) {
    NONE("NONE", "Único"),
    DAILY("DAILY", "Diário"),
    WEEKLY("WEEKLY", "Semanal"),
    MONTHLY("MONTHLY", "Mensal");

    companion object {
        fun fromWire(value: String?): ReminderRecurrence =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

@IgnoreExtraProperties
data class FinanceTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scope: String = FinanceScope.PERSONAL.wireValue,
    val type: String = TransactionType.EXPENSE.wireValue,
    val title: String = "",
    val category: String = "Outros",
    val amount: Double = 0.0,
    val accountId: String = "",
    val accountName: String = "Carteira",
    val monthKey: String = "",
    val dayOfMonth: Int = 1,
    @get:PropertyName("isPaid") val isPaid: Boolean = true,
    @get:PropertyName("isRecurring") val isRecurring: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FinanceAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scope: String = FinanceScope.PERSONAL.wireValue,
    val name: String = "",
    val kind: String = AccountKind.CHECKING.wireValue,
    val balance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val closingDay: Int = 10,
    val dueDay: Int = 17,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FinanceReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scope: String = FinanceScope.PERSONAL.wireValue,
    val title: String = "",
    val amount: Double = 0.0,
    val dueDateText: String = "",
    val dueAt: Timestamp? = null,
    val recurrence: String = ReminderRecurrence.NONE.wireValue,
    val monthKey: String = "",
    val anchorDay: Int = 0,
    val generatedNextId: String = "",
    @get:PropertyName("isCompleted") val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FinanceGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scope: String = FinanceScope.PERSONAL.wireValue,
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val deadlineText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AlfredChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = ChatSender.ALFRED.wireValue,
    val text: String = "",
    val scope: String = FinanceScope.PERSONAL.wireValue,
    val actionSummary: String = "",
    @get:Exclude val debugTtftMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
