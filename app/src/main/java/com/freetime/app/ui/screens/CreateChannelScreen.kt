package com.freetime.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme

@Composable
fun CreateChannelScreen(
    onChannelCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    // ⚠️ DEPRECATED: Channel feature is no longer active
    // This screen exists for legacy code compatibility only
    // Please use Groups instead for team collaboration
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "⚠️ Channel Feature Deprecated",
                color = Color.Yellow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Channels are no longer active. Please use Groups for team collaboration instead.",
                color = CyberpunkTheme.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
            ) {
                Text("Back")
            }
        }
    }
}
