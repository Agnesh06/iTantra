package com.itantra.app

import com.itantra.app.transport.fec.FecEngine
import com.itantra.app.transport.fec.FrameGroup
import com.itantra.app.transport.protocol.Packet
import com.itantra.app.transport.protocol.PacketConstants
import com.itantra.app.transport.protocol.PacketHeader
import org.junit.Assert.*
import org.junit.Test

class FecEngineTest {

    @Test
    fun testParityFrameGenerationAndSingleLossRecovery() {
        val msgId = 42L
        val data0 = "AAA".toByteArray(Charsets.UTF_8)
        val data1 = "BBBB".toByteArray(Charsets.UTF_8)
        val data2 = "CCCCC".toByteArray(Charsets.UTF_8)

        val packet0 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = msgId, seq = 0, total = 3, payloadLen = data0.size), data0, 0L)
        val packet1 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = msgId, seq = 1, total = 3, payloadLen = data1.size), data1, 0L)
        val packet2 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = msgId, seq = 2, total = 3, payloadLen = data2.size), data2, 0L)

        // 1. Generate parity frame
        val parity = FecEngine.createParityFrame(
            messageId = msgId,
            frameGroupId = 0,
            totalDataFrames = 3,
            flags = 0,
            dataPackets = listOf(packet0, packet1, packet2)
        )

        // Verify non-colliding FEC SEQ equals frameGroupId
        assertEquals(PacketConstants.TYPE_FEC, parity.header.type)
        assertEquals(0, parity.header.seq)
        assertEquals(5, parity.payload.size) // max length is 5 ("CCCCC")

        // 2. Simulate loss of packet1 (seq=1)
        val group = FrameGroup(frameGroupId = 0, expectedCount = 3)
        group.dataFrames[0] = packet0
        // packet1 is missing!
        group.dataFrames[2] = packet2
        group.fecFrame = parity

        // 3. Reconstruct missing frame
        val reconstructed = FecEngine.reconstructMissingFrame(
            frameGroup = group,
            messageId = msgId,
            totalDataFrames = 3,
            flags = 0,
            expectedSeqs = listOf(0, 1, 2)
        )

        assertNotNull(reconstructed)
        assertEquals(1, reconstructed!!.header.seq)
        assertEquals(PacketConstants.TYPE_DATA, reconstructed.header.type)

        // The reconstructed payload will match original data1 bytes (truncated/padded)
        for (i in data1.indices) {
            assertEquals(data1[i], reconstructed.payload[i])
        }
    }

    @Test
    fun testMultiLossFailureDetection() {
        val msgId = 42L
        val data0 = "AAA".toByteArray()
        val packet0 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = msgId, seq = 0, total = 3, payloadLen = data0.size), data0, 0L)

        val parity = FecEngine.createParityFrame(
            messageId = msgId,
            frameGroupId = 0,
            totalDataFrames = 3,
            flags = 0,
            dataPackets = listOf(packet0)
        )

        // Group has lost 2 packets (seq=1 and seq=2 missing)
        val group = FrameGroup(frameGroupId = 0, expectedCount = 3)
        group.dataFrames[0] = packet0
        group.fecFrame = parity

        val reconstructed = FecEngine.reconstructMissingFrame(
            frameGroup = group,
            messageId = msgId,
            totalDataFrames = 3,
            flags = 0,
            expectedSeqs = listOf(0, 1, 2)
        )

        // More than 1 frame missing -> cannot reconstruct via single XOR parity
        assertNull(reconstructed)
    }
}
