package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlfredAiServiceTest {

    private val service = AlfredAiService()

    @Test
    fun conversationalFinanceAndBusinessPrompts_useStreamingChat() {
        assertFalse(service.shouldExtractStructuredAction("Oi Alfred, vamos conversar sobre minhas finanças", null))
        assertFalse(service.shouldExtractStructuredAction("Como melhorar o lucro da minha empresa?", null))
        assertFalse(service.shouldExtractStructuredAction("Qual a melhor estratégia para separar PF e PJ?", null))
        assertFalse(service.shouldExtractStructuredAction("Quanto gastei este mês no cartão?", null))
        assertFalse(service.shouldExtractStructuredAction("Me dá dicas de capital de giro para o negócio", null))
    }

    @Test
    fun explicitRecordingAndReminderCommands_useStructuredExtraction() {
        assertTrue(service.shouldExtractStructuredAction("Gastei R$ 38 no almoço", null))
        assertTrue(service.shouldExtractStructuredAction("Vendi R$ 850 na empresa hoje", null))
        assertTrue(service.shouldExtractStructuredAction("Me lembra amanhã às 14h da conta de luz", null))
        assertTrue(service.shouldExtractStructuredAction("Criar meta de R$ 5000 para reserva", null))
        assertTrue(service.shouldExtractStructuredAction("Registra 120 de combustível", null))
    }
}
