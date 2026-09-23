package com.itantra.app.transport.protocol

import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

data class PendingGroup(
    val messageId: Long,
    val frameGroupId: Int,
    val frames: List<Packet>,
    val byteSize: Int,
    var retryCount: Int = 0,
    val sentTimeNanos: Long = System.nanoTime(),
    var timerJob: Job? = null
)

/**
 * Section 15.5: ACK Timeout and Selective Retry Manager
 */
class AckRetryManager(
    private val scope: CoroutineScope,
    private val onRetransmitGroup: suspend (PendingGroup) -> Unit,
    private val onGroupFailed: (messageId: Long, frameGroupId: Int) -> Unit
) {
    companion object {
        const val MAX_RETRIES = 3
        const val FALLBACK_RTT_MS = 250L
        const val MIN_ACK_TIMEOUT_MS = 500L
        private const val EMA_ALPHA = 0.125
    }

    // Key: "$messageId-$frameGroupId"
    private val pendingGroups = ConcurrentHashMap<String, PendingGroup>()

    // Track acknowledged groups to safely ignore duplicate ACKs
    private val acknowledgedGroups = ConcurrentHashMap.newKeySet<String>()

    // RTT tracking
    private val measuredRttMs = AtomicLong(0)

    // Telemetry counters
    val totalRetransmissions = AtomicInteger(0)
    val incompleteTimeouts = AtomicInteger(0)

    fun getPendingGroupCount(): Int = pendingGroups.size

    fun getMeasuredRtt(): Long = measuredRttMs.get()

    /**
     * Updates rolling average RTT using EMA (alpha = 0.125).
     */
    fun recordRttSample(sampleMs: Long) {
        val current = measuredRttMs.get()
        if (current == 0L) {
            measuredRttMs.set(sampleMs)
        } else {
            val updated = ((1.0 - EMA_ALPHA) * current + EMA_ALPHA * sampleMs).toLong()
            measuredRttMs.set(updated)
        }
    }

    /**
     * Calculates the exact ACK timeout for a frame group.
     */
    fun calculateAckTimeout(groupByteSize: Int, rateBps: Int): Long {
        val currentRtt = if (measuredRttMs.get() > 0) measuredRttMs.get() else FALLBACK_RTT_MS
        val serializationTimeMs = if (rateBps > 0) {
            (groupByteSize * 8L * 1000L) / rateBps
        } else {
            0L
        }
        val expectedGroupRtt = currentRtt + serializationTimeMs
        return max(MIN_ACK_TIMEOUT_MS, 2 * expectedGroupRtt)
    }

    /**
     * Registers a sent frame-group and starts its selective ACK timer.
     */
    fun registerGroupSent(
        messageId: Long,
        frameGroupId: Int,
        frames: List<Packet>,
        rateBps: Int
    ) {
        val key = "$messageId-$frameGroupId"
        val totalBytes = frames.sumOf { it.payload.size + PacketConstants.HEADER_TOTAL_SIZE }
        val timeoutMs = calculateAckTimeout(totalBytes, rateBps)

        val pending = PendingGroup(
            messageId = messageId,
            frameGroupId = frameGroupId,
            frames = frames,
            byteSize = totalBytes
        )

        pending.timerJob = scope.launch {
            delay(timeoutMs)
            handleTimeout(key, rateBps)
        }

        pendingGroups[key] = pending
    }

    private suspend fun handleTimeout(key: String, rateBps: Int) {
        val pending = pendingGroups[key] ?: return
        if (pending.retryCount < MAX_RETRIES) {
            pending.retryCount++
            totalRetransmissions.incrementAndGet()
            AppLogger.w(
                "AckRetry",
                "Selective retry ${pending.retryCount}/$MAX_RETRIES for msg=${pending.messageId}, group=${pending.frameGroupId}",
                category = LogCategory.NETWORK
            )

            // Reschedule timer
            val timeoutMs = calculateAckTimeout(pending.byteSize, rateBps)
            pending.timerJob = scope.launch {
                delay(timeoutMs)
                handleTimeout(key, rateBps)
            }

            // Resend ONLY this unacknowledged frame-group
            onRetransmitGroup(pending)
        } else {
            // Retries exhausted
            incompleteTimeouts.incrementAndGet()
            AppLogger.e(
                "AckRetry",
                "Retries exhausted for msg=${pending.messageId}, group=${pending.frameGroupId}",
                category = LogCategory.NETWORK
            )
            pendingGroups.remove(key)
            onGroupFailed(pending.messageId, pending.frameGroupId)
        }
    }

    /**
     * Handles an incoming ACK for a completed frame-group.
     */
    fun onAckReceived(messageId: Long, frameGroupId: Int) {
        val key = "$messageId-$frameGroupId"
        if (acknowledgedGroups.contains(key)) {
            AppLogger.i("AckRetry", "Duplicate ACK ignored for $key", category = LogCategory.NETWORK)
            return
        }

        val pending = pendingGroups.remove(key)
        if (pending != null) {
            pending.timerJob?.cancel()
            acknowledgedGroups.add(key)

            // Compute and record RTT sample
            val elapsedMs = (System.nanoTime() - pending.sentTimeNanos) / 1_000_000L
            recordRttSample(elapsedMs)

            AppLogger.i(
                "AckRetry",
                "ACK received for msg=$messageId, group=$frameGroupId in ${elapsedMs}ms",
                category = LogCategory.NETWORK
            )
        }
    }

    fun clear() {
        pendingGroups.values.forEach { it.timerJob?.cancel() }
        pendingGroups.clear()
        acknowledgedGroups.clear()
    }
}
