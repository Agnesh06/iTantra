package com.itantra.app.di

import android.content.Context
import com.itantra.app.data.local.AppDatabase
import com.itantra.app.data.local.entity.MessageDirection
import com.itantra.app.data.local.entity.MessageEntity
import com.itantra.app.data.local.entity.MessageStatus
import com.itantra.app.domain.ai.*
import com.itantra.app.domain.audio.AudioRecorder
import com.itantra.app.domain.model.ModelManager
import com.itantra.app.domain.playback.PlaybackManager
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import com.itantra.app.transport.diagnostics.LossSimulator
import com.itantra.app.transport.throttle.TokenBucketLimiter
import com.itantra.app.transport.wifi.WiFiDirectUdpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class AppContainer(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Data layer
    val database = AppDatabase.getInstance(context)
    val conversationDao = database.conversationDao()
    val messageDao = database.messageDao()
    val modelPackDao = database.modelPackDao()
    val telemetryDao = database.telemetryDao()
    val deviceDao = database.deviceDao()

    // Audio & Playback
    val audioRecorder = AudioRecorder()
    val playbackManager = PlaybackManager(context)

    // AI Layer
    val vadManager: VadManager = SileroVadManager(appScope)
    val asrManager: AsrManager = IndicConformerAsrManager(context)
    val translationManager: TranslationManager = IndicTrans2TranslationManager(context)
    val ttsManager: TtsManager = LocalTtsManager(context)
    val modelManager = ModelManager(context, modelPackDao)

    // Transport Layer
    val tokenBucketLimiter = TokenBucketLimiter(2000)
    val lossSimulator = LossSimulator()

    val messageIdGenerator = AtomicLong(System.currentTimeMillis())

    val transport = WiFiDirectUdpTransport(
        context = context,
        scope = appScope,
        tokenBucketLimiter = tokenBucketLimiter,
        lossSimulator = lossSimulator,
        onMessageReceived = { assembledMessage ->
            handleIncomingAssembledMessage(assembledMessage)
        },
        onMessageFailed = { messageId, reason ->
            handleMessageFailed(messageId, reason)
        }
    )

    private fun handleIncomingAssembledMessage(assembled: com.itantra.app.transport.protocol.AssembledMessage) {
        appScope.launch(Dispatchers.IO) {
            try {
                val jsonString = String(assembled.payloadBytes, Charsets.UTF_8)
                val json = JSONObject(jsonString)

                val messageId = json.optLong("messageId", assembled.messageId)
                val sourceLang = json.optString("sourceLanguage", "en")
                val targetLang = json.optString("targetLanguage", "hi")
                val alertFlag = json.optBoolean("alertFlag", assembled.isAlert)
                val translationUsed = json.optBoolean("translationUsed", false)
                val text = json.optString("text", "")
                val confArray = json.optJSONArray("confidenceFlags") ?: JSONArray()
                val confFlagsJson = confArray.toString()

                val audioCacheDir = File(context.cacheDir, "audio_messages").apply { mkdirs() }
                val audioFile = File(audioCacheDir, "msg_${messageId}.wav")

                // Synthesis safe check
                val ttsResult = ttsManager.synthesize(
                    text = text,
                    language = targetLang,
                    outputPath = audioFile,
                    isTranslationFailed = false
                )

                val audioPath = if (ttsResult.isSuccess) audioFile.absolutePath else null

                val entity = MessageEntity(
                    id = messageId,
                    conversationId = "default_conv",
                    direction = MessageDirection.INCOMING,
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang,
                    text = text,
                    translatedText = if (translationUsed) text else null,
                    alertFlag = alertFlag,
                    confidenceFlagsJson = confFlagsJson,
                    status = MessageStatus.READY,
                    receivedAt = System.currentTimeMillis(),
                    audioPath = audioPath
                )
                messageDao.insertOrUpdate(entity)

                // Section 13.1: Receiver branching
                if (alertFlag && audioFile.exists()) {
                    AppLogger.i("Container", "Auto-playing received ALERT message $messageId", category = LogCategory.APP)
                    playbackManager.playAlert(audioFile) {
                        appScope.launch {
                            messageDao.updateStatus(messageId, MessageStatus.COMPLETED)
                        }
                    }
                } else {
                    AppLogger.i("Container", "Stored received NORMAL message $messageId (tappable voice note)", category = LogCategory.APP)
                }

            } catch (e: Exception) {
                AppLogger.e("Container", "Failed to process incoming message: ${e.message}", e, category = LogCategory.APP)
            }
        }
    }

    private fun handleMessageFailed(messageId: Long, reason: String) {
        appScope.launch(Dispatchers.IO) {
            val status = if (reason.contains("15s")) {
                MessageStatus.INCOMPLETE_TIMEOUT
            } else {
                MessageStatus.ERROR
            }
            messageDao.updateStatus(messageId, status)
            AppLogger.w("Container", "Message $messageId status updated to $status (reason: $reason)", category = LogCategory.NETWORK)
        }
    }
}
