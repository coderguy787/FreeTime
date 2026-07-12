package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.theme.LocalDisplaySettings
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.UserData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberInviteScreen(
    groupId: String,
    groupName: String = "Group",
    isAdmin: Boolean = false,
    onBackClick: () -> Unit = {},
    onInviteComplete: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var availableUsers by remember { mutableStateOf<List<UserData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isInviting by remember { mutableStateOf<String?>(null) } // userId being invited
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isGeneratingCode by remember { mutableStateOf(false) }
    var isRevokingCode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    val clipboardManager = LocalClipboardManager.current
    val displaySettings = LocalDisplaySettings.current
    val accentColor = displaySettings.getAccentColor()

    // Load friends not already in the group + current invite code
    LaunchedEffect(groupId) {
        isLoading = true
        try {
            val friendsResult = apiService.getFriendsNotInGroup(groupId)
            friendsResult.onSuccess { friends -> availableUsers = friends }
            friendsResult.onFailure { e -> errorMessage = "Failed to load friends: ${e.message}" }

            // Load group details to get existing invite code
            val groupResult = apiService.getGroupDetails(groupId)
            groupResult.onSuccess { g -> inviteCode = g.inviteCode ?: "" }
        } catch (e: Exception) {
            errorMessage = "Failed to load data: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    fun inviteUser(userId: String) {
        coroutineScope.launch {
            isInviting = userId
            errorMessage = ""
            try {
                val result = apiService.inviteToGroup(groupId, userId)
                result.fold(
                    onSuccess = {
                        successMessage = "User invited successfully!"
                        availableUsers = availableUsers.filter { it.userId != userId }
                    },
                    onFailure = { error -> errorMessage = "Failed to invite: ${error.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isInviting = null
            }
        }
    }

    fun generateCode() {
        coroutineScope.launch {
            isGeneratingCode = true
            errorMessage = ""
            try {
                val result = apiService.generateGroupInviteCode(groupId)
                result.fold(
                    onSuccess = { code ->
                        inviteCode = code
                        successMessage = "Invite code generated!"
                    },
                    onFailure = { e -> errorMessage = "Failed to generate code: ${e.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isGeneratingCode = false
            }
        }
    }

    fun revokeCode() {
        coroutineScope.launch {
            isRevokingCode = true
            errorMessage = ""
            try {
                val result = apiService.revokeGroupInviteCode(groupId)
                result.fold(
                    onSuccess = {
                        inviteCode = ""
                        successMessage = "Invite code revoked."
                    },
                    onFailure = { e -> errorMessage = "Failed to revoke code: ${e.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isRevokingCode = false
            }
        }
    }
    
    val filteredUsers = availableUsers.filter { user ->
        searchQuery.isBlank() ||
        user.username.contains(searchQuery, ignoreCase = true) ||
        user.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.DarkBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
            }
            Text(
                text = "Invite to $groupName",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(Modifier.size(48.dp))
        }

        // Status messages
        if (errorMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
            ) {
                Text(text = errorMessage, modifier = Modifier.padding(12.dp), color = Color.Red, fontSize = 13.sp)
            }
        }
        if (successMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00FF00).copy(alpha = 0.1f))
            ) {
                Text(text = successMessage, modifier = Modifier.padding(12.dp), color = Color(0xFF00FF00), fontSize = 13.sp)
            }
        }

        // Invite Code Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Invite Link", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
                if (inviteCode.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("Code", color = CyberpunkTheme.LightGray, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CyberpunkTheme.CyberCyan,
                                unfocusedTextColor = CyberpunkTheme.CyberCyan,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = accentColor.copy(alpha = 0.5f)
                            )
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(inviteCode))
                            successMessage = "Code copied to clipboard!"
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = CyberpunkTheme.CyberCyan)
                        }
                    }
                } else {
                    Text(
                        text = if (isAdmin) "No invite code yet. Generate one below." else "No invite code available. Ask an admin to generate one.",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 13.sp
                    )
                }
                if (isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { generateCode() },
                            enabled = !isGeneratingCode && !isRevokingCode,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.CyberCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isGeneratingCode) CircularProgressIndicator(Modifier.size(14.dp), color = CyberpunkTheme.DarkBlack, strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (inviteCode.isBlank()) "Generate Code" else "Regenerate", fontSize = 12.sp)
                        }
                        if (inviteCode.isNotBlank()) {
                            Button(
                                onClick = { revokeCode() },
                                enabled = !isGeneratingCode && !isRevokingCode,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isRevokingCode) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Revoke", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search friends...", color = CyberpunkTheme.LightGray) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = accentColor) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = accentColor.copy(alpha = 0.5f)
            )
        )

        // Friends list
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No friends found" else "All your friends are already in this group",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredUsers) { user ->
                    UserInviteCard(
                        user = user,
                        onInvite = { inviteUser(user.userId) },
                        isInviting = isInviting == user.userId
                    )
                }
            }
        }
    }
}

@Composable
fun UserInviteCard(
    user: UserData,
    onInvite: () -> Unit,
    isInviting: Boolean
) {
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (user.isOnline) Color(0xFF00FF00).copy(alpha = 0.3f) else accentColor.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (user.isOnline) Color(0xFF00FF00) else accentColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        color = if (user.isOnline) Color(0xFF00FF00) else accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column {
                    Text(
                        text = user.name,
                        color = CyberpunkTheme.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${user.username}",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (user.isOnline) "Online" else "Offline",
                        color = if (user.isOnline) Color(0xFF00FF00) else CyberpunkTheme.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
            
            Button(
                onClick = onInvite,
                enabled = !isInviting,
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                if (isInviting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyberpunkTheme.DarkBlack,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Invite", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Invite", color = CyberpunkTheme.DarkBlack)
                }
            }
        }
    }
}
