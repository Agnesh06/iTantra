package com.itantra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.transport.throttle.TokenBucketLimiter
import com.itantra.app.transport.wifi.WiFiDirectUdpTransport
import com.itantra.app.ui.theme.AlertRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tokenBucketLimiter: TokenBucketLimiter,
    transport: WiFiDirectUdpTransport,
    connectionState: String,
    onShowToast: (String) -> Unit,
    onClearAudioCache: () -> Unit,
    onResetAppData: () -> Unit,
    onBack: () -> Unit
) {
    var bitrateKbps by remember { mutableStateOf((tokenBucketLimiter.rateBps / 1000).toFloat()) }
    var vadSilenceGapMs by remember { mutableStateOf(700f) }
    var peerIpInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Tuners", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Wi-Fi Direct Peer Connection Card (Demo Helper)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wi-Fi Direct Peer Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Enter peer's Wi-Fi Direct IP address to establish UDP connection",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = peerIpInput,
                            onValueChange = { peerIpInput = it },
                            label = { Text("Peer IP Address (e.g. 192.168.49.2)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status: $connectionState",
                                fontWeight = FontWeight.SemiBold,
                                color = if (connectionState == "CONNECTED") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = {
                                    if (peerIpInput.isBlank()) {
                                        onShowToast("Please enter peer IP address")
                                        return@Button
                                    }
                                    try {
                                        transport.connectPeer(peerIpInput.trim())
                                        onShowToast("Connected to peer at $peerIpInput")
                                    } catch (e: Exception) {
                                        onShowToast("Connection failed: ${e.message}")
                                    }
                                }
                            ) {
                                Text("Connect Peer")
                            }
                        }
                    }
                }
            }

            // Software Bitrate Throttling
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Demo Software Rate Limiter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Simulates low-bitrate radio links via Token Bucket (1..10 kbps)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bitrate: ${bitrateKbps.toInt()} kbps (${(bitrateKbps * 1000).toInt()} bps)",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = bitrateKbps,
                            onValueChange = {
                                bitrateKbps = it
                                tokenBucketLimiter.rateBps = (it * 1000).toInt()
                            },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                    }
                }
            }

            // VAD Silence Threshold
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("VAD Silence Segmentation Gap", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Duration of silence before segment is finalized (600..800 ms)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Silence Gap: ${vadSilenceGapMs.toInt()} ms",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = vadSilenceGapMs,
                            onValueChange = { vadSilenceGapMs = it },
                            valueRange = 600f..800f,
                            steps = 19
                        )
                    }
                }
            }

            // Fixed Invariant Readouts
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Protocol Invariants", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TelemetryRow("Confidence Threshold", "0.75 (Fixed)")
                        TelemetryRow("Max Utterance Cutoff", "15 seconds")
                        TelemetryRow("Incomplete Message Timeout", "15 seconds")
                        TelemetryRow("FEC Group Scheme", "3 DATA + 1 XOR Parity")
                        TelemetryRow("Selective Retry Limit", "3 Retries")
                    }
                }
            }

            // Data clearing actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onClearAudioCache,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Message Audio Cache")
                    }
                    Button(
                        onClick = onResetAppData,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App Data")
                    }
                }
            }
        }
    }
}
