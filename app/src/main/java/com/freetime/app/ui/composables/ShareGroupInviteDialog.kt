package com.freetime.app.ui.composables

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.freetime.app.ui.theme.CyberpunkTheme

/**
 * Share Group Invitation Dialog
 * Allows users to share group invitation links across social platforms
 * Supports both deep links (app) and web links (browser/social media)
 * Users can choose expiration duration: 24 hours or never expire
 */
@Composable
fun ShareGroupInviteDialog(
    groupId: String,
    groupName: String,
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val apiService = remember { com.freetime.app.api.FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()
    
    // Generate links
    val deepLink = "freetime://group/invite/$groupId"
    val webLink = "https://example.com/group/invite/$inviteCode"
    val shareMessage = "Join my group '$groupName' on FreeTime! $webLink"
    
    var copiedLink by remember { mutableStateOf<String?>(null) }
    var selectedExpiration by remember { mutableStateOf("24hours") } // "24hours" or "never"
    var generatedLink by remember { mutableStateOf(webLink) }
    var expiryText by remember { mutableStateOf("Expires in 24 hours") }
    var isGeneratingLink by remember { mutableStateOf(false) }
    var showExpirationPicker by remember { mutableStateOf(true) }
    
    // Trigger link generation when button is clicked
    if (!showExpirationPicker && isGeneratingLink) {
        LaunchedEffect(selectedExpiration) {
            try {
                val expiresIn = if (selectedExpiration == "24hours") 86400000L else 0L // 0 = never expire
                val result = apiService.generateExpiringInviteLink(groupId, expiresIn)
                result.onSuccess { inviteLink ->
                    generatedLink = inviteLink.shareLink
                    val expiryTime = if (selectedExpiration == "24hours") {
                        "Expires in 24 hours"
                    } else {
                        "Never expires"
                    }
                    expiryText = expiryTime
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to generate link: ${error.message}", Toast.LENGTH_SHORT).show()
                }
                isGeneratingLink = false
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isGeneratingLink = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .background(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(16.dp)
            ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Share Group Invite",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFFF00FF)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFFF00FF)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info text
                Text(
                    "Share this group invitation with friends across social platforms or copy the link",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )

                // Group info card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            "Invite Code: $inviteCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF80FF00)
                        )
                    }
                }

                // ✅ NEW: Expiration duration picker
                if (showExpirationPicker) {
                    Text(
                        "Link Expiration",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF00FF)
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 24 hours option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExpiration = "24hours"
                                }
                                .background(
                                    color = if (selectedExpiration == "24hours") Color(0xFF2A4A2A) else Color(0xFF2A2A2A),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "24 Hour Expiry",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        "Link expires after 24 hours",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB0B0B0)
                                    )
                                }
                                RadioButton(
                                    selected = selectedExpiration == "24hours",
                                    onClick = { selectedExpiration = "24hours" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF80FF00)
                                    )
                                )
                            }
                        }
                        
                        // Never expire option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExpiration = "never"
                                }
                                .background(
                                    color = if (selectedExpiration == "never") Color(0xFF2A4A2A) else Color(0xFF2A2A2A),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "Never Expires",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        "Link works indefinitely",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB0B0B0)
                                    )
                                }
                                RadioButton(
                                    selected = selectedExpiration == "never",
                                    onClick = { selectedExpiration = "never" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF80FF00)
                                    )
                                )
                            }
                        }
                    }

                    // Generate link button
                    Button(
                        onClick = { isGeneratingLink = true },
                        enabled = !isGeneratingLink,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF00FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isGeneratingLink) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isGeneratingLink) "Generating..." else "Generate Link")
                    }
                } else {
                    // Show generated link info
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF2A4A2A),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Link Generated",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF80FF00)
                            )
                            Text(
                                expiryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF80FF00)
                            )
                        }
                    }

                    // Change expiration option
                    TextButton(
                        onClick = { showExpirationPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Change Expiration",
                            color = Color(0xFFFF00FF)
                        )
                    }
                }

                // Web Link Section (for social sharing)
                Text(
                    "Web Link (for social media)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF00FF)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                generatedLink,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF80FF00),
                                maxLines = 2
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(generatedLink))
                                copiedLink = "Web link"
                                Toast.makeText(context, "Web link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF80FF00)
                            )
                        }
                    }
                }

                // Deep Link Section (for app)
                Text(
                    "Deep Link (for FreeTime app)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF00FF)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                deepLink,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF80FF00),
                                maxLines = 2
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(deepLink))
                                copiedLink = "Deep link"
                                Toast.makeText(context, "Deep link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF80FF00)
                            )
                        }
                    }
                }

                // Share buttons
                Text(
                    "Share on Social Media",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF00FF)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share with system share sheet (all platforms)
                    Button(
                        onClick = {
                            val shareMsg = "Join my group '$groupName' on FreeTime! $generatedLink"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join my FreeTime group!")
                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share group invite via"))
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF80FF00),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Share to All Platforms")
                    }

                    // Share to WhatsApp
                    Button(
                        onClick = {
                            val shareMsg = "Join my group '$groupName' on FreeTime! $generatedLink"
                            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage("com.whatsapp")
                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                            }
                            try {
                                context.startActivity(whatsappIntent)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Share via WhatsApp")
                    }

                    // Share to Telegram
                    Button(
                        onClick = {
                            val shareMsg = "Join my group '$groupName' on FreeTime! $generatedLink"
                            val telegramIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage("org.telegram.messenger")
                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                            }
                            try {
                                context.startActivity(telegramIntent)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Telegram not installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0088cc),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Share via Telegram")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF80FF00),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        },
        containerColor = Color(0xFF1A1A1A),
        tonalElevation = 8.dp
    )
}
