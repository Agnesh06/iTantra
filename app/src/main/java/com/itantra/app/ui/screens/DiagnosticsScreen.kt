package com.itantra.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import com.itantra.app.transport.diagnostics.LossSimulator
import com.itantra.app.transport.protocol.AckRetryManager
import com.itantra.app.transport.throttle.TokenBucketLimiter
import com.itantra.app.ui.theme.AlertRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    lossSimulator: LossSimulator,
    ackRetryManager: AckRetryManager,
    tokenBucketLimiter: TokenBucketLimiter,
    connectionState: String,
    onRunComponentTest: (String) -> Unit,
    onBack: () -> Unit
) {
    var simulateLoss by remember { mutableStateOf(lossSimulator.isSimulationEnabled()) }
    var lossPercent by remember { mutableStateOf(lossSimulator.getLossPercent().toFloat()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Loss Simulator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loss Simulator Section (Section 24.1)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "UDP Packet Loss Simulator",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Drops DATA/FEC datagrams before send(); HELLO/ACK never dropped",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = simulateLoss,
                                onCheckedChange = {
                                    simulateLoss = it
                                    lossSimulator.setSimulationEnabled(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Loss Rate: ${lossPercent.toInt()}%",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = lossPercent,
                            onValueChange = {
                                lossPercent = it
                                lossSimulator.setLossPercent(it.toInt())
                            },
                            valueRange = 0f..30f,
                            steps = 29,
                            enabled = simulateLoss
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBadge("Simulated Drops", "${lossSimulator.simulatedDrops.get()}")
                            MetricBadge("Pending ACK Groups", "${ackRetryManager.getPendingGroupCount()}")
                            MetricBadge("Retransmissions", "${ackRetryManager.totalRetransmissions.get()}")
                            MetricBadge("Timeouts", "${ackRetryManager.incompleteTimeouts.get()}")
                        }
                    }
                }
            }

            // Transport Telemetry
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Transport & Protocol Telemetry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TelemetryRow("Connection State", connectionState)
                        TelemetryRow("Configured Bitrate", "${tokenBucketLimiter.rateBps} bps")
                        TelemetryRow("Measured UDP RTT", "${ackRetryManager.getMeasuredRtt()} ms")
                        TelemetryRow("Total Transmitted Bytes", "${tokenBucketLimiter.totalTransmittedBytes.get()} B")
                        TelemetryRow("Total Payload Bytes", "${tokenBucketLimiter.totalPayloadBytes.get()} B")
                    }
                }
            }

            // Device & Hardware Telemetry
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Device & Hardware Specs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TelemetryRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                        TelemetryRow("Android API", "${Build.VERSION.SDK_INT}")
                        val runtime = Runtime.getRuntime()
                        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
                        TelemetryRow("App Heap RAM", "$usedMemMb MB / $maxMemMb MB")
                        TelemetryRow("Offline Mode", "Strict Offline (No Cloud)")
                    }
                }
            }

            // Interactive Component Tests
            item {
                Text("Verification & Component Tests", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onRunComponentTest("VAD") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test VAD", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onRunComponentTest("ASR") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test ASR", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onRunComponentTest("Translation") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test IndicTrans2", fontSize = 11.sp)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onRunComponentTest("TTS") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test TTS", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onRunComponentTest("Playback") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test Playback", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onRunComponentTest("FullLoop") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Full Loop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
