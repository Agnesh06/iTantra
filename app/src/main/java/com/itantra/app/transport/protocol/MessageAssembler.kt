package com.itantra.app.transport.protocol

import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import com.itantra.app.transport.fec.FecEngine
import com.itantra.app.transport.fec.FrameGroup
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

data class AssembledMessage(
    val messageId: Long,
    val flags: Byte,
    val payloadBytes: ByteArray
) {
    val isAlert: Boolean get() = (flags.toInt() and PacketConstants.FLAG_ALERT.toInt()) != 0
    val hasConfidence: Boolean get() = (flags.toInt() and PacketConstants.FLAG_CONFIDENCE_PRESENT.toInt()) != 0
    val isTranslationUsed: Boolean get() = (flags.toInt() and PacketConstants.FLAG_TRANSLATION_USED.toInt()) != 0
}

/**
 * Section 18.2.1 & 21.4.1: Message Assembler
 * Manages frame reordering, XOR FEC reconstruction, duplicate rejection,
 * and 15-second assembly inactivity timeout -> INCOMPLETE_TIMEOUT.
 */
class MessageAssembler(
    private val scope: CoroutineScope,
    private val onSendAck: suspend (messageId: Long, ackPayload: AckPayload) -> Unit,
    private val onMessageComplete: (AssembledMessage) -> Unit,
    private val onIncompleteTimeout: (messageId: Long) -> Unit
) {
    companion object {
        const val INACTIVITY_TIMEOUT_MS = 15_000L
    }

    private class MessageBuffer(
        val messageId: Long,
        val totalDataFrames: Int,
        val flags: Byte,
        val scope: CoroutineScope,
        val onTimeout: () -> Unit
    ) {
        val frameGroups = ConcurrentHashMap<Int, FrameGroup>()
        val acknowledgedGroups = ConcurrentSkipListSet<Int>()
        var timeoutJob: Job? = null

        fun resetInactivityTimer() {
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(INACTIVITY_TIMEOUT_MS)
                onTimeout()
            }
        }
    }

    // Key: messageId
    private val activeBuffers = ConcurrentHashMap<Long, MessageBuffer>()

    // Track received packets to ignore duplicates: "${messageId}-${type}-${seq}"
    private val seenPackets = ConcurrentHashMap.newKeySet<String>()

    fun handleIncomingPacket(packet: Packet) {
        val key = "${packet.header.messageId}-${packet.header.type}-${packet.header.seq}"
        if (!seenPackets.add(key)) {
            AppLogger.i("Assembler", "Duplicate packet ignored: $key", category = LogCategory.NETWORK)
            return
        }

        val messageId = packet.header.messageId
        val buffer = activeBuffers.computeIfAbsent(messageId) {
            MessageBuffer(
                messageId = messageId,
                totalDataFrames = packet.header.total,
                flags = packet.header.flags,
                scope = scope,
                onTimeout = {
                    handleBufferTimeout(messageId)
                }
            )
        }

        buffer.resetInactivityTimer()

        val frameGroupId = if (packet.header.type == PacketConstants.TYPE_FEC) {
            packet.header.seq // Parity frame SEQ is frameGroupId
        } else {
            FecEngine.calculateGroupId(packet.header.seq)
        }

        val group = buffer.frameGroups.computeIfAbsent(frameGroupId) {
            val startSeq = frameGroupId * FecEngine.GROUP_SIZE
            val endSeq = minOf(startSeq + FecEngine.GROUP_SIZE - 1, buffer.totalDataFrames - 1)
            val expectedInGroup = (endSeq - startSeq + 1).coerceAtLeast(1)
            FrameGroup(
                frameGroupId = frameGroupId,
                expectedCount = expectedInGroup
            )
        }

        if (packet.header.type == PacketConstants.TYPE_FEC) {
            group.fecFrame = packet
        } else if (packet.header.type == PacketConstants.TYPE_DATA) {
            group.dataFrames[packet.header.seq] = packet
        }

        checkAndProcessGroup(buffer, group)
    }

    private fun checkAndProcessGroup(buffer: MessageBuffer, group: FrameGroup) {
        val startSeq = group.frameGroupId * FecEngine.GROUP_SIZE
        val endSeq = minOf(startSeq + FecEngine.GROUP_SIZE - 1, buffer.totalDataFrames - 1)
        val expectedSeqs = (startSeq..endSeq).toList()

        var ackStatus: Byte? = null

        if (group.isComplete) {
            ackStatus = PacketConstants.ACK_STATUS_COMPLETE
        } else {
            // Attempt FEC reconstruction if 1 frame is missing
            val reconstructed = FecEngine.reconstructMissingFrame(
                frameGroup = group,
                messageId = buffer.messageId,
                totalDataFrames = buffer.totalDataFrames,
                flags = buffer.flags,
                expectedSeqs = expectedSeqs
            )
            if (reconstructed != null) {
                group.dataFrames[reconstructed.header.seq] = reconstructed
                AppLogger.i(
                    "Assembler",
                    "Recovered missing frame ${reconstructed.header.seq} via XOR FEC",
                    category = LogCategory.NETWORK
                )
                ackStatus = PacketConstants.ACK_STATUS_FEC_RECONSTRUCTED
            }
        }

        if (ackStatus != null && buffer.acknowledgedGroups.add(group.frameGroupId)) {
            // Send exactly one ACK for this completed frame group
            val ackPayload = AckPayload(
                frameGroupId = group.frameGroupId,
                status = ackStatus,
                seqStart = startSeq,
                seqEnd = endSeq
            )

            scope.launch {
                onSendAck(buffer.messageId, ackPayload)
            }

            // Check if all groups for the entire message are now complete
            checkWholeMessageCompletion(buffer)
        }
    }

    private fun checkWholeMessageCompletion(buffer: MessageBuffer) {
        val totalExpectedFrames = buffer.totalDataFrames
        val allReceivedDataFrames = mutableMapOf<Int, Packet>()
        for (g in buffer.frameGroups.values) {
            allReceivedDataFrames.putAll(g.dataFrames)
        }

        if (allReceivedDataFrames.size >= totalExpectedFrames) {
            // All DATA frames 0..totalDataFrames-1 are present!
            buffer.timeoutJob?.cancel()
            activeBuffers.remove(buffer.messageId)

            val outStream = ByteArrayOutputStream()
            for (seq in 0 until totalExpectedFrames) {
                val frame = allReceivedDataFrames[seq]
                if (frame != null) {
                    outStream.write(frame.payload)
                }
            }

            val assembled = AssembledMessage(
                messageId = buffer.messageId,
                flags = buffer.flags,
                payloadBytes = outStream.toByteArray()
            )
            AppLogger.i(
                "Assembler",
                "Successfully reassembled message ${buffer.messageId} (${assembled.payloadBytes.size} bytes)",
                category = LogCategory.NETWORK
            )
            onMessageComplete(assembled)
        }
    }

    private fun handleBufferTimeout(messageId: Long) {
        val buffer = activeBuffers.remove(messageId) ?: return
        buffer.timeoutJob?.cancel()
        AppLogger.w(
            "Assembler",
            "Message $messageId reached 15s inactivity -> INCOMPLETE_TIMEOUT",
            category = LogCategory.NETWORK
        )
        onIncompleteTimeout(messageId)
    }

    fun clear() {
        activeBuffers.values.forEach { it.timeoutJob?.cancel() }
        activeBuffers.clear()
        seenPackets.clear()
    }
}
