package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.data.network.VersionInfoResponse
import com.freetime.app.ui.theme.CyberpunkTheme

/**
 * Full-screen gate shown when an update has been available for more than
 * GATE_DELAY_MS. Covers the entire app so the user only sees this screen.
 */
@Composable
fun UpdateGateScreen(
    info: VersionInfoResponse,
    onDownload: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "📡",
                    fontSize = 56.sp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "A new update is available",
                    color = CyberpunkTheme.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "FreeTime v${info.latestVersion.removePrefix("v")}",
                    color = CyberpunkTheme.PrimaryMagenta,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (info.forceUpdate) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This update is required to keep using FreeTime.",
                        color = CyberpunkTheme.WarningOrange,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A1A),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "What's new",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val notes = info.releaseNotes.ifBlank { "New features and bug fixes" }
                        notes.split("\n").forEach { line ->
                            if (line.isNotBlank()) {
                                Text(
                                    line.removePrefix("- ").let { "- $it" },
                                    color = CyberpunkTheme.GhostGray,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.PrimaryMagenta,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Download Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip) {
                    Text("Skip for now", color = CyberpunkTheme.GhostGray, fontSize = 14.sp)
                }
            }
        }
    }
}
