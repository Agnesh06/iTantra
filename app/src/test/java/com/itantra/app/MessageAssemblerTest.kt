package com.itantra.app

import com.itantra.app.transport.protocol.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageAssemblerTest {

    @Test
    fun testDuplicatePacketIgnored() = runTest {
        var acksSent = 0
        val assembler = MessageAssembler(
            scope = this,
            onSendAck = { _, _ -> acksSent++ },
            onMessageComplete = {},
            onIncompleteTimeout = {}
        )

        val frame = Packet(
            PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 55L, seq = 0, total = 1, payloadLen = 4),
            "TEST".toByteArray(),
            0L
        )

        assembler.handleIncomingPacket(frame)
        advanceTimeBy(50)
        assertEquals(1, acksSent)

        // Duplicate incoming packet
        assembler.handleIncomingPacket(frame)
        advanceTimeBy(50)
        // ACK count must NOT increase; duplicate is silently rejected
        assertEquals(1, acksSent)
    }

    @Test
    fun testInactivityTimeoutFiresAt15Seconds() = runTest {
        var timedOutMessageId = -1L
        val assembler = MessageAssembler(
            scope = this,
            onSendAck = { _, _ -> },
            onMessageComplete = {},
            onIncompleteTimeout = { msgId -> timedOutMessageId = msgId }
        )

        // Incomplete message: frame 0 of 2 arrives
        val frame0 = Packet(
            PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 777L, seq = 0, total = 2, payloadLen = 4),
            "PART".toByteArray(),
            0L
        )
        assembler.handleIncomingPacket(frame0)

        // Advance 10 seconds -> still active
        advanceTimeBy(10_000)
        assertEquals(-1L, timedOutMessageId)

        // Advance past 15 seconds -> Inactivity timeout triggers!
        advanceTimeBy(6_000)
        assertEquals(777L, timedOutMessageId)
    }

    @Test
    fun testOutOfOrderReassemblyAndCompletion() = runTest {
        var completedMsg: AssembledMessage? = null
        val assembler = MessageAssembler(
            scope = this,
            onSendAck = { _, _ -> },
            onMessageComplete = { msg -> completedMsg = msg },
            onIncompleteTimeout = {}
        )

        val chunk0 = "Hello ".toByteArray()
        val chunk1 = "World!".toByteArray()

        val frame0 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 888L, seq = 0, total = 2, payloadLen = chunk0.size), chunk0, 0L)
        val frame1 = Packet(PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 888L, seq = 1, total = 2, payloadLen = chunk1.size), chunk1, 0L)

        // Arrive out of order: frame1 arrives before frame0
        assembler.handleIncomingPacket(frame1)
        assertNull(completedMsg)

        assembler.handleIncomingPacket(frame0)
        advanceTimeBy(50)

        assertNotNull(completedMsg)
        assertEquals("Hello World!", String(completedMsg!!.payloadBytes, Charsets.UTF_8))
    }
}
