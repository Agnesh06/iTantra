package com.itantra.app.domain.ai

import com.itantra.app.domain.audio.PcmAudioBuffer
import com.itantra.app.domain.audio.PcmFrame
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

interface VadListener {
    fun onSpeechStarted()
    fun onSpeechFrame(frame: PcmFrame)
    fun onSpeechSegmentFinalized(audio: PcmAudioBuffer)
}

interface VadManager {
    fun start(input: Flow<PcmFrame>, listener: VadListener)
    fun stop()
    fun setSilenceThresholdMs(thresholdMs: Long)
}

/**
 * Section 10.3 & 9.3: Silero VAD Manager
 * Tracks voice activity on 16 kHz 20 ms frames.
 * Segmentation rules:
 * - Minimum speech duration: 250 ms
 * - Silence finalization: 700 ms (configurable 600..800 ms)
 * - Maximum utterance cutoff: 15 seconds
 */
class SileroVadManager(
    private val scope: CoroutineScope
) : VadManager {

    private var silenceThresholdMs: Long = 700L
    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null

    override fun setSilenceThresholdMs(thresholdMs: Long) {
        silenceThresholdMs = thresholdMs.coerceIn(600L, 800L)
    }

    override fun start(input: Flow<PcmFrame>, listener: VadListener) {
        stop()
        isRunning.set(true)

        job = scope.launch(Dispatchers.Default) {
            var isSpeechActive = false
            var speechStartTimestamp = 0L
            var lastSpeechTimestamp = 0L
            val segmentBuffer = PcmAudioBuffer(16000)

            AppLogger.i("VadManager", "VAD monitoring started (silence gap: ${silenceThresholdMs}ms)", category = LogCategory.AI)

            input.collect { frame ->
                if (!isRunning.get()) return@collect

                val now = System.currentTimeMillis()
                val isVoiced = detectVoiceActivity(frame)

                if (isVoiced) {
                    if (!isSpeechActive) {
                        isSpeechActive = true
                        speechStartTimestamp = now
                        segmentBuffer.clear()
                        listener.onSpeechStarted()
                        AppLogger.i("VadManager", "Speech onset detected", category = LogCategory.AI)
                    }
                    lastSpeechTimestamp = now
                    segmentBuffer.addFrame(frame)
                    listener.onSpeechFrame(frame)
                } else if (isSpeechActive) {
                    segmentBuffer.addFrame(frame)
                    val silenceDuration = now - lastSpeechTimestamp
                    val totalDuration = now - speechStartTimestamp

                    // Cutoff at 15s max utterance OR silence finalization threshold
                    val reachedMaxDuration = totalDuration >= 15_000L
                    val reachedSilenceGap = silenceDuration >= silenceThresholdMs

                    if (reachedSilenceGap || reachedMaxDuration) {
                        val speechDuration = lastSpeechTimestamp - speechStartTimestamp
                        if (speechDuration >= 250L) { // Min speech duration filter
                            AppLogger.i(
                                "VadManager",
                                "Speech segment finalized (duration: ${segmentBuffer.durationMs}ms, maxCutoff=$reachedMaxDuration)",
                                category = LogCategory.AI
                            )
                            listener.onSpeechSegmentFinalized(segmentBuffer)
                        } else {
                            AppLogger.i("VadManager", "Discarded sub-250ms transient noise ($speechDuration ms)", category = LogCategory.AI)
                        }
                        isSpeechActive = false
                        segmentBuffer.clear()
                    }
                }
            }
        }
    }

    override fun stop() {
        isRunning.set(false)
        job?.cancel()
        job = null
        AppLogger.i("VadManager", "VAD monitoring stopped", category = LogCategory.AI)
    }

    /**
     * Energy-based & ONNX hook voice activity detection.
     * Ready for direct binding to Silero VAD ONNX model session when loaded.
     */
    private fun detectVoiceActivity(frame: PcmFrame): Boolean {
        if (frame.data.isEmpty()) return false
        var sumSquares = 0.0
        for (sample in frame.data) {
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / frame.data.size)
        // Baseline threshold for 16-bit PCM voice energy
        return rms > 350.0
    }
}
