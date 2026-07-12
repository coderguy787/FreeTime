package com.freetime.app.ui.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.viewmodel.CallViewModel

/**
 * Active Call UI - Shows during an ongoing call
 * Displays participant, call duration, connection status, and call controls
 */
@Composable
fun ActiveCallView(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    val remoteName by viewModel.remoteName.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()
    val callType by viewModel.callType.collectAsState()

    // Pulsing animation for connecting state
    val scale by animateFloatAsState(
        targetValue = if (connectionStatus == "connecting") 1.1f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Call info and status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Participant avatar with pulsing effect
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f))
                        .scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Participant",
                        modifier = Modifier.size(60.dp),
                        tint = CyberpunkTheme.PrimaryPurple
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Participant name
                Text(
                    text = remoteName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status indicator
                Row(
                    modifier = Modifier
                        .background(
                            color = viewModel.getStatusColor(connectionStatus)
                                .copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(viewModel.getStatusColor(connectionStatus))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connectionStatus.replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = viewModel.getStatusColor(connectionStatus),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Call duration
                Text(
                    text = viewModel.getFormattedDuration(callDuration),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.PrimaryPurple,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quality indicator
                Row(
                    modifier = Modifier
                        .background(
                            color = when (connectionQuality) {
                                "excellent" -> Color.Green.copy(alpha = 0.2f)
                                "good" -> Color(0xFF00FF00).copy(alpha = 0.2f)
                                "fair" -> Color.Yellow.copy(alpha = 0.2f)
                                else -> Color.Red.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Quality: ${connectionQuality.replaceFirstChar { it.uppercase() }}",
                        fontSize = 11.sp,
                        color = when (connectionQuality) {
                            "excellent" -> Color.Green
                            "good" -> Color(0xFF00FF00)
                            "fair" -> Color.Yellow
                            else -> Color.Red
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Middle: Call controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // First row: Mute, Speaker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute button
                    CallControlButton(
                        icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted,
                        onClick = { viewModel.toggleMute() }
                    )

                    // Speaker button
                    CallControlButton(
                        icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        label = if (isSpeakerOn) "Speaker On" else "Speaker Off",
                        isActive = isSpeakerOn,
                        onClick = { viewModel.toggleSpeaker() }
                    )

                    // Video button (only if video call)
                    if (callType == "video") {
                        CallControlButton(
                            icon = if (isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            label = if (isVideoEnabled) "Video On" else "Video Off",
                            isActive = isVideoEnabled,
                            onClick = { viewModel.toggleVideo(!isVideoEnabled) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // End call button - large and prominent
                Button(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Bottom: Call info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tap end call to disconnect",
                    fontSize = 12.sp,
                    color = CyberpunkTheme.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Individual call control button
 */
@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive)
                    CyberpunkTheme.PrimaryPurple
                else
                    CyberpunkTheme.DarkGray,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = CyberpunkTheme.LightGray,
            textAlign = TextAlign.Center
        )
    }
}
