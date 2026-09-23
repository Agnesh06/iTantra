package com.itantra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.data.local.entity.MessageDirection
import com.itantra.app.data.local.entity.MessageEntity
import com.itantra.app.data.local.entity.MessageStatus
import com.itantra.app.ui.theme.AlertAmber
import com.itantra.app.ui.theme.AlertRed
import com.itantra.app.ui.theme.ConfidenceHighlight
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    messages: List<MessageEntity>,
    onPlayAudio: (File, Boolean) -> Unit, // file, isAlert
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation History", fontWeight = FontWeight.Bold) },
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
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet.\nUse PTT or Hands-Free mode to transmit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageCard(message = message, onPlayAudio = onPlayAudio)
                }
            }
        }
    }
}

@Composable
fun MessageCard(
    message: MessageEntity,
    onPlayAudio: (File, Boolean) -> Unit
) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val isAlert = message.alertFlag
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Parse confidence flags
    val confidenceFlags = remember(message.confidenceFlagsJson) {
        try {
            val jsonArray = JSONArray(message.confidenceFlagsJson)
            List(jsonArray.length()) { jsonArray.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val words = remember(message.text) { message.text.split(" ") }

    val annotatedText = remember(message.text, confidenceFlags) {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val isLowConf = confidenceFlags.getOrNull(index) == 1
                if (isLowConf) {
                    withStyle(
                        style = SpanStyle(
                            background = ConfidenceHighlight,
                            color = AlertAmber,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (index < words.size - 1) append(" ")
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = if (isAlert) {
                    AlertRed.copy(alpha = 0.15f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAlert) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ALERT",
                                color = AlertRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "${message.sourceLanguage.uppercase()} -> ${message.targetLanguage.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = timeFormat.format(Date(message.createdAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status or Error
                if (message.status == MessageStatus.INCOMPLETE_TIMEOUT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Message not received (15s timeout)", color = AlertAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (message.status == MessageStatus.TranslationFailed) {
                    Text(text = annotatedText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[Translation Failed - Showing original text]",
                        color = AlertAmber,
                        fontSize = 11.sp
                    )
                } else {
                    Text(text = annotatedText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                // Voice Note Play button (Normal message = manual tap, Alert = auto-played)
                if (message.audioPath != null && message.status != MessageStatus.INCOMPLETE_TIMEOUT) {
                    val audioFile = File(message.audioPath)
                    if (audioFile.exists()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { onPlayAudio(audioFile, isAlert) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAlert) "Replay Alert Announcement" else "Play Voice Note", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
