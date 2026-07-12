package com.freetime.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.BuildConfig
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.theme.LocalDisplaySettings
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.launch

private fun getBaseUrl(): String {
    return if (BuildConfig.DEBUG) {
        "http://10.0.2.2:8080"
    } else {
        "https://example.com"
    }
}

data class SelectableFriend(
    val userId: String,
    val name: String,
    val username: String,
    val isOnline: Boolean,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }
    var showInviteLink by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf(listOf<SelectableFriend>()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    var currentStep by remember { mutableStateOf(1) } // 1 = details, 2 = select members
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    val displaySettings = LocalDisplaySettings.current
    val accentColor = displaySettings.getAccentColor()

    // Load friends list
    LaunchedEffect(Unit) {
        try {
            val result = apiService.getFriends()
            result.onSuccess { friendList ->
                friends = friendList.map { friend ->
                    SelectableFriend(
                        userId = friend.userId,
                        name = friend.name.ifEmpty { friend.username },
                        username = friend.username,
                        isOnline = friend.isOnline
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("CreateGroup", "Error loading friends: ${e.message}")
        } finally {
            isLoadingFriends = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.Black)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        if (currentStep == 1) "Create New Group" else "Add Members",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == 2) currentStep = 1 else onCancel()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )

            if (currentStep == 1) {
                // Step 1: Group Details
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Group Details", fontSize = 18.sp, color = accentColor, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = { groupDescription = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { currentStep = 2 },
                        enabled = groupName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Next: Select Members")
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            } else {
                // Step 2: Select Members
                Column(modifier = Modifier.fillMaxSize()) {
                    val selectedCount = friends.count { it.isSelected }
                    Text(
                        "$selectedCount member${if (selectedCount != 1) "s" else ""} selected",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    if (isLoadingFriends) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    } else if (friends.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No friends yet", color = Color.Gray, fontSize = 16.sp)
                            Text("Add friends first to create a group", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(friends) { friend ->
                                FriendSelectionItem(
                                    friend = friend,
                                    onToggle = {
                                        friends = friends.map {
                                            if (it.userId == friend.userId) it.copy(isSelected = !it.isSelected) else it
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (errorMessage.isNotBlank()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    // Create button
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = ""
                            coroutineScope.launch {
                                try {
                                    val selectedMemberIds = friends.filter { it.isSelected }.map { it.userId }
                                    val result = apiService.createGroup(groupName, groupDescription, selectedMemberIds)
                                    result.fold(
                                        onSuccess = { group ->
                                            // ✅ SUCCESS: Group created, navigate to it
                                            onGroupCreated(group.groupId)
                                        },
                                        onFailure = { error ->
                                            // ✅ GRACEFUL: Show error message (API layer handles 503 gracefully)
                                            errorMessage = "Failed to create group: ${error.message}"
                                        }
                                    )
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    errorMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = groupName.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Creating...")
                        } else {
                            Text("Create Group")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendSelectionItem(
    friend: SelectableFriend,
    onToggle: () -> Unit
) {
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (friend.isSelected) accentColor else Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (friend.isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    friend.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "@${friend.username}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // Online status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (friend.isOnline) Color(0xFF4CAF50) else Color.Gray)
        )
    }
}
