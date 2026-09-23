package com.itantra.app.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.domain.model.ModelManager
import com.itantra.app.ui.theme.AlertRed

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedSourceLang: String,
    selectedTargetLang: String,
    onSourceLangChange: (String) -> Unit,
    onTargetLangChange: (String) -> Unit,
    isPttMode: Boolean,
    onPttModeToggle: (Boolean) -> Unit,
    isAlertMode: Boolean,
    onAlertModeToggle: (Boolean) -> Unit,
    connectionState: String,
    onPttPressStart: () -> Unit,
    onPttPressRelease: () -> Unit,
    onHandsFreeToggle: () -> Unit,
    isHandsFreeActive: Boolean,
    onSendCustomText: (String) -> Unit,
    onNavigateToConversation: () -> Unit,
    onNavigateToLanguagePacks: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var targetDropdownExpanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("iTantra", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (connectionState == "CONNECTED") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = connectionState,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (connectionState == "CONNECTED") Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToConversation) {
                        Icon(Icons.Default.Chat, contentDescription = "Messages")
                    }
                    IconButton(onClick = onNavigateToLanguagePacks) {
                        Icon(Icons.Default.Translate, contentDescription = "Languages")
                    }
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.Build, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source Language Box
                    Box {
                        Column {
                            Text("Source Language", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = ModelManager.SUPPORTED_LANGUAGES.find { it.code == selectedSourceLang }?.displayName ?: selectedSourceLang,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { sourceDropdownExpanded = true }
                            )
                        }
                        DropdownMenu(
                            expanded = sourceDropdownExpanded,
                            onDismissRequest = { sourceDropdownExpanded = false }
                        ) {
                            ModelManager.SUPPORTED_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.displayName) },
                                    onClick = {
                                        onSourceLangChange(lang.code)
                                        sourceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Target Language Box
                    Box {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target Language", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = ModelManager.SUPPORTED_LANGUAGES.find { it.code == selectedTargetLang }?.displayName ?: selectedTargetLang,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable { targetDropdownExpanded = true }
                            )
                        }
                        DropdownMenu(
                            expanded = targetDropdownExpanded,
                            onDismissRequest = { targetDropdownExpanded = false }
                        ) {
                            ModelManager.SUPPORTED_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.displayName) },
                                    onClick = {
                                        onTargetLangChange(lang.code)
                                        targetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Demo Text Input (Bypasses offline ASR error 13 so you can send any message instantly)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Live Demo Quick Message (Bypass ASR)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            placeholder = { Text("Type custom message...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onSendCustomText(customText.trim())
                                    customText = ""
                                }
                            }
                        ) {
                            Text("Send")
                        }
                    }
                }
            }

            // Mode Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // PTT Toggle (ON = Hold to speak, OFF = Hands-free VAD)
                FilterChip(
                    selected = isPttMode,
                    onClick = { onPttModeToggle(!isPttMode) },
                    label = { Text(if (isPttMode) "PTT: ON" else "HANDS-FREE") },
                    leadingIcon = {
                        Icon(
                            if (isPttMode) Icons.Default.TouchApp else Icons.Default.Hearing,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Alert Mode Toggle (NORMAL vs ALERT)
                FilterChip(
                    selected = isAlertMode,
                    onClick = { onAlertModeToggle(!isAlertMode) },
                    label = { Text(if (isAlertMode) "ALERT MODE" else "NORMAL") },
                    leadingIcon = {
                        Icon(
                            if (isAlertMode) Icons.Default.Warning else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isAlertMode) AlertRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isAlertMode) AlertRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }

            // Big Action Button / PTT Microphone
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val micColor = if (isAlertMode) AlertRed else MaterialTheme.colorScheme.primary

                Surface(
                    shape = CircleShape,
                    color = micColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(120.dp)
                        .then(
                            if (isPttMode) {
                                Modifier.pointerInteropFilter { motionEvent ->
                                    when (motionEvent.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            onPttPressStart()
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            onPttPressRelease()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            } else {
                                Modifier.clickable { onHandsFreeToggle() }
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPttMode) Icons.Default.Mic else (if (isHandsFreeActive) Icons.Default.Stop else Icons.Default.Mic),
                            contentDescription = "Microphone",
                            tint = Color.Black,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isPttMode) "HOLD TO SPEAK • RELEASE TO SEND" else (if (isHandsFreeActive) "LISTENING (VAD ACTIVE)" else "TAP TO START HANDS-FREE"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isAlertMode) AlertRed else MaterialTheme.colorScheme.onBackground
                )
            }

            // Quick bottom metrics preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Bandwidth: ~2 kbps • XOR Parity FEC Active • Zero Cloud",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
