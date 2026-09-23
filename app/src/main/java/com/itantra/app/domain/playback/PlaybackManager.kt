package com.itantra.app.domain.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class PlayingNormal(val audioPath: String, val progressMs: Int, val durationMs: Int) : PlaybackState()
    data class PlayingAlert(val audioPath: String, val progressMs: Int, val durationMs: Int) : PlaybackState()
    data class Completed(val audioPath: String) : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

/**
 * Section 13: Alert vs Normal Audio Playback Manager.
 */
class PlaybackManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var priorAlarmVolume: Int? = null
    private var currentPlayingAlertPath: String? = null

    /**
     * Section 13.2: Play Alert Message
     * USAGE_ALARM, CONTENT_TYPE_SPEECH, volume boosted to max, non-interruptible via UI.
     */
    fun playAlert(audioFile: File, onComplete: () -> Unit) {
        if (!audioFile.exists()) {
            AppLogger.e("Playback", "Alert audio file does not exist: ${audioFile.absolutePath}", category = LogCategory.APP)
            _playbackState.value = PlaybackState.Error("Audio file not found")
            return
        }

        stopCurrentPlayback()

        try {
            // Save prior alarm volume and boost to max
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            priorAlarmVolume = currentVol
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            AppLogger.i("Playback", "Alarm volume boosted from $currentVol to $maxVol for alert", category = LogCategory.APP)

            currentPlayingAlertPath = audioFile.absolutePath

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, Uri.fromFile(audioFile))
                prepare()
            }

            player.setOnCompletionListener {
                restoreAlarmVolume()
                val path = currentPlayingAlertPath ?: audioFile.absolutePath
                currentPlayingAlertPath = null
                _playbackState.value = PlaybackState.Completed(path)
                AppLogger.i("Playback", "Alert playback completed", category = LogCategory.APP)
                it.release()
                mediaPlayer = null
                onComplete()
            }

            player.setOnErrorListener { _, what, extra ->
                restoreAlarmVolume()
                currentPlayingAlertPath = null
                AppLogger.e("Playback", "Alert playback error: what=$what extra=$extra", category = LogCategory.APP)
                _playbackState.value = PlaybackState.Error("Playback error ($what, $extra)")
                true
            }

            mediaPlayer = player
            player.start()
            _playbackState.value = PlaybackState.PlayingAlert(
                audioPath = audioFile.absolutePath,
                progressMs = 0,
                durationMs = player.duration
            )
            AppLogger.i("Playback", "Alert playback started (auto-play)", category = LogCategory.APP)

        } catch (e: Exception) {
            restoreAlarmVolume()
            currentPlayingAlertPath = null
            AppLogger.e("Playback", "Failed to start alert playback", e, category = LogCategory.APP)
            _playbackState.value = PlaybackState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Section 13.3: Play Normal Voice Note
     * USAGE_MEDIA, respects user media volume, manual play only (no auto-play).
     */
    fun playNormalVoiceNote(audioFile: File, onComplete: () -> Unit) {
        if (!audioFile.exists()) {
            AppLogger.e("Playback", "Voice note audio file does not exist: ${audioFile.absolutePath}", category = LogCategory.APP)
            _playbackState.value = PlaybackState.Error("Audio file not found")
            return
        }

        stopCurrentPlayback()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, Uri.fromFile(audioFile))
                prepare()
            }

            player.setOnCompletionListener {
                _playbackState.value = PlaybackState.Completed(audioFile.absolutePath)
                AppLogger.i("Playback", "Normal voice note playback completed", category = LogCategory.APP)
                it.release()
                mediaPlayer = null
                onComplete()
            }

            player.setOnErrorListener { _, what, extra ->
                AppLogger.e("Playback", "Normal voice note playback error: what=$what extra=$extra", category = LogCategory.APP)
                _playbackState.value = PlaybackState.Error("Playback error ($what, $extra)")
                true
            }

            mediaPlayer = player
            player.start()
            _playbackState.value = PlaybackState.PlayingNormal(
                audioPath = audioFile.absolutePath,
                progressMs = 0,
                durationMs = player.duration
            )
            AppLogger.i("Playback", "Normal voice note playback started", category = LogCategory.APP)

        } catch (e: Exception) {
            AppLogger.e("Playback", "Failed to play voice note", e, category = LogCategory.APP)
            _playbackState.value = PlaybackState.Error(e.message ?: "Unknown error")
        }
    }

    fun stopCurrentPlayback() {
        // If alert is playing, alert player requirements state normal pause/stop is disabled in UI,
        // but if system resets or new alert overrides, clean up.
        restoreAlarmVolume()
        currentPlayingAlertPath = null
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState.Idle
    }

    private fun restoreAlarmVolume() {
        priorAlarmVolume?.let { vol ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
                AppLogger.i("Playback", "Restored alarm volume to $vol", category = LogCategory.APP)
            } catch (e: Exception) {
                AppLogger.w("Playback", "Failed to restore alarm volume: ${e.message}", e, category = LogCategory.APP)
            }
            priorAlarmVolume = null
        }
    }

    fun isAlertPlaying(): Boolean = currentPlayingAlertPath != null
}
