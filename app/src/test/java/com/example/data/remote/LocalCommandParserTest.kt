package com.example.data.remote

import com.example.data.model.FinanceScope
import com.example.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandParserTest {

    @Test
    fun parseLunchExpense_extractsAmountTitleCategoryAndPaid() {
        val result = LocalCommandParser.parseTransaction(
            "gastei 48 reais no almoço",
            FinanceScope.PERSONAL
        )
        assertNotNull(result)
        result!!
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(48.0, result.amount, 0.001)
        assertEquals("Almoço", result.title)
        assertEquals("Alimentação", result.category)
        assertEquals(FinanceScope.PERSONAL, result.scope)
        assertTrue(result.isPaid)
    }

    @Test
    fun parseSaleIncomeWithThousandSeparator_detectsBusinessAndCurrency() {
        val result = LocalCommandParser.parseTransaction(
            "recebi R$ 1.250,90 da venda",
            FinanceScope.PERSONAL
        )
        assertNotNull(result)
        result!!
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(1250.90, result.amount, 0.001)
        assertEquals("Venda", result.title)
        assertEquals("Vendas", result.category)
        assertEquals(FinanceScope.BUSINESS, result.scope)
        assertTrue(result.isPaid)
    }

    @Test
    fun parseElectricityBill_detectsDecimalCommaAndMoradiaCategory() {
        val result = LocalCommandParser.parseTransaction(
            "paguei 120,50 de luz",
            FinanceScope.PERSONAL
        )
        assertNotNull(result)
        result!!
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(120.50, result.amount, 0.001)
        assertEquals("Luz", result.title)
        assertEquals("Moradia", result.category)
        assertEquals(FinanceScope.PERSONAL, result.scope)
        assertTrue(result.isPaid)
    }

    @Test
    fun parseBusinessSale_detectsBusinessScopeAndIncome() {
        val result = LocalCommandParser.parseTransaction(
            "vendi 300 na empresa",
            FinanceScope.PERSONAL
        )
        assertNotNull(result)
        result!!
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(300.0, result.amount, 0.001)
        assertEquals(FinanceScope.BUSINESS, result.scope)
        assertEquals("Vendas", result.category)
        assertTrue(result.isPaid)
    }

    @Test
    fun parseConversationalQuestion_returnsNull() {
        assertNull(LocalCommandParser.parseTransaction("como economizar?", FinanceScope.PERSONAL))
        assertNull(LocalCommandParser.parseTransaction("como melhorar o lucro da minha empresa?", FinanceScope.BUSINESS))
        assertNull(LocalCommandParser.parseTransaction("quanto gastei este mês?", FinanceScope.PERSONAL))
    }

    @Test
    fun extractManualDraft_prefillsAmountAndScopeForFallback() {
        val draft = LocalCommandParser.extractManualDraft("paguei 89,90 de fornecedor", FinanceScope.PERSONAL)
        assertEquals(89.90, draft.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(FinanceScope.BUSINESS, draft.scope)
        assertEquals("Fornecedores", draft.category)
    }

    @Test
    fun parseAccountAfterPreposition_usesAccountNameAndDefaultTitle() {
        val result = LocalCommandParser.parseTransaction(
            "recebi 500 reais na conta pessoal",
            FinanceScope.BUSINESS
        )
        assertNotNull(result)
        result!!
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(FinanceScope.PERSONAL, result.scope)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals("Receita", result.title)
        assertEquals("Conta pessoal", result.accountName)
        assertTrue(result.toAlfredConfirmation().contains("nas suas finanças pessoais"))
    }

    @Test
    fun parseItemPlusAccountAfterPreposition_extractsBothCorrectly() {
        val result = LocalCommandParser.parseTransaction(
            "gastei 45 no almoço no nubank",
            FinanceScope.PERSONAL
        )
        assertNotNull(result)
        result!!
        assertEquals("Almoço", result.title)
        assertEquals("Nubank", result.accountName)
        assertEquals("Alimentação", result.category)
    }

    @Test
    fun toAlfredConfirmation_usesCorrectPortuguesePrepositions() {
        val personalTx = LocalCommandParser.parseTransaction("gastei 30 no café", FinanceScope.PERSONAL)!!
        assertTrue(personalTx.toAlfredConfirmation().contains("registrada nas suas finanças pessoais."))

        val businessTx = LocalCommandParser.parseTransaction("vendi 200 na empresa", FinanceScope.PERSONAL)!!
        assertTrue(businessTx.toAlfredConfirmation().contains("registrada na sua empresa."))
    }

    @Test
    fun splitAndResolve_compoundMessage_separatesTransactionsGoalContributionAndReminder() {
        val result = LocalCommandParser.splitAndResolve(
            rawInput = "gastei 30 no almoço, me lembra de pagar a luz amanhã às 14h e guardei 200 na meta do iPhone",
            activeScope = FinanceScope.PERSONAL,
        )

        assertEquals(1, result.localTransactions.size)
        assertEquals(30.0, result.localTransactions[0].amount, 0.001)
        assertEquals("Almoço", result.localTransactions[0].title)

        assertEquals(1, result.localGoalContributions.size)
        assertEquals(200.0, result.localGoalContributions[0].amount, 0.001)
        assertEquals("iPhone", result.localGoalContributions[0].goalQuery)

        assertTrue(result.unresolvedPrompt.contains("lembra", ignoreCase = true))
        assertTrue(result.unresolvedPrompt.contains("14h", ignoreCase = true))
    }

    @Test
    fun splitCompoundSegments_preservesDecimalsAndSingleClauseWithAnd() {
        val decimalSegs = LocalCommandParser.splitCompoundSegments("gastei 48,50 no lanche; recebi 1.200,00 da venda")
        assertEquals(2, decimalSegs.size)
        assertEquals("gastei 48,50 no lanche", decimalSegs[0])
        assertEquals("recebi 1.200,00 da venda", decimalSegs[1])

        val singleClause = LocalCommandParser.splitCompoundSegments("paguei 120 de luz e água")
        assertEquals(1, singleClause.size)
        assertEquals("paguei 120 de luz e água", singleClause[0])
    }

    @Test
    fun parseTransaction_extractsRelativeAndExplicitDatesWithoutConfusingAmount() {
        val ref = java.time.LocalDate.of(2026, 11, 16)

        val ontem = LocalCommandParser.parseTransaction(
            rawInput = "gastei 35 no lanche ontem",
            activeScope = FinanceScope.PERSONAL,
            referenceDate = ref,
        )!!
        assertEquals(35.0, ontem.amount, 0.001)
        assertEquals("Lanche", ontem.title)
        assertEquals(java.time.LocalDate.of(2026, 11, 15), ontem.date)

        val dia10 = LocalCommandParser.parseTransaction(
            rawInput = "dia 10 gastei 50 no mercado",
            activeScope = FinanceScope.PERSONAL,
            referenceDate = ref,
        )!!
        assertEquals(50.0, dia10.amount, 0.001)
        assertEquals("Mercado", dia10.title)
        assertEquals(java.time.LocalDate.of(2026, 11, 10), dia10.date)

        val slashDate = LocalCommandParser.parseTransaction(
            rawInput = "paguei 80 no uber em 10/10",
            activeScope = FinanceScope.PERSONAL,
            referenceDate = ref,
        )!!
        assertEquals(80.0, slashDate.amount, 0.001)
        assertEquals("Uber", slashDate.title)
        assertEquals(java.time.LocalDate.of(2026, 10, 10), slashDate.date)
    }
}
