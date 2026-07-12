package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme

/**
 * ⚠️ DEPRECATED: Channel feature is no longer active
 * This admin panel exists for legacy code compatibility only
 * Please use GroupSettingsScreen for group administration instead
 */
@Composable
fun ChannelAdminPanelEnhanced(
    channelId: String = "CHANNEL_001",
    channelName: String = "DEVELOPERS",
    onBackClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.Archive,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Channel Admin Panel Archived",
                color = Color.Yellow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Channels have been deprecated in favor of Groups. Please manage your team using the Groups feature instead.",
                color = CyberpunkTheme.LightGray,
                fontSize = 14.sp
            )
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
            ) {
                Text("Back")
            }
        }
    }
}
