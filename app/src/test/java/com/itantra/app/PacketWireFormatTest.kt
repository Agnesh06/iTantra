package com.itantra.app

import com.itantra.app.transport.protocol.*
import org.junit.Assert.*
import org.junit.Test

class PacketWireFormatTest {

    @Test
    fun testDataPacketRoundTrip() {
        val payload = "Hello ISRO iTantra low-bitrate radio".toByteArray(Charsets.UTF_8)
        val header = PacketHeader(
            version = PacketConstants.CURRENT_VERSION,
            type = PacketConstants.TYPE_DATA,
            flags = (PacketConstants.FLAG_ALERT.toInt() or PacketConstants.FLAG_CONFIDENCE_PRESENT.toInt()).toByte(),
            messageId = 1234567890123456789L,
            seq = 2,
            total = 10,
            payloadLen = payload.size
        )

        val serialized = PacketSerializer.serialize(header, payload)
        assertEquals(PacketConstants.HEADER_TOTAL_SIZE + payload.size, serialized.size)

        val deserialized = PacketSerializer.deserialize(serialized)
        assertEquals(PacketConstants.CURRENT_VERSION, deserialized.header.version)
        assertEquals(PacketConstants.TYPE_DATA, deserialized.header.type)
        assertTrue(deserialized.header.isAlert)
        assertTrue(deserialized.header.hasConfidence)
        assertFalse(deserialized.header.isTranslationUsed)
        assertEquals(1234567890123456789L, deserialized.header.messageId)
        assertEquals(2, deserialized.header.seq)
        assertEquals(10, deserialized.header.total)
        assertArrayEquals(payload, deserialized.payload)
    }

    @Test(expected = SecurityException::class)
    fun testCrc32CorruptionDetection() {
        val payload = "Testing CRC corruption".toByteArray(Charsets.UTF_8)
        val header = PacketHeader(
            version = PacketConstants.CURRENT_VERSION,
            type = PacketConstants.TYPE_DATA,
            flags = 0,
            messageId = 999L,
            seq = 0,
            total = 1,
            payloadLen = payload.size
        )

        val serialized = PacketSerializer.serialize(header, payload)
        // Corrupt one payload byte
        serialized[PacketConstants.HEADER_TOTAL_SIZE - 2] = (serialized[PacketConstants.HEADER_TOTAL_SIZE - 2].toInt() xor 0xFF).toByte()

        // Deserialization must throw SecurityException on CRC mismatch
        PacketSerializer.deserialize(serialized)
    }

    @Test
    fun testAckPayloadSerialization() {
        val ack = AckPayload(
            frameGroupId = 4,
            status = PacketConstants.ACK_STATUS_FEC_RECONSTRUCTED,
            seqStart = 12,
            seqEnd = 14
        )
        val bytes = ack.toByteArray()
        assertEquals(7, bytes.size)

        val decoded = AckPayload.fromByteArray(bytes)
        assertEquals(4, decoded.frameGroupId)
        assertEquals(PacketConstants.ACK_STATUS_FEC_RECONSTRUCTED, decoded.status)
        assertEquals(12, decoded.seqStart)
        assertEquals(14, decoded.seqEnd)
    }
}
