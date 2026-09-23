package com.itantra.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long?,
    val stage: String, // e.g. "VAD", "ASR", "TRANSLATION", "PACKETIZE", "TX_UDP", "RX_UDP", "TTS", "PLAYBACK"
    val durationMs: Long,
    val cpuPercent: Float,
    val ramBytes: Long,
    val bytesTx: Long,
    val bytesRx: Long,
    val timestamp: Long = System.currentTimeMillis()
)
