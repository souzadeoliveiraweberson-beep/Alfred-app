package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoicePhase {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

class AlfredVoiceController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private var tts: TextToSpeech? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private var pendingSpeakText: String? = null
    private var pendingOnComplete: (() -> Unit)? = null
    private var activeOnComplete: (() -> Unit)? = null
    private var isShutdown = false

    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stopSpeaking()
        }
    }

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (isShutdown) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val ptBr = Locale.forLanguageTag("pt-BR")
                    val langResult = engine.setLanguage(ptBr)
                    val available = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!available) {
                        engine.language = Locale.getDefault()
                    }
                    engine.setSpeechRate(1.03f)
                    engine.setPitch(0.96f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post { _isSpeaking.value = true }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                finishUtterance(invokeCallback = true)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                finishUtterance(invokeCallback = false)
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            mainHandler.post {
                                finishUtterance(invokeCallback = false)
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            mainHandler.post {
                                finishUtterance(invokeCallback = false)
                            }
                        }
                    })
                    _isTtsReady.value = true
                    val queued = pendingSpeakText
                    val queuedCallback = pendingOnComplete
                    pendingSpeakText = null
                    pendingOnComplete = null
                    if (!queued.isNullOrBlank()) {
                        speak(queued, queuedCallback)
                    }
                } else {
                    _isTtsReady.value = false
                }
            }
        }
    }

    fun speak(
        rawText: String,
        onComplete: (() -> Unit)? = null,
    ) {
        if (isShutdown) return
        val spoken = formatForSpeech(rawText)
        if (spoken.isBlank()) {
            onComplete?.invoke()
            return
        }

        val engine = tts
        if (engine == null || !_isTtsReady.value) {
            pendingSpeakText = spoken
            pendingOnComplete = onComplete
            return
        }

        stopSpeakingInternal(invokeCallback = false)
        activeOnComplete = onComplete
        requestTransientAudioFocus()

        val utteranceId = "alfred_${UUID.randomUUID()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
        val result = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            finishUtterance(invokeCallback = false)
        } else {
            _isSpeaking.value = true
        }
    }

    fun stopSpeaking() {
        pendingSpeakText = null
        pendingOnComplete = null
        stopSpeakingInternal(invokeCallback = false)
    }

    fun shutdown() {
        isShutdown = true
        pendingSpeakText = null
        pendingOnComplete = null
        activeOnComplete = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        _isSpeaking.value = false
        abandonAudioFocus()
    }

    private fun stopSpeakingInternal(invokeCallback: Boolean) {
        runCatching { tts?.stop() }
        finishUtterance(invokeCallback = invokeCallback)
    }

    private fun finishUtterance(invokeCallback: Boolean) {
        _isSpeaking.value = false
        abandonAudioFocus()
        val cb = activeOnComplete
        activeOnComplete = null
        if (invokeCallback) {
            cb?.invoke()
        }
    }

    private fun requestTransientAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(legacyFocusListener, mainHandler)
                .build()
            activeFocusRequest = req
            manager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                legacyFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { manager.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { manager.abandonAudioFocus(legacyFocusListener) }
        }
    }

    companion object {
        fun formatForSpeech(rawText: String): String {
            return rawText
                .replace(Regex("""\*\*|\*|`|#"""), "")
                .replace("•", ",")
                .replace("R$", "reais ")
                .replace(Regex("""reais\s+(\d[\d.]*),(\d{2})""")) { match ->
                    val inteiro = match.groupValues[1].replace(".", "")
                    val centavos = match.groupValues[2]
                    if (centavos == "00") "$inteiro reais" else "$inteiro reais e $centavos centavos"
                }
                .replace(Regex("""reais\s+(\d[\d.]*)(?!\d)""")) { match ->
                    "${match.groupValues[1].replace(".", "")} reais"
                }
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        fun mapSpeechError(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH ->
                "Não consegui entender sua fala. Toque no microfone e tente novamente."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Nenhuma fala detectada. Toque no microfone quando estiver pronto."
            SpeechRecognizer.ERROR_AUDIO ->
                "Erro ao captar áudio do microfone."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Sem conexão estável para transcrever a voz."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Permissão de microfone necessária para falar com o Alfred."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Microfone ocupado no momento. Tente novamente em um segundo."
            SpeechRecognizer.ERROR_SERVER ->
                "Serviço de reconhecimento de voz indisponível temporariamente."
            else ->
                "Não foi possível captar a voz (código $errorCode). Tente novamente."
        }

        fun isStopVoicePhrase(text: String): Boolean {
            val clean = text.lowercase(Locale.ROOT).trim()
            return clean in setOf(
                "parar", "pare", "chega", "encerrar", "encerrar voz",
                "sair do modo voz", "desligar voz", "obrigado alfred", "só isso alfred"
            )
        }
    }
}
