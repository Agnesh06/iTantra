package com.itantra.app.domain.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Section 10.1 & 10.2: Audio Capture using Android AudioRecord.
 * Raw 16 kHz 16-bit mono little-endian PCM frames delivered on Dispatchers.IO.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_MS = 20
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000 // 320 samples
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(): Flow<PcmFrame> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, SAMPLES_PER_FRAME * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AppLogger.e("AudioRecorder", "Failed to initialize AudioRecord", category = LogCategory.APP)
                return@flow
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            AppLogger.i("AudioRecorder", "Audio recording started (16 kHz mono 20ms frames)", category = LogCategory.APP)

            val buffer = ShortArray(SAMPLES_PER_FRAME)
            while (isRecording.get() && currentCoroutineContext().isActive) {
                val readSamples = audioRecord?.read(buffer, 0, SAMPLES_PER_FRAME) ?: 0
                if (readSamples > 0) {
                    val frameData = if (readSamples == SAMPLES_PER_FRAME) {
                        buffer.copyOf()
                    } else {
                        buffer.copyOfRange(0, readSamples)
                    }
                    emit(PcmFrame(data = frameData, sampleRate = SAMPLE_RATE))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AudioRecorder", "Exception during audio recording", e, category = LogCategory.APP)
        } finally {
            cleanup()
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        isRecording.set(false)
        cleanup()
    }

    fun release() {
        stop()
    }

    private fun cleanup() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null
            AppLogger.i("AudioRecorder", "AudioRecord released", category = LogCategory.APP)
        } catch (e: Exception) {
            AppLogger.e("AudioRecorder", "Error cleaning up AudioRecord", e, category = LogCategory.APP)
        }
    }
}
