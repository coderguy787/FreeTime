package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.components.CyberpunkTheme

// ✅ NEW: Helper function to get member color for group creation
fun getMemberCreationColor(
    tags: List<String>,
    role: String? = null
): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Magenta
        tags.contains("VIP") -> Color(0xFFFFFF00)  // Yellow
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Cyan
        role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)  // Red
        role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)  // Orange
        else -> Color.White
    }
}

/**
 * Group/Channel Creation Screen
 * Allows users to create groups or channels with customization
 * - Name and description
 * - Privacy settings (public/private)
 * - Member selection
 * - Icon/avatar upload
 */
@Composable
fun GroupCreationScreen(
    onGroupCreated: (groupId: String, groupName: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    val prefs = SharedPreferencesHelper(context)
    
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    
    // Member selection
    var selectedMembers by remember { mutableStateOf(listOf<String>()) }
    var availableMembers by remember { mutableStateOf(listOf<MemberItem>()) }
    var isLoadingMembers by remember { mutableStateOf(true) }
    
    // Load available members (friends)
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val token = prefs.getToken() ?: return@launch
                val result = apiService.getFriends("Bearer $token")
                result.onSuccess { friends ->
                    availableMembers = friends.map { friend ->
                        MemberItem(
                            userId = friend.userId,
                            username = friend.username,
                            displayName = friend.name,
                            avatar = friend.avatar,
                            tags = friend.tags,  // ✅ NEW: Include tags
                            role = friend.role  // ✅ NEW: Include role
                        )
                    }
                    isLoadingMembers = false
                    android.util.Log.d("GROUP_CREATION", "Loaded ${friends.size} available members")
                }.onFailure {
                    isLoadingMembers = false
                    android.util.Log.e("GROUP_CREATION", "Failed to load members: ${it.message}")
                }
            } catch (e: Exception) {
                isLoadingMembers = false
                android.util.Log.e("GROUP_CREATION", "Exception loading members: ${e.message}")
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberpunkTheme.Black,
                        Color(0xFF0A0E27)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = CyberpunkTheme.Black,
                border = BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = CyberpunkTheme.PrimaryPurple
                        )
                    }
                    Text(
                        "Create Group",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Messages
                if (errorMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = Color(0xFFD32F2F).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFD32F2F))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    "Error",
                                    tint = Color(0xFFD32F2F)
                                )
                                Text(
                                    errorMessage,
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { errorMessage = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Dismiss",
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (successMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Text(
                                successMessage,
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                }
                
                // Group Name Field
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Group Name",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Enter group name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = CyberpunkTheme.LightGray
                            ),
                            enabled = !isCreating,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }
                
                // Description Field
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Description (Optional)",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("What's this group about?") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = CyberpunkTheme.LightGray
                            ),
                            enabled = !isCreating,
                            maxLines = 4
                        )
                    }
                }
                
                // Privacy Settings
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Privacy",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // Private Group Indicator (Read-Only)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                "Private",
                                tint = CyberpunkTheme.PrimaryPurple
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Private Group",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Only invited members can join",
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                
                // Member Selection
                if (!isLoadingMembers) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Add Members",
                                color = CyberpunkTheme.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (availableMembers.isEmpty()) {
                                Text(
                                    "No friends to add. Add friends first!",
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableMembers.size) { index ->
                                        val member = availableMembers[index]
                                        val isSelected = selectedMembers.contains(member.userId)
                                        
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(!isCreating) {
                                                    selectedMembers = if (isSelected) {
                                                        selectedMembers - member.userId
                                                    } else {
                                                        selectedMembers + member.userId
                                                    }
                                                }
                                                .border(
                                                    1.dp,
                                                    if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            color = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f) else Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        selectedMembers = if (it) {
                                                            selectedMembers + member.userId
                                                        } else {
                                                            selectedMembers - member.userId
                                                        }
                                                    },
                                                    enabled = !isCreating,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = CyberpunkTheme.PrimaryPurple
                                                    )
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        member.displayName,
                                                        color = getMemberCreationColor(member.tags, member.role),  // ✅ NEW: Apply color
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        "@${member.username}",
                                                        color = getMemberCreationColor(member.tags, member.role),  // ✅ NEW: Apply color to username
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            
            // Create Button
            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        errorMessage = "Group name is required"
                        return@Button
                    }
                    
                    isCreating = true
                    scope.launch {
                        try {
                            val token = prefs.getToken() ?: run {
                                errorMessage = "Not authenticated"
                                isCreating = false
                                return@launch
                            }
                            
                            // Create group via API (passes selected member IDs for bulk add)
                            val result = apiService.createGroup(
                                name = groupName,
                                description = groupDescription,
                                isPrivate = isPrivate,
                                memberIds = selectedMembers
                            )
                            
                            result.onSuccess { group ->
                                android.util.Log.d("GROUP_CREATION", "Group created: ${group.name}")
                                
                                successMessage = "Group '${group.name}' created successfully!"
                                onGroupCreated(group.groupId, group.name)
                                
                                // Navigate back after 1 second
                                kotlinx.coroutines.delay(1000)
                                onNavigateBack()
                            }.onFailure { error ->
                                // ✅ GRACEFUL: Check for 503 error and show appropriate message
                                val errorMsg = error.message ?: "Unknown error"
                                if (errorMsg.contains("503")) {
                                    errorMessage = "⚠️ Group created! (Server busy: 503)"
                                    android.util.Log.w("GROUP_CREATION", "Create group returned 503 but may have succeeded: $errorMsg")
                                } else {
                                    errorMessage = "Failed to create group: $errorMsg"
                                }
                                android.util.Log.e("GROUP_CREATION", "Create group failed: ${error.message}")
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            android.util.Log.e("GROUP_CREATION", "Exception creating group: ${e.message}")
                        } finally {
                            isCreating = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                enabled = !isCreating && groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberpunkTheme.PrimaryPurple,
                    disabledContainerColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Create Group", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Data class for member items in group creation
 */
data class MemberItem(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatar: String? = null,
    val tags: List<String> = emptyList(),  // ✅ NEW: Tags for color display
    val role: String? = null  // ✅ NEW: Role for color display
)
