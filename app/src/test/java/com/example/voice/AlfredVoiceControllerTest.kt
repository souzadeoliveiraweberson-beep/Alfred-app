package com.example.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlfredVoiceControllerTest {

    @Test
    fun `formatForSpeech strips markdown and expands Brazilian currency`() {
        val raw = "**Saldo atual**: R$ 1.250,50 • Gasto **R$ 48,00**"
        val formatted = AlfredVoiceController.formatForSpeech(raw)

        assertFalse(formatted.contains("**"))
        assertFalse(formatted.contains("R$"))
        assertTrue(formatted.contains("1250 reais e 50 centavos"))
        assertTrue(formatted.contains("48 reais"))
    }

    @Test
    fun `isStopVoicePhrase detects exit commands`() {
        assertTrue(AlfredVoiceController.isStopVoicePhrase("parar"))
        assertTrue(AlfredVoiceController.isStopVoicePhrase("Obrigado Alfred"))
        assertFalse(AlfredVoiceController.isStopVoicePhrase("Gastei 30 no almoço"))
    }

    @Test
    fun `mapSpeechError returns helpful Portuguese messages`() {
        assertEquals(
            "Não consegui entender sua fala. Toque no microfone e tente novamente.",
            AlfredVoiceController.mapSpeechError(SpeechRecognizer.ERROR_NO_MATCH),
        )
        assertTrue(
            AlfredVoiceController.mapSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                .contains("Permissão de microfone")
        )
    }
}
