package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.composables.DownloadProgressBar

@Composable
fun MediaDisplayScreen(
    mediaId: String,
    mediaType: String,
    fileName: String,
    fileSize: Long,
    onClose: () -> Unit,
    onRequestDownload: (String) -> Unit,
    onDownloadApproved: (String) -> Unit,
    downloadStatus: String = "pending"
) {
    // media viewer, downloads need sender approval
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var downloadReason by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack),
        color = CyberpunkTheme.CyberBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Media",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    color = CyberpunkTheme.DarkGray
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val icon = when (mediaType) {
                            "image" -> Icons.Filled.Image
                            "video" -> Icons.Filled.Videocam
                            "document" -> Icons.Filled.Description
                            else -> Icons.Filled.Attachment
                        }

                        Icon(
                            icon,
                            contentDescription = mediaType,
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            mediaType.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "File: $fileName",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 11.sp
                        )

                        Text(
                            "Size: ${formatFileSize(fileSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    color = CyberpunkTheme.DarkGray.copy(alpha = 0.6f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Download Status:",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray
                            )

                            Text(
                                when (downloadStatus) {
                                    "approved" -> " Ready"
                                    "downloading" -> "⋯ Downloading"
                                    "completed" -> " Complete"
                                    "denied" -> " Denied"
                                    else -> "⧖ Pending Approval"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (downloadStatus) {
                                    "approved", "completed" -> CyberpunkTheme.PrimaryPurple
                                    "denied" -> Color(0xFFFF6B6B)
                                    "downloading" -> CyberpunkTheme.PrimaryPurple
                                    else -> Color.Yellow
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (downloadStatus == "downloading") {
                            DownloadProgressBar(
                                progress = downloadProgress,
                                fileName = fileName,
                                trackColor = CyberpunkTheme.DarkGray,
                                progressColor = CyberpunkTheme.PrimaryPurple,
                                textColor = CyberpunkTheme.GhostGray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                when (downloadStatus) {
                    "pending" -> {
                        Button(
                            onClick = { showRequestDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF667EEA)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudDownload,
                                contentDescription = "Request",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Request Download",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    "approved" -> {
                        Button(
                            onClick = { isDownloading = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberpunkTheme.PrimaryPurple
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            enabled = !isDownloading
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isDownloading) "Downloading..." else "Download Now",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    "downloading" -> {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Downloading...",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    "completed" -> {
                        Button(
                            onClick = { },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberpunkTheme.PrimaryPurple
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Open",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Download Complete",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    "denied" -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                            color = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Block,
                                    contentDescription = "Denied",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "Download request was denied",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF6B6B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("Request Download") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter reason for download request:",
                        color = Color.White
                    )
                    TextField(
                        value = downloadReason,
                        onValueChange = { downloadReason = it },
                        placeholder = { Text("Optional reason...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = CyberpunkTheme.DarkGray,
                            focusedContainerColor = CyberpunkTheme.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRequestDownload(downloadReason)
                        showRequestDialog = false
                    }
                ) {
                    Text("Request", color = CyberpunkTheme.PrimaryPurple)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRequestDialog = false }
                ) {
                    Text("Cancel", color = CyberpunkTheme.GhostGray)
                }
            },
            containerColor = CyberpunkTheme.DarkGray
        )
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

