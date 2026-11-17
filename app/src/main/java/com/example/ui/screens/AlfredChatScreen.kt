package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AlfredChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.FinanceScope
import com.example.data.remote.ManualEntryDraft
import com.example.ui.components.GothamCard
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamHairlineStrong
import com.example.ui.theme.GothamObsidian
import com.example.ui.theme.GothamPearl
import com.example.ui.theme.GothamPureWhite
import com.example.ui.theme.GothamSilver
import com.example.ui.theme.GothamSlate
import com.example.ui.theme.GothamSurface
import com.example.ui.theme.GothamSurfaceElevated
import com.example.ui.theme.GothamVoid
import com.example.ui.viewmodel.ChatStatus
import com.example.voice.AlfredVoiceController
import kotlinx.coroutines.delay

private val QUICK_PROMPTS = listOf(
    "Como melhorar o lucro da empresa?",
    "Analise minhas finanças e meu caixa PJ",
    "Dicas para organizar reserva e cartão",
    "Gastei R$ 38 no almoço",
    "Vendi R$ 850 na empresa hoje",
    "Me lembra amanhã às 14h da conta de luz",
)

@Composable
fun AlfredChatScreen(
    messages: List<AlfredChatMessage>,
    chatStatus: ChatStatus,
    activeScope: FinanceScope,
    onScopeChange: (FinanceScope) -> Unit,
    voiceRepliesEnabled: Boolean = true,
    continuousVoiceEnabled: Boolean = false,
    onToggleVoiceReplies: (Boolean) -> Unit = {},
    onToggleContinuousVoice: (Boolean) -> Unit = {},
    pendingAutoVoice: Boolean = false,
    onConsumeAutoVoice: () -> Unit = {},
    onSendMessage: (String, Bitmap?) -> Unit,
    onRequestWeeklySummary: () -> Unit,
    onOpenManualEntry: (ManualEntryDraft) -> Unit,
    onDismissError: () -> Unit,
    contentBottomPadding: Dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var lastSentByVoice by remember { mutableStateOf(false) }
    var pendingReplyVoiceTrigger by remember { mutableStateOf(false) }
    var knownMessageCountOnStart by remember { mutableStateOf<Int?>(null) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val voiceController = remember(context) { AlfredVoiceController(context.applicationContext) }
    val isSpeaking by voiceController.isSpeaking.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            attachedBitmap = decodeScaledBitmap(context, uri)
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer, voiceController) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
            voiceController.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, speechRecognizer, voiceController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isListening = false
                runCatching { speechRecognizer?.cancel() }
                voiceController.stopSpeaking()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startVoiceCapture: () -> Unit = {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            speechError = "Reconhecimento de voz indisponível neste dispositivo."
        } else {
            speechError = null
            voiceController.stopSpeaking()
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    speechError = AlfredVoiceController.mapSpeechError(error)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    if (spoken.isNotBlank()) {
                        if (AlfredVoiceController.isStopVoicePhrase(spoken)) {
                            lastSentByVoice = false
                            pendingReplyVoiceTrigger = false
                            onToggleContinuousVoice(false)
                            voiceController.speak("Às suas ordens, senhor. Modo contínuo encerrado.")
                            return
                        }
                        lastSentByVoice = true
                        pendingReplyVoiceTrigger = true
                        inputText = ""
                        onSendMessage(spoken, attachedBitmap)
                        attachedBitmap = null
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        inputText = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            speechError = "Permissão de microfone negada. Você pode digitar normalmente."
        }
    }

    val triggerMicAction: () -> Unit = {
        if (isListening) {
            runCatching { speechRecognizer?.stopListening() }
            isListening = false
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startVoiceCapture()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(pendingAutoVoice) {
        if (pendingAutoVoice) {
            onConsumeAutoVoice()
            delay(180)
            if (!isListening) {
                triggerMicAction()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (knownMessageCountOnStart == null) {
            knownMessageCountOnStart = messages.size
            lastSpokenMessageId = messages.lastOrNull { it.sender == ChatSender.ALFRED.wireValue }?.id
            return@LaunchedEffect
        }
        val latest = messages.lastOrNull() ?: return@LaunchedEffect
        if (latest.sender == ChatSender.ALFRED.wireValue && latest.id != lastSpokenMessageId) {
            lastSpokenMessageId = latest.id
            val shouldSpeak = voiceRepliesEnabled && (pendingReplyVoiceTrigger || lastSentByVoice || continuousVoiceEnabled)
            val rearmMicAfterSpeak = continuousVoiceEnabled &&
                (pendingReplyVoiceTrigger || lastSentByVoice) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            pendingReplyVoiceTrigger = false
            if (shouldSpeak) {
                voiceController.speak(latest.text) {
                    if (rearmMicAfterSpeak && !isListening) {
                        startVoiceCapture()
                    }
                }
            }
        }
    }

    val streamingText = (chatStatus as? ChatStatus.Streaming)?.partialText
    val isBusy = chatStatus is ChatStatus.Thinking || chatStatus is ChatStatus.Streaming

    LaunchedEffect(messages.size, streamingText, chatStatus) {
        val totalItems = messages.size + if (isBusy) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GothamVoid),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .then(
                    if (imeBottom > 0.dp) {
                        Modifier.imePadding()
                    } else {
                        Modifier.padding(bottom = contentBottomPadding)
                    }
                ),
        ) {
            // Top bar inspired by Tony
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, GothamHairlineStrong, CircleShape),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_alfred_emblem_1794796240073),
                            contentDescription = "Alfred",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alfred",
                            style = MaterialTheme.typography.headlineMedium,
                            color = GothamPureWhite,
                            maxLines = 1,
                        )
                        Text(
                            text = "Finanças & Negócios",
                            style = MaterialTheme.typography.labelMedium,
                            color = GothamSilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Voice toggles + Active Scope Pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                val next = !voiceRepliesEnabled
                                onToggleVoiceReplies(next)
                                if (!next) {
                                    voiceController.stopSpeaking()
                                }
                            }
                            .testTag("chat_voice_replies_toggle"),
                        shape = CircleShape,
                        color = if (voiceRepliesEnabled) GothamPureWhite else GothamSurface,
                        border = BorderStroke(1.dp, if (voiceRepliesEnabled) GothamPureWhite else GothamHairline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (voiceRepliesEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = if (voiceRepliesEnabled) "Desativar voz do Alfred" else "Ativar voz do Alfred",
                                tint = if (voiceRepliesEnabled) GothamVoid else GothamSilver,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    FinanceScope.entries.forEach { scopeOption ->
                        val selected = activeScope == scopeOption
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onScopeChange(scopeOption) }
                                .testTag("chat_scope_${scopeOption.wireValue.lowercase()}"),
                            shape = RoundedCornerShape(50),
                            color = if (selected) GothamPureWhite else GothamSurface,
                            border = BorderStroke(1.dp, if (selected) GothamPureWhite else GothamHairline),
                        ) {
                            Text(
                                text = scopeOption.labelPt,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) GothamVoid else GothamSilver,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            // Compact Voice Controls Strip (Conversa Contínua / Speaking indicator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            val next = !continuousVoiceEnabled
                            onToggleContinuousVoice(next)
                            if (next && !voiceRepliesEnabled) {
                                onToggleVoiceReplies(true)
                            }
                        }
                        .testTag("chat_continuous_voice_toggle"),
                    shape = RoundedCornerShape(50),
                    color = if (continuousVoiceEnabled) GothamSurfaceElevated else GothamSurface,
                    border = BorderStroke(1.dp, if (continuousVoiceEnabled) GothamPureWhite else GothamHairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (continuousVoiceEnabled) GothamPureWhite else GothamSilver,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (continuousVoiceEnabled) "Conversa contínua ON" else "Conversa contínua",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (continuousVoiceEnabled) GothamPureWhite else GothamSilver,
                        )
                    }
                }

                AnimatedVisibility(visible = isSpeaking || isListening) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                if (isSpeaking) voiceController.stopSpeaking()
                                if (isListening) {
                                    runCatching { speechRecognizer?.stopListening() }
                                    isListening = false
                                }
                            }
                            .testTag("chat_voice_active_pill"),
                        shape = RoundedCornerShape(50),
                        color = GothamPureWhite,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Stop,
                                contentDescription = "Parar voz",
                                tint = GothamVoid,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (isListening) "Ouvindo... tocar para parar" else "Alfred falando... parar",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GothamVoid,
                            )
                        }
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GothamSurface,
                                border = BorderStroke(1.dp, GothamHairline),
                            ) {
                                Text(
                                    text = "HOJE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GothamSilver,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = "Você vive. O Alfred organiza.",
                                style = MaterialTheme.typography.headlineLarge,
                                color = GothamPureWhite,
                            )
                            Text(
                                text = "Converse sobre suas finanças pessoais, estratégias para sua empresa ou diga um gasto para eu registrar na hora.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(message = msg)
                }

                if (chatStatus is ChatStatus.Streaming) {
                    item(key = "streaming_alfred_bubble") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = chatStatus.partialText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = GothamPearl,
                                modifier = Modifier.padding(end = 24.dp),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = GothamSilver,
                                    strokeWidth = 1.5.dp,
                                )
                                Text(
                                    text = if (BuildConfig.DEBUG && chatStatus.ttftMs != null) {
                                        "Alfred respondendo... (${chatStatus.ttftMs}ms)"
                                    } else {
                                        "Alfred respondendo..."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GothamSlate,
                                )
                            }
                        }
                    }
                } else if (chatStatus is ChatStatus.Thinking) {
                    item(key = "thinking_alfred_indicator") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GothamPureWhite,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Alfred está pensando...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                }
            }

            // Error banner if any
            val chatError = chatStatus as? ChatStatus.Error
            val errorText = chatError?.message ?: speechError
            AnimatedVisibility(visible = errorText != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GothamSurfaceElevated,
                    border = BorderStroke(1.dp, GothamHairlineStrong),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = errorText.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamPearl,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    speechError = null
                                    onDismissError()
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Fechar aviso",
                                    tint = GothamPureWhite,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        val failedPrompt = chatError?.failedPrompt
                        val manualDraft = chatError?.manualDraft
                        if (!failedPrompt.isNullOrBlank() || manualDraft != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!failedPrompt.isNullOrBlank()) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                onDismissError()
                                                onSendMessage(failedPrompt, null)
                                            }
                                            .testTag("alfred_retry_button"),
                                        shape = RoundedCornerShape(50),
                                        color = GothamPureWhite,
                                    ) {
                                        Text(
                                            text = "Tentar novamente",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = GothamVoid,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                                if (manualDraft != null) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                onDismissError()
                                                onOpenManualEntry(manualDraft)
                                            }
                                            .testTag("alfred_manual_entry_button"),
                                        shape = RoundedCornerShape(50),
                                        color = GothamSurface,
                                        border = BorderStroke(1.dp, GothamHairlineStrong),
                                    ) {
                                        Text(
                                            text = "Registrar manualmente",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GothamPureWhite,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Prompt Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onRequestWeeklySummary() }
                        .testTag("alfred_weekly_summary_button"),
                    shape = RoundedCornerShape(50),
                    color = GothamPureWhite,
                ) {
                    Text(
                        text = "Resumo da semana",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GothamVoid,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                QUICK_PROMPTS.forEach { prompt ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                onSendMessage(prompt, attachedBitmap)
                                attachedBitmap = null
                            },
                        shape = RoundedCornerShape(50),
                        color = GothamSurface,
                        border = BorderStroke(1.dp, GothamHairline),
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.labelMedium,
                            color = GothamPearl,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Attached Receipt Preview
            if (attachedBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(56.dp)) {
                        Image(
                            bitmap = attachedBitmap!!.asImageBitmap(),
                            contentDescription = "Comprovante anexado",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GothamHairlineStrong, RoundedCornerShape(12.dp)),
                        )
                        IconButton(
                            onClick = { attachedBitmap = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(GothamVoid),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remover imagem",
                                tint = GothamPureWhite,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Comprovante pronto para leitura do Alfred",
                        style = MaterialTheme.typography.labelMedium,
                        color = GothamSilver,
                    )
                }
            }

            // Tony-inspired Composer Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = GothamSurface,
                border = BorderStroke(1.dp, GothamHairlineStrong),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("alfred_attach_photo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Anexar comprovante",
                            tint = GothamPureWhite,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = if (isListening) "Ouvindo você..." else "Digite sua mensagem...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = GothamSlate,
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = GothamPureWhite),
                            cursorBrush = SolidColor(GothamPureWhite),
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alfred_chat_input"),
                        )
                    }

                    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
                    val micScale by pulseTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isListening) 1.15f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(550),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "mic_scale",
                    )

                    IconButton(
                        onClick = triggerMicAction,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .scale(micScale)
                            .clip(CircleShape)
                            .background(if (isListening) GothamPureWhite else Color.Transparent)
                            .testTag("alfred_voice_button"),
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (isListening) "Parar gravação" else "Falar com Alfred",
                            tint = if (isListening) GothamVoid else GothamPureWhite,
                        )
                    }

                    val canSend = (inputText.isNotBlank() || attachedBitmap != null) && !isBusy

                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) {
                                val toSend = inputText
                                val bmp = attachedBitmap
                                inputText = ""
                                attachedBitmap = null
                                lastSentByVoice = false
                                pendingReplyVoiceTrigger = voiceRepliesEnabled
                                onSendMessage(toSend, bmp)
                            }
                            .testTag("alfred_send_button"),
                        shape = CircleShape,
                        color = if (canSend) GothamPureWhite else GothamSurfaceElevated,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Enviar mensagem",
                                tint = if (canSend) GothamVoid else GothamSlate,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: AlfredChatMessage) {
    val isUser = message.sender == ChatSender.USER.wireValue
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 290.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 6.dp,
                ),
                color = GothamSurfaceElevated,
                border = BorderStroke(1.dp, GothamHairlineStrong),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GothamPureWhite,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = GothamPearl,
                modifier = Modifier.padding(end = 24.dp),
            )
            if (BuildConfig.DEBUG && message.debugTtftMs != null) {
                Text(
                    text = "1º token: ${message.debugTtftMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = GothamSlate,
                )
            }
            val badges = message.actionSummary
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (badges.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge ->
                        GothamCard(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = GothamObsidian,
                            borderColor = GothamHairlineStrong,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = GothamPureWhite,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GothamPureWhite,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeScaledBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val maxDim = 1280
        if (raw.width <= maxDim && raw.height <= maxDim) {
            raw
        } else {
            val scale = maxDim.toFloat() / maxOf(raw.width, raw.height).toFloat()
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
    } catch (_: Exception) {
        null
    }
}
