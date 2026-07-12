package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.data.storage.StorageManager
import com.freetime.app.data.storage.StorageStatus
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.animations.*

/**
 * Storage Management Screen - Monitor and manage encrypted storage
 */
@Composable
fun StorageManagementScreen(modifier: Modifier = Modifier) {
    var storageManager by remember { mutableStateOf<StorageManager?>(null) }
    var totalUsage by remember { mutableStateOf(0L) }
    var storageStatus by remember { mutableStateOf(StorageStatus.HEALTHY) }
    var percentage by remember { mutableStateOf(0) }
    var breakdown by remember { mutableStateOf<String>("") }
    var recommendations by remember { mutableStateOf(listOf<String>()) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // In real app, initialize StorageManager from context
        // storageManager = StorageManager(context)
        // This is a simulation for UI layout
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberpunkTheme.DarkGray.copy(alpha = 0.8f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            FadeInAnimation(delayMillis = 0) {
                Text(
                    "Storage Management",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CyberpunkTheme.PrimaryPurple,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Storage Usage Gauge
        item {
            FadeInAnimation(delayMillis = 100) {
                StorageGaugeCard(
                    percentage = percentage,
                    status = storageStatus,
                    totalUsage = totalUsage
                )
            }
        }
        
        // Quick Stats
        item {
            FadeInAnimation(delayMillis = 200) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StorageStatCard(
                        label = "Messages",
                        value = "150 MB",
                        icon = Icons.Filled.Mail,
                        modifier = Modifier.weight(1f)
                    )
                    StorageStatCard(
                        label = "Media",
                        value = "320 MB",
                        icon = Icons.Filled.Image,
                        modifier = Modifier.weight(1f)
                    )
                    StorageStatCard(
                        label = "Archive",
                        value = "45 MB",
                        icon = Icons.Filled.Archive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Storage Breakdown
        item {
            FadeInAnimation(delayMillis = 300) {
                StorageBreakdownCard(breakdown = breakdown)
            }
        }
        
        // Cleanup Recommendations
        item {
            FadeInAnimation(delayMillis = 400) {
                Text(
                    "Cleanup Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberpunkTheme.PrimaryPurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        items(recommendations) { recommendation ->
            CleanupRecommendationCard(
                recommendation = recommendation,
                onApply = { showCleanupDialog = true }
            )
        }
        
        // Action Buttons
        item {
            FadeInAnimation(delayMillis = 600) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto Cleanup Button
                    Button(
                        onClick = { showCleanupDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Cleanup",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp)
                            )
                            Text("Run Cleanup", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Archive Messages Button
                    Button(
                        onClick = { /* Archive old messages */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = "Archive",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp)
                            )
                            Text("Archive Old Messages", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Compression Button
                    Button(
                        onClick = { /* Compress media */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Compress,
                                contentDescription = "Compress",
                                tint = CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp)
                            )
                            Text("Compress Media", color = CyberpunkTheme.PrimaryPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Info Section
        item {
            FadeInAnimation(delayMillis = 700) {
                StorageInfoCard()
            }
        }
    }
    
    // Cleanup Confirmation Dialog
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = {
                Text(
                    "Run Cleanup?",
                    color = CyberpunkTheme.PrimaryPurple,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will safely remove old media (30+ days) and archive old messages (90+ days).\n\nEstimated space to free: ~450 MB",
                    color = CyberpunkTheme.GhostGray
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCleanupDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.PrimaryPurple
                    )
                ) {
                    Text("Cleanup")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCleanupDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.DarkGray
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun StorageGaugeCard(
    percentage: Int,
    status: StorageStatus,
    totalUsage: Long
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Encrypted Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberpunkTheme.PrimaryPurple,
                    fontWeight = FontWeight.Bold
                )
                
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp)),
                    color = when (status) {
                        StorageStatus.HEALTHY -> CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                        StorageStatus.WARNING -> Color(0xFFFF00FF).copy(alpha = 0.2f)
                        StorageStatus.CRITICAL -> Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        when (status) {
                            StorageStatus.HEALTHY -> "✓ HEALTHY"
                            StorageStatus.WARNING -> "⚠ WARNING"
                            StorageStatus.CRITICAL -> "✗ CRITICAL"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            StorageStatus.HEALTHY -> CyberpunkTheme.PrimaryPurple
                            StorageStatus.WARNING -> Color(0xFFFF00FF)
                            StorageStatus.CRITICAL -> Color(0xFFFF6B6B)
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$percentage% Used",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberpunkTheme.GhostGray
                    )
                    Text(
                        "1.0 GB Max",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.GhostGray.copy(alpha = 0.7f)
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (percentage.toFloat() / 100f))
                            .background(
                                color = when (status) {
                                    StorageStatus.HEALTHY -> CyberpunkTheme.PrimaryPurple.copy(alpha = 0.7f)
                                    StorageStatus.WARNING -> Color(0xFFFF00FF).copy(alpha = 0.7f)
                                    StorageStatus.CRITICAL -> Color(0xFFFF6B6B).copy(alpha = 0.7f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun StorageStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(24.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = CyberpunkTheme.PrimaryPurple,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkTheme.GhostGray
            )
        }
    }
}

@Composable
fun StorageBreakdownCard(breakdown: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Storage Breakdown",
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkTheme.PrimaryPurple,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                breakdown.ifEmpty {
                    """
                    Messages: 150 MB
                    Media: 320 MB
                    Archive: 45 MB
                    Database: 35 MB
                    ─────────────────
                    Total: 550 MB
                    """.trimIndent()
                },
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkTheme.GhostGray,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CleanupRecommendationCard(
    recommendation: String,
    onApply: () -> Unit
) {
    SlideInFromBottom(delayMillis = 400) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .clickable { onApply() },
            color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        recommendation.take(40) + if (recommendation.length > 40) "..." else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberpunkTheme.PrimaryPurple,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Estimated: ~120 MB savings",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.GhostGray
                    )
                }
                
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Apply",
                    tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StorageInfoCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "📋 Storage Information",
                style = MaterialTheme.typography.titleSmall,
                color = CyberpunkTheme.PrimaryPurple,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Your messages and media are encrypted and stored securely on your device. " +
                "As you send/receive messages and media, storage grows over time.\n\n" +
                "• Old media (30+ days) can be safely removed\n" +
                "• Messages auto-archive after 90 days\n" +
                "• Media can be re-downloaded if needed\n" +
                "• Storage limit: 1 GB (configurable)\n\n" +
                "💚 FreeTime respects your privacy - data stays on your device!",
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkTheme.GhostGray,
                lineHeight = 18.sp
            )
        }
    }
}




