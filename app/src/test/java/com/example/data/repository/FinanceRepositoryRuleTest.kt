package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private suspend inline fun <reified T : Throwable> assertFailsWithSuspend(
        crossinline block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            fail("Expected ${T::class.java.simpleName}, but threw ${t::class.java.simpleName}: ${t.message}")
        }
        fail("Expected ${T::class.java.simpleName}, but block succeeded without throwing")
        throw AssertionError("Unreachable")
    }

    @Test
    fun addAndObserveTransactions_personalAndBusiness_succeedsForOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FinanceRepository(firestore, auth)

        val personalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTransaction(
                FinanceTransaction(
                    scope = FinanceScope.PERSONAL.wireValue,
                    type = TransactionType.EXPENSE.wireValue,
                    title = "Jantar Gotham",
                    category = "Alimentação",
                    amount = 89.90,
                    accountName = "Cartão Black",
                    monthKey = "2026-11",
                    dayOfMonth = 15,
                    isPaid = true,
                )
            )
        }

        val businessId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTransaction(
                FinanceTransaction(
                    scope = FinanceScope.BUSINESS.wireValue,
                    type = TransactionType.INCOME.wireValue,
                    title = "Contrato Wayne Tech",
                    category = "Serviços",
                    amount = 4200.0,
                    accountName = "Conta PJ",
                    monthKey = "2026-11",
                    dayOfMonth = 15,
                    isPaid = true,
                )
            )
        }

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTransactions(aliceUid).first { list ->
                list.any { it.id == personalId } && list.any { it.id == businessId }
            }
        }
        assertEquals(2, items.count { it.id == personalId || it.id == businessId })
    }

    @Test
    fun accountsRemindersGoalsAndChat_crudOperationsSucceedForOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FinanceRepository(firestore, auth)

        val accountId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addAccount(
                FinanceAccount(
                    scope = FinanceScope.BUSINESS.wireValue,
                    name = "Conta PJ",
                    kind = AccountKind.BUSINESS_CASH.wireValue,
                    balance = 15000.0,
                )
            )
        }
        val reminderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addReminder(
                FinanceReminder(
                    scope = FinanceScope.PERSONAL.wireValue,
                    title = "Conta de Luz",
                    amount = 210.0,
                    dueDateText = "16 Nov, 14:00",
                    monthKey = "2026-11",
                )
            )
        }
        val goalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addGoal(
                FinanceGoal(
                    scope = FinanceScope.BUSINESS.wireValue,
                    title = "Reserva Caixa",
                    targetAmount = 50000.0,
                    currentAmount = 10000.0,
                    deadlineText = "Dez 2026",
                )
            )
        }
        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addChatMessage(
                AlfredChatMessage(
                    sender = ChatSender.ALFRED.wireValue,
                    text = "Anotado, senhor.",
                    scope = FinanceScope.PERSONAL.wireValue,
                    actionSummary = "Despesa registrada",
                )
            )
        }

        assertTrue(accountId.isNotBlank())
        assertTrue(reminderId.isNotBlank())
        assertTrue(goalId.isNotBlank())
        assertTrue(msgId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleReminderCompleted(reminderId, true)
            repository.updateGoalAmount(goalId, 12500.0)
        }

        val dueMillis = System.currentTimeMillis() + 86_400_000L
        val customId = "rem_custom_${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
        val timedReminderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addReminder(
                FinanceReminder(
                    scope = FinanceScope.PERSONAL.wireValue,
                    title = "Aluguel recorrente",
                    amount = 2200.0,
                    dueDateText = "Amanhã, 09:00",
                    dueAt = Timestamp(java.util.Date(dueMillis)),
                    recurrence = ReminderRecurrence.MONTHLY.wireValue,
                    monthKey = "2026-11",
                ),
                presetId = customId,
            )
        }
        assertEquals(customId, timedReminderId)

        val nextMillis = ReminderScheduleHelper.computeNextOccurrenceMillis(
            dueMillis,
            ReminderRecurrence.MONTHLY,
        )!!
        val nextId = ReminderScheduleHelper.deterministicNextOccurrenceId(timedReminderId, nextMillis)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.completeRecurringReminder(
                reminder = FinanceReminder(
                    id = timedReminderId,
                    scope = FinanceScope.PERSONAL.wireValue,
                    title = "Aluguel recorrente",
                    amount = 2200.0,
                    dueDateText = "Amanhã, 09:00",
                    dueAt = Timestamp(java.util.Date(dueMillis)),
                    recurrence = ReminderRecurrence.MONTHLY.wireValue,
                    monthKey = "2026-11",
                ),
                nextOccurrenceId = nextId,
                nextDueAt = Timestamp(java.util.Date(nextMillis)),
                nextDueDateText = "17 Dez, 09:00",
                nextMonthKey = "2026-12",
            )
        }

        val goals = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGoals(aliceUid).first { list -> list.any { it.id == goalId && it.currentAmount == 12500.0 } }
        }
        assertTrue(goals.any { it.id == goalId })

        val reminders = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeReminders(aliceUid).first { list -> list.any { it.id == nextId } }
        }
        assertTrue(reminders.any { it.id == timedReminderId && it.isCompleted })
        assertTrue(reminders.any { it.id == nextId && !it.isCompleted })
    }

    @Test
    fun crossUserRead_isDeniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FinanceRepository(firestore, auth)
        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addTransaction(
                FinanceTransaction(
                    scope = FinanceScope.PERSONAL.wireValue,
                    type = TransactionType.EXPENSE.wireValue,
                    title = "Privado",
                    category = "Outros",
                    amount = 50.0,
                    monthKey = "2026-11",
                    dayOfMonth = 15,
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        val exception = assertFailsWithSuspend<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("transactions").document(txId).get().await()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception.code)
    }

    @Test
    fun unauthenticatedObserve_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = FinanceRepository(firestore, auth)

        val exception = assertFailsWithSuspend<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTransactions(aliceUid).first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_alfred@test.com"
        const val BOB_EMAIL = "bob_alfred@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
