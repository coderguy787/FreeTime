@file:Suppress(
    "DEPRECATION",
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "NO_CAST_NEEDED"
)

package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import com.freetime.app.ui.screens.components.GroupMessageInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.Group
import com.freetime.app.api.GroupMessage
import com.freetime.app.api.UserData
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.theme.LocalDisplaySettings
import android.util.Log
import android.webkit.MimeTypeMap
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.freetime.app.data.network.ApiClient
import com.freetime.app.ui.composables.MessageContextMenu
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.freetime.app.utils.GroupRefreshManager  // ✅ NEW: Import global refresh manager
import com.freetime.app.ui.composables.GifPickerDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

fun extractMediaIdFromContent(content: String): String? {
    return """\[Media:\s*([^|\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
}

fun extractMediaKeyFromContent(content: String): String? {
    return """\[Media:\s*[^|\]\s]+\|([^\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
}

// ✅ NEW: Extract media filename from message content
// Format: "[Media: id|key] filename.ext" or "[Media: id] filename.ext"
fun extractMediaNameFromContent(content: String): String? {
    val match = """\]\s*(.+)""".toRegex().find(content)
    return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

fun inferMediaTypeFromName(mediaType: String?, fileName: String?): String {
    if (!mediaType.isNullOrEmpty()) return mediaType
    val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
    return when (extension) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "image"
        "mp4", "mov", "mkv", "webm" -> "video"
        "mp3", "wav", "aac", "m4a", "flac" -> "audio"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> "document"
        else -> "document"
    }
}

fun com.freetime.app.services.WebSocketManager.MediaDownloadResponseData.toMediaDownloadApproval(): com.freetime.app.api.MediaDownloadApproval? {
    if (!approved) return null
    val downloadLink = downloadUrl ?: return null
    val fileName = fileName ?: return null
    val mimeType = mimeType ?: return null

    return com.freetime.app.api.MediaDownloadApproval(
        downloadLink = downloadLink,
        mediaId = mediaId ?: "",
        fileName = fileName,
        mimeType = mimeType,
        encrypted = encrypted,
        encryptionKey = encryptionKey,
        iv = iv
    )
}

data class GroupMember(
    val id: String,
    val name: String,
    val displayName: String = "",
    val role: String = "member",
    val isOnline: Boolean = false,
    val avatarColor: Color = CyberpunkTheme.PrimaryPurple,
    val joinedDate: String = "2 months ago",
    val avatarUrl: String? = null,
    val userId: String = id,
    val isAdmin: Boolean = (role == "admin"),
    val tags: List<String> = emptyList(),
    val isSystemAdmin: Boolean = false,
    val isSystemModerator: Boolean = false
)

data class GroupInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val members: List<GroupMember> = emptyList(),
    val avatar: String = "G",
    val messageCount: Int = 0,
    val createDate: String = "",
    val isPrivate: Boolean = false,
    val isMuted: Boolean = false,
    val inviteLink: String? = null,
    val inviteCode: String? = null,
    val mutedMembers: List<Any>? = null,
    val profilePictureUrl: String? = null,
    val profilePictureThumbnailUrl: String? = null,
    val profilePictureUpdatedAt: String? = null,
    val creatorId: String = "",
    val admins: List<String> = emptyList(),
    val adminIds: List<String> = emptyList() // ✅ NEW: Add adminIds field
)

/**
 * ✅ NEW: Helper function to detect URLs in text and create AnnotatedString with clickable links
 */
@Composable
fun LinkifyText(
    text: String, 
    modifier: Modifier = Modifier, 
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(),
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val urlPattern = Regex("https?://[^\\s]+|www\\.[^\\s]+")
    val annotatedString = remember(text) {
        buildAnnotatedString {
            var lastIndex = 0
            for (result in urlPattern.findAll(text)) {
                // Add non-link text
                append(text.substring(lastIndex, result.range.first))
                
                // Add link with special styling
                val url = result.value
                val linkStart = length
                append(url)
                val linkEnd = length
                
                // Apply blue color and underline to link
                addStyle(
                    style = SpanStyle(
                        color = Color(0xFF00BFFF),
                        textDecoration = TextDecoration.Underline
                    ),
                    start = linkStart,
                    end = linkEnd
                )
                
                // Add clickable annotation for handling clicks
                addStringAnnotation(
                    tag = "URL",
                    annotation = url,
                    start = linkStart,
                    end = linkEnd
                )
                
                lastIndex = result.range.last + 1
            }
            // Add remaining text
            append(text.substring(lastIndex))
        }
    }
    
    val layoutResult = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    androidx.compose.foundation.text.BasicText(
        text = annotatedString,
        modifier = modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    val url = if (annotation.item.startsWith("http")) {
                                        annotation.item
                                    } else {
                                        "https://${annotation.item}"
                                    }
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                    android.widget.Toast.makeText(context, "Opening: $url", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open link: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                },
                onLongPress = {
                    onLongPress()
                }
            )
        },
        style = style,
        onTextLayout = { layoutResult.value = it }
    )
}

