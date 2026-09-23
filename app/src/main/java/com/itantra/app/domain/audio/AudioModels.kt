package com.itantra.app.domain.audio

data class PcmFrame(
    val data: ShortArray,
    val sampleRate: Int = 16000,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PcmFrame
        return data.contentEquals(other.data) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

class PcmAudioBuffer(
    val sampleRate: Int = 16000
) {
    private val samples = mutableListOf<Short>()

    @Synchronized
    fun addFrame(frame: PcmFrame) {
        frame.data.forEach { samples.add(it) }
    }

    @Synchronized
    fun toShortArray(): ShortArray = samples.toShortArray()

    @Synchronized
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    val durationMs: Long
        @Synchronized get() = (samples.size * 1000L) / sampleRate

    @Synchronized
    fun clear() {
        samples.clear()
    }
}
