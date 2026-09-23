package com.itantra.app

import com.itantra.app.transport.protocol.AckRetryManager
import com.itantra.app.transport.protocol.Packet
import com.itantra.app.transport.protocol.PacketConstants
import com.itantra.app.transport.protocol.PacketHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AckRetryManagerTest {

    @Test
    fun testInitialAckTimeoutCalculationWithFallbackRtt() {
        val testScope = TestScope()
        val manager = AckRetryManager(
            scope = testScope,
            onRetransmitGroup = {},
            onGroupFailed = { _, _ -> }
        )

        // With fallback RTT = 250ms, rate = 2000 bps (250 bytes/sec)
        // Group size = 100 bytes -> serialization time = (100 * 8 * 1000) / 2000 = 400 ms
        // expectedGroupRTT = 250 + 400 = 650 ms
        // ackTimeout = max(500, 2 * 650) = 1300 ms
        val timeout = manager.calculateAckTimeout(groupByteSize = 100, rateBps = 2000)
        assertEquals(1300L, timeout)

        // When group size is very small (10 bytes -> serialization = 40 ms)
        // expectedGroupRTT = 250 + 40 = 290 ms
        // 2 * 290 = 580 ms >= 500 ms
        val timeoutSmall = manager.calculateAckTimeout(groupByteSize = 10, rateBps = 2000)
        assertEquals(580L, timeoutSmall)

        // When expectedGroupRTT <= 250 ms, floor at 500 ms
        val timeoutZeroRate = manager.calculateAckTimeout(groupByteSize = 0, rateBps = 0)
        assertEquals(500L, timeoutZeroRate)
    }

    @Test
    fun testRttEmaUpdate() {
        val testScope = TestScope()
        val manager = AckRetryManager(
            scope = testScope,
            onRetransmitGroup = {},
            onGroupFailed = { _, _ -> }
        )

        assertEquals(0L, manager.getMeasuredRtt())
        manager.recordRttSample(100L)
        assertEquals(100L, manager.getMeasuredRtt())

        // EMA alpha = 0.125: (1 - 0.125)*100 + 0.125*200 = 87.5 + 25 = 112
        manager.recordRttSample(200L)
        assertEquals(112L, manager.getMeasuredRtt())
    }

    @Test
    fun testSelectiveRetryAndRetriesExhaustion() = runTest {
        var retransmittedGroup = -1
        var failedMessageId = -1L

        val manager = AckRetryManager(
            scope = this,
            onRetransmitGroup = { group ->
                retransmittedGroup = group.frameGroupId
            },
            onGroupFailed = { msgId, _ ->
                failedMessageId = msgId
            }
        )

        val frame = Packet(
            PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 101L, seq = 0, total = 1, payloadLen = 10),
            ByteArray(10),
            0L
        )

        // Register group sent
        manager.registerGroupSent(messageId = 101L, frameGroupId = 0, frames = listOf(frame), rateBps = 2000)
        assertEquals(1, manager.getPendingGroupCount())

        // Advance time to trigger retry 1
        advanceTimeBy(800)
        assertEquals(0, retransmittedGroup)
        assertEquals(1, manager.totalRetransmissions.get())

        // Advance time to trigger retry 2
        advanceTimeBy(800)
        assertEquals(2, manager.totalRetransmissions.get())

        // Advance time to trigger retry 3
        advanceTimeBy(800)
        assertEquals(3, manager.totalRetransmissions.get())

        // Advance time past 3rd retry -> Retries exhausted!
        advanceTimeBy(800)
        assertEquals(101L, failedMessageId)
        assertEquals(1, manager.incompleteTimeouts.get())
        assertEquals(0, manager.getPendingGroupCount())
    }

    @Test
    fun testAckReceiptCancelsTimerAndIgnoresDuplicate() = runTest {
        var retransmissions = 0
        val manager = AckRetryManager(
            scope = this,
            onRetransmitGroup = { retransmissions++ },
            onGroupFailed = { _, _ -> }
        )

        val frame = Packet(
            PacketHeader(type = PacketConstants.TYPE_DATA, flags = 0, messageId = 202L, seq = 0, total = 1, payloadLen = 10),
            ByteArray(10),
            0L
        )

        manager.registerGroupSent(messageId = 202L, frameGroupId = 1, frames = listOf(frame), rateBps = 2000)
        assertEquals(1, manager.getPendingGroupCount())

        // Incoming ACK arrives before timeout
        advanceTimeBy(100)
        manager.onAckReceived(messageId = 202L, frameGroupId = 1)
        assertEquals(0, manager.getPendingGroupCount())

        // Advance time past original timeout
        advanceTimeBy(1000)
        // No retransmission must occur!
        assertEquals(0, retransmissions)

        // Duplicate ACK arrives -> safely ignored
        manager.onAckReceived(messageId = 202L, frameGroupId = 1)
        assertEquals(0, manager.getPendingGroupCount())
    }
}
