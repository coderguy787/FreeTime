package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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


@Composable
fun EditGroupScreenEnhanced(groupName: String = "DEVELOPERS", onBackClick: () -> Unit = {}, onSave: (String) -> Unit = {}) {
    var name by remember { mutableStateOf(groupName) }
    var description by remember { mutableStateOf("Development and deployment discussions") }
    var isArchived by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(CyberpunkTheme.CyberBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)),
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
                    Text("> EDIT_GROUP <", style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                        color = CyberpunkTheme.PrimaryPurple, modifier = Modifier.weight(1f))
                }
            }

            LazyColumn(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Text("[GROUP_INFO]", style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = CyberpunkTheme.PrimaryPurple)
                }

                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("GROUP_NAME", color = CyberpunkTheme.GhostGray) },
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
                }

                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                        TextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("DESCRIPTION", color = CyberpunkTheme.GhostGray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .padding(4.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = CyberpunkTheme.PrimaryPurple),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 4)
                    }
                }

                item {
                    Text("[SETTINGS]", style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = CyberpunkTheme.PrimaryPurple)
                }

                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isArchived = !isArchived }
                        .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(6.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archive",
                                tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(18.dp))
                            Text("ARCHIVE_GROUP", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = CyberpunkTheme.PrimaryPurple, modifier = Modifier.weight(1f))
                            Surface(modifier = Modifier
                                .size(width = 48.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, if (isArchived) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                                    RoundedCornerShape(14.dp)),
                                color = if (isArchived) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f) else Color.Transparent) {
                                Box(modifier = Modifier.fillMaxSize(),
                                    contentAlignment = if (isArchived) Alignment.CenterEnd else Alignment.CenterStart) {
                                    Surface(modifier = Modifier
                                        .size(22.dp)
                                        .padding(2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isArchived) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray) {}
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBackClick() }
                    .border(1.dp, CyberpunkTheme.GhostGray.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    color = Color.Transparent) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("CANCEL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberpunkTheme.GhostGray)
                    }
                }
                Surface(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSave(name) }
                    .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(6.dp)),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("SAVE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberpunkTheme.PrimaryPurple)
                    }
                }
            }
        }
    }
}

data class BackupCode(val code: String, val isUsed: Boolean = false)

@Composable
fun BackupCodesScreenEnhanced(onBackClick: () -> Unit = {}, onCopyAll: () -> Unit = {}) {
    val codes = listOf(
        BackupCode("A1B2C3D4E5F6"),
        BackupCode("G7H8I9J0K1L2"),
        BackupCode("M3N4O5P6Q7R8"),
        BackupCode("S9T0U1V2W3X4"),
        BackupCode("Y5Z6A7B8C9D0"),
        BackupCode("E1F2G3H4I5J6", isUsed = true)
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(CyberpunkTheme.CyberBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)),
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
                    Text("> BACKUP_CODES <", style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                        color = CyberpunkTheme.PrimaryPurple)
                }
            }

            LazyColumn(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFFFFA500), RoundedCornerShape(8.dp)),
                        color = Color(0xFFFFA500).copy(alpha = 0.1f)) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Warning, contentDescription = "Warning",
                                tint = Color(0xFFFFA500), modifier = Modifier.size(18.dp))
                            Text("Save these codes in a safe place. Each code can only be used once to recover your account.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray, fontSize = 10.sp)
                        }
                    }
                }

                items(codes) { code ->
                    BackupCodeItem(code)
                }

                item {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCopyAll() }
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(6.dp)),
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy",
                                tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(16.dp))
                            Text("COPY_ALL_CODES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = CyberpunkTheme.PrimaryPurple)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupCodeItem(code: BackupCode) {
    Surface(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .border(1.dp, if (code.isUsed) CyberpunkTheme.GhostGray.copy(alpha = 0.2f) else CyberpunkTheme.DarkGray,
            RoundedCornerShape(6.dp)),
        color = if (code.isUsed) CyberpunkTheme.DarkGray.copy(alpha = 0.2f) else CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (code.isUsed) {
                Icon(Icons.Filled.Check, contentDescription = "Used",
                    tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(16.dp))
            }
            Text(code.code, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (code.isUsed) CyberpunkTheme.GhostGray else CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.weight(1f))
            if (code.isUsed) {
                Text("USED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = CyberpunkTheme.GhostGray)
            } else {
                IconButton(onClick = { /* Copy code */ }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy",
                        tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}


