package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.Group
import com.freetime.app.api.UserData
import com.freetime.app.ui.theme.LocalDisplaySettings
import kotlinx.coroutines.launch
import com.freetime.app.utils.GroupRefreshManager  // ✅ NEW: Import global refresh manager

// ✅ NEW: Helper function to get member color for group settings
fun getGroupSettingsMemberColor(
    tags: List<String>,
    role: String? = null,
    isSystemAdmin: Boolean = false,
    isSystemModerator: Boolean = false
): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)
        tags.contains("VIP") -> Color(0xFFFFFF00)
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
        isSystemAdmin || role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)
        isSystemModerator || role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)
        else -> Color.White
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBackClick: () -> Unit = {},
    onGroupUpdated: (Group) -> Unit = {},
    onGroupDeleted: () -> Unit = {},
    onGroupLeft: () -> Unit = {},
    onNavigateToVoting: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    val displaySettings = LocalDisplaySettings.current
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    val accentColor = displaySettings.getAccentColor()

    var group by remember { mutableStateOf<Group?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdating by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    
    // Form states
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isGeneratingCode by remember { mutableStateOf(false) }
    var isRevokingCode by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var expandedMemberId by remember { mutableStateOf<String?>(null) }  // ✅ FIX: Track which member's menu is open
    
    // Group picture upload launcher
    var isUploadingPicture by remember { mutableStateOf(false) }
    val groupPictureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            coroutineScope.launch {
                isUploadingPicture = true
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val result = apiService.uploadGroupPicture(groupId, bytes)
                        result.fold(
                            onSuccess = { pictureUrl ->
                                // Refresh group data to get updated picture URL
                                apiService.getGroupDetails(groupId).onSuccess { loadedGroup ->
                                    group = loadedGroup
                                    onGroupUpdated(loadedGroup)
                                }
                                successMessage = "Group picture updated successfully!"
                            },
                            onFailure = { error ->
                                errorMessage = "Failed to upload picture: ${error.message}"
                            }
                        )
                    }
                    inputStream?.close()
                } catch (e: Exception) {
                    errorMessage = "Error uploading picture: ${e.message}"
                } finally {
                    isUploadingPicture = false
                }
            }
        }
    }
    
    // Add Friends state
    var showAddFriends by remember { mutableStateOf(false) }
    var friendsNotInGroup by remember { mutableStateOf(emptyList<UserData>()) }
    var loadingFriendsForGroup by remember { mutableStateOf(false) }
    var selectedFriendsToAdd by remember { mutableStateOf(setOf<String>()) }
    var isAddingFriendsToGroup by remember { mutableStateOf(false) }

    // Load group data
    LaunchedEffect(groupId) {
        isLoading = true
        try {
            val result = apiService.getGroupDetails(groupId)
            result.fold(
                onSuccess = { loadedGroup ->
                    group = loadedGroup
                    groupName = loadedGroup.name
                    groupDescription = loadedGroup.description
                    isPrivate = loadedGroup.isPrivate
                    inviteCode = loadedGroup.inviteCode ?: ""
                    inviteLink = loadedGroup.inviteLink ?: ""
                    val currentUserId: String = prefs.getUserId() ?: ""
                    // ✅ FIX: Combine both admins and adminIds (server sends both)
                    val adminList = (loadedGroup.admins + loadedGroup.adminIds).distinct()
                    isAdmin = adminList.contains(currentUserId)
                },
                onFailure = { error ->
                    errorMessage = "Failed to load group: ${error.message}"
                }
            )
        } catch (e: Exception) {
            errorMessage = "Failed to load group: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    fun updateGroup() {
        if (groupName.isBlank()) {
            errorMessage = "Group name cannot be empty"
            return
        }
        
        isUpdating = true
        coroutineScope.launch {
            try {
                val result = apiService.updateGroupDetails(groupId, groupName, groupDescription, isPrivate)
                result.fold(
                    onSuccess = {
                        // Refresh group data
                        apiService.getGroupDetails(groupId).onSuccess { loadedGroup ->
                            group = loadedGroup
                            onGroupUpdated(loadedGroup)
                            successMessage = "Group updated successfully!"
                        }
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to update group: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error updating group: ${e.message}"
            } finally {
                isUpdating = false
            }
        }
    }

    fun deleteGroup() {
        isDeleting = true
        coroutineScope.launch {
            try {
                val result = apiService.deleteGroup(groupId)
                result.fold(
                    onSuccess = { 
                        // ✅ IMPROVED: Wait for backend to process, then refresh
                        kotlinx.coroutines.delay(500)  // Allow backend to process
                        GroupRefreshManager.triggerRefresh()  // Notify home screen to refresh groups
                        kotlinx.coroutines.delay(100)  // Brief delay for refresh to start
                        onGroupDeleted() 
                    },
                    onFailure = { error ->
                        android.util.Log.e("GROUP_DELETE", "Failed to delete group: ${error.message}")
                        errorMessage = "Failed to delete group: ${error.message}"
                        isDeleting = false
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("GROUP_DELETE", "Error deleting group: ${e.message}")
                errorMessage = "Error deleting group: ${e.message}"
                isDeleting = false
            }
        }
    }

    fun leaveGroup() {
        isLeaving = true
        coroutineScope.launch {
            try {
                val result = apiService.leaveGroup(groupId)
                result.fold(
                    onSuccess = { 
                        // ✅ IMPROVED: Wait for backend to process, then refresh
                        kotlinx.coroutines.delay(500)  // Allow backend to process
                        GroupRefreshManager.triggerRefresh()  // Notify home screen to refresh groups
                        kotlinx.coroutines.delay(100)  // Brief delay for refresh to start
                        onGroupLeft() 
                    },
                    onFailure = { error ->
                        android.util.Log.e("GROUP_LEAVE", "Failed to leave group: ${error.message}")
                        errorMessage = "Failed to leave group: ${error.message}"
                        isLeaving = false
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error leaving group: ${e.message}"
                isLeaving = false
            }
        }
    }

    fun generateInviteCode() {
        isGeneratingCode = true
        coroutineScope.launch {
            try {
                val result = apiService.generateGroupInviteCode(groupId)
                result.fold(
                    onSuccess = { code ->
                        inviteCode = code
                        inviteLink = "https://example.com/api/groups/join/$code"
                        successMessage = "New invite code generated!"
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to generate code: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error generating code: ${e.message}"
            } finally {
                isGeneratingCode = false
            }
        }
    }

    fun revokeInviteCode() {
        isRevokingCode = true
        coroutineScope.launch {
            try {
                val result = apiService.revokeGroupInviteCode(groupId)
                result.fold(
                    onSuccess = {
                        inviteCode = ""
                        inviteLink = ""
                        successMessage = "Invite code revoked"
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to revoke code: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error revoking code: ${e.message}"
            } finally {
                isRevokingCode = false
            }
        }
    }

    fun loadFriendsNotInGroup() {
        loadingFriendsForGroup = true
        coroutineScope.launch {
            try {
                val result = apiService.getFriends()
                result.fold(
                    onSuccess = { friends ->
                        val currentMembers = group?.members ?: emptyList()
                        friendsNotInGroup = friends.filter { friend ->
                            !currentMembers.any { member ->
                                (member as? Map<*, *>)?.get("userId") == friend.userId ||
                                (member as? String) == friend.userId
                            }
                        }
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to load friends: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error loading friends: ${e.message}"
            } finally {
                loadingFriendsForGroup = false
            }
        }
    }

    fun addSelectedFriends() {
        if (selectedFriendsToAdd.isEmpty()) return
        
        isAddingFriendsToGroup = true
        coroutineScope.launch {
            try {
                val result = apiService.inviteToGroup(groupId, selectedFriendsToAdd.toList())
                result.fold(
                    onSuccess = {
                        successMessage = "Friends added successfully!"
                        showAddFriends = false
                        selectedFriendsToAdd = emptySet()
                        val updatedResult = apiService.getGroupDetails(groupId)
                        updatedResult.onSuccess { group = it }
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to add friends: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error adding friends: ${e.message}"
            } finally {
                isAddingFriendsToGroup = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.DarkerGray)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
            }
            Text("Group Settings", fontSize = 22.sp, color = accentColor)
            
            if (isAdmin) {
                IconButton(
                    onClick = { groupPictureLauncher.launch("image/*") },
                    enabled = !isUploadingPicture
                ) {
                    if (isUploadingPicture) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Update group picture", tint = accentColor)
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        if (errorMessage.isNotBlank()) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(3000)
                errorMessage = ""
            }
            Text(errorMessage, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
        }
        
        if (successMessage.isNotBlank()) {
            LaunchedEffect(successMessage) {
                kotlinx.coroutines.delay(3000)
                successMessage = ""
            }
            Text(successMessage, color = Color.Green, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Group not found", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Group Information", fontWeight = FontWeight.Bold, color = Color.Gray)
                
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Gray
                    ),
                    enabled = isAdmin
                )
                
                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Gray
                    ),
                    enabled = true // ✅ ALLOW: Both admins and members can edit description
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Private Group", color = Color.White)
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = isAdmin,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // ✅ UPDATE: Show button for both admins and members
                Button(
                    onClick = { updateGroup() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    enabled = !isUpdating
                ) {
                    if (isUpdating) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else {
                        val buttonText = if (isAdmin) "Update Settings" else "Update Description"
                        Text(buttonText, color = Color.Black)
                    }
                }
                
                HorizontalDivider(color = Color.DarkGray)
                
                Text("Invitations", fontWeight = FontWeight.Bold, color = Color.Gray)
                
                if (inviteCode.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CyberpunkTheme.DarkGray,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Active Invite Code", color = Color.Gray, fontSize = 12.sp)
                                Text(inviteCode, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Row {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(inviteLink))
                                    successMessage = "Link copied to clipboard!"
                                }) {
                                    Icon(Icons.Filled.ContentCopy, "Copy Link", tint = accentColor)
                                }
                                if (isAdmin) {
                                    IconButton(onClick = { revokeInviteCode() }, enabled = !isRevokingCode) {
                                        Icon(Icons.Filled.Delete, "Revoke", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (isAdmin) {
                        Button(
                            onClick = { generateInviteCode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            enabled = !isGeneratingCode
                        ) {
                            if (isGeneratingCode) CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                            else Text("Generate Invite Code", color = Color.White)
                        }
                    } else {
                        Text("No active invite code", color = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color.DarkGray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Members (${group?.members?.size ?: 0})", fontWeight = FontWeight.Bold, color = Color.Gray)
                    if (isAdmin) {
                        TextButton(onClick = { 
                            showAddFriends = true
                            loadFriendsNotInGroup()
                        }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Friends", color = accentColor)
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray)
                
                // ✅ Members List Display
                if (group?.members.isNullOrEmpty()) {
                    Text("No members", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = group?.members ?: emptyList(),
                            key = { it.userId }  // ✅ FIX: Add key for proper recomposition
                        ) { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        member.username ?: "Unknown User",
                                        color = getGroupSettingsMemberColor(member.tags, member.role, member.isSystemAdmin, member.isSystemModerator),
                                        fontWeight = FontWeight.Bold
                                    )
                                    // ✅ FIX: Combine both admins and adminIds fields
                                    val adminList = ((group?.admins ?: emptyList()) + (group?.adminIds ?: emptyList())).distinct()
                                    val memberIsAdmin = adminList.contains(member.userId) || member.isAdmin
                                    
                                    // ✅ Display admin status and tags
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (memberIsAdmin) {
                                            Text("👑 Admin", color = accentColor, fontSize = 10.sp)
                                        }
                                        // ✅ Display member tags
                                        if (member.tags.isNotEmpty()) {
                                            member.tags.take(2).forEach { tag ->
                                                Text(
                                                    "#$tag",
                                                    color = Color.Yellow,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(Color.Yellow.copy(alpha = 0.2f))
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (isAdmin && member.userId != prefs.getUserId() && member.userId != group?.creatorId) {
                                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                                        IconButton(
                                            onClick = { expandedMemberId = if (expandedMemberId == member.userId) null else member.userId },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.MoreVert, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = expandedMemberId == member.userId && member.userId != group?.creatorId,
                                            onDismissRequest = { expandedMemberId = null }
                                        ) {
                                            // ✅ FIX: Combine both admins and adminIds fields for admin status
                                            val adminList = ((group?.admins ?: emptyList()) + (group?.adminIds ?: emptyList())).distinct()
                                            val memberIsAdmin = adminList.contains(member.userId) || member.isAdmin
                                            if (!memberIsAdmin) {
                                                DropdownMenuItem(
                                                    text = { Text("Make Admin", color = Color.White) },
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            try {
                                                                val result = apiService.promoteGroupAdmin(groupId, member.userId)
                                                                result.fold(
                                                                    onSuccess = {
                                                                        successMessage = "${member.username} is now admin"
                                                                        apiService.getGroupDetails(groupId).onSuccess { updatedGroup ->
                                                                            group = updatedGroup
                                                                        }
                                                                        expandedMemberId = null
                                                                    },
                                                                    onFailure = { error ->
                                                                        errorMessage = "Failed to promote member: ${error.message}"
                                                                    }
                                                                )
                                                            } catch (e: Exception) {
                                                                errorMessage = "Error: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700)) }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text("Remove Admin", color = Color.Yellow) },
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            try {
                                                                val result = apiService.demoteGroupAdmin(groupId, member.userId)
                                                                result.fold(
                                                                    onSuccess = {
                                                                        successMessage = "${member.username} is no longer admin"
                                                                        apiService.getGroupDetails(groupId).onSuccess { updatedGroup ->
                                                                            group = updatedGroup
                                                                        }
                                                                        expandedMemberId = null
                                                                    },
                                                                    onFailure = { error ->
                                                                        errorMessage = "Failed to demote member: ${error.message}"
                                                                    }
                                                                )
                                                            } catch (e: Exception) {
                                                                errorMessage = "Error: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = Color.Yellow) }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Remove Member", color = Color.Red) },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        try {
                                                            val result = apiService.removeGroupMember(groupId, member.userId)
                                                            result.fold(
                                                                onSuccess = {
                                                                    successMessage = "${member.username} removed"
                                                                    apiService.getGroupDetails(groupId).onSuccess { updatedGroup ->
                                                                        group = updatedGroup
                                                                    }
                                                                    expandedMemberId = null
                                                                },
                                                                onFailure = { error ->
                                                                    errorMessage = "Failed to remove member: ${error.message}"
                                                                }
                                                            )
                                                        } catch (e: Exception) {
                                                            errorMessage = "Error: ${e.message}"
                                                        }
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color.Red) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray)
                Text("Actions", fontWeight = FontWeight.Bold, color = Color.Gray)
                
                Button(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Filled.ExitToApp, null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Group", color = Color.Red)
                }
                
                if (isAdmin) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Group", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group", color = Color.White) },
            text = { Text("Are you sure you want to delete this group? This action cannot be undone.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { 
                        showDeleteConfirm = false
                        deleteGroup() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = accentColor)
                }
            },
            containerColor = CyberpunkTheme.DarkGray
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Group", color = Color.White) },
            text = { Text("Are you sure you want to leave this group?", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { 
                        showLeaveConfirm = false
                        leaveGroup() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel", color = accentColor)
                }
            },
            containerColor = CyberpunkTheme.DarkGray
        )
    }

    if (showAddFriends) {
        ModalBottomSheet(
            onDismissRequest = { showAddFriends = false },
            containerColor = CyberpunkTheme.DarkerGray
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp)
            ) {
                Text("Add Friends to Group", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                if (loadingFriendsForGroup) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (friendsNotInGroup.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No friends to add", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friendsNotInGroup) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedFriendsToAdd.contains(friend.userId)) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        selectedFriendsToAdd = if (selectedFriendsToAdd.contains(friend.userId)) {
                                            selectedFriendsToAdd - friend.userId
                                        } else {
                                            selectedFriendsToAdd + friend.userId
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFriendsToAdd.contains(friend.userId),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(friend.username, color = Color.White)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = { addSelectedFriends() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = selectedFriendsToAdd.isNotEmpty() && !isAddingFriendsToGroup
                    ) {
                        if (isAddingFriendsToGroup) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        else Text("Add Selected (${selectedFriendsToAdd.size})", color = Color.Black)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
