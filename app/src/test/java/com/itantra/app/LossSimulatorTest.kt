package com.itantra.app

import com.itantra.app.transport.diagnostics.LossSimulator
import com.itantra.app.transport.protocol.PacketConstants
import org.junit.Assert.*
import org.junit.Test

class LossSimulatorTest {

    @Test
    fun testHelloAndAckPacketsAreNeverDropped() {
        val sim = LossSimulator()
        sim.setSimulationEnabled(true)
        sim.setLossPercent(30) // Maximum allowable loss

        // Test 1000 times: HELLO and ACK packets must NEVER be dropped
        for (i in 0 until 1000) {
            val dropHello = sim.shouldDrop(PacketConstants.TYPE_HELLO)
            val dropAck = sim.shouldDrop(PacketConstants.TYPE_ACK)
            assertFalse("HELLO packet must never be dropped", dropHello)
            assertFalse("ACK packet must never be dropped", dropAck)
        }
        assertEquals(0, sim.simulatedDrops.get())
    }

    @Test
    fun testLossPercentBounds() {
        val sim = LossSimulator()
        sim.setLossPercent(50) // Above 30
        assertEquals(30, sim.getLossPercent())

        sim.setLossPercent(-10) // Below 0
        assertEquals(0, sim.getLossPercent())
    }

    @Test
    fun testDataPacketLossInjection() {
        val sim = LossSimulator()
        sim.setSimulationEnabled(true)
        sim.setLossPercent(30)

        var drops = 0
        val trials = 1000
        for (i in 0 until trials) {
            if (sim.shouldDrop(PacketConstants.TYPE_DATA)) {
                drops++
            }
        }

        assertEquals(drops, sim.simulatedDrops.get())
        // For 30% loss across 1000 trials, drops should fall statistically in [200, 400]
        assertTrue("Expected drops around 300, got $drops", drops in 200..400)
    }
}
