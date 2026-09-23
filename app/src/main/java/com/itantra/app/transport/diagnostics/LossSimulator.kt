package com.itantra.app.transport.diagnostics

import com.itantra.app.transport.protocol.PacketConstants
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Section 15.4.1 & 24.1: Loss Simulator
 * Injects 0-30% packet loss on outgoing DATA and FEC frames.
 * HELLO and ACK control packets are never dropped.
 */
class LossSimulator {

    private val isEnabled = AtomicBoolean(false)
    private val lossRatePercent = AtomicInteger(0)
    val simulatedDrops = AtomicInteger(0)
    private val random = Random()

    fun setSimulationEnabled(enabled: Boolean) {
        isEnabled.set(enabled)
    }

    fun isSimulationEnabled(): Boolean = isEnabled.get()

    fun setLossPercent(percent: Int) {
        lossRatePercent.set(percent.coerceIn(0, 30))
    }

    fun getLossPercent(): Int = lossRatePercent.get()

    /**
     * Determines whether the given outgoing packet should be dropped.
     * @return true if the packet must be dropped (simulated drop), false to transmit normally.
     */
    fun shouldDrop(packetType: Byte): Boolean {
        // Invariant: Control packets (HELLO and ACK) are NEVER dropped
        if (packetType == PacketConstants.TYPE_HELLO || packetType == PacketConstants.TYPE_ACK) {
            return false
        }

        if (!isEnabled.get()) {
            return false
        }

        val rate = lossRatePercent.get()
        if (rate <= 0) {
            return false
        }

        val roll = random.nextInt(100) // 0..99
        val drop = roll < rate
        if (drop) {
            simulatedDrops.incrementAndGet()
        }
        return drop
    }

    fun resetStats() {
        simulatedDrops.set(0)
    }
}
