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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.UserData
import com.freetime.app.ui.components.CyberpunkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Contact(
    val id: String,
    val name: String,
    val username: String,
    val status: ContactStatus,
    val avatar: String? = null,
    val lastSeen: String = "2 min ago"
)

enum class ContactStatus {
    Online, Away, DND, Offline
}

@Composable
fun ContactsScreenEnhanced(
    onBackClick: () -> Unit = {},
    onContactClick: (contactId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedTab by remember { mutableStateOf("CONTACTS") }
    var friendRequestSent by remember { mutableStateOf(false) }
    var friendRequestUsername by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<UserData>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    // Load contacts on screen open
    LaunchedEffect(Unit) {
        scope.launch {
            apiService.getFriends().onSuccess { friends ->
                contacts = friends.map { user ->
                    // Map online status from API: isOnline -> ContactStatus
                    val contactStatus = when {
                        user.isOnline -> ContactStatus.Online  // Real online status
                        user.actualOnlineStatus == "occupied" -> ContactStatus.DND
                        else -> ContactStatus.Offline
                    }
                    Contact(
                        id = user.userId,
                        name = user.name,
                        username = user.username,
                        status = contactStatus,  // Use REAL online status
                        avatar = user.avatar,
                        lastSeen = if (user.isOnline) "Online now" else (user.lastSeen ?: "Offline")
                    )
                }
                isLoading = false
            }.onFailure { error ->
                errorMessage = "Failed to load contacts: ${error.message}"
                isLoading = false
            }
        }
    }

    // Search users when query changes
    LaunchedEffect(searchQuery) {
        if (selectedTab == "ADD_FRIENDS" && searchQuery.isNotEmpty()) {
            scope.launch {
                apiService.searchUsers(searchQuery).onSuccess { results ->
                    searchResults = results.filter { user ->
                        !contacts.any { it.username == user.username }
                    }
                }.onFailure { error ->
                    errorMessage = "Search failed: ${error.message}"
                    searchResults = emptyList()
                }
            }
        } else {
            searchResults = emptyList()
        }
    }

    val filteredContacts = contacts.filter { contact ->
        contact.name.contains(searchQuery, ignoreCase = true) && when (selectedCategory) {
            "ONLINE" -> contact.status == ContactStatus.Online
            "OFFLINE" -> contact.status == ContactStatus.Offline
            else -> true
        }
    }

    val onlineCount = contacts.count { it.status == ContactStatus.Online }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // HEADER
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(CyberpunkTheme.DarkGray),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        "Contacts",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CyberpunkTheme.White,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = CircleShape,
                            color = CyberpunkTheme.PrimaryPurple
                        ) {}
                        Text(
                            onlineCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.PrimaryPurple,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // TAB NAVIGATION
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(CyberpunkTheme.DarkGray.copy(alpha = 0.5f)),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("CONTACTS" to Icons.Filled.People, "ADD_FRIENDS" to Icons.Filled.PersonAdd).forEach { (tab, icon) ->
                        Surface(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = tab }
                                .border(
                                    width = if (selectedTab == tab) 2.dp else 1.dp,
                                    color = if (selectedTab == tab) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.MediumGray.copy(
                                        alpha = 0.3f
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            color = if (selectedTab == tab) CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.2f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = tab,
                                    tint = if (selectedTab == tab) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.GhostGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    tab,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (selectedTab == tab) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.GhostGray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SEARCH BAR
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = CyberpunkTheme.PrimaryMagenta,
                        modifier = Modifier.size(18.dp)
                    )

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (selectedTab == "CONTACTS") "Search contacts..." else "Search by username...",
                                color = CyberpunkTheme.GhostGray,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = CyberpunkTheme.White
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = CyberpunkTheme.PrimaryMagenta,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CATEGORY TABS (Only for CONTACTS tab)
            if (selectedTab == "CONTACTS") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "ONLINE", "OFFLINE").forEach { category ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedCategory = category }
                                .border(
                                    width = if (selectedCategory == category) 2.dp else 1.dp,
                                    color = if (selectedCategory == category) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.DarkGray,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selectedCategory == category) CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f) else Color.Transparent
                        ) {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 0.6.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (selectedCategory == category) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.GhostGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // CONTENT BASED ON TAB
            if (selectedTab == "CONTACTS") {
                ContactsContent(filteredContacts, onContactClick)
            } else {
                AddFriendsContent(
                    searchResults, searchQuery, isLoading
                ) { user ->
                    scope.launch {
                        apiService.sendFriendRequest(user.username)
                            .onSuccess {
                                friendRequestUsername = user.username
                                friendRequestSent = true
                            }
                            .onFailure { error ->
                                errorMessage = error.message ?: "Failed to send friend request"
                            }
                    }
                }
            }

            // Friend Request Success Message
            if (friendRequestSent) {
                LaunchedEffect(Unit) {
                    delay(3000)
                    friendRequestSent = false
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Success",
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Friend request sent to $friendRequestUsername",
                            color = CyberpunkTheme.PrimaryPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsContent(
    filteredContacts: List<Contact>,
    onContactClick: (contactId: String) -> Unit
) {
    if (filteredContacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = "No contacts",
                    tint = CyberpunkTheme.GhostGray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No contacts found",
                    color = CyberpunkTheme.GhostGray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredContacts) { contact ->
                ContactItemCard(contact, { onContactClick(contact.id) })
            }
        }
    }
}

@Composable
fun AddFriendsContent(
    searchResults: List<UserData>,
    searchQuery: String,
    isLoading: Boolean,
    onSendRequest: (UserData) -> Unit
) {
    when {
        isLoading && searchQuery.isNotEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyberpunkTheme.PrimaryMagenta)
            }
        }
        searchQuery.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Search users",
                        tint = CyberpunkTheme.GhostGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Search for users by username",
                        color = CyberpunkTheme.GhostGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enter a username above to find and add friends",
                        color = CyberpunkTheme.GhostGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
        searchResults.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.PersonOff,
                        contentDescription = "No results",
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No users found",
                        color = CyberpunkTheme.GhostGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Try searching with a different username",
                        color = CyberpunkTheme.GhostGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(searchResults) { user ->
                    AddFriendCard(
                        user,
                        { onSendRequest(user) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddFriendCard(
    user: UserData,
    onSendRequest: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.username.take(1),
                        color = CyberpunkTheme.PrimaryMagenta,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.White
                )
                Text(
                    "@${user.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.GhostGray,
                    fontSize = 10.sp
                )
            }

            Button(
                onClick = onSendRequest,
                modifier = Modifier
                    .height(36.dp)
                    .border(1.5.dp, CyberpunkTheme.PrimaryMagenta, RoundedCornerShape(6.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.2f),
                    contentColor = CyberpunkTheme.PrimaryMagenta
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Add friend",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Add",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItemCard(
    contact: Contact,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when (contact.status) {
                    ContactStatus.Online -> CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                    ContactStatus.Away -> Color(0xFFFFA500).copy(alpha = 0.2f)
                    ContactStatus.DND -> Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    ContactStatus.Offline -> CyberpunkTheme.DarkGray.copy(alpha = 0.4f)
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.name.take(1),
                        color = CyberpunkTheme.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.White
                )
                Text(
                    when (contact.status) {
                        ContactStatus.Online -> "● Online"
                        ContactStatus.Away -> "● Away"
                        ContactStatus.DND -> "● Do Not Disturb"
                        ContactStatus.Offline -> "Last seen ${contact.lastSeen}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (contact.status) {
                        ContactStatus.Online -> CyberpunkTheme.PrimaryPurple
                        ContactStatus.Away -> Color(0xFFFFA500)
                        ContactStatus.DND -> Color(0xFFFF6B6B)
                        ContactStatus.Offline -> CyberpunkTheme.GhostGray
                    },
                    fontSize = 10.sp
                )
            }

            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = when (contact.status) {
                    ContactStatus.Online -> CyberpunkTheme.PrimaryPurple
                    ContactStatus.Away -> Color(0xFFFFA500)
                    ContactStatus.DND -> Color(0xFFFF6B6B)
                    ContactStatus.Offline -> CyberpunkTheme.GhostGray
                }
            ) {}

            IconButton(
                onClick = { /* Send message */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Message,
                    contentDescription = "Message",
                    tint = CyberpunkTheme.PrimaryMagenta,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


