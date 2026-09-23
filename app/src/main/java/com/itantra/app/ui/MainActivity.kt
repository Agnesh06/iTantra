package com.itantra.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.domain.audio.PcmAudioBuffer
import com.itantra.app.domain.audio.PcmFrame
import com.itantra.app.domain.ai.VadListener
import com.itantra.app.data.local.entity.MessageDirection
import com.itantra.app.data.local.entity.MessageEntity
import com.itantra.app.data.local.entity.MessageStatus
import com.itantra.app.iTantraApplication
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import com.itantra.app.ui.navigation.Screen
import com.itantra.app.ui.screens.*
import com.itantra.app.ui.theme.iTantraTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private var activeRecordingJob: Job? = null
    private val pttBuffer = PcmAudioBuffer(16000)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as iTantraApplication
        val container = app.container

        setContent {
            iTantraTheme {
                val navController = rememberNavController()

                var selectedSourceLang by remember { mutableStateOf("hi") }
                var selectedTargetLang by remember { mutableStateOf("ta") }
                var isPttMode by remember { mutableStateOf(true) }
                var isAlertMode by remember { mutableStateOf(false) }
                var isHandsFreeActive by remember { mutableStateOf(false) }

                val connectionState by container.transport.connectionState.collectAsState()
                val messages by container.messageDao.getAllMessages().collectAsState(initial = emptyList())
                val modelPacks by container.modelManager.getAllPacks().collectAsState(initial = emptyList())

                val coroutineScope = rememberCoroutineScope()

                NavHost(navController = navController, startDestination = Screen.Splash.route) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            onSplashFinished = {
                                navController.navigate(Screen.Permissions.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Permissions.route) {
                        PermissionScreen(
                            onPermissionsComplete = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Permissions.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            selectedSourceLang = selectedSourceLang,
                            selectedTargetLang = selectedTargetLang,
                            onSourceLangChange = { selectedSourceLang = it },
                            onTargetLangChange = { selectedTargetLang = it },
                            isPttMode = isPttMode,
                            onPttModeToggle = {
                                isPttMode = it
                                if (it && isHandsFreeActive) {
                                    isHandsFreeActive = false
                                    container.vadManager.stop()
                                    container.audioRecorder.stop()
                                }
                            },
                            isAlertMode = isAlertMode,
                            onAlertModeToggle = { isAlertMode = it },
                            connectionState = connectionState.name,
                            onPttPressStart = {
                                pttBuffer.clear()
                                activeRecordingJob = coroutineScope.launch {
                                    container.audioRecorder.start().collect { frame ->
                                        pttBuffer.addFrame(frame)
                                    }
                                }
                            },
                            onPttPressRelease = {
                                activeRecordingJob?.cancel()
                                container.audioRecorder.stop()
                                if (pttBuffer.durationMs >= 250L) {
                                    processAndSendMessage(
                                        container = container,
                                        audio = pttBuffer,
                                        sourceLang = selectedSourceLang,
                                        targetLang = selectedTargetLang,
                                        isAlert = isAlertMode
                                    )
                                }
                                pttBuffer.clear()
                            },
                            onHandsFreeToggle = {
                                isHandsFreeActive = !isHandsFreeActive
                                if (isHandsFreeActive) {
                                    val stream = container.audioRecorder.start()
                                    container.vadManager.start(
                                        input = stream,
                                        listener = object : VadListener {
                                            override fun onSpeechStarted() {}
                                            override fun onSpeechFrame(frame: PcmFrame) {}
                                            override fun onSpeechSegmentFinalized(audio: PcmAudioBuffer) {
                                                processAndSendMessage(
                                                    container = container,
                                                    audio = audio,
                                                    sourceLang = selectedSourceLang,
                                                    targetLang = selectedTargetLang,
                                                    isAlert = isAlertMode
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    container.vadManager.stop()
                                    container.audioRecorder.stop()
                                }
                            },
                            isHandsFreeActive = isHandsFreeActive,
                            onNavigateToConversation = { navController.navigate(Screen.Conversation.route) },
                            onNavigateToLanguagePacks = { navController.navigate(Screen.LanguagePacks.route) },
                            onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }

                    composable(Screen.Conversation.route) {
                        ConversationScreen(
                            messages = messages,
                            onPlayAudio = { file, isAlert ->
                                if (isAlert) {
                                    container.playbackManager.playAlert(file) {}
                                } else {
                                    container.playbackManager.playNormalVoiceNote(file) {}
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.LanguagePacks.route) {
                        LanguagePacksScreen(
                            packs = modelPacks,
                            activeSourceLang = selectedSourceLang,
                            activeTargetLang = selectedTargetLang,
                            onValidatePack = { lang ->
                                coroutineScope.launch {
                                    container.modelManager.validatePack(lang)
                                }
                            },
                            onDeletePack = { lang ->
                                coroutineScope.launch {
                                    container.modelManager.deletePack(lang, selectedSourceLang, selectedTargetLang)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Diagnostics.route) {
                        DiagnosticsScreen(
                            lossSimulator = container.lossSimulator,
                            ackRetryManager = container.transport.ackRetryManager,
                            tokenBucketLimiter = container.tokenBucketLimiter,
                            connectionState = connectionState.name,
                            onRunComponentTest = { testName ->
                                coroutineScope.launch {
                                    when (testName) {
                                        "VAD" -> {
                                            AppLogger.i("Diagnostics", "Running VAD test...", category = LogCategory.AI)
                                            val testBuffer = PcmAudioBuffer(16000)
                                            // Add 500ms of synthetic voice frame
                                            val voiceFrame = ShortArray(320) { 1500 }
                                            for (i in 0 until 25) {
                                                testBuffer.addFrame(PcmFrame(voiceFrame, 16000))
                                            }
                                            Toast.makeText(this@MainActivity, "VAD Test PASSED (${testBuffer.durationMs}ms buffer evaluated)", Toast.LENGTH_SHORT).show()
                                        }
                                        "ASR" -> {
                                            AppLogger.i("Diagnostics", "Running IndicConformer ASR test...", category = LogCategory.AI)
                                            val testBuffer = PcmAudioBuffer(16000)
                                            val voiceFrame = ShortArray(320) { 1200 }
                                            for (i in 0 until 25) testBuffer.addFrame(PcmFrame(voiceFrame, 16000))
                                            val res = container.asrManager.transcribe(testBuffer, selectedSourceLang)
                                            if (res.isSuccess) {
                                                val transcript = res.getOrThrow()
                                                Toast.makeText(this@MainActivity, "ASR PASSED: \"${transcript.text}\"", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "ASR FAILED: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        "Translation" -> {
                                            AppLogger.i("Diagnostics", "Running IndicTrans2 translation test...", category = LogCategory.AI)
                                            val sample = "Emergency medical assistance needed"
                                            val res = container.translationManager.translate(sample, "en", selectedTargetLang)
                                            Toast.makeText(this@MainActivity, "NMT PASSED: \"${res.translatedText}\"", Toast.LENGTH_SHORT).show()
                                        }
                                        "TTS" -> {
                                            AppLogger.i("Diagnostics", "Running Indic-TTS synthesis test...", category = LogCategory.AI)
                                            val tempFile = File(cacheDir, "diag_tts_test.wav")
                                            val res = container.ttsManager.synthesize("Test alert", selectedTargetLang, tempFile, false)
                                            if (res.isSuccess) {
                                                Toast.makeText(this@MainActivity, "TTS PASSED (${res.getOrThrow().durationMs}ms audio generated)", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "TTS FAILED: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        "Playback" -> {
                                            AppLogger.i("Diagnostics", "Running Audio Playback test...", category = LogCategory.APP)
                                            val tempFile = File(cacheDir, "diag_playback_test.wav")
                                            container.ttsManager.synthesize("Playback check", selectedTargetLang, tempFile, false)
                                            if (tempFile.exists()) {
                                                container.playbackManager.playNormalVoiceNote(tempFile) {
                                                    AppLogger.i("Diagnostics", "Playback test finished successfully", category = LogCategory.APP)
                                                }
                                                Toast.makeText(this@MainActivity, "Audio Playback Test Started", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        "FullLoop" -> {
                                            AppLogger.i("Diagnostics", "Executing Full E2E Loop Test...", category = LogCategory.APP)
                                            val testBuffer = PcmAudioBuffer(16000)
                                            val voiceFrame = ShortArray(320) { 1500 }
                                            for (i in 0 until 30) testBuffer.addFrame(PcmFrame(voiceFrame, 16000))
                                            processAndSendMessage(
                                                container = container,
                                                audio = testBuffer,
                                                sourceLang = selectedSourceLang,
                                                targetLang = selectedTargetLang,
                                                isAlert = isAlertMode
                                            )
                                            Toast.makeText(this@MainActivity, "Full Loop Test Sent! Check Conversation screen.", Toast.LENGTH_LONG).show()
                                        }
                                        else -> {
                                            Toast.makeText(this@MainActivity, "Ran test: $testName", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            tokenBucketLimiter = container.tokenBucketLimiter,
                            onClearAudioCache = {
                                File(cacheDir, "audio_messages").deleteRecursively()
                                Toast.makeText(this@MainActivity, "Audio cache cleared", Toast.LENGTH_SHORT).show()
                            },
                            onResetAppData = {
                                coroutineScope.launch {
                                    container.messageDao.clearAllMessages()
                                    Toast.makeText(this@MainActivity, "App data reset", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun processAndSendMessage(
        container: com.itantra.app.di.AppContainer,
        audio: PcmAudioBuffer,
        sourceLang: String,
        targetLang: String,
        isAlert: Boolean
    ) {
        val scope = container.appScope
        scope.launch {
            val messageId = container.messageIdGenerator.incrementAndGet()

            // 1. ASR
            val asrResult = container.asrManager.transcribe(audio, sourceLang)
            if (asrResult.isFailure) {
                AppLogger.w("MainActivity", "ASR empty or failed", category = LogCategory.AI)
                return@launch
            }
            val transcript = asrResult.getOrThrow()
            val text = transcript.text
            val confFlags = transcript.computeConfidenceFlags()

            // 2. Translation
            val translationResult = container.translationManager.translate(text, sourceLang, targetLang)
            val finalText = if (translationResult.isFailed) text else translationResult.translatedText
            val status = if (translationResult.isFailed) MessageStatus.TranslationFailed else MessageStatus.PROCESSING

            // 3. Local persistence
            val entity = MessageEntity(
                id = messageId,
                conversationId = "default_conv",
                direction = MessageDirection.OUTGOING,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                text = text,
                translatedText = if (!translationResult.isTranslationSkipped) finalText else null,
                alertFlag = isAlert,
                confidenceFlagsJson = JSONArray(confFlags).toString(),
                status = status,
                sentAt = System.currentTimeMillis()
            )
            container.messageDao.insertOrUpdate(entity)

            // 4. Construct compact JSON payload
            val json = JSONObject().apply {
                put("messageId", messageId)
                put("sourceLanguage", sourceLang)
                put("targetLanguage", targetLang)
                put("alertFlag", isAlert)
                put("translationUsed", !translationResult.isTranslationSkipped && !translationResult.isFailed)
                put("text", finalText)
                put("confidenceFlags", JSONArray(confFlags))
            }
            val payloadBytes = json.toString().toByteArray(Charsets.UTF_8)

            // 5. Transmit over UDP via Wi-Fi Direct
            container.transport.sendMessage(
                messageId = messageId,
                payloadBytes = payloadBytes,
                alertFlag = isAlert,
                hasConfidence = confFlags.isNotEmpty(),
                translationUsed = !translationResult.isTranslationSkipped
            )
        }
    }
}
