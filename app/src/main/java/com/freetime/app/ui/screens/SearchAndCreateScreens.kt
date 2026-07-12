package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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


data class SearchResult(val id: String, val title: String, val description: String, val type: String)

@Composable
fun SearchResultsScreenEnhanced(query: String = "security", onBackClick: () -> Unit = {}) {
    val results = listOf(
        SearchResult("1", "Encryption Setup Guide", "Complete guide for E2E encryption", "GUIDE"),
        SearchResult("2", "Security Settings", "Configure your security preferences", "SETTING"),
        SearchResult("3", "CYBER_USER_SECURITY", "User profile from search", "CONTACT")
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(CyberpunkTheme.CyberBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // HEADER
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f)),
                color = Color.Transparent) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(20.dp))
                    }
                    Text("> SEARCH_RESULTS <", style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                        color = CyberpunkTheme.PrimaryPurple)
                }
            }

            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                        Text("[QUERY: \"$query\"] - Found ${results.size} results",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 10.sp)
                    }
                }

                items(results) { result ->
                    SearchResultCard(result)
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: SearchResult) {
    Surface(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .clickable { }
        .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Icon(when (result.type) {
                "GUIDE" -> Icons.Filled.BookmarkBorder
                "SETTING" -> Icons.Filled.Settings
                else -> Icons.Filled.Person
            }, contentDescription = result.type,
                tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(20.dp))
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(result.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.PrimaryPurple)
                Text(result.description, style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.GhostGray, fontSize = 10.sp)
            }

            Surface(modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.2f))
                .padding(6.dp)) {
                Text(result.type, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = CyberpunkTheme.PrimaryMagenta)
            }
        }
    }
}

@Composable
fun CreateChannelScreenEnhanced(onCancel: () -> Unit = {}, onCreate: (String) -> Unit = {}) {
    var channelName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("> CREATE_CHANNEL <", style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = CyberpunkTheme.PrimaryPurple)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                    TextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text("[CHANNEL_NAME]", color = CyberpunkTheme.GhostGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CyberpunkTheme.PrimaryPurple),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ))
                }

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isPrivate = !isPrivate }
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = "Private",
                        tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(16.dp))
                    Text("PRIVATE_CHANNEL", style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.PrimaryPurple, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier
                        .size(width = 40.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (isPrivate) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                            RoundedCornerShape(12.dp)),
                        color = if (isPrivate) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f) else Color.Transparent) {
                        Box(modifier = Modifier.fillMaxSize(),
                            contentAlignment = if (isPrivate) Alignment.CenterEnd else Alignment.CenterStart) {
                            Surface(modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp),
                                shape = CircleShape,
                                color = if (isPrivate) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (channelName.isNotEmpty()) onCreate(channelName) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)) {
                Text("CREATE", color = CyberpunkTheme.CyberBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.DarkGray)) {
                Text("CANCEL", color = CyberpunkTheme.PrimaryPurple)
            }
        },
        containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp)
    )
}


