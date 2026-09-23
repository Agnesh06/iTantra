package com.itantra.app.transport.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object PacketConstants {
    const val MAGIC_0: Byte = 0x49 // 'I'
    const val MAGIC_1: Byte = 0x54 // 'T'
    const val CURRENT_VERSION: Byte = 0x01

    const val TYPE_DATA: Byte = 0
    const val TYPE_FEC: Byte = 1
    const val TYPE_HELLO: Byte = 2
    const val TYPE_ACK: Byte = 3

    const val FLAG_ALERT: Byte = 0x01
    const val FLAG_CONFIDENCE_PRESENT: Byte = 0x02
    const val FLAG_TRANSLATION_USED: Byte = 0x04

    const val HEADER_SIZE_WITHOUT_CRC = 17
    const val HEADER_TOTAL_SIZE = 21 // 17 + 4 CRC32

    const val ACK_STATUS_COMPLETE: Byte = 0
    const val ACK_STATUS_FEC_RECONSTRUCTED: Byte = 1
}

data class PacketHeader(
    val version: Byte = PacketConstants.CURRENT_VERSION,
    val type: Byte,
    val flags: Byte,
    val messageId: Long,
    val seq: Int,
    val total: Int,
    val payloadLen: Int
) {
    val isAlert: Boolean get() = (flags.toInt() and PacketConstants.FLAG_ALERT.toInt()) != 0
    val hasConfidence: Boolean get() = (flags.toInt() and PacketConstants.FLAG_CONFIDENCE_PRESENT.toInt()) != 0
    val isTranslationUsed: Boolean get() = (flags.toInt() and PacketConstants.FLAG_TRANSLATION_USED.toInt()) != 0
}

data class Packet(
    val header: PacketHeader,
    val payload: ByteArray,
    val crc32: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Packet
        return header == other.header &&
                payload.contentEquals(other.payload) &&
                crc32 == other.crc32
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crc32.hashCode()
        return result
    }
}

data class AckPayload(
    val frameGroupId: Int,
    val status: Byte, // 0 = COMPLETE, 1 = FEC_RECONSTRUCTED
    val seqStart: Int,
    val seqEnd: Int
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(frameGroupId.toShort())
        buffer.put(status)
        buffer.putShort(seqStart.toShort())
        buffer.putShort(seqEnd.toShort())
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): AckPayload {
            require(bytes.size >= 7) { "ACK payload too short: ${bytes.size}" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val groupId = buffer.short.toInt() and 0xFFFF
            val status = buffer.get()
            val start = buffer.short.toInt() and 0xFFFF
            val end = buffer.short.toInt() and 0xFFFF
            return AckPayload(groupId, status, start, end)
        }
    }
}

data class HelloPayload(
    val deviceId: String,
    val advertisedUdpPort: Int
) {
    fun toByteArray(): ByteArray {
        val idBytes = deviceId.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + idBytes.size + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.putInt(advertisedUdpPort)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): HelloPayload {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val idLen = buffer.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val port = buffer.int
            return HelloPayload(String(idBytes, Charsets.UTF_8), port)
        }
    }
}

object PacketSerializer {

    fun serialize(header: PacketHeader, payload: ByteArray): ByteArray {
        val totalLength = PacketConstants.HEADER_TOTAL_SIZE + payload.size
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        // Write magic
        buffer.put(PacketConstants.MAGIC_0)
        buffer.put(PacketConstants.MAGIC_1)

        // Write header fields
        buffer.put(header.version)
        buffer.put(header.type)
        buffer.put(header.flags)
        buffer.putLong(header.messageId)
        buffer.putShort(header.seq.toShort())
        buffer.putShort(header.total.toShort())
        buffer.putShort(payload.size.toShort())

        // Write payload
        buffer.put(payload)

        // Compute CRC32 over all bytes written so far
        val crc = CRC32()
        crc.update(buffer.array(), 0, buffer.position())
        val crcValue = crc.value

        // Write CRC32 at end
        buffer.putInt(crcValue.toInt())

        return buffer.array()
    }

    fun deserialize(raw: ByteArray): Packet {
        if (raw.size < PacketConstants.HEADER_TOTAL_SIZE) {
            throw IllegalArgumentException("Packet too short: size ${raw.size}")
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)

        val m0 = buffer.get()
        val m1 = buffer.get()
        if (m0 != PacketConstants.MAGIC_0 || m1 != PacketConstants.MAGIC_1) {
            throw IllegalArgumentException("Invalid magic bytes: $m0, $m1")
        }

        val version = buffer.get()
        if (version != PacketConstants.CURRENT_VERSION) {
            throw IllegalArgumentException("Unsupported protocol version: $version")
        }

        val type = buffer.get()
        val flags = buffer.get()
        val messageId = buffer.long
        val seq = buffer.short.toInt() and 0xFFFF
        val total = buffer.short.toInt() and 0xFFFF
        val payloadLen = buffer.short.toInt() and 0xFFFF

        if (raw.size != PacketConstants.HEADER_TOTAL_SIZE + payloadLen) {
            throw IllegalArgumentException("Payload size mismatch: declared $payloadLen, total raw ${raw.size}")
        }

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val declaredCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val crcCheck = CRC32()
        crcCheck.update(raw, 0, raw.size - 4)
        if (crcCheck.value != declaredCrc) {
            throw SecurityException("CRC32 mismatch: calculated ${crcCheck.value}, declared $declaredCrc")
        }

        val header = PacketHeader(
            version = version,
            type = type,
            flags = flags,
            messageId = messageId,
            seq = seq,
            total = total,
            payloadLen = payloadLen
        )

        return Packet(header, payload, declaredCrc)
    }
}
