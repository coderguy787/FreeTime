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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.animations.scaleOnPressEffect
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
import com.freetime.app.utils.GroupRefreshManager
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
    val adminIds: List<String> = emptyList()
)

// makes urls in messages clickable
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
                append(text.substring(lastIndex, result.range.first))

                val url = result.value
                val linkStart = length
                append(url)
                val linkEnd = length

                addStyle(
                    style = SpanStyle(
                        color = Color(0xFF00BFFF),
                        textDecoration = TextDecoration.Underline
                    ),
                    start = linkStart,
                    end = linkEnd
                )

                addStringAnnotation(
                    tag = "URL",
                    annotation = url,
                    start = linkStart,
                    end = linkEnd
                )

                lastIndex = result.range.last + 1
            }
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

@Composable
fun GroupMembersTab(show: Boolean = true, members: List<GroupMember>, onMenuClick: (String) -> Unit, selectedMemberId: String?, isCurrentUserAdmin: Boolean, currentUserId: String, onKickMember: (String) -> Unit, onPromoteMember: (String) -> Unit, onDemoteMember: (String) -> Unit = {}, creatorId: String = "", adminList: List<String> = emptyList()) {
    if (!show) return
    var expandedMemberId by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(members) { member ->
            val isMe = member.id == currentUserId
            val isGroupOwner = member.id == creatorId
            val memberIsAdmin = member.isAdmin || adminList.contains(member.id)
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        val memberAvatarUrl = resolveAvatarUrl(member.avatarUrl)
                        if (!memberAvatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(memberAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val displayNameToShow = member.displayName.ifBlank { member.name }
                        Text(if (isMe) "${displayNameToShow} (You)" else displayNameToShow,
                            color = getUsernameColorGroup(member.tags, member.isSystemAdmin || memberIsAdmin, member.isSystemModerator, member.role),
                            fontWeight = FontWeight.Bold)
                        if (isGroupOwner) Text(" Owner", color = Color(0xFFFFD700), fontSize = 10.sp)
                        else if (memberIsAdmin) Text(" Admin", color = Color(0xFFFFD700), fontSize = 10.sp)
                    }
                        if (isCurrentUserAdmin && !isMe && !isGroupOwner) {
                        Box {
                            IconButton(onClick = { expandedMemberId = member.id }) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                            DropdownMenu(expanded = expandedMemberId == member.id, onDismissRequest = { expandedMemberId = null }) {
                                if (!memberIsAdmin) DropdownMenuItem(text = { Text("Make Admin") }, onClick = { onPromoteMember(member.id); expandedMemberId = null })
                                if (memberIsAdmin && !isGroupOwner) DropdownMenuItem(text = { Text("Remove Admin", color = Color.Yellow) }, onClick = { onDemoteMember(member.id); expandedMemberId = null })
                                if (!isGroupOwner) DropdownMenuItem(text = { Text("Remove Member", color = Color.Red) }, onClick = { onKickMember(member.id); expandedMemberId = null })
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
    show: Boolean = true,
    group: GroupInfo,
    isMuted: Boolean,
    onMuteToggle: (Boolean) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onUpdateGroup: (String, String) -> Unit,
    onClearHistoryVote: () -> Unit = {},
    isStartingVote: Boolean = false,
    isAdmin: Boolean = false,
    members: List<GroupMember> = emptyList(),
    currentUserId: String = "",
    creatorId: String = "",
    onPromoteMember: (String) -> Unit = {},
    onDemoteMember: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    onInviteMembers: () -> Unit = {},
    currentChatBgPath: String? = null,
    onSetChatBackground: () -> Unit = {},
    onClearChatBackground: () -> Unit = {}
) {
    if (!show) return
    var name by remember(group.name) { mutableStateOf(group.name) }
    var desc by remember(group.description) { mutableStateOf(group.description) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isAdmin) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(value = name, onValueChange = {}, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
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
            enabled = (isAdmin || desc != group.description) && name.isNotBlank()
        ) {
            Text("Save Changes")
        }

        HorizontalDivider(color = Color.DarkGray)

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

        HorizontalDivider(color = Color.DarkGray)

        Button(
            onClick = onSetChatBackground,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Color(0xFF4CAF50))
        ) {
            Text(if (currentChatBgPath != null) "Change Chat Background" else "Set Chat Background", color = Color(0xFF4CAF50))
        }
        if (currentChatBgPath != null) {
            Button(
                onClick = onClearChatBackground,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFFFF9800))
            ) {
                Text("Remove Chat Background", color = Color(0xFFFF9800))
            }
        }

        Button(onClick = onLeaveGroup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, Color.Red)) {
            Text("Leave Group", color = Color.Red)
        }
        val isCreator = currentUserId == creatorId
        val isLastMember = members.size <= 1 && members.any { it.userId == currentUserId }
        if (isCreator || isLastMember) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Black,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F1E))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMediaClick,
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                Icon(Icons.Default.AttachFile, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
            if (onGifClick != null) {
                IconButton(
                    onClick = onGifClick,
                    modifier = Modifier.size(32.dp).scaleOnPressEffect()
                ) {
                    Text("GIF", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Gray)
                }
            }
            IconButton(
                onClick = onEmojiClick,
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                Icon(Icons.Default.EmojiEmotions, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
            TextField(
                value = value,
                onValueChange = { newValue ->
                    val filtered = newValue.replace("\n", "").replace("\r", "")
                    onValueChange(filtered)
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState -> onFocusChange(focusState.isFocused) },
                placeholder = { Text("Message", color = Color.Gray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A2E),
                    unfocusedContainerColor = Color(0xFF1A1A2E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accentColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                textStyle = TextStyle(fontSize = 13.sp)
            )
            IconButton(
                onClick = onSendMessage,
                enabled = value.isNotBlank() && !isSending,
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Send,
                        null,
                        tint = if (value.isNotBlank()) accentColor else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupEmojiPickerRow(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    LazyRow(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F0F1E)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(emojis) { emoji ->
            Surface(modifier = Modifier.size(36.dp).clickable { onEmojiSelected(emoji) }, color = Color.Transparent) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
            }
        }
    }
}

fun getUsernameColorGroup(tags: List<String>, isAdmin: Boolean, isModerator: Boolean, role: String? = null): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)
        tags.contains("VIP") -> Color(0xFFFFFF00)
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
        isAdmin || role == "admin" -> Color(0xFFFF0000)
        isModerator || role == "moderator" -> Color(0xFFFF8C00)
        else -> Color.White
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
    val database = remember { com.freetime.app.data.local.database.FreeTimeDatabase.getInstance(context) }
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
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var isForwarding by remember { mutableStateOf(false) }
    var replyingToMessageId by remember { mutableStateOf<String?>(null) }
    var replyingToUsername by remember { mutableStateOf("") }
    var replyingToText by remember { mutableStateOf("") }
    var mediaDownloadApprovals by remember { mutableStateOf(mapOf<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData>()) }
    fun getFileExtensionFromMimeType(mimeType: String): String {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) return ".$extension"

        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "video/mp4" -> ".mp4"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> ""
        }
    }

    fun ensureCorrectFileExtension(originalFileName: String, mimeType: String): String {
        if (originalFileName.contains('.')) {
            android.util.Log.d("FREETIME_MEDIA", " Preserving original extension: $originalFileName")
            return originalFileName
        }

        val extension = getFileExtensionFromMimeType(mimeType)
        if (extension.isEmpty()) return originalFileName

        val fileName = originalFileName + extension
        android.util.Log.d("FREETIME_MEDIA", " File extension mapping: $originalFileName $fileName (mimeType: $mimeType)")
        return fileName
    }

    fun extractMediaIdFromContent(content: String): String? {
        return """\[Media:\s*([^|\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
    }

    fun extractMediaKeyFromContent(content: String): String? {
        return """\[Media:\s*[^|\]\s]+\|([^\]\s]+)""".toRegex().find(content)?.groupValues?.get(1)
    }

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

    fun downloadAndSaveMediaFile(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("FREETIME_MEDIA", " Starting auto-download of approved media: ${data.mediaId}")

                if (token.isEmpty()) {
                    android.util.Log.e("FREETIME_MEDIA", " Cannot download media: Authentication token is empty")
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
                    android.util.Log.e("FREETIME_MEDIA", " No download URL provided in approval")
                    return@launch
                }

                android.util.Log.d("FREETIME_MEDIA", " Download URL: $downloadUrl (with Bearer token)")

                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

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
                    android.util.Log.e("FREETIME_MEDIA", " Download failed: HTTP ${response.code} - ${response.message}")
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

                val originalFileName = data.fileName ?: "media_${System.currentTimeMillis()}"
                val fileName = ensureCorrectFileExtension(originalFileName, mimeType)

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

                android.util.Log.d("FREETIME_MEDIA", " Media saved to private storage: ${file.absolutePath}")
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Media saved to app storage", Toast.LENGTH_SHORT).show()
                }

                android.util.Log.d("FREETIME_MEDIA", " Media saved to gallery: $fileName")
                coroutineScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Media saved to gallery", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_MEDIA", "Error downloading media: ${e.message}", e)
                coroutineScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to download media: ${e.message ?: "Unknown error. Please try again."}", android.widget.Toast.LENGTH_SHORT).show()
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
    var currentGroupBgPath by remember { mutableStateOf<String?>(null) }

    val groupBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bgDir = java.io.File(context.getExternalFilesDir(null), "chat_backgrounds")
                if (!bgDir.exists()) bgDir.mkdirs()
                val destFile = java.io.File(bgDir, "bg_group_${groupId}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                prefs.setChatBackgroundForUser(groupId, destFile.absolutePath)
                currentGroupBgPath = destFile.absolutePath
                Toast.makeText(context, "Chat background set!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to set background: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(prefs.isGroupMuted(groupId)) }
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

    var activeVotes by remember { mutableStateOf(listOf<com.freetime.app.api.GroupDeletionVote>()) }
    var isStartingVote by remember { mutableStateOf(false) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                coroutineScope.launch {
                    try {
                        isUploadingMedia = true
                        uploadProgress = 0f

                        val fileName = com.freetime.app.utils.FileUtils.getFileNameFromUri(context, uri) ?: "media_${System.currentTimeMillis()}"
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val mediaType = when {
                            mimeType.startsWith("image/") -> "image"
                            mimeType.startsWith("video/") -> "video"
                            else -> "document"
                        }

                        val fileData = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } ?: byteArrayOf()

                        if (fileData.isNotEmpty()) {
                            if (token.isEmpty()) {
                                android.util.Log.e("FREETIME_GROUP_MEDIA", " Cannot upload media: Authentication token is empty")
                                Toast.makeText(context, "Error: Not authenticated. Please log in again.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val isPublicMedia = mediaShareMode == "public"

                            if (isPublicMedia) {
                                android.util.Log.d("FREETIME_GROUP_MEDIA", " Uploading PUBLIC media to group (no encryption)")

                                val mediaId = apiService.uploadPublicMediaToChat(
                                    mediaData = fileData,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    recipientId = groupId,
                                    token = "Bearer $token",
                                    groupId = groupId
                                )

                                if (mediaId != null && mediaId.isNotEmpty()) {
                                    val mediaMessage = "[Media: $mediaId] $fileName"
                                    apiService.sendGroupMessage(groupId, mediaMessage, mediaShareMode = "public").onSuccess { groupMsg ->
                                        android.util.Log.d("FREETIME_GROUP_MEDIA", " PUBLIC media message sent. mediaId=$mediaId")

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
                                        errorMessage = " $mediaType shared publicly with group"
                                    }.onFailure { error ->
                                        android.util.Log.e("FREETIME_GROUP_MEDIA", " Failed to send public media message: ${error.message}", error)
                                        errorMessage = "Failed to share media: ${error.message}"
                                    }
                                } else {
                                    errorMessage = "Media uploaded but no ID returned"
                                }
                            } else {
                                android.util.Log.d("FREETIME_GROUP_MEDIA", " Uploading PROTECTED media to group (with encryption)")

                                val uploadResult = apiService.uploadMediaToChat(
                                    mediaData = fileData,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    recipientId = groupId,
                                    token = "Bearer $token",
                                    groupId = groupId,
                                    mediaShareMode = "protected"
                                )

                                if (uploadResult != null) {
                                    val mediaId = uploadResult.first
                                    val encryptionKey = uploadResult.second
                                    if (mediaId.isNotEmpty()) {
                                        val mediaMessage = "[Media: $mediaId|$encryptionKey] $fileName"
                                        apiService.sendGroupMessage(groupId, mediaMessage, mediaShareMode = "protected").onSuccess { groupMsg ->
                                            android.util.Log.d("FREETIME_GROUP_MEDIA", " PROTECTED media message sent to group. mediaId=$mediaId")

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
                                            errorMessage = " $mediaType shared with download protection"
                                        }.onFailure { error ->
                                            android.util.Log.e("FREETIME_GROUP_MEDIA", " Failed to send protected media message: ${error.message}", error)
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

    LaunchedEffect(groupId) {
        currentGroupBgPath = prefs.getChatBackgroundForUser(groupId)
    }

    // load group info and members
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

    LaunchedEffect(groupId, reloadTrigger) {
        while (true) {
            try {
                apiService.getActiveGroupVotes(groupId).onSuccess {
                    activeVotes = it
                }
            } catch (e: Exception) {
                Log.e("GROUP_CHAT", "Error loading votes: ${e.message}")
            }
            kotlinx.coroutines.delay(5000)
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

    DisposableEffect(groupId) {
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
                    if (message.groupId == groupId && message.senderId != currentUserId) {
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
                            val publicRegex = """^\[Shared ([^:]+): ([^\]]+)\]""".toRegex()
                            val publicMatch = publicRegex.find(message.content)
                            if (publicMatch != null) {
                                extractedMediaType = publicMatch.groupValues[1]
                                extractedMediaName = publicMatch.groupValues[2]
                                extractedShareMode = "public"
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
            override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {}
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onAvatarUpdated(data: com.freetime.app.services.WebSocketManager.AvatarUpdatedData) {}
            override fun onGroupMemberJoined(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberLeft(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberPromoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberDemoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupMemberRemoved(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) { if(data.groupId == groupId) coroutineScope.launch { reloadTrigger++ } }
            override fun onGroupHistoryCleared(data: com.freetime.app.services.WebSocketManager.GroupHistoryClearedData) {
                if(data.groupId == groupId) coroutineScope.launch {
                    messages = emptyList()
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        database.messageDao().deleteAllMessagesForChat(groupId)
                    }
                    reloadTrigger++
                }
            }
            override fun onGroupMessageDeleted(data: com.freetime.app.services.WebSocketManager.GroupMessageDeletedData) {
                if (data.groupId == groupId) {
                    coroutineScope.launch {
                        messages = messages.filterNot { it.messageId == data.messageId }
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        database.messageDao().deleteMessageById(data.messageId)
                    }
                }
            }
            override fun onConnectionEstablished() {}
            override fun onConnectionLost() {}
            override fun onError(error: String) {}

            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                android.util.Log.d("FREETIME_GROUP_MEDIA", " RECEIVED onMediaDownloadRequested: mediaId=${data.mediaId}, requester=${data.requesterName}, requestId=${data.requestId}, for groupId=$groupId")

                coroutineScope.launch(Dispatchers.Main) {
                    var attached = false
                    messages = messages.map { msg ->
                        if (!data.mediaId.isNullOrEmpty() && msg.mediaId == data.mediaId) {
                            attached = true
                            android.util.Log.d("FREETIME_GROUP_MEDIA", " Attached request to message: ${msg.messageId}")
                            msg.copy(pendingRequests = msg.pendingRequests + data)
                        } else msg
                    }

                    if (!attached) {
                        android.util.Log.w("FREETIME_GROUP_MEDIA", " Could not find message with mediaId=${data.mediaId} in current messages")
                        val requestIdToResolve = data.requestId
                        val apiServiceLocal = apiService
                        val dataCopy = data
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val pending = apiServiceLocal?.getPendingMediaDownloadRequests()
                                val resolved = pending?.getOrNull()?.find { it.requestId == requestIdToResolve }
                                val resolvedMediaId = resolved?.mediaId
                                if (!resolvedMediaId.isNullOrEmpty()) {
                                    android.util.Log.d("FREETIME_GROUP_MEDIA", " Resolved mediaId via REST: $resolvedMediaId")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        messages = messages.map { msg ->
                                            if (msg.mediaId == resolvedMediaId) {
                                                msg.copy(pendingRequests = msg.pendingRequests + dataCopy.copy(mediaId = resolvedMediaId))
                                            } else msg
                                        }
                                    }
                                } else {
                                    android.util.Log.w("FREETIME_GROUP_MEDIA", " Could not resolve mediaId via REST for requestId=$requestIdToResolve")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("FREETIME_GROUP_MEDIA", "Failed to resolve pending request via REST: ${e.message}")
                            }
                        }
                    }
                }
            }

            override fun onMediaDownloadApproved(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                android.util.Log.d("FREETIME_GROUP", " Media download approved: ${data.mediaId}, encrypted=${data.encrypted}, key=${!data.encryptionKey.isNullOrEmpty()}")
                coroutineScope.launch(Dispatchers.Main) {
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
                                android.util.Log.e("FREETIME_MEDIA", " Error during auto-download after approval: ${e.message}", e)
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

    // typing indicator handling
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
            android.util.Log.d("FREETIME_CHAT", " Group debounce: message send throttled")
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
                val msgWithReply = it.copy(
                    replyToMessageId = replyToId?.takeIf { id -> id.isNotEmpty() && id != "null" },
                    replyToUsername = replyUsername?.takeIf { u -> u.isNotEmpty() && u != "null" },
                    replyToText = replyText?.takeIf { t -> t.isNotEmpty() && t != "null" }
                )
                addOrUpdateMessage(msgWithReply)
            }.onFailure {
                errorMessage = "Failed to send: ${it.message}"
                messageText = text
                replyingToMessageId = replyToId
                replyingToUsername = replyUsername
                replyingToText = replyText
            }
            isSendingMessage = false
        }
    }

    val chatBgPath = currentGroupBgPath

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CyberpunkTheme.Black, Color(0xFF0A0E27))))) {
        if (chatBgPath != null) {
            val bgFile = java.io.File(chatBgPath)
            if (bgFile.exists()) {
                AsyncImage(
                    model = bgFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
            }
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
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

            Column(modifier = Modifier.weight(1f)) {
                GroupMessagesTab(
                show = selectedTab == "messages",
                messages = messages,
                currentUserId = currentUserId,
                token = token,
                typingUsers = typingUsers,
                errorMessage = errorMessage,
                isLoadingMessages = isLoadingMessages,
                accentColor = accentColor,
                onMessageLongPress = { messageId, messageText, isOwn ->
                    if (isMultiSelectMode) {
                        selectedMessages = if (selectedMessages.contains(messageId)) {
                            selectedMessages - messageId
                        } else {
                            selectedMessages + messageId
                        }
                        if (selectedMessages.isEmpty()) isMultiSelectMode = false
                    } else {
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
                            isMultiSelectMode = true
                            selectedMessages = setOf(messageId)
                        }
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
                isInputFocused = isInputFocused,
                isMultiSelectMode = isMultiSelectMode,
                selectedMessages = selectedMessages,
                onToggleSelection = { msgId ->
                    selectedMessages = if (selectedMessages.contains(msgId)) {
                        selectedMessages - msgId
                    } else {
                        selectedMessages + msgId
                    }
                    if (selectedMessages.isEmpty()) isMultiSelectMode = false
                },
                onClearMultiSelect = {
                    isMultiSelectMode = false
                    selectedMessages = emptySet()
                },
                onForward = {
                    coroutineScope.launch {
                        try {
                            val result = apiService.getFriends()
                            result.onSuccess { friends ->
                                friendsList = friends
                                showForwardDialog = true
                            }.onFailure {
                                Toast.makeText(context, "Failed to load friends", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to load friends", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDeleteSelected = {
                    coroutineScope.launch {
                        var deleted = 0
                        for (msgId in selectedMessages) {
                            apiService.deleteGroupMessage(groupId, msgId).onSuccess {
                                messages = messages.filterNot { it.messageId == msgId }
                                kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    database.messageDao().deleteMessageById(msgId)
                                }
                                deleted++
                            }
                        }
                        if (deleted > 0) {
                            Toast.makeText(context, "$deleted message(s) deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                        isMultiSelectMode = false
                        selectedMessages = emptySet()
                    }
                },
                allSelectedAreOwn = selectedMessages.isNotEmpty() && messages.filter { it.messageId in selectedMessages }.all { it.senderId == currentUserId }
            )

            GroupMembersTab(
                show = selectedTab == "members",
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
                creatorId = loadedGroup.creatorId,
                adminList = adminList
            )

            GroupInfoTab(
                show = selectedTab == "info",
                group = loadedGroup,
                isMuted = isMuted,
                onMuteToggle = { checked: Boolean ->
                    isMuted = checked
                    if (checked) prefs.muteGroup(groupId)
                    else prefs.unmuteGroup(groupId)
                },
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
                        android.util.Log.e("GROUP_CHAT", "Cannot remove member $mid", error)
                        Toast.makeText(context, "Cannot remove member: $message", Toast.LENGTH_LONG).show()
                    }
                } },
                onInviteMembers = { showInviteDialog = true },
                currentChatBgPath = currentGroupBgPath,
                onSetChatBackground = { groupBgPickerLauncher.launch("image/*") },
                onClearChatBackground = {
                    prefs.clearChatBackgroundForUser(groupId)
                    currentGroupBgPath = null
                    Toast.makeText(context, "Chat background removed", Toast.LENGTH_SHORT).show()
                }
                )
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
                onDelete = {
                    selectedMessageId?.let { msgId ->
                        coroutineScope.launch {
                            apiService.deleteGroupMessage(groupId, msgId).onSuccess {
                                messages = messages.filterNot { it.messageId == msgId }
                                kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    database.messageDao().deleteMessageById(msgId)
                                }
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    showMessageContextMenu = false
                },
                onReact = { emoji -> selectedMessageId?.let { toggleGroupReaction(it, emoji) } },
                onReply = { selectedMessageId?.let { replyToGroupMessage(it) } },
                onEdit = { },
                onSelect = {
                    selectedMessageId?.let { msgId ->
                        isMultiSelectMode = true
                        selectedMessages = setOf(msgId)
                    }
                    showMessageContextMenu = false
                },
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

        if (showForwardDialog) {
            val selectedMessagesContent = messages
                .filter { it.messageId in selectedMessages }
                .sortedByDescending { it.timestamp }
            AlertDialog(
                onDismissRequest = { showForwardDialog = false },
                title = {
                    Text(
                        "Forward ${selectedMessagesContent.size} message(s) to:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (friendsList.isEmpty()) {
                            Text(
                                "No friends found. Add friends first.",
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            friendsList.forEach { friend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isForwarding) return@clickable
                                            isForwarding = true
                                            coroutineScope.launch {
                                                var successCount = 0
                                                selectedMessagesContent.forEach { msg ->
                                                    try {
                                                        val fwdContent = "\uD83D\uDD01 Forwarded from ${msg.senderUsername}:\n\n${msg.message}"
                                                        apiService.sendGroupMessage(groupId, fwdContent).onSuccess { successCount++ }
                                                    } catch (_: Exception) {}
                                                }
                                                isForwarding = false
                                                showForwardDialog = false
                                                isMultiSelectMode = false
                                                selectedMessages = emptySet()
                                                val toastMsg = if (successCount > 0)
                                                    "Forwarded $successCount message(s) to group"
                                                else
                                                    "Failed to forward messages"
                                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = apiService.resolveAvatarUrl(friend.avatar),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            friend.name.ifEmpty { friend.username },
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                        if (friend.name.isNotEmpty() && friend.username.isNotEmpty()) {
                                            Text(
                                                "@${friend.username}",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    if (isForwarding) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF9D4EDD),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showForwardDialog = false }) {
                        Text("Cancel", color = Color(0xFF9D4EDD))
                    }
                },
                containerColor = Color(0xFF1A1A2E),
                textContentColor = Color.White,
                titleContentColor = Color(0xFF9D4EDD)
            )
        }

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
                            android.util.Log.d("GROUP_LEAVE", " Starting leave group API call for groupId=$groupId")
                            apiService.leaveGroup(groupId).onSuccess {
                                android.util.Log.d("GROUP_LEAVE", " Leave group API succeeded! Response received")
                                kotlinx.coroutines.delay(500)
                                android.util.Log.d("GROUP_LEAVE", " 500ms delay completed, triggering refresh...")
                                GroupRefreshManager.triggerRefresh()
                                kotlinx.coroutines.delay(100)
                                android.util.Log.d("GROUP_LEAVE", " Refresh signal sent, navigating away...")
                                onNavigateBack()
                                onGroupLeft()
                            }.onFailure { error ->
                                val errorMsg = error.message ?: "Unknown error"
                                android.util.Log.e("GROUP_LEAVE", " Failed to leave group: $errorMsg", error)
                                android.util.Log.e("GROUP_LEAVE", "Error type: ${error::class.simpleName}")
                                isLeaving = false
                                showLeaveConfirm = false
                                android.widget.Toast.makeText(
                                    context,
                                    " Cannot leave: $errorMsg",
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
            val deepLink = "freetime://group/invite/$groupId"
            var webLink by remember { mutableStateOf("https://freetime.app/group/invite/${loadedGroup.inviteCode ?: groupId}") }
            var isLoadingLink by remember { mutableStateOf(true) }

            LaunchedEffect(showShareInvite) {
                isLoadingLink = true
                try {
                    val result = apiService.generateExpiringInviteLink(groupId, 3600000L)
                    result.onSuccess { link ->
                        webLink = link.shareLink.ifEmpty { "https://freetime.app/group/invite/${loadedGroup.inviteCode ?: groupId}" }
                    }
                } catch (_: Exception) {}
                isLoadingLink = false
            }

            AlertDialog(
                onDismissRequest = { showShareInvite = false },
                title = { Text("Share Group Invite") },
                text = {
                    Column {
                        Text("Join my group '${loadedGroup.name}' on FreeTime!", color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = deepLink,
                                color = Color(0xFF00D4FF),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        if (!isLoadingLink) {
                            Spacer(Modifier.height(4.dp))
                            Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = webLink,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        Button(onClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Join my group '${loadedGroup.name}' on FreeTime!\n\n$deepLink\n\n$webLink")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Group Invite"))
                            showShareInvite = false
                        }) { Text("Share") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString("$deepLink\n\n$webLink"))
                            Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            showShareInvite = false
                        }) { Text("Copy Link") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareInvite = false }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1A1A2E),
                textContentColor = Color.White,
                titleContentColor = Color(0xFF00D4FF)
            )
        }

        if (showInviteDialog) {
            var isLoadingFriends by remember { mutableStateOf(true) }
            var friendsLoadError by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                try {
                    isLoadingFriends = true
                    friendsLoadError = ""
                    apiService.getFriendsNotInGroup(groupId).onSuccess { friends ->
                        friendsList = friends
                        android.util.Log.d("FREETIME_INVITE", " Loaded ${friends.size} friends not in group")
                        isLoadingFriends = false
                    }.onFailure { error ->
                        friendsLoadError = error.message ?: "Unknown error"
                        android.util.Log.e("FREETIME_INVITE", " Failed to load friends: ${error.message}", error)
                        Toast.makeText(context, "Failed to load friends: ${error.message}", Toast.LENGTH_SHORT).show()
                        isLoadingFriends = false
                    }
                } catch (e: Exception) {
                    friendsLoadError = e.message ?: "Unknown error"
                    android.util.Log.e("FREETIME_INVITE", " Exception loading friends: ${e.message}", e)
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
                                android.util.Log.d("FREETIME_INVITE", " Sending invitations to: ${selectedFriendsForInvite.joinToString(", ") { it.username }}")

                                val inviteResult = apiService.inviteToGroup(groupId, selectedUserIds)
                                inviteResult.onSuccess { result ->
                                    android.util.Log.d("FREETIME_INVITE", " Invitations sent successfully: $result")
                                    Toast.makeText(context, "Invitations sent to ${selectedFriendsForInvite.size} friend${if (selectedFriendsForInvite.size != 1) "s" else ""}!", Toast.LENGTH_SHORT).show()
                                    showInviteDialog = false
                                    selectedFriendsForInvite = setOf()
                                    friendsList = emptyList()
                                }.onFailure { error ->
                                    android.util.Log.e("FREETIME_INVITE", " Failed to send invitations: ${error.message}", error)
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
                                    errorMessage = " GIF sent to group"
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
        IconButton(onClick = onNavigateBack, modifier = Modifier.scaleOnPressEffect()) { Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = accentColor) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(0.2f)).border(1.dp, accentColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(group.name.take(1), color = accentColor, fontWeight = FontWeight.Bold)
            val groupPictureUrl = resolveAvatarUrl(group.profilePictureUrl)
            if (!groupPictureUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(groupPictureUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.members.size} members", color = Color.Gray, fontSize = 12.sp)
        }
        if (isCurrentUserAdmin) IconButton(onClick = onUploadClick) { Icon(Icons.Default.CameraAlt, null, tint = accentColor) }
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
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(36.dp).weight(1f)
            ) { Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupMessagesTab(
    show: Boolean = true,
    messages: List<GroupMessage>,
    currentUserId: String,
    token: String,
    typingUsers: Map<String, String>,
    errorMessage: String,
    isLoadingMessages: Boolean = false,
    accentColor: Color = Color.Cyan,
    onMessageLongPress: (messageId: String, messageText: String, isOwn: Boolean) -> Unit = { _, _, _ -> },
    onApproveRequest: (requestId: String) -> Unit = {},
    onDenyRequest: (requestId: String) -> Unit = {},
    onRequestDownload: (String) -> Unit = {},
    activeVotes: List<com.freetime.app.api.GroupDeletionVote> = emptyList(),
    onVote: (String, String) -> Unit = { _, _ -> },
    mediaDownloadApprovals: Map<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData> = emptyMap(),
    downloadAndSaveMediaFile: (com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) -> Unit = {},
    isInputFocused: Boolean = false,
    isMultiSelectMode: Boolean = false,
    selectedMessages: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    onClearMultiSelect: () -> Unit = {},
    onForward: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    allSelectedAreOwn: Boolean = false
) {
    if (!show) return
    if (isLoadingMessages) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
        return
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !listState.isScrollInProgress) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (e: Exception) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(if (activeVotes.isNotEmpty() || isMultiSelectMode) 120.dp else 16.dp)) }
            items(messages, key = { it.messageId }) { msg ->
            val isMe = msg.senderId == currentUserId
            val isSelected = selectedMessages.contains(msg.messageId)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) Color(0xFF9D4EDD).copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .pointerInput(msg.messageId) {
                        detectTapGestures(
                            onTap = {
                                if (isMultiSelectMode) {
                                    onToggleSelection(msg.messageId)
                                }
                            },
                            onLongPress = {
                                onMessageLongPress(msg.messageId, msg.message, isMe)
                            }
                        )
                    },
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection(msg.messageId) },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF9D4EDD))
                    )
                }
                if (!isMe && msg.senderId != "__SYSTEM__") {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9D4EDD).copy(alpha = 0.3f))
                            .border(1.dp, Color(0xFF9D4EDD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(msg.senderUsername.firstOrNull()?.toString() ?: "?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val senderAvatarUrl = resolveAvatarUrl(msg.senderAvatar)
                        if (!senderAvatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(senderAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = msg.senderUsername,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Column(
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
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
                        val isMediaMessage = msg.message?.startsWith("[Media:") == true
                        if (!isMediaMessage &&
                            ((!msg.replyToMessageId.isNullOrEmpty() && msg.replyToMessageId != "null") ||
                            (!msg.replyToUsername.isNullOrEmpty() && msg.replyToUsername != "null") ||
                            (!msg.replyToText.isNullOrEmpty() && msg.replyToText != "null"))) {
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

                        val directMediaId = msg.mediaId?.takeIf { it.isNotEmpty() && it != "null" }
                        val extractedMediaId = if (directMediaId == null) extractMediaIdFromContent(msg.message) else null
                        val messageMediaId = directMediaId ?: extractedMediaId
                        val resolvedMediaName = msg.mediaName ?: extractMediaNameFromContent(msg.message)
                        val effectiveMediaType = if (messageMediaId != null) inferMediaTypeFromName(msg.mediaType, resolvedMediaName) else null

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
                                val fileLabel = when {
                                    isVideo -> " Video: ${resolvedMediaName ?: "video"}"
                                    resolvedMediaName != null -> " File: $resolvedMediaName"
                                    else -> " File"
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
                                                            coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Media ID not found", Toast.LENGTH_SHORT).show() }
                                                            return@launch
                                                        }
                                                        val approval = mediaDownloadApprovals[mediaIdToUse]
                                                        if (approval != null && approval.approved) {
                                                            approval.toMediaDownloadApproval()?.let {
                                                                freshApiService.downloadAndDecryptApprovedMedia(it).onSuccess {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Protected media downloaded!", Toast.LENGTH_SHORT).show() }
                                                                }.onFailure {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
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
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Media decrypted & saved!", Toast.LENGTH_SHORT).show() }
                                                                }.onFailure {
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                                                                }
                                                            } else {
                                                                if (!isMe) {
                                                                    onRequestDownload(mediaIdToUse)
                                                                    coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Request sent to file owner!", Toast.LENGTH_SHORT).show() }
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                                    }
                                                }
                                            } else {
                                                onRequestDownload(messageMediaId)
                                                coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, " Request sent to file owner!", Toast.LENGTH_SHORT).show() }
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
                                            Text(" Approved - tap to download", color = Color.LightGray, fontSize = 9.sp)
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
                                            Text(if (isMe) " Protected - Tap to share" else " Protected - Tap to request", color = Color.LightGray, fontSize = 9.sp)
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
        }
        if (typingUsers.isNotEmpty()) {
            item { Text("${typingUsers.values.joinToString()} is typing...", color = Color.Gray, fontSize = 11.sp, fontStyle = FontStyle.Italic) }
        }
        }

        if (isMultiSelectMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = Color(0xFF1A1A2E),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = onClearMultiSelect) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            "${selectedMessages.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = onForward) {
                            Icon(Icons.Default.Forward, "Forward", tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(22.dp))
                        }
                        if (allSelectedAreOwn) {
                            IconButton(onClick = onDeleteSelected) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveAvatarUrl(url: String?): String? {
    if (url.isNullOrEmpty() || url == "null" || url == "undefined") return null
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "${com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')}$url"
        else -> "${com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')}/$url"
    }
}

private fun mapToGroupInfo(groupId: String, apiGroup: Group): GroupInfo {
    fun sanitizeCode(code: String?): String? {
        return if (code.isNullOrEmpty() || code == "undefined" || code == "null") null else code
    }

    val cleanInviteCode = sanitizeCode(apiGroup.inviteCode)
    val cleanInviteLink = sanitizeCode(apiGroup.inviteLink)

    val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
    val finalInviteLink = when {
        !cleanInviteLink.isNullOrEmpty() && cleanInviteLink.contains("http") -> cleanInviteLink
        !sanitizeCode(apiGroup.webInviteLink).isNullOrEmpty() && sanitizeCode(apiGroup.webInviteLink)!!.contains("http") -> sanitizeCode(apiGroup.webInviteLink)
        !cleanInviteCode.isNullOrEmpty() -> "$baseUrl/api/groups/web/invite/$cleanInviteCode"
        else -> "$baseUrl/api/groups/web/invite/$groupId"
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
                avatarUrl = resolveAvatarUrl(apiMember.avatar),
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
        profilePictureUrl = resolveAvatarUrl(apiGroup.profilePictureUrl)?.let { resolved ->
            if (apiGroup.profilePictureUpdatedAt != null) "$resolved?t=${java.net.URLEncoder.encode(apiGroup.profilePictureUpdatedAt, "UTF-8")}" else resolved
        } ?: "",
        profilePictureThumbnailUrl = resolveAvatarUrl(apiGroup.profilePictureUrl)?.let { resolved ->
            if (apiGroup.profilePictureUpdatedAt != null) "$resolved?t=${java.net.URLEncoder.encode(apiGroup.profilePictureUpdatedAt, "UTF-8")}" else resolved
        } ?: "",
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

