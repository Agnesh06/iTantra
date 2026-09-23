package com.itantra.app.transport.fec

import com.itantra.app.transport.protocol.Packet
import com.itantra.app.transport.protocol.PacketConstants
import com.itantra.app.transport.protocol.PacketHeader
import kotlin.math.max

data class FrameGroup(
    val frameGroupId: Int,
    val dataFrames: MutableMap<Int, Packet> = mutableMapOf(), // seq -> Packet
    var fecFrame: Packet? = null,
    val expectedCount: Int // Expected number of DATA frames in this group (1, 2, or 3)
) {
    val isComplete: Boolean get() = dataFrames.size == expectedCount
}

object FecEngine {

    const val GROUP_SIZE = 3

    fun calculateGroupId(seq: Int): Int = seq / GROUP_SIZE

    /**
     * Computes an XOR parity frame for up to 3 DATA packets in a frame group.
     * The parity packet has TYPE = 1 (FEC) and its SEQ is set to frameGroupId.
     */
    fun createParityFrame(
        messageId: Long,
        frameGroupId: Int,
        totalDataFrames: Int,
        flags: Byte,
        dataPackets: List<Packet>
    ): Packet {
        require(dataPackets.isNotEmpty()) { "Cannot create parity from empty packet list" }
        require(dataPackets.size <= GROUP_SIZE) { "Group size cannot exceed $GROUP_SIZE" }

        var maxLen = 0
        for (p in dataPackets) {
            maxLen = max(maxLen, p.payload.size)
        }

        val parityPayload = ByteArray(maxLen)
        for (p in dataPackets) {
            for (i in p.payload.indices) {
                parityPayload[i] = (parityPayload[i].toInt() xor p.payload[i].toInt()).toByte()
            }
        }

        val header = PacketHeader(
            version = PacketConstants.CURRENT_VERSION,
            type = PacketConstants.TYPE_FEC,
            flags = flags,
            messageId = messageId,
            seq = frameGroupId, // Explicit non-colliding group identifier
            total = totalDataFrames,
            payloadLen = parityPayload.size
        )

        return Packet(header, parityPayload, 0L)
    }

    /**
     * Attempts to reconstruct one missing DATA frame in a group using the remaining DATA frames and the FEC parity frame.
     * Returns the reconstructed Packet, or null if reconstruction is not possible (e.g., >1 frame missing or no FEC).
     */
    fun reconstructMissingFrame(
        frameGroup: FrameGroup,
        messageId: Long,
        totalDataFrames: Int,
        flags: Byte,
        expectedSeqs: List<Int>
    ): Packet? {
        val fec = frameGroup.fecFrame ?: return null
        val missingSeqs = expectedSeqs.filter { !frameGroup.dataFrames.containsKey(it) }

        if (missingSeqs.size != 1) {
            // Can only reconstruct exactly one missing DATA frame
            return null
        }

        val missingSeq = missingSeqs.first()
        val parityBytes = fec.payload.copyOf()

        // XOR with all available DATA frames in this group
        for ((_, dataPacket) in frameGroup.dataFrames) {
            for (i in dataPacket.payload.indices) {
                parityBytes[i] = (parityBytes[i].toInt() xor dataPacket.payload[i].toInt()).toByte()
            }
        }

        val header = PacketHeader(
            version = PacketConstants.CURRENT_VERSION,
            type = PacketConstants.TYPE_DATA,
            flags = flags,
            messageId = messageId,
            seq = missingSeq,
            total = totalDataFrames,
            payloadLen = parityBytes.size
        )

        return Packet(header, parityBytes, 0L)
    }
}
