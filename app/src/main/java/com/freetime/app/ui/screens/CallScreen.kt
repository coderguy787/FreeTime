package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.data.repository.CallRepository
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.CallHistoryItem
import android.media.AudioManager
import android.content.Context
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callRepository: CallRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val apiService = remember { FreeTimeApiService(context) }
    
    var callHistory by remember { mutableStateOf<List<CallHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRecipientId by remember { mutableStateOf("") }
    var selectedRecipientName by remember { mutableStateOf("") }
    var isCallActive by remember { mutableStateOf(false) }
    var currentCallId by remember { mutableStateOf("") }
    var currentCallType by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var callStartTime by remember { mutableStateOf(0L) }
    var callDuration by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isVideoEnabled by remember { mutableStateOf(false) }
    var missingCallId by remember { mutableStateOf<String?>(null) }
    var isSavingMetrics by remember { mutableStateOf(false) }
    
    // ✅ NEW: Mute/speaker control callbacks that wire to WebRTC
    val onMuteToggle: () -> Unit = {
        isMuted = !isMuted
        audioManager.isMicrophoneMute = isMuted
        android.util.Log.d("CALL_SCREEN", if (isMuted) "🔇 Muted" else "🎙️ Unmuted")
    }
    
    val onSpeakerToggle: () -> Unit = {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        android.util.Log.d("CALL_SCREEN", if (isSpeakerOn) "📢 Speaker ON" else "🔊 Speaker OFF")
    }

    // Load call history from backend
    LaunchedEffect(Unit) {
        com.freetime.app.notifications.InAppNotificationStore.removeByType("call")
        com.freetime.app.notifications.InAppNotificationStore.removeByType("missedCall")
        
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                val result = apiService.getCallHistory(limit = 50)
                result.fold(
                    onSuccess = { history ->
                        callHistory = history
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to load call history: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Failed to load call history: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ✅ Real-time call duration timer with proper coroutine management
    LaunchedEffect(isCallActive, callStartTime) {
        if (isCallActive && callStartTime > 0) {
            while (isCallActive) {
                try {
                    callDuration = ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    android.util.Log.e("CALL_SCREEN", "Timer error: ${e.message}")
                    break
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Calls", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                // Refresh button
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        val result = apiService.getCallHistory(limit = 50)
                        result.onSuccess { history ->
                            callHistory = history
                        }.onFailure { error ->
                            errorMessage = error.message
                        }
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Filled.Refresh, "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFFF00FF)
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (isCallActive) {
            // Active Call View with full controls
            CallActiveView(
                recipientName = selectedRecipientName,
                recipientId = selectedRecipientId,
                callType = currentCallType ?: "audio",
                callDuration = callDuration,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isVideoEnabled = isVideoEnabled,
                onMuteToggle = onMuteToggle,
                onSpeakerToggle = onSpeakerToggle,
                onVideoToggle = { isVideoEnabled = !isVideoEnabled },
                onEndCall = {
                    scope.launch {
                        // End call via API
                        apiService.endCall(currentCallId)
                        // Save call metrics before ending
                        if (callDuration > 0) {
                            apiService.saveCallMetrics(
                                callId = currentCallId,
                                packetLoss = 0.5f,  // Placeholder - would get from actual metrics
                                latency = 50,
                                jitter = 10
                            )
                        }
                        isCallActive = false
                        currentCallId = ""
                        currentCallType = null
                        callDuration = 0
                        callStartTime = 0
                        // Refresh history after call ends
                        val result = apiService.getCallHistory(limit = 50)
                        result.onSuccess { history ->
                            callHistory = history
                        }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Error message if any
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF662A2A)
                        )
                    ) {
                        Text(
                            errorMessage ?: "Unknown error",
                            color = Color(0xFFFF6B6B),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                // Recent Calls Section
                Text(
                    "Recent Calls (${callHistory.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (callHistory.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2A2A2A)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.PhoneDisabled,
                                contentDescription = "No calls",
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No recent calls",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(callHistory) { callRecord ->
                            CallHistoryItemCard(
                                item = callRecord,
                                onReportMissedClick = {
                                    scope.launch {
                                        missingCallId = callRecord.callId
                                        val result = apiService.reportMissedCall(callRecord.callId)
                                        result.onSuccess { message ->
                                            // Refresh after reporting
                                            val updatedResult = apiService.getCallHistory(limit = 50)
                                            updatedResult.onSuccess { history ->
                                                callHistory = history
                                            }
                                        }.onFailure { error ->
                                            errorMessage = "Failed to report missed call: ${error.message}"
                                        }
                                        missingCallId = null
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            callRepository.clearCallHistory()
                            callHistory = emptyList()
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF00FF)
                    )
                ) {
                    Text("Clear Call History", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Card component for displaying individual call history items with action buttons
 */
@Composable
fun CallHistoryItemCard(
    item: CallHistoryItem,
    onReportMissedClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val callTimeText = dateFormat.format(Date(item.startTime))
    val statusColor = when (item.status) {
        "completed" -> Color(0xFF4CAF50)
        "missed" -> Color(0xFFFF6B6B)
        "declined" -> Color(0xFFFFA500)
        else -> Color.Gray
    }
    val directionIcon = if (item.direction == "incoming") Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        directionIcon,
                        contentDescription = item.direction,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            item.recipientName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            callTimeText,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "${item.durationSeconds}s",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        item.status.uppercase(),
                        fontSize = 10.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Action buttons for missed calls
            if (item.status == "missed") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onReportMissedClick,
                        modifier = Modifier.size(height = 28.dp, width = 100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            "Mark Reviewed",
                            fontSize = 10.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallActiveView(
    recipientName: String,
    @Suppress("UNUSED_PARAMETER") recipientId: String,
    callType: String,
    callDuration: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoEnabled: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    // ✅ FIX: Proper null safety and safe defaults
    val safeRecipientName = recipientName.takeIf { it.isNotBlank() } ?: "Unknown"
    val safeCallType = callType.takeIf { it.isNotBlank() } ?: "audio"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Participant Avatar & Name
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFF9D4EDD), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                safeRecipientName.take(1).uppercase(),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recipient Name
        Text(
            safeRecipientName,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        // Call Type Badge
        Surface(
            modifier = Modifier
                .padding(top = 8.dp),
            color = Color(0xFF9D4EDD),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            Text(
                safeCallType.uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Call Duration Timer
        Text(
            formatDuration(callDuration),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        Spacer(modifier = Modifier.weight(1f))

        // Call Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ FIX: Mute Toggle with safer onClick handler
            FloatingActionButton(
                onClick = {
                    try {
                        onMuteToggle()
                    } catch (e: Exception) {
                        android.util.Log.e("CALL_SCREEN", "Mute toggle error: ${e.message}")
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = if (isMuted) Color(0xFFFF00FF) else Color(0xFF2A2A2A),
                contentColor = Color.White
            ) {
                Icon(
                    if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    "Mute",
                    tint = Color.White
                )
            }

            // End Call (Red)
            FloatingActionButton(
                onClick = {
                    try {
                        onEndCall()
                    } catch (e: Exception) {
                        android.util.Log.e("CALL_SCREEN", "End call error: ${e.message}")
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.PhoneDisabled, "End Call", tint = Color.White)
            }

            // ✅ FIX: Speaker Toggle with safer onClick handler
            FloatingActionButton(
                onClick = {
                    try {
                        onSpeakerToggle()
                    } catch (e: Exception) {
                        android.util.Log.e("CALL_SCREEN", "Speaker toggle error: ${e.message}")
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = if (isSpeakerOn) Color(0xFFFF00FF) else Color(0xFF2A2A2A),
                contentColor = Color.White
            ) {
                Icon(
                    if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    "Speaker",
                    tint = Color.White
                )
            }
        }

        // ✅ FIX: Video Control with proper error handling (if applicable)
        if (safeCallType == "video") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        try {
                            onVideoToggle()
                        } catch (e: Exception) {
                            android.util.Log.e("CALL_SCREEN", "Video toggle error: ${e.message}")
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = if (isVideoEnabled) Color(0xFFFF00FF) else Color(0xFF2A2A2A),
                    contentColor = Color.White
                ) {
                    Icon(
                        if (isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        "Video",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%02d:%02d", minutes, secs)
    }
}

@Composable
private fun CallHistoryItem(
    callRecord: String,
    onCallClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    callRecord.take(30),
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Recently", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = { onCallClick(callRecord) }) {
                Icon(Icons.Filled.Phone, "Call again", tint = Color(0xFFFF00FF))
            }
        }
    }
}


