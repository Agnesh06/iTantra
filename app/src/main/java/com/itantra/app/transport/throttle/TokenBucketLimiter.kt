package com.itantra.app.transport.throttle

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Section 15.4: Software Token Bucket Rate Limiter
 * Enforces 1..10000 bps transmission rate with 2-second bucket capacity.
 */
class TokenBucketLimiter(
    initialRateBps: Int = 2000
) {
    var rateBps: Int = initialRateBps
        set(value) {
            field = value.coerceIn(1, 10000)
            maxTokensBits = field * 2.0 // 2 seconds worth of tokens
        }

    private var maxTokensBits: Double = initialRateBps * 2.0
    private var availableTokensBits: Double = maxTokensBits
    private var lastRefillNanoTime: Long = System.nanoTime()

    val totalPayloadBytes = AtomicLong(0)
    val totalTransmittedBytes = AtomicLong(0)

    @Synchronized
    private fun refill() {
        val now = System.nanoTime()
        val elapsedSec = (now - lastRefillNanoTime) / 1_000_000_000.0
        lastRefillNanoTime = now
        val addedTokens = elapsedSec * rateBps
        availableTokensBits = min(maxTokensBits, availableTokensBits + addedTokens)
    }

    /**
     * Suspends until sufficient tokens are available for transmitting the given packet byte size.
     */
    suspend fun acquire(byteCount: Int, payloadBytes: Int = byteCount) {
        val requiredBits = byteCount * 8.0

        while (true) {
            val waitTimeMs: Long = synchronized(this) {
                refill()
                if (availableTokensBits >= requiredBits) {
                    availableTokensBits -= requiredBits
                    totalTransmittedBytes.addAndGet(byteCount.toLong())
                    totalPayloadBytes.addAndGet(payloadBytes.toLong())
                    return
                } else {
                    val missingBits = requiredBits - availableTokensBits
                    val waitSec = missingBits / rateBps
                    (waitSec * 1000).toLong().coerceAtLeast(10L)
                }
            }
            delay(waitTimeMs)
        }
    }

    fun resetStats() {
        totalPayloadBytes.set(0)
        totalTransmittedBytes.set(0)
    }
}