// ✅ Helper: GroupVotesOverlay (moved before GroupChatScreen for proper compilation order)
@Composable
fun GroupVotesOverlay(
    votes: List<com.freetime.app.api.GroupDeletionVote>,
    onVote: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        votes.forEach { vote ->
            Surface(
                color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LocalDisplaySettings.current.getAccentColor()),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ListAlt, null, tint = LocalDisplaySettings.current.getAccentColor(), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (vote.voteType) {
                                "clear_history", "CLEAR_HISTORY" -> "Vote: Clear Group History"
                                "delete_group", "DELETE_GROUP" -> "Vote: Delete Group"
                                else -> "Vote: ${vote.voteType.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }}"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val requiredCount = maxOf(1, vote.totalMembers / 2)
                    Text(
                        "Proposed by ${vote.initiatedByUsername}. Need ${requiredCount}/${vote.totalMembers} votes (${vote.approvalThreshold}%) - ${vote.approvingVotes} yes so far",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (vote.hasUserVoted) {
                        Text("You have already voted.", color = Color.Gray, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onVote(vote.voteId, "yes") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Green.copy(0.2f), contentColor = Color.Green),
                                border = BorderStroke(1.dp, Color.Green.copy(0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Approve") }
                            Button(
                                onClick = { onVote(vote.voteId, "no") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.2f), contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red.copy(0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Reject") }
                        }
                    }
                }
            }
        }
    }
}

// ✅ Helper Composables (moved before GroupChatScreen for proper resolution order)

@Composable
fun GroupMembersTab(members: List<GroupMember>, onMenuClick: (String) -> Unit, selectedMemberId: String?, isCurrentUserAdmin: Boolean, currentUserId: String, onKickMember: (String) -> Unit, onPromoteMember: (String) -> Unit, onDemoteMember: (String) -> Unit = {}, modifier: Modifier, creatorId: String = "", adminList: List<String> = emptyList()) {
    var expandedMemberId by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(members) { member ->
            val isMe = member.id == currentUserId
            val isGroupOwner = member.id == creatorId
            val memberIsAdmin = member.isAdmin || adminList.contains(member.id)
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (!member.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(model = member.avatarUrl, contentDescription = member.name, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
                    } else {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                            Text(member.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val displayNameToShow = member.displayName.ifBlank { member.name }  // ✅ UPDATED: Show displayName with fallback to username
                        Text(if (isMe) "${displayNameToShow} (You)" else displayNameToShow, 
                            color = getUsernameColorGroup(member.tags, member.isSystemAdmin || memberIsAdmin, member.isSystemModerator, member.role),
                            fontWeight = FontWeight.Bold)
                        // ✅ FIX: Show admin badge for all admins; show owner badge for group creator
                        if (isGroupOwner) Text("👑 Owner", color = Color(0xFFFFD700), fontSize = 10.sp)
                        else if (memberIsAdmin) Text("👑 Admin", color = Color(0xFFFFD700), fontSize = 10.sp)
                    }
                    if (isCurrentUserAdmin && !isMe) {
                        Box {
                            IconButton(onClick = { expandedMemberId = member.id }) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                            DropdownMenu(expanded = expandedMemberId == member.id, onDismissRequest = { expandedMemberId = null }) {
                                // ✅ FIX: Admins can promote members to admins
                                if (!memberIsAdmin) DropdownMenuItem(text = { Text("Make Admin") }, onClick = { onPromoteMember(member.id); expandedMemberId = null })
                                // ✅ FIX: Admins can demote other admins except the group owner
                                if (memberIsAdmin && !isGroupOwner) DropdownMenuItem(text = { Text("Remove Admin", color = Color.Yellow) }, onClick = { onDemoteMember(member.id); expandedMemberId = null })
                                DropdownMenuItem(text = { Text("Remove Member", color = Color.Red) }, onClick = { onKickMember(member.id); expandedMemberId = null })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
    fun GroupInfoTab(
        group: GroupInfo, 
        isMuted: Boolean, 
        onMuteToggle: (Boolean) -> Unit, 
        onLeaveGroup: () -> Unit, 
        onDeleteGroup: () -> Unit, 
        onUpdateGroup: (String, String) -> Unit, 
        onClearHistoryVote: () -> Unit = {},
        isStartingVote: Boolean = false,
        modifier: Modifier, 
        isAdmin: Boolean = false, 
        members: List<GroupMember> = emptyList(), 
        currentUserId: String = "", 
        creatorId: String = "", 
        onPromoteMember: (String) -> Unit = {}, 
        onDemoteMember: (String) -> Unit = {}, 
        onRemoveMember: (String) -> Unit = {},
        onInviteMembers: () -> Unit = {}
    ) {
        var name by remember(group.name) { mutableStateOf(group.name) }
        var desc by remember(group.description) { mutableStateOf(group.description) }
        val context = LocalContext.current
    
    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ✅ FIX: Only admins can change the group name
        if (isAdmin) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(value = name, onValueChange = {}, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
        // ✅ FIX: Members CAN change the group description
        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { 
                if (name.isNotBlank()) {
                    onUpdateGroup(name, desc)
                } else {
                    Toast.makeText(context, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }, 
            modifier = Modifier.fillMaxWidth(),
            // ✅ FIX: Enable save button for: admins (can change name) OR anyone with description changes
            enabled = (isAdmin || desc != group.description) && name.isNotBlank()
        ) { 
            Text("Save Changes") 
        }
        
        HorizontalDivider(color = Color.DarkGray)
        
        // ✅ NEW: Invite Members Button - Only admins can invite
        if (isAdmin) {
            Button(
                onClick = onInviteMembers, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Cyan)
            ) {
                Text("Invite Members", color = Color.Cyan)
            }
        } else {
            Button(
                onClick = {}, 
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("Invite Members (Admin only)", color = Color.Gray)
            }
        }
        
        HorizontalDivider(color = Color.DarkGray)
        
        // ✅ NEW: Clear History Vote Button (Visible to all members/admins)
        Button(
            onClick = onClearHistoryVote, 
            modifier = Modifier.fillMaxWidth(),
            enabled = !isStartingVote,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, LocalDisplaySettings.current.getAccentColor())
        ) {
            Text(if (isStartingVote) "Starting Vote..." else "Clear Group History (Vote)", color = LocalDisplaySettings.current.getAccentColor())
        }

        HorizontalDivider(color = Color.DarkGray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mute Notifications", color = Color.White)
            Switch(checked = isMuted, onCheckedChange = onMuteToggle)
        }
        Button(onClick = onLeaveGroup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, Color.Red)) {
            Text("Leave Group", color = Color.Red)
        }
        if (isAdmin) {
            Button(onClick = onDeleteGroup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, Color(0xFFFF4444))) {
                Text("Delete Group", color = Color(0xFFFF4444))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun GroupMessageInput(value: String, onValueChange: (String) -> Unit, onSendMessage: () -> Unit, onEmojiClick: () -> Unit = {}, onMediaClick: () -> Unit = {}, onGifClick: (() -> Unit)? = null, isSending: Boolean, onFocusChange: ((Boolean) -> Unit) = {}) {
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    Row(Modifier.fillMaxWidth().background(Color(0xFF0F0F1E)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMediaClick) { Icon(Icons.Default.AttachFile, null, tint = Color.Gray) }
        if (onGifClick != null) {
            IconButton(onClick = onGifClick) { Text("GIF", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray) }
        }
        IconButton(onClick = onEmojiClick) { Icon(Icons.Default.EmojiEmotions, null, tint = Color.Gray) }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).onFocusChanged { focusState -> onFocusChange(focusState.isFocused) },
            placeholder = { Text("Type a message...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A2E), unfocusedContainerColor = Color(0xFF1A1A2E), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = accentColor, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        )
        IconButton(onClick = onSendMessage, enabled = value.isNotBlank() && !isSending) {
            if(isSending) CircularProgressIndicator(Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Default.Send, null, tint = accentColor)
        }
    }
}

@Composable
fun GroupEmojiPickerRow(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😢", "😭", "😤", "😡", "😠", "🤬", "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👋", "💪", "🙏", "🎉", "🎊", "🔥", "⭐", "✨", "💫", "❤️", "🧡", "💛", "💚", "💙", "💜")
    LazyRow(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F0F1E)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(emojis) { emoji ->
            Surface(modifier = Modifier.size(36.dp).clickable { onEmojiSelected(emoji) }, color = Color.Transparent) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
            }
        }
    }
}

// ✅ NEW: Helper function to determine username color based on FreeTime role/tags
fun getUsernameColorGroup(tags: List<String>, isAdmin: Boolean, isModerator: Boolean, role: String? = null): Color {
    // Priority: OWNER tag → VIP tag → BETA TESTER tag → role=admin → role=moderator → default white
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Bright Magenta for OWNER tag
        tags.contains("VIP") -> Color(0xFFFFFF00)    // Yellow for VIP tag
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Cyan for BETA TESTER tag
        isAdmin || role == "admin" -> Color(0xFFFF0000)    // Bright Red for Admin
        isModerator || role == "moderator" -> Color(0xFFFF8C00)  // Bright Orange for Moderator
        else -> Color.White  // Default white
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onNavigateBack: () -> Unit = {},
    onGroupLeft: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToInvite: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    val retrofitService = remember { ApiClient.getInstance() }
    val prefs = remember { SharedPreferencesHelper(context) }
    val currentUserId = prefs.getUserId() ?: ""
    val currentUsername = prefs.getUsername() ?: "You"
    val token = prefs.getToken() ?: ""
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    val gifDownloaderScope = rememberCoroutineScope()

    GroupChatScreenBody(
        groupId = groupId,
        onNavigateBack = onNavigateBack,
        onGroupLeft = onGroupLeft,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToInvite = onNavigateToInvite,
        apiService = apiService
    )
}

@Composable
private fun GroupChatScreenBody(
    groupId: String,
    onNavigateBack: () -> Unit,
    onGroupLeft: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInvite: (String) -> Unit,
    apiService: FreeTimeApiService
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { SharedPreferencesHelper(context) }
    val currentUserId = prefs.getUserId() ?: ""
    val currentUsername = prefs.getUsername() ?: "You"
    val token = prefs.getToken() ?: ""
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    var selectedTab by remember { mutableStateOf("messages") }
    var messageText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMemberMenu by remember { mutableStateOf<String?>(null) }
    var mediaShareMode by remember { mutableStateOf<String?>(null) }
    var showMediaModeDialog by remember { mutableStateOf(false) }
    var showMessageContextMenu by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageText by remember { mutableStateOf("") }
    var selectedMessageIsOwn by remember { mutableStateOf(false) }
    var replyingToMessageId by remember { mutableStateOf<String?>(null) }
    var replyingToUsername by remember { mutableStateOf("") }
    var replyingToText by remember { mutableStateOf("") }
    var mediaDownloadApprovals by remember { mutableStateOf(mapOf<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData>()) }
    // ✅ NEW: Helper function to map MIME types to file extensions
    fun getFileExtensionFromMimeType(mimeType: String): String {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) return ".$extension"
        
        return when (mimeType.lowercase()) {
            // Fallbacks for common types MimeTypeMap might miss
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "video/mp4" -> ".mp4"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> ""
        }
    }

    // ✅ NEW: Helper function to ensure filename has correct extension
    fun ensureCorrectFileExtension(originalFileName: String, mimeType: String): String {
        // If filename already has an extension, preserve it
        if (originalFileName.contains('.')) {
            android.util.Log.d("FREETIME_MEDIA", "📝 Preserving original extension: $originalFileName")
            return originalFileName
        }
        
        // No extension - add one based on MIME type
        val extension = getFileExtensionFromMimeType(mimeType)
        if (extension.isEmpty()) return originalFileName
        
        val fileName = originalFileName + extension
        android.util.Log.d("FREETIME_MEDIA", "📝 File extension mapping: $originalFileName → $fileName (mimeType: $mimeType)")
        return fileName
    }

    fun extractMediaIdFromContent(content: String): String? {
        return """\[Media:\s*([^|\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
    }

    fun extractMediaKeyFromContent(content: String): String? {
        return """\[Media:\s*[^|\]\s]+\|([^\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
    }

    // ✅ NEW: Extract media filename from message content
    // Format: "[Media: id|key] filename.ext" or "[Media: id] filename.ext"
    fun extractMediaNameFromContent(content: String): String? {
        val match = """\]\s*(.+)""".toRegex().find(content)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun inferMediaTypeFromName(mediaType: String?, fileName: String?): String {
        if (!mediaType.isNullOrEmpty()) return mediaType
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        return when (extension) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "image"
            "mp4", "mov", "mkv", "webm" -> "video"
            "mp3", "wav", "aac", "m4a", "flac" -> "audio"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> "document"
            else -> "document"
        }
    }

    // Download and save media file function
    fun downloadAndSaveMediaFile(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("FREETIME_MEDIA", "🎬 Starting auto-download of approved media: ${data.mediaId}")
                
                // ✅ FIX: Ensure token is not empty before attempting download
                if (token.isEmpty()) {
                    android.util.Log.e("FREETIME_MEDIA", "❌ Cannot download media: Authentication token is empty")
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Error: Not authenticated. Please log in again.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val downloadUrl = if (data.downloadUrl?.startsWith("http") == true) {
                    data.downloadUrl
                } else if (!data.downloadUrl.isNullOrEmpty()) {
                    "${apiService.getBaseUrl().trimEnd('/')}${data.downloadUrl}"
                } else {
                    android.util.Log.e("FREETIME_MEDIA", "❌ No download URL provided in approval")
                    return@launch
                }
                
                android.util.Log.d("FREETIME_MEDIA", "📥 Download URL: $downloadUrl (with Bearer token)")
                
                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                // Create OkHttpClient with SSL bypass (same as FreeTimeApiService)
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                })
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                
                val client = okhttp3.OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    android.util.Log.e("FREETIME_MEDIA", "❌ Download failed: HTTP ${response.code} - ${response.message}")
                }
                
                val encryptedBytes = response.body?.bytes() ?: return@launch
                
                val decryptedBytes = if (data.encrypted && !data.encryptionKey.isNullOrEmpty()) {
                    try {
                        val encryptor = com.freetime.app.security.MediaEncryption(context)
                        encryptor.decryptMedia(encryptedBytes, data.encryptionKey)
                    } catch (decryptError: Exception) {
                        android.util.Log.e("FREETIME_MEDIA", "Decryption failed: ${decryptError.message}")
                        return@launch
                    }
                } else {
                    encryptedBytes
                }
                
                val mimeType = data.mimeType ?: "application/octet-stream"
                val mediaType = when {
                    mimeType.startsWith("image") -> "image"
                    mimeType.startsWith("video") -> "video"
                    mimeType.startsWith("audio") -> "audio"
                    else -> "document"
                }
                
                // ✅ FIXED: Ensure filename has correct extension based on MIME type
                val originalFileName = data.fileName ?: "media_${System.currentTimeMillis()}"
                val fileName = ensureCorrectFileExtension(originalFileName, mimeType)
                
                // ✅ FIX: Use private app storage instead of MediaStore for automatic cleanup
                val mediaDir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "FreeTimeMedia")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                
                val file = java.io.File(mediaDir, fileName)
                
                context.contentResolver.openOutputStream(
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                )?.use { outputStream ->
                    outputStream.write(decryptedBytes)
                    outputStream.flush()
                } ?: run {
                    android.util.Log.e("FREETIME_MEDIA", "Failed to open output stream")
                    return@launch
                }
                
                android.util.Log.d("FREETIME_MEDIA", "✅ Media saved to private storage: ${file.absolutePath}")
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Media saved to app storage", Toast.LENGTH_SHORT).show()
                }
                
                android.util.Log.d("FREETIME_MEDIA", "✅ Media saved to gallery: $fileName")
                coroutineScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Media saved to gallery", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_MEDIA", "Error downloading media: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to download media: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var messages by remember { mutableStateOf(listOf<GroupMessage>()) }
    var isLoadingMessages by remember { mutableStateOf(true) }
    var isLoadingMembers by remember { mutableStateOf(false) }
    var typingUsers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val typingTimeouts = remember { mutableMapOf<String, Long>() }
    
    var loadedGroup by remember { mutableStateOf(GroupInfo(id = groupId, name = "Loading...")) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var uploadProgress by remember { mutableStateOf(0f) }
    
    var isUploadingMedia by remember { mutableStateOf(false) }
    var isSendingMessage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showGroupPictureUpload by remember { mutableStateOf(false) }
    var isUploadingGroupPicture by remember { mutableStateOf(false) }
    var groupPictureUploadStatus by remember { mutableStateOf("") }
    var showShareInvite by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var friendsList by remember { mutableStateOf(listOf<UserData>()) }
    var selectedFriendsForInvite by remember { mutableStateOf(setOf<UserData>()) }
    var isInvitingSending by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    val gifDownloaderScope = rememberCoroutineScope()
    
    // ✅ NEW: Voting state
    var activeVotes by remember { mutableStateOf(listOf<com.freetime.app.api.GroupDeletionVote>()) }
    var isStartingVote by remember { mutableStateOf(false) }

    // ✅ Media picker launcher for group media sharing (supports all file types)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // Take persistable permission to prevent ENOENT on delayed access
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                coroutineScope.launch {
                    try {
                        isUploadingMedia = true
                        uploadProgress = 0f

                        // Get file info from URI
                        val fileName = com.freetime.app.utils.FileUtils.getFileNameFromUri(context, uri) ?: "media_${System.currentTimeMillis()}"
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val mediaType = when {
                            mimeType.startsWith("image/") -> "image"
                            mimeType.startsWith("video/") -> "video"
                            else -> "document"
                        }

                        // Read file data on IO dispatcher with auto-close
                        val fileData = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } ?: byteArrayOf()

                        if (fileData.isNotEmpty()) {
                            // ✅ FIX: Ensure token is not empty before uploading
                            if (token.isEmpty()) {
                                android.util.Log.e("FREETIME_GROUP_MEDIA", "❌ Cannot upload media: Authentication token is empty")
                                Toast.makeText(context, "Error: Not authenticated. Please log in again.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            
                            // ✅ NEW: Handle both public and protected media modes
                            val isPublicMedia = mediaShareMode == "public"
                            
                            if (isPublicMedia) {
                                // PUBLIC MODE: Upload WITHOUT encryption, available to all members
                                android.util.Log.d("FREETIME_GROUP_MEDIA", "📤 Uploading PUBLIC media to group (no encryption)")
                                
                                // ✅ USE: uploadPublicMediaToChat for unencrypted public media
                                val mediaId = apiService.uploadPublicMediaToChat(
                                    mediaData = fileData,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    recipientId = groupId,
                                    token = "Bearer $token",
                                    groupId = groupId
                                )

                                if (mediaId != null && mediaId.isNotEmpty()) {
                                    // Send group message with mediaId so all members can view it
                                    // Include the mediaId in the message tag so receivers can extract it from the content
                                    val mediaMessage = "[Media: $mediaId] $fileName"
                                    apiService.sendGroupMessage(groupId, mediaMessage, mediaShareMode = "public").onSuccess { groupMsg ->
                                        android.util.Log.d("FREETIME_GROUP_MEDIA", "✅ PUBLIC media message sent. mediaId=$mediaId")
                                        
                                        val msg = GroupMessage(
                                            messageId = groupMsg.messageId,
                                            groupId = groupId,
                                            senderId = currentUserId,
                                            senderUsername = currentUsername,
                                            senderAvatar = null,
                                            message = mediaMessage,
                                            timestamp = groupMsg.timestamp,
                                            mediaId = mediaId,
                                            mediaType = mediaType,
                                            mediaName = fileName,
                                            reactions = emptyMap(),
                                            pendingRequests = emptyList(),
                                            replyToMessageId = null,
                                            replyToUsername = null,
                                            replyToText = null,
                                            mediaShareMode = "public"
                                        )
                                        messages = messages + msg
                                        errorMessage = "✓ $mediaType shared publicly with group"
                                    }.onFailure { error ->
                                        android.util.Log.e("FREETIME_GROUP_MEDIA", "❌ Failed to send public media message: ${error.message}", error)
                                        errorMessage = "Failed to share media: ${error.message}"
                                    }
                                } else {
                                    errorMessage = "Media uploaded but no ID returned"
                                }
                            } else {
                                // PROTECTED MODE: Upload with encryption, requires download requests (like private chat)
                                android.util.Log.d("FREETIME_GROUP_MEDIA", "🔒 Uploading PROTECTED media to group (with encryption)")
                                
                                // Upload using uploadMediaToChat (recipientId = groupId for group uploads)
                                val uploadResult = apiService.uploadMediaToChat(
                                    mediaData = fileData,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    recipientId = groupId,  // Use group ID as recipient for group media
                                    token = "Bearer $token",
                                    groupId = groupId,
                                    mediaShareMode = "protected"
                                )

                                if (uploadResult != null) {
                                    val mediaId = uploadResult.first
                                    val encryptionKey = uploadResult.second
                                    if (mediaId.isNotEmpty()) {
                                        // Send group message with mediaId so all members are notified
                                        // ✅ FIX: Use the standard media tag format [Media: id|key] fileName
                                        val mediaMessage = "[Media: $mediaId|$encryptionKey] $fileName"
                                        apiService.sendGroupMessage(groupId, mediaMessage, mediaShareMode = "protected").onSuccess { groupMsg ->
                                            android.util.Log.d("FREETIME_GROUP_MEDIA", "✅ PROTECTED media message sent to group. mediaId=$mediaId")
                                            
                                            // Add to local chat with mediaId for display
                                            val msg = GroupMessage(
                                                messageId = groupMsg.messageId,
                                                groupId = groupId,
                                                senderId = currentUserId,
                                                senderUsername = currentUsername,
                                                senderAvatar = null,
                                                message = mediaMessage,
                                                timestamp = groupMsg.timestamp,
                                                mediaId = mediaId,
                                                mediaType = mediaType,
                                                mediaName = fileName,
                                                reactions = emptyMap(),
                                                pendingRequests = emptyList(),
                                                replyToMessageId = null,
                                                replyToUsername = null,
                                                replyToText = null,
                                                mediaShareMode = "protected"
                                            )
                                            messages = messages + msg
                                            errorMessage = "✓ $mediaType shared with download protection"
                                        }.onFailure { error ->
                                            android.util.Log.e("FREETIME_GROUP_MEDIA", "❌ Failed to send protected media message: ${error.message}", error)
                                            errorMessage = "Failed to send media message: ${error.message}"
                                        }
                                    } else {
                                        errorMessage = "Media uploaded but no ID returned"
                                    }
                                } else {
                                    errorMessage = "Failed to upload media"
                                }
                            }
                        } else {
                            errorMessage = "Failed to read selected file"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        android.util.Log.e("FREETIME_GROUP", "Media upload error: ${e.message}", e)
                    } finally {
                        isUploadingMedia = false
                        uploadProgress = 0f
                    }
                }
            }
        }
    )

    // Load full group details
    LaunchedEffect(groupId, reloadTrigger) {
        isLoadingMembers = true
        try {
            val result = apiService.getGroupDetails(groupId)
            result.onSuccess { apiGroup ->
                loadedGroup = mapToGroupInfo(groupId, apiGroup)
            }
            result.onFailure { error ->
                errorMessage = "Failed to load group: ${error.message}"
            }
        } catch (e: Exception) {
            errorMessage = "Error loading group: ${e.message}"
        } finally {
            isLoadingMembers = false
        }
    }

    // Load messages
    LaunchedEffect(groupId, reloadTrigger) {
        isLoadingMessages = true
        try {
            val result = apiService.getGroupMessages(groupId)
            result.onSuccess { apiMessages ->
                messages = apiMessages
            }
            result.onFailure { error ->
                errorMessage = "Failed to load messages: ${error.message}"
            }
        } catch (e: Exception) {
            errorMessage = "Error loading messages: ${e.message}"
        } finally {
            isLoadingMessages = false
        }
    }

    // ✅ NEW: Load active votes (with periodic refresh)
    LaunchedEffect(groupId, reloadTrigger) {
        while (true) {
            try {
                apiService.getActiveGroupVotes(groupId).onSuccess {
                    activeVotes = it
                }
            } catch (e: Exception) {
                Log.e("GROUP_CHAT", "Error loading votes: ${e.message}")
            }
            kotlinx.coroutines.delay(5000) // Refresh every 5 seconds
        }
    }

    fun addOrUpdateMessage(newMsg: GroupMessage) {
        val index = messages.indexOfFirst { it.messageId == newMsg.messageId }
        if (index != -1) {
            val existing = messages[index]
            messages = messages.toMutableList().apply {
                this[index] = newMsg.copy(
                    reactions = if (newMsg.reactions.isEmpty()) existing.reactions else newMsg.reactions,
                    replyToMessageId = newMsg.replyToMessageId?.takeIf { it != "null" } ?: existing.replyToMessageId,
                    replyToUsername = newMsg.replyToUsername?.takeIf { it != "null" } ?: existing.replyToUsername,
                    replyToText = newMsg.replyToText?.takeIf { it != "null" } ?: existing.replyToText
                )
            }
        } else {
            messages = messages + newMsg
        }
    }

    // WebSocket logic
    DisposableEffect(groupId) {
        // ✅ Set active chat ID to suppress notifications while viewing
        com.freetime.app.notifications.NotificationHelper.currentActiveChatId = groupId
        
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        try {
            val joinObj = JSONObject()
            joinObj.put("groupId", groupId)
            wsManager.send("group.join", joinObj)
        } catch (e: Exception) {}

        val listener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                coroutineScope.launch(Dispatchers.Main) {
                    // Skip own messages (already added via HTTP response) to prevent duplicates
                    if (message.groupId == groupId && message.senderId != currentUserId) {
                        // ✅ MEDIA EXTRACTION: Extract mediaId from content if present
                        // Format: [Media: mediaId|mediaKey] fileName OR [Shared type: fileName] (for public)
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val mediaMatch = mediaIdRegex.find(message.content)
                        val extractedMediaId = mediaMatch?.groupValues?.get(1)
                        
                        var extractedMediaName: String? = null
                        var extractedMediaType: String? = null
                        var extractedShareMode: String = message.mediaShareMode ?: if (mediaMatch != null && mediaMatch.groupValues.getOrNull(2).isNullOrEmpty()) "public" else "protected"

                        if (extractedMediaId != null) {
                            extractedMediaName = message.content.substringAfter("] ").takeIf { it.isNotEmpty() }
                            extractedMediaType = if (message.content.contains("video", ignoreCase = true)) "video" else "image"
                        } else if (message.content.startsWith("[Shared ")) {
                            // Public media format: [Shared type: fileName]
                            val publicRegex = """^\[Shared ([^:]+): ([^\]]+)\]""".toRegex()
                            val publicMatch = publicRegex.find(message.content)
                            if (publicMatch != null) {
                                extractedMediaType = publicMatch.groupValues[1]
                                extractedMediaName = publicMatch.groupValues[2]
                                extractedShareMode = "public"
                                // Note: mediaId should ideally be in the WebSocket payload
                                // If missing from payload, it's hard to download
                            }
                        }

                        val newMsg = GroupMessage(
                            messageId = message.messageId,
                            groupId = message.groupId,
                            senderId = message.senderId,
                            senderUsername = message.senderUsername,
                            senderAvatar = message.senderAvatar,
                            message = message.content,
                            timestamp = message.createdAt.toString(),
                            replyToMessageId = message.replyToMessageId,
                            replyToUsername = message.replyToUsername,
                            replyToText = message.replyToText,
                            mediaId = message.mediaId ?: extractedMediaId,
                            mediaType = message.mediaType ?: extractedMediaType,
                            mediaName = message.mediaName ?: extractedMediaName,
                            mediaShareMode = extractedShareMode
                        )
                        addOrUpdateMessage(newMsg)
                    }
                }
            }
            
            override fun onGroupPictureUpdated(data: com.freetime.app.services.WebSocketManager.GroupPictureUpdatedData) {
                if (data.groupId == groupId) {
                    coroutineScope.launch(Dispatchers.Main) {
                        val cacheBuster = if (data.updatedAt.isNotEmpty()) "?t=${java.net.URLEncoder.encode(data.updatedAt, "UTF-8")}" else "?t=${System.currentTimeMillis()}"
                        loadedGroup = loadedGroup.copy(
                            profilePictureUrl = data.pictureUrl + cacheBuster,
                            profilePictureUpdatedAt = data.updatedAt
                        )
                    }
                }
            }

            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {
                if (typingData.userId != currentUserId) {
                    coroutineScope.launch(Dispatchers.Main) {
                        typingUsers = typingUsers.toMutableMap().apply { this[typingData.userId] = typingData.username }
                        typingTimeouts[typingData.userId] = System.currentTimeMillis() + 3000L
                    }
                }
            }

            override fun onNotificationReceived(data: com.freetime.app.services.WebSocketManager.InternalNotificationData) {
                coroutineScope.launch(Dispatchers.Main) {
                    if (data.title == "Clear History Passed") {
                        onNavigateBack()
                        return@launch
                    }
                    reloadTrigger++
                }
            }
            
            override fun onGroupReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                if (reactionData.groupId == groupId) {
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = messages.map { msg ->
                            val cleanMsgId = if (msg.messageId.startsWith("msg_")) msg.messageId.substring(4) else msg.messageId
                            if (cleanMsgId == reactionData.messageId) {
                                val newReactions = msg.reactions.toMutableMap()
                                val users = newReactions.getOrDefault(reactionData.emoji, emptyList()).toMutableList()
                                if (!users.contains(reactionData.userId)) {
                                    users.add(reactionData.userId)
                                    newReactions[reactionData.emoji] = users
                                    msg.copy(reactions = newReactions)
                                } else msg
                            } else msg
                        }
                    }
                }
            }
            
            override fun onGroupReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                if (reactionData.groupId == groupId) {
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = messages.map { msg ->
                            val cleanMsgId = if (msg.messageId.startsWith("msg_")) msg.messageId.substring(4) else msg.messageId
                            if (cleanMsgId == reactionData.messageId) {
                                val newReactions = msg.reactions.toMutableMap()
                                val users = newReactions[reactionData.emoji]?.filterNot { it == reactionData.userId } ?: emptyList()
                                if (users.isEmpty()) {
                                    newReactions.remove(reactionData.emoji)
                                } else {
                                    newReactions[reactionData.emoji] = users
                                }
                                msg.copy(reactions = newReactions)
                            } else msg
                        }
                    }
                }
            }

            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {}
            override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {}
            override fun onMessageRead(readData: com.freetime.app.services.WebSocketManager.ReadReceiptData) {}
            override fun onConversationAllRead(readData: com.freetime.app.services.WebSocketManager.ConversationReadData) {}
            override fun onIncomingCall(callData: com.freetime.app.services.WebSocketManager.IncomingCallData) {
                com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                    context, 
                    callData.callerUsername, 
                    callData.callerId, 
                    callData.callType,
                    callId = callData.callId,
                    callerAvatarUrl = callData.callerAvatar,
                    offerSdp = callData.sdpOffer
                )
            }
            override fun onCallAnswered(callData: com.freetime.app.services.WebSocketManager.CallAnsweredData) {}
            override fun onCallRejected(callData: com.freetime.app.services.WebSocketManager.CallRejectedData) {}
            override fun onCallEnded(callData: com.freetime.app.services.WebSocketManager.CallEndedData) {}
            override fun onIceCandidate(iceData: com.freetime.app.services.WebSocketManager.IceCandidateData) {}
            override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {}
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onAvatarUpdated(data: com.freetime.app.services.WebSocketManager.AvatarUpdatedData) {}
            override fun onGroupMemberJoined(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberLeft(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberPromoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberDemoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberRemoved(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupHistoryCleared(data: com.freetime.app.services.WebSocketManager.GroupHistoryClearedData) { if(data.groupId == groupId) coroutineScope.launch { messages = emptyList(); reloadTrigger++ } }
            override fun onConnectionEstablished() {}
            override fun onConnectionLost() {}
            override fun onError(error: String) {}

            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                android.util.Log.d("FREETIME_GROUP_MEDIA", "🔔 RECEIVED onMediaDownloadRequested: mediaId=${data.mediaId}, requester=${data.requesterName}, requestId=${data.requestId}, for groupId=$groupId")
                
                coroutineScope.launch(Dispatchers.Main) {
                    var attached = false
                    messages = messages.map { msg ->
                        if (!data.mediaId.isNullOrEmpty() && msg.mediaId == data.mediaId) {
                            attached = true
                            android.util.Log.d("FREETIME_GROUP_MEDIA", "✅ Attached request to message: ${msg.messageId}")
                            msg.copy(pendingRequests = msg.pendingRequests + data)
                        } else msg
                    }

                    if (!attached) {
                        android.util.Log.w("FREETIME_GROUP_MEDIA", "⚠️ Could not find message with mediaId=${data.mediaId} in current messages")
                        // Fallback: query pending requests via REST to resolve mediaId by requestId
                        val requestIdToResolve = data.requestId
                        val apiServiceLocal = apiService
                        val dataCopy = data
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val pending = apiServiceLocal?.getPendingMediaDownloadRequests()
                                val resolved = pending?.getOrNull()?.find { it.requestId == requestIdToResolve }
                                val resolvedMediaId = resolved?.mediaId
                                if (!resolvedMediaId.isNullOrEmpty()) {
                                    android.util.Log.d("FREETIME_GROUP_MEDIA", "✅ Resolved mediaId via REST: $resolvedMediaId")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        messages = messages.map { msg ->
                                            if (msg.mediaId == resolvedMediaId) {
                                                msg.copy(pendingRequests = msg.pendingRequests + dataCopy.copy(mediaId = resolvedMediaId))
                                            } else msg
                                        }
                                    }
                                } else {
                                    android.util.Log.w("FREETIME_GROUP_MEDIA", "❌ Could not resolve mediaId via REST for requestId=$requestIdToResolve")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("FREETIME_GROUP_MEDIA", "Failed to resolve pending request via REST: ${e.message}")
                            }
                        }
                    }
                }
            }

            override fun onMediaDownloadApproved(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                android.util.Log.d("FREETIME_GROUP", "✅ Media download approved: ${data.mediaId}, encrypted=${data.encrypted}, key=${!data.encryptionKey.isNullOrEmpty()}")
                coroutineScope.launch(Dispatchers.Main) {
                    // Store approval payload for manual download actions
                    mediaDownloadApprovals = mediaDownloadApprovals + (data.mediaId to data)
                    messages = messages.map { msg ->
                        if (msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.requestId == data.requestId })
                        } else msg
                    }
                    if (data.downloadUrl != null && data.downloadUrl.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                downloadAndSaveMediaFile(data)
                                coroutineScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Downloaded", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_MEDIA", "❌ Error during auto-download after approval: ${e.message}", e)
                            }
                        }
                    }
                }
            }

            override fun onMediaDownloadDenied(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                coroutineScope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.requestId == data.requestId })
                        } else msg
                    }
                }
            }
        }
        
        wsManager.addListener(listener)
        onDispose { 
            wsManager.removeListener(listener) 
            com.freetime.app.notifications.NotificationHelper.currentActiveChatId = null
        }
    }

    // Typing timeout cleanup
    LaunchedEffect(typingUsers) {
        while (typingUsers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val expired = typingTimeouts.filter { it.value <= now }.keys
            if (expired.isNotEmpty()) {
                typingUsers = typingUsers.toMutableMap().apply { expired.forEach { remove(it); typingTimeouts.remove(it) } }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    fun toggleGroupReaction(messageId: String, emoji: String) {
        coroutineScope.launch {
            val message = messages.find { it.messageId == messageId } ?: return@launch
            val currentUsersReacted = message.reactions[emoji] ?: emptyList()
            val cleanMessageId = if (messageId.startsWith("msg_")) messageId.substring(4) else messageId
            
            if (currentUsersReacted.contains(currentUserId)) {
                apiService.removeGroupReaction(groupId, cleanMessageId, emoji).onSuccess {
                    val newReactions = message.reactions.toMutableMap()
                    val updatedUsers = newReactions[emoji]?.filterNot { it == currentUserId } ?: emptyList()
                    if (updatedUsers.isEmpty()) newReactions.remove(emoji) else newReactions[emoji] = updatedUsers
                    messages = messages.map { if (it.messageId == messageId) it.copy(reactions = newReactions) else it }
                }
            } else {
                apiService.addGroupReaction(groupId, cleanMessageId, emoji).onSuccess {
                    val newReactions = message.reactions.toMutableMap()
                    val updatedUsers = (newReactions[emoji] ?: emptyList()).toMutableList().apply { add(currentUserId) }
                    newReactions[emoji] = updatedUsers
                    messages = messages.map { if (it.messageId == messageId) it.copy(reactions = newReactions) else it }
                }
            }
        }
    }
    
    fun replyToGroupMessage(messageId: String) {
        val message = messages.find { it.messageId == messageId }
        if (message != null) {
            replyingToMessageId = messageId
            replyingToUsername = message.senderUsername
            replyingToText = message.message.take(50)
        }
    }

    val lastGroupSendTimeMs = remember { mutableStateOf(0L) }
    
    fun sendMessage() {
        if (messageText.isBlank() || isSendingMessage) return
        val now = System.currentTimeMillis()
        if (now - lastGroupSendTimeMs.value < 1500) {
            android.util.Log.d("FREETIME_CHAT", "⏱️ Group debounce: message send throttled")
            return
        }
        lastGroupSendTimeMs.value = now
        val text = messageText.trim()
        val replyToId = replyingToMessageId
        val replyUsername = replyingToUsername
        val replyText = replyingToText
        
        messageText = ""
        replyingToMessageId = null
        replyingToUsername = ""
        replyingToText = ""
        
        coroutineScope.launch {
            isSendingMessage = true
            apiService.sendGroupMessage(groupId, text, replyToId).onSuccess {
                // ✅ CRITICAL FIX: Manually populate reply fields for immediate UI feedback
                val msgWithReply = it.copy(
                    replyToMessageId = replyToId?.takeIf { id -> id.isNotEmpty() && id != "null" },
                    replyToUsername = replyUsername?.takeIf { u -> u.isNotEmpty() && u != "null" },
                    replyToText = replyText?.takeIf { t -> t.isNotEmpty() && t != "null" }
                )
                addOrUpdateMessage(msgWithReply)
            }.onFailure {
                errorMessage = "Failed to send: ${it.message}"
                messageText = text
                // Restore reply state if failed
                replyingToMessageId = replyToId
                replyingToUsername = replyUsername
                replyingToText = replyText
            }
            isSendingMessage = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CyberpunkTheme.Black, Color(0xFF0A0E27))))) {
        Column(modifier = Modifier
            .fillMaxSize()) {
            // ✅ IMPROVED: Check both admins and adminIds (server sends adminIds)
            val adminList = (loadedGroup.admins + loadedGroup.adminIds).distinct()
            val isCurrentUserAdmin = adminList.contains(currentUserId)
            
            GroupChatHeader(
                group = loadedGroup,
                onNavigateBack = onNavigateBack,
                isCurrentUserAdmin = isCurrentUserAdmin,
                onUploadClick = { showGroupPictureUpload = true },
                onShareInvite = { showShareInvite = true }
            )
            
            GroupTabBar(selectedTab, { selectedTab = it }, loadedGroup.members.size)

            when (selectedTab) {
                "messages" -> {
                    if (isLoadingMessages) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
                    } else {
                        GroupMessagesTab(
                            messages, 
                            currentUserId, 
                            token,
                            typingUsers, 
                            errorMessage, 
                            Modifier.weight(1f),
                            onMessageLongPress = { messageId, messageText, isOwn ->
                                val msg = messages.find { it.messageId == messageId }
                                val mediaId = msg?.mediaId?.takeIf { it.isNotEmpty() && it != "null" }
                                    ?: extractMediaIdFromContent(msg?.message ?: "")
                                if (msg != null && !isOwn && msg.mediaShareMode == "protected" && mediaId != null) {
                                    coroutineScope.launch {
                                        apiService.requestMediaDownload(mediaId).onSuccess {
                                            Toast.makeText(context, "Download request sent!", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Failed to send download request", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    selectedMessageId = messageId
                                    selectedMessageText = messageText
                                    selectedMessageIsOwn = isOwn
                                    showMessageContextMenu = true
                                }
                            },
                            onApproveRequest = { requestId ->
                                coroutineScope.launch {
                                    apiService.approveMediaDownloadRequest(requestId)
                                    messages = messages.map { m ->
                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                    }
                                }
                            },
                            onDenyRequest = { requestId ->
                                coroutineScope.launch {
                                    apiService.denyMediaDownloadRequest(requestId)
                                    messages = messages.map { m ->
                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                    }
                                }
                            },
                            onRequestDownload = { mediaId ->
                                coroutineScope.launch {
                                    apiService.requestMediaDownload(mediaId).onSuccess {
                                        Toast.makeText(context, "Download request sent!", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            activeVotes = activeVotes,
                            onVote = { voteId, vote ->
                                coroutineScope.launch {
                                    apiService.castClearHistoryVote(groupId, voteId, vote == "yes").onSuccess {
                                        reloadTrigger++
                                    }
                                }
                            },
                            mediaDownloadApprovals = mediaDownloadApprovals,
                            downloadAndSaveMediaFile = ::downloadAndSaveMediaFile,
                            isInputFocused = isInputFocused
                        )
                    }
                }
                "members" -> {
                    GroupMembersTab(
                        members = loadedGroup.members,
                        onMenuClick = {},
                        selectedMemberId = null,
                        isCurrentUserAdmin = isCurrentUserAdmin,
                        currentUserId = currentUserId,
                        onKickMember = { mid: String -> coroutineScope.launch {
                            apiService.removeGroupMember(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to kick member $mid", error)
                                Toast.makeText(context, "Cannot remove member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        onPromoteMember = { mid: String -> coroutineScope.launch {
                            apiService.promoteGroupAdmin(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to promote member $mid", error)
                                Toast.makeText(context, "Cannot promote member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        onDemoteMember = { mid: String -> coroutineScope.launch {
                            apiService.demoteGroupAdmin(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to demote member $mid", error)
                                Toast.makeText(context, "Cannot demote member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        modifier = Modifier.weight(1f),
                        creatorId = loadedGroup.creatorId,
                        adminList = adminList
                    )
                }
                "info" -> {
                    GroupInfoTab(
                        group = loadedGroup,
                        isMuted = isMuted,
                        onMuteToggle = { checked: Boolean -> isMuted = checked },
                        onLeaveGroup = { showLeaveConfirm = true },
                        onDeleteGroup = { showDeleteConfirm = true },
                            onUpdateGroup = { n: String, d: String -> 
                                coroutineScope.launch { 
                                    val nameToUpdate = if (isCurrentUserAdmin) n else loadedGroup.name
                                    apiService.updateGroupDetails(groupId, nameToUpdate, d, loadedGroup.isPrivate)
                                        .onSuccess { 
                                            reloadTrigger++
                                            Toast.makeText(context, "Group updated successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(context, "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } 
                            },
                        onClearHistoryVote = {
                            coroutineScope.launch {
                                isStartingVote = true
                                apiService.initiateClearHistoryVote(groupId).onSuccess {
                                    reloadTrigger++
                                    Toast.makeText(context, "Clear history vote initiated!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                                isStartingVote = false
                            }
                        },
                        isStartingVote = isStartingVote,
                        isAdmin = isCurrentUserAdmin,
                        members = loadedGroup.members,
                        currentUserId = currentUserId,
                        creatorId = loadedGroup.creatorId,
                        onPromoteMember = { mid: String -> coroutineScope.launch {
                            apiService.promoteGroupAdmin(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to promote member $mid", error)
                                Toast.makeText(context, "Cannot promote member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        onDemoteMember = { mid: String -> coroutineScope.launch {
                            apiService.demoteGroupAdmin(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to demote member $mid", error)
                                Toast.makeText(context, "Cannot demote member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        onRemoveMember = { mid: String -> coroutineScope.launch {
                            apiService.removeGroupMember(groupId, mid).onSuccess {
                                reloadTrigger++
                            }.onFailure { error ->
                                val message = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_CHAT", "Failed to remove member $mid", error)
                                Toast.makeText(context, "Cannot remove member: $message", Toast.LENGTH_LONG).show()
                            }
                        } },
                        onInviteMembers = { showInviteDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            if (selectedTab == "messages") {
                Column {
                    if (showEmojiPicker) {
                        GroupEmojiPickerRow(onEmojiSelected = { emoji: String -> messageText += emoji })
                    }
                    if (replyingToMessageId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(2.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Reply to: $replyingToUsername",
                                        fontSize = 12.sp,
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = replyingToText.take(50),
                                        fontSize = 13.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingToMessageId = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = Color.Gray)
                                }
                            }
                        }
                    }
                    GroupMessageInput(
                        value = messageText,
                        onValueChange = { messageText = it },
                        onSendMessage = { sendMessage() },
                        onEmojiClick = { showEmojiPicker = !showEmojiPicker },
                        onMediaClick = { showMediaModeDialog = true },
                        onGifClick = { showGifPicker = true },
                        isSending = isSendingMessage,
                        onFocusChange = { focused -> isInputFocused = focused }
                    )
                }
            }
        }
        
        if (showMessageContextMenu && selectedMessageId != null) {
            val selectedMsg = messages.find { it.messageId == selectedMessageId }
            val hasPublicMedia = selectedMsg?.mediaId != null && selectedMsg?.mediaShareMode == "public"
            MessageContextMenu(
                messageId = selectedMessageId ?: "",
                messageText = selectedMessageText,
                isOwnMessage = selectedMessageIsOwn,
                showMenu = showMessageContextMenu,
                onDismiss = { showMessageContextMenu = false },
                onCopy = { 
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", selectedMessageText))
                    showMessageContextMenu = false
                },
                onDelete = { },
                onReact = { emoji -> selectedMessageId?.let { toggleGroupReaction(it, emoji) } },
                onReply = { selectedMessageId?.let { replyToGroupMessage(it) } },
                onEdit = { },
                currentReactions = messages.find { it.messageId == selectedMessageId }?.reactions ?: emptyMap(),
                hasPublicMedia = hasPublicMedia,
                onDownload = {
                    selectedMsg?.mediaId?.let { mediaId ->
                        coroutineScope.launch(Dispatchers.IO) {
                            apiService.downloadAndSavePublicMedia(mediaId, selectedMsg.mediaName ?: "media", selectedMsg.mediaType ?: "image", token)
                        }
                    }
                }
            )
        }
        
        // ✅ NEW: Floating votes overlay (visible on all tabs)
        if (activeVotes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp)
            ) {
                GroupVotesOverlay(
                    votes = activeVotes,
                    onVote = { voteId: String, vote: String ->
                        coroutineScope.launch {
                            apiService.castClearHistoryVote(groupId, voteId, vote == "yes").onSuccess {
                                reloadTrigger++
                            }
                        }
                    }
                )
            }
        }
        
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text("Leave Group") },
                text = { Text("Are you sure you want to leave ${loadedGroup.name}?") },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            isLeaving = true
                            android.util.Log.d("GROUP_LEAVE", "🔴 Starting leave group API call for groupId=$groupId")
                            apiService.leaveGroup(groupId).onSuccess {
                                android.util.Log.d("GROUP_LEAVE", "✅ Leave group API succeeded! Response received")
                                // ✅ IMPROVED: Wait for backend to process, then refresh
                                kotlinx.coroutines.delay(500)  // Allow backend to process
                                android.util.Log.d("GROUP_LEAVE", "✅ 500ms delay completed, triggering refresh...")
                                GroupRefreshManager.triggerRefresh()  // Notify home screen to refresh groups
                                kotlinx.coroutines.delay(100)  // Brief delay for refresh to start
                                android.util.Log.d("GROUP_LEAVE", "✅ Refresh signal sent, navigating away...")
                                onNavigateBack()
                                onGroupLeft()
                            }.onFailure { error ->
                                val errorMsg = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_LEAVE", "❌ Failed to leave group: $errorMsg", error)
                                android.util.Log.e("GROUP_LEAVE", "Error type: ${error::class.simpleName}")
                                isLeaving = false
                                showLeaveConfirm = false
                                // ✅ NEW: Show error to user as toast
                                android.widget.Toast.makeText(
                                    context,
                                    "❌ Cannot leave: $errorMsg",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }, enabled = !isLeaving, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                        Text(if (isLeaving) "Leaving..." else "Leave")
                    }
                },
                dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } }
            )
        }
        
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Group") },
                text = { Text("Are you sure you want to permanently delete ${loadedGroup.name}? This cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            isDeleting = true
                            apiService.deleteGroup(groupId).onSuccess {
                                onNavigateBack()
                                onGroupLeft()
                            }.onFailure {
                                isDeleting = false
                            }
                        }
                    }, enabled = !isDeleting, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                        Text(if (isDeleting) "Deleting..." else "Delete")
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
            )
        }
        
        // ✅ NEW: Media sharing mode selection dialog
        if (showMediaModeDialog) {
            AlertDialog(
                onDismissRequest = { showMediaModeDialog = false },
                title = { Text("Share Media As:") },
                text = { 
                    Text(
                        "Choose how to share this media in the group:\n\n" +
                        "• PUBLIC: Shared with all members, viewable immediately\n\n" +
                        "• PROTECTED: Encrypted, sent with download requests (like private chats)"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mediaShareMode = "public"
                            showMediaModeDialog = false
                            mediaPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA00))
                    ) { 
                        Text("Public") 
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            mediaShareMode = "protected"
                            showMediaModeDialog = false
                            mediaPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA8800))
                    ) { 
                        Text("Protected") 
                    }
                }
            )
        }
        
        val clipboardManager = LocalClipboardManager.current
        if (showShareInvite) {
            val inviteCode = if (!loadedGroup.inviteCode.isNullOrEmpty() && loadedGroup.inviteCode != "undefined") loadedGroup.inviteCode else groupId
            val webLink = loadedGroup.inviteLink ?: "https://example.com/group/invite/$inviteCode"
            AlertDialog(
                onDismissRequest = { showShareInvite = false },
                title = { Text("Share Group Invite") },
                text = { Text("Join my group '${loadedGroup.name}' on FreeTime!\n\n$webLink") },
                confirmButton = {
                    Row {
                        Button(onClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Join my group '${loadedGroup.name}' on FreeTime!\n\n$webLink")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Group Invite"))
                            showShareInvite = false
                        }) { Text("Share") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(webLink))
                            Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            showShareInvite = false
                        }) { Text("Copy Link") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareInvite = false }) { Text("Cancel") }
                }
            )
        }
        
        // ✅ NEW: Invite Members Dialog
        if (showInviteDialog) {
            var isLoadingFriends by remember { mutableStateOf(true) }
            var friendsLoadError by remember { mutableStateOf("") }
            
            LaunchedEffect(Unit) {
                try {
                    isLoadingFriends = true
                    friendsLoadError = ""
                    apiService.getFriendsNotInGroup(groupId).onSuccess { friends ->
                        friendsList = friends
                        android.util.Log.d("FREETIME_INVITE", "✅ Loaded ${friends.size} friends not in group")
                        isLoadingFriends = false
                    }.onFailure { error ->
                        friendsLoadError = error.message ?: "Unknown error"
                        android.util.Log.e("FREETIME_INVITE", "❌ Failed to load friends: ${error.message}", error)
                        Toast.makeText(context, "Failed to load friends: ${error.message}", Toast.LENGTH_SHORT).show()
                        isLoadingFriends = false
                    }
                } catch (e: Exception) {
                    friendsLoadError = e.message ?: "Unknown error"
                    android.util.Log.e("FREETIME_INVITE", "❌ Exception loading friends: ${e.message}", e)
                    isLoadingFriends = false
                }
            }
            
            AlertDialog(
                onDismissRequest = { 
                    showInviteDialog = false
                    selectedFriendsForInvite = setOf()
                    friendsList = emptyList()
                },
                title = { Text("Invite Members to ${loadedGroup.name}") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        if (isLoadingFriends) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else if (friendsLoadError.isNotEmpty()) {
                            Text(
                                "Error loading friends: $friendsLoadError",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else if (friendsList.isEmpty()) {
                            Text(
                                "No friends available to invite or all friends are already in this group",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                            ) {
                                friendsList.forEach { friend ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                selectedFriendsForInvite = if (selectedFriendsForInvite.contains(friend)) {
                                                    selectedFriendsForInvite - friend
                                                } else {
                                                    selectedFriendsForInvite + friend
                                                }
                                            }
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(friend.username, color = Color.White)
                                        Checkbox(
                                            checked = selectedFriendsForInvite.contains(friend),
                                            onCheckedChange = { checked ->
                                                selectedFriendsForInvite = if (checked) {
                                                    selectedFriendsForInvite + friend
                                                } else {
                                                    selectedFriendsForInvite - friend
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedFriendsForInvite.isEmpty()) {
                                Toast.makeText(context, "Please select at least one friend", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            coroutineScope.launch {
                                isInvitingSending = true
                                val selectedUserIds = selectedFriendsForInvite.map { it.userId }
                                android.util.Log.d("FREETIME_INVITE", "📤 Sending invitations to: ${selectedFriendsForInvite.joinToString(", ") { it.username }}")
                                
                                val inviteResult = apiService.inviteToGroup(groupId, selectedUserIds)
                                inviteResult.onSuccess { result ->
                                    android.util.Log.d("FREETIME_INVITE", "✅ Invitations sent successfully: $result")
                                    Toast.makeText(context, "Invitations sent to ${selectedFriendsForInvite.size} friend${if (selectedFriendsForInvite.size != 1) "s" else ""}!", Toast.LENGTH_SHORT).show()
                                    showInviteDialog = false
                                    selectedFriendsForInvite = setOf()
                                    friendsList = emptyList()
                                }.onFailure { error ->
                                    android.util.Log.e("FREETIME_INVITE", "❌ Failed to send invitations: ${error.message}", error)
                                    Toast.makeText(context, "Failed to send invitations: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                                isInvitingSending = false
                            }
                        },
                        enabled = selectedFriendsForInvite.isNotEmpty() && !isInvitingSending && !isLoadingFriends
                    ) {
                        if (isInvitingSending) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Sending...")
                            }
                        } else {
                            Text("Invite ${selectedFriendsForInvite.size}")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showInviteDialog = false
                        selectedFriendsForInvite = setOf()
                        friendsList = emptyList()
                    }) { 
                        Text("Cancel") 
                    }
                }
            )
        }
        
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch {
                    isUploadingGroupPicture = true
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val imageBytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (imageBytes != null) {
                        val uploadResult = apiService.uploadGroupPicture(groupId, imageBytes)
                        uploadResult.onSuccess {
                            reloadTrigger++
                        }
                    }
                    isUploadingGroupPicture = false
                }
            }
        }
        
        LaunchedEffect(showGroupPictureUpload) {
            if (showGroupPictureUpload) {
                imagePickerLauncher.launch("image/*")
                showGroupPictureUpload = false
            }
        }

        GifPickerDialog(
            visible = showGifPicker,
            onDismiss = { showGifPicker = false },
            onGifSelected = { gifUrl, _ ->
                gifDownloaderScope.launch {
                    isUploadingMedia = true
                    uploadProgress = 0f
                    try {
                        val gifFile = withContext(Dispatchers.IO) {
                            downloadGroupGif(gifUrl)
                        }
                        if (gifFile != null) {
                            val fileData = withContext(Dispatchers.IO) {
                                gifFile.readBytes()
                            }
                            val fileName = "gif_${System.currentTimeMillis()}.gif"
                            val mediaId = apiService.uploadPublicMediaToChat(
                                mediaData = fileData,
                                fileName = fileName,
                                mimeType = "image/gif",
                                recipientId = groupId,
                                token = "Bearer $token",
                                groupId = groupId
                            )
                            if (mediaId != null && mediaId.isNotEmpty()) {
                                val mediaMessage = "[Media: $mediaId] $fileName"
                                apiService.sendGroupMessage(groupId, mediaMessage, mediaShareMode = "public").onSuccess { groupMsg ->
                                    addOrUpdateMessage(
                                        GroupMessage(
                                            messageId = groupMsg.messageId,
                                            groupId = groupId,
                                            senderId = currentUserId,
                                            senderUsername = currentUsername,
                                            senderAvatar = null,
                                            message = mediaMessage,
                                            timestamp = groupMsg.timestamp,
                                            mediaId = mediaId,
                                            mediaType = "image",
                                            mediaName = fileName,
                                            reactions = emptyMap(),
                                            pendingRequests = emptyList(),
                                            replyToMessageId = null,
                                            replyToUsername = null,
                                            replyToText = null,
                                            mediaShareMode = "public"
                                        )
                                    )
                                    errorMessage = "✓ GIF sent to group"
                                }.onFailure {
                                    errorMessage = "Failed to send GIF message: ${it.message}"
                                }
                            } else {
                                errorMessage = "GIF uploaded but no ID returned"
                            }
                            gifFile.delete()
                        } else {
                            Toast.makeText(context, "Failed to download GIF", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("GIF_SEND_GROUP", "Error sending GIF", e)
                        Toast.makeText(context, "Failed to send GIF: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingMedia = false
                        uploadProgress = 0f
                    }
                }
            }
        )
    }
}

@Composable
fun GroupChatHeader(group: GroupInfo, onNavigateBack: () -> Unit, isCurrentUserAdmin: Boolean, onUploadClick: () -> Unit, onShareInvite: () -> Unit) {
    val accentColor = LocalDisplaySettings.current.getAccentColor()
    Row(Modifier.fillMaxWidth().background(Color(0xFF0F0F1E)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = accentColor) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(0.2f)).border(1.dp, accentColor, CircleShape), contentAlignment = Alignment.Center) {
            if (!group.profilePictureUrl.isNullOrEmpty()) {
                AsyncImage(model = group.profilePictureUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(group.name.take(1), color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.members.size} members", color = Color.Gray, fontSize = 12.sp)
        }
        if (isCurrentUserAdmin) IconButton(onClick = onUploadClick) { Icon(Icons.Default.CameraAlt, null, tint = accentColor) }
        // ✅ FIX: Only admins can share the invite on other socials
        if (isCurrentUserAdmin) {
            IconButton(onClick = onShareInvite) { Icon(Icons.Default.Share, null, tint = accentColor) }
        }
    }
}

@Composable
fun GroupTabBar(selectedTab: String, onTabSelected: (String) -> Unit, memberCount: Int) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF0F0F1E)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Messages" to "messages", "Members ($memberCount)" to "members", "Info" to "info").forEach { (label, id) ->
            val selected = selectedTab == id
            val accentColor = LocalDisplaySettings.current.getAccentColor()
            Button(
                onClick = { onTabSelected(id) },
                colors = ButtonDefaults.buttonColors(containerColor = if(selected) accentColor.copy(0.2f) else Color.Transparent, contentColor = if(selected) accentColor else Color.Gray),
                shape = RoundedCornerShape(8.dp),
                border = if(selected) BorderStroke(1.dp, accentColor) else null,
                modifier = Modifier.height(36.dp).weight(1f)
            ) { Text(label, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupMessagesTab(
    messages: List<GroupMessage>, 
    currentUserId: String, 
    token: String,
    typingUsers: Map<String, String>, 
    error: String, 
    modifier: Modifier,
    onMessageLongPress: (messageId: String, messageText: String, isOwn: Boolean) -> Unit = { _, _, _ -> },
    onApproveRequest: (requestId: String) -> Unit = {},
    onDenyRequest: (requestId: String) -> Unit = {},
    onRequestDownload: (String) -> Unit = {},
    activeVotes: List<com.freetime.app.api.GroupDeletionVote> = emptyList(),
    onVote: (String, String) -> Unit = { _, _ -> },
    mediaDownloadApprovals: Map<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData> = emptyMap(),
    downloadAndSaveMediaFile: (com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) -> Unit = {},
    apiService: FreeTimeApiService? = null,
    isInputFocused: Boolean = false
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Scroll to bottom when new messages arrive (only if user hasn't scrolled up)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !listState.isScrollInProgress) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (e: Exception) {
                // Ignore scroll exceptions - they can happen during configuration changes
            }
        }
    }
    
    // Scroll to bottom when keyboard opens
    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (e: Exception) {
                // Ignore scroll exceptions
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(if (activeVotes.isNotEmpty()) 120.dp else 16.dp)) }
            items(messages, key = { it.messageId }) { msg ->
            val isMe = msg.senderId == currentUserId
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(msg.messageId) {
                        detectTapGestures(
                            onLongPress = {
                                onMessageLongPress(msg.messageId, msg.message, isMe)
                            }
                        )
                    },
                horizontalAlignment = if(isMe) Alignment.End else Alignment.Start
            ) {
                if (!isMe && msg.senderId != "__SYSTEM__") {
                    Text(msg.senderUsername, color = getUsernameColorGroup(msg.senderTags, msg.senderIsAdmin, msg.senderIsModerator, msg.senderRole), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Surface(
                    color = if(isMe) LocalDisplaySettings.current.getAccentColor().copy(0.2f) else Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if(isMe) LocalDisplaySettings.current.getAccentColor().copy(0.5f) else Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // ✅ FIXED: Show reply context if this message is replying to another
                        // CRITICAL: Only show reply if it's not a media message itself
                        // This prevents regular text messages after media from being shown as replies to media
                        val isMediaMessage = msg.message?.startsWith("[Media:") == true
                        if (!isMediaMessage &&
                            ((!msg.replyToMessageId.isNullOrEmpty() && msg.replyToMessageId != "null") ||
                            (!msg.replyToUsername.isNullOrEmpty() && msg.replyToUsername != "null") ||
                            (!msg.replyToText.isNullOrEmpty() && msg.replyToText != "null"))) {
                            // ✅ FIXED: Use defaults if fields are missing
                            val replyUsername = if (msg.replyToUsername.isNullOrEmpty() || msg.replyToUsername == "null") "Unknown" else msg.replyToUsername
                            val replyTextContent = if (msg.replyToText == "null" || msg.replyToText.isNullOrEmpty()) "(Message)" else msg.replyToText
                            
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clickable { 
                                        val targetId = msg.replyToMessageId
                                        if (!targetId.isNullOrEmpty() && targetId != "null") {
                                            val index = messages.indexOfFirst { it.messageId == targetId || it.messageId == "msg_$targetId" }
                                            if (index != -1) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(index)
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(LocalDisplaySettings.current.getAccentColor())
                                    )
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(
                                            text = replyUsername,
                                            fontSize = 11.sp,
                                            color = LocalDisplaySettings.current.getAccentColor(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = replyTextContent.take(100),
                                            fontSize = 12.sp,
                                            color = Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        
                        // ✅ NEW: Extract mediaId from message content if msg.mediaId is null
                        // Messages come as: "[Media: UUID|key] filename.ext" format
                        val directMediaId = msg.mediaId?.takeIf { it.isNotEmpty() && it != "null" }
                        val extractedMediaId = if (directMediaId == null) extractMediaIdFromContent(msg.message) else null
                        val messageMediaId = directMediaId ?: extractedMediaId
                        val resolvedMediaName = msg.mediaName ?: extractMediaNameFromContent(msg.message)
                        val effectiveMediaType = if (messageMediaId != null) inferMediaTypeFromName(msg.mediaType, resolvedMediaName) else null
                        
                        // ✅ CRITICAL FIX: Show media UI if we have a mediaId (either from field or extracted from content)
                        if (messageMediaId != null && effectiveMediaType != null) {
                            val context = LocalContext.current
                            val isPublicMedia = msg.mediaShareMode == "public"
                            val isImage = effectiveMediaType == "image" || resolvedMediaName?.lowercase()?.endsWith(".gif") == true
                            val isVideo = effectiveMediaType == "video"
                            
                            if (isPublicMedia && isImage) {
                                val imageUrl = "${com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')}/api/media/$messageMediaId/download"
                                Log.d("GROUP_CHAT_IMAGE", "Loading public image URL: $imageUrl, token present: ${!token.isNullOrEmpty()}")
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .addHeader("Authorization", "Bearer $token")
                                        .crossfade(true)
                                        .size(800)
                                        .build(),
                                    contentDescription = extractMediaNameFromContent(msg.message) ?: "image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    onError = { state ->
                                        Log.e("GROUP_CHAT_IMAGE", "Image load error: ${state.result.throwable?.message}")
                                    }
                                )
                            } else if (isPublicMedia) {
                                // Match private chat: compact label, no download button (only via long-press context menu)
                                val fileLabel = when {
                                    isVideo -> "🎥 Video: ${resolvedMediaName ?: "video"}"
                                    resolvedMediaName != null -> "📁 File: $resolvedMediaName"
                                    else -> "📁 File"
                                }
                                Text(
                                    text = fileLabel,
                                    color = LocalDisplaySettings.current.getAccentColor(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable {
                                            if (isMe) {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val freshApiService = FreeTimeApiService(context)
                                                        val token = SharedPreferencesHelper(context).getToken() ?: return@launch
                                                        val cleanMediaId = msg.mediaId?.takeIf { it.isNotEmpty() && it != "null" }
                                                        val extractedId = extractMediaIdFromContent(msg.message)
                                                        val mediaIdToUse = if (cleanMediaId != null && cleanMediaId != "null") cleanMediaId else extractedId
                                                        if (mediaIdToUse.isNullOrEmpty()) {
                                                            coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "❌ Media ID not found", Toast.LENGTH_SHORT).show() }
                                                            return@launch
                                                        }
                                                        val approval = mediaDownloadApprovals[mediaIdToUse]
                                                        if (approval != null && approval.approved) {
                                                            approval.toMediaDownloadApproval()?.let {
                                                                freshApiService.downloadAndDecryptApprovedMedia(it).onSuccess {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "✅ Protected media downloaded!", Toast.LENGTH_SHORT).show() }
                                                                }.onFailure {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "❌ Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                                                                }
                                                            }
                                                        } else {
                                                            val mediaKeyFromMessage = extractMediaKeyFromContent(msg.message)
                                                            if (!mediaKeyFromMessage.isNullOrEmpty()) {
                                                                var effectiveFileName = resolvedMediaName ?: "media"
                                                                if (!effectiveFileName.contains('.')) {
                                                                    val extension = when (inferMediaTypeFromName(msg.mediaType, resolvedMediaName)?.lowercase()) { "video" -> ".mp4"; "image" -> ".jpg"; else -> ".bin" }
                                                                    effectiveFileName = "$effectiveFileName$extension"
                                                                }
                                                                freshApiService.downloadMediaFile(mediaIdToUse, effectiveFileName, inferMediaTypeFromName(msg.mediaType, resolvedMediaName) ?: "application/octet-stream", mediaKeyFromMessage).onSuccess {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "✅ Media decrypted & saved!", Toast.LENGTH_SHORT).show() }
                                                                }.onFailure {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "❌ Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                                                                }
                                                            } else {
                                                                if (!isMe) {
                                                                    onRequestDownload(mediaIdToUse)
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "🔒 Request sent to file owner!", Toast.LENGTH_SHORT).show() }
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                                    }
                                                }
                                            } else {
                                                onRequestDownload(messageMediaId)
                                                coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "🔒 Request sent to file owner!", Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val mediaTypeLabel = when { isImage -> "image"; isVideo -> "video"; else -> "file" }
                                    val mediaName = resolvedMediaName ?: "$mediaTypeLabel received"
                                    val fileLabel = "$mediaTypeLabel: $mediaName"
                                    val approval = mediaDownloadApprovals[msg.mediaId]
                                    if (approval != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.InsertPhoto, null, tint = LocalDisplaySettings.current.getAccentColor(), modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(fileLabel, color = LocalDisplaySettings.current.getAccentColor(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("🔓 Approved - tap to download", color = Color.LightGray, fontSize = 9.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        downloadAndSaveMediaFile(approval)
                                                        coroutineScope.launch(Dispatchers.Main) { android.widget.Toast.makeText(context, "Downloaded", android.widget.Toast.LENGTH_SHORT).show() }
                                                    } catch (e: Exception) { android.util.Log.e("FREETIME_MEDIA", "Manual download error: ${e.message}") }
                                                }
                                            }) { Text("Download", fontSize = 12.sp) }
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Lock, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(fileLabel, color = LocalDisplaySettings.current.getAccentColor(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(if (isMe) "🔒 Protected - Tap to share" else "🔒 Protected - Tap to request", color = Color.LightGray, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (messageMediaId == null) {
                            LinkifyText(
                                text = msg.message,
                                modifier = Modifier,
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                onLongPress = { onMessageLongPress(msg.messageId, msg.message, isMe) }
                            )
                        }

                        // ✅ NEW: Show pending download requests for media (only for sender)
                        if (isMe && msg.pendingRequests.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        "Download Requests:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberpunkTheme.CyberCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    msg.pendingRequests.forEach { request ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                request.requesterName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row {
                                                IconButton(
                                                    onClick = { onApproveRequest(request.requestId) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { onDenyRequest(request.requestId) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (msg.reactions.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                msg.reactions.forEach { (emoji, users) ->
                                    val hasUserReacted = users.contains(currentUserId)
                                    Surface(
                                        color = if (hasUserReacted) LocalDisplaySettings.current.getAccentColor().copy(0.2f) else Color.White.copy(0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (hasUserReacted) LocalDisplaySettings.current.getAccentColor().copy(0.5f) else Color.White.copy(0.15f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(emoji, fontSize = 13.sp)
                                            if (users.size > 0) {
                                                Text("${users.size}", fontSize = 11.sp, color = if (hasUserReacted) Color.White else Color.White.copy(0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (typingUsers.isNotEmpty()) {
            item { Text("${typingUsers.values.joinToString()} is typing...", color = Color.Gray, fontSize = 11.sp, fontStyle = FontStyle.Italic) }
        }
    }
}

}

private fun mapToGroupInfo(groupId: String, apiGroup: Group): GroupInfo {
    fun ensureHttpsUrl(url: String?): String? {
        return url?.let {
            when {
                it.isEmpty() || it == "null" || it == "undefined" -> null
                it.startsWith("http://") || it.startsWith("https://") -> it
                it.startsWith("/") -> "https://example.com$it"
                else -> "https://example.com/$it"
            }
        }
    }

    fun sanitizeCode(code: String?): String? {
        return if (code.isNullOrEmpty() || code == "undefined" || code == "null") null else code
    }

    val cleanInviteCode = sanitizeCode(apiGroup.inviteCode)
    val cleanInviteLink = sanitizeCode(apiGroup.inviteLink)

    val finalInviteLink = when {
        !cleanInviteLink.isNullOrEmpty() && cleanInviteLink.contains("http") -> cleanInviteLink
        !sanitizeCode(apiGroup.webInviteLink).isNullOrEmpty() && sanitizeCode(apiGroup.webInviteLink)!!.contains("http") -> sanitizeCode(apiGroup.webInviteLink)
        !cleanInviteCode.isNullOrEmpty() -> "https://example.com/group/invite/$cleanInviteCode"
        else -> "https://example.com/group/invite/$groupId"
    }

    Log.d("GROUP_CHAT", "Loading group: ${apiGroup.name}, inviteCode=${cleanInviteCode ?: "NULL"}, inviteLink=$finalInviteLink")

    return GroupInfo(
        id = apiGroup.groupId,
        name = apiGroup.name,
        description = apiGroup.description,
        members = apiGroup.members.map { apiMember ->
            val adminList = (apiGroup.admins + apiGroup.adminIds).distinct()
            GroupMember(
                id = apiMember.userId,
                userId = apiMember.userId,
                name = apiMember.username,
                displayName = apiMember.displayName ?: "",
                role = if (adminList.contains(apiMember.userId)) "admin" else "member",
                avatarUrl = ensureHttpsUrl(apiMember.avatar),
                tags = apiMember.tags,
                isAdmin = adminList.contains(apiMember.userId) || apiMember.isAdmin,
                isSystemAdmin = apiMember.isSystemAdmin,
                isSystemModerator = apiMember.isSystemModerator
            )
        },
        avatar = (apiGroup.avatar ?: "G") as String,
        isPrivate = apiGroup.isPrivate,
        inviteCode = cleanInviteCode ?: "",
        inviteLink = finalInviteLink,
        profilePictureUrl = ensureHttpsUrl(apiGroup.profilePictureUrl) + if (apiGroup.profilePictureUpdatedAt != null) "?t=${java.net.URLEncoder.encode(apiGroup.profilePictureUpdatedAt, "UTF-8")}" else "",
        profilePictureThumbnailUrl = ensureHttpsUrl(apiGroup.profilePictureUrl) + if (apiGroup.profilePictureUpdatedAt != null) "?t=${java.net.URLEncoder.encode(apiGroup.profilePictureUpdatedAt, "UTF-8")}" else "",
        creatorId = apiGroup.creatorId,
        admins = apiGroup.admins,
        adminIds = apiGroup.adminIds
    )
}

private fun downloadGroupGif(gifUrl: String): File? {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(gifUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: return null
        val tempFile = File.createTempFile("gif_", ".gif")
        FileOutputStream(tempFile).use { output ->
            output.write(body.bytes())
        }
        tempFile
    } catch (e: Exception) {
        Log.e("GIF_DOWNLOAD_GROUP", "Failed to download GIF", e)
        null
    }
}

