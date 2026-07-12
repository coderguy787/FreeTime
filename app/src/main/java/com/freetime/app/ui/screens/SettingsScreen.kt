@file:Suppress("UNUSED_VARIABLE", "DEPRECATION")

package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.FriendRequest
import com.freetime.app.api.UserProfile
import com.freetime.app.api.OnlineStatus
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import coil.request.ImageRequest


@Composable
fun SettingsScreenEnhanced(
    onLogoutClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(true) }
    var encryptionEnabled by remember { mutableStateOf(true) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("PERSONALIZE") }
    val context = LocalContext.current
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    val apiService = remember { FreeTimeApiService(context) }

    // Load dark mode preference on startup
    LaunchedEffect(Unit) {
        darkModeEnabled = prefs.isDarkModeEnabled()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkModeEnabled) Color(0xFF0A0A0A) else Color(0xFFF0F0F0)) // Light grey instead of white
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== MODERN HEADER =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (darkModeEnabled) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)),
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
                            tint = if (darkModeEnabled) CyberpunkTheme.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (darkModeEnabled) CyberpunkTheme.White else Color.Black,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Help dialog
            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    title = { Text("FreeTime Settings Help", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("• PERSONALIZE — theme, font size, accent color", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                            Text("• ACCOUNT — username, display name, bio, avatar", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                            Text("• PRIVACY — who can message you and see your profile", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                            Text("• NOTIFICATIONS — push notification preferences", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                            Text("• SECURITY — change password, 2FA, account deletion", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                            Text("• ABOUT — app version and legal information", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showHelpDialog = false }) {
                            Text("Got it", color = CyberpunkTheme.PrimaryPurple)
                        }
                    },
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = CyberpunkTheme.White,
                    textContentColor = CyberpunkTheme.LightGray
                )
            }

            // ===== HORIZONTAL TABS =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(if (darkModeEnabled) Color(0xFF151515).copy(alpha = 0.8f) else Color(0xFFEEEEEE)),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("PERSONALIZE", "ACCOUNT", "PRIVACY", "NOTIFICATIONS", "SECURITY", "ABOUT", "SHARE").forEach { tab ->
                        Surface(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = tab }
                                .border(
                                    width = if (selectedTab == tab) 2.dp else 1.dp,
                                    color = if (selectedTab == tab) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.MediumGray.copy(
                                        alpha = 0.3f
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            color = if (selectedTab == tab) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f) else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tab,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (selectedTab == tab) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.LightGray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== CONTENT BASED ON SELECTED TAB =====
            when (selectedTab) {
                "PERSONALIZE" -> PersonalizationContent()
                "ACCOUNT" -> AccountSettingsContent(
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsChange = { notificationsEnabled = it }
                )
                "PRIVACY" -> PrivacySettingsContent()
                "NOTIFICATIONS" -> NotificationsContent()
                "SECURITY" -> SecuritySettingsContent(
                    onLogoutClick = onLogoutClick
                )
                "ABOUT" -> AboutContent()
                "SHARE" -> ShareContent(context)
            }
        }
    }
}

@Composable
fun PersonalizationContent() {
    val context = LocalContext.current
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    
    // Initialize state from SharedPreferences
    var fontSizeIndex by remember { mutableStateOf(1) }
    var animationSpeedIndex by remember { mutableStateOf(1) }
    var compactModeEnabled by remember { mutableStateOf(false) }
    
    // Load all settings from SharedPreferences on first compose
    LaunchedEffect(Unit) {
        try {
            fontSizeIndex = prefs.getFontSizeIndex()
            animationSpeedIndex = prefs.getAnimationSpeedIndex()
            compactModeEnabled = prefs.isCompactModeEnabled()
        } catch (e: Exception) {
            android.util.Log.e("PersonalizationContent", "Error loading settings", e)
        }
    }
    
    val fontSizes = listOf("Small", "Medium", "Large")
    val animationSpeeds = listOf("Slow", "Normal", "Fast")
    
    // Get current font size multiplier
    fun getFontSizeMultiplier(): Float {
        return when (fontSizeIndex) {
            0 -> 0.85f
            1 -> 1.0f
            2 -> 1.15f
            else -> 1.0f
        }
    }
    
    val spacingMultiplier = if (compactModeEnabled) 0.7f else 1.0f

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp * spacingMultiplier),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsSectionHeader("Display & Theme")
        }

        item {
            SettingsDropdownItem(
                icon = Icons.Filled.TextFields,
                title = "Font Size",
                description = "Adjust text size throughout the app",
                options = fontSizes,
                selectedIndex = fontSizeIndex,
                onSelect = { newIndex ->
                    fontSizeIndex = newIndex
                    prefs.setFontSizeIndex(newIndex)
                },
                fontSizeMultiplier = getFontSizeMultiplier(),
                spacingMultiplier = spacingMultiplier
            )
        }

        item {
            SettingsDropdownItem(
                icon = Icons.Filled.Tune,
                title = "Animation Speed",
                description = "Set animation and transition speed",
                options = animationSpeeds,
                selectedIndex = animationSpeedIndex,
                onSelect = { newIndex ->
                    animationSpeedIndex = newIndex
                    prefs.setAnimationSpeedIndex(newIndex)
                },
                fontSizeMultiplier = getFontSizeMultiplier(),
                spacingMultiplier = spacingMultiplier
            )
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.ViewStream,
                title = "Compact Mode",
                description = "Reduce spacing and padding for more content",
                checked = compactModeEnabled,
                onCheckedChange = { newState ->
                    compactModeEnabled = newState
                    prefs.setCompactModeEnabled(newState)
                },
                fontSizeMultiplier = getFontSizeMultiplier(),
                spacingMultiplier = spacingMultiplier
            )
        }
        


        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    description: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fontSizeMultiplier: Float = 1.0f,
    spacingMultiplier: Float = 1.0f,
    accentColor: androidx.compose.ui.graphics.Color = CyberpunkTheme.PrimaryPurple
) {
    var expanded by remember { mutableStateOf(false) }
    val baseSpacing = 8.dp * spacingMultiplier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberpunkTheme.DarkGray.copy(alpha = 0.3f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { expanded = true },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp * spacingMultiplier)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(baseSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp * fontSizeMultiplier
                        ),
                        color = CyberpunkTheme.White
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp * fontSizeMultiplier
                        ),
                        color = CyberpunkTheme.LightGray
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(8.dp * spacingMultiplier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = options.getOrNull(selectedIndex) ?: "Select",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp * fontSizeMultiplier
                        ),
                        color = accentColor
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 200.dp)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                option,
                                fontSize = 12.sp * fontSizeMultiplier
                            )
                        },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (index == selectedIndex) accentColor.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSettingsContent(
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit
) {
    var currentUsername by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var currentDisplayName by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var newBio by remember { mutableStateOf("") }
    var userTags by remember { mutableStateOf(listOf<String>()) }
    var originalTags by remember { mutableStateOf(listOf<String>()) }
    var originalPronouns by remember { mutableStateOf("") }
    var isLoadingProfile by remember { mutableStateOf(false) }
    var isUpdatingUsername by remember { mutableStateOf(false) }
    var isUpdatingDisplayName by remember { mutableStateOf(false) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUpdatingBio by remember { mutableStateOf(false) }
    var lastUsernameChange by remember { mutableStateOf<Long?>(null) }
    var lastDisplayNameChange by remember { mutableStateOf<Long?>(null) }
    var usernameError by remember { mutableStateOf("") }
    var usernameSuccess by remember { mutableStateOf("") }
    var displayNameError by remember { mutableStateOf("") }
    var bioError by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var userRole by remember { mutableStateOf("") }
    var isEditingProfile by remember { mutableStateOf(false) }
    var hasChanges by remember { mutableStateOf(false) }
    var profileLoadError by remember { mutableStateOf("") }
    var lastSaveTime by remember { mutableStateOf<Long?>(null) }
    var pronouns by remember { mutableStateOf("") }
    var showPhotoCropDialog by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    val scope = rememberCoroutineScope()
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            showPhotoCropDialog = true
        }
    }

    // Cooldown logic (same as before)
    fun canChangeUsername(): Boolean {
        if (lastUsernameChange == null) return true
        val daysSinceChange = (System.currentTimeMillis() - lastUsernameChange!!) / (1000 * 60 * 60 * 24)
        return daysSinceChange >= 30
    }

    fun canChangeDisplayName(): Boolean {
        if (lastDisplayNameChange == null) return true
        val daysSinceChange = (System.currentTimeMillis() - lastDisplayNameChange!!) / (1000 * 60 * 60 * 24)
        return daysSinceChange >= 14
    }

    fun getDaysUntilUsernameChange(): Long {
        if (lastUsernameChange == null) return 0
        val daysSinceChange = (System.currentTimeMillis() - lastUsernameChange!!) / (1000 * 60 * 60 * 24)
        return maxOf(0, 30 - daysSinceChange)
    }

    fun getDaysUntilDisplayNameChange(): Long {
        if (lastDisplayNameChange == null) return 0
        val daysSinceChange = (System.currentTimeMillis() - lastDisplayNameChange!!) / (1000 * 60 * 60 * 24)
        return maxOf(0, 14 - daysSinceChange)
    }

    fun loadProfile() {
        scope.launch {
            isLoadingProfile = true
            profileLoadError = ""
            try {
                val result = apiService.getCurrentUserProfile()
                result.onSuccess { profile ->
                    currentUsername = profile.username
                    currentDisplayName = profile.displayName
                    bio = profile.bio
                    profileImageUrl = profile.avatar
                    userTags = profile.tags
                    originalTags = profile.tags
                    userRole = profile.role ?: "User"
                    pronouns = profile.pronouns
                    originalPronouns = profile.pronouns
                    lastUsernameChange = profile.lastUsernameChangeAt?.toLongOrNull()
                    lastDisplayNameChange = profile.lastDisplayNameChangeAt?.toLongOrNull()
                    // Sync cached username so chat screens see the latest value
                    prefs.setUsername(profile.username)
                    // Populate editing fields with current values
                    newDisplayName = profile.displayName
                    newUsername = profile.username
                    newBio = profile.bio
                    hasChanges = false
                    profileLoadError = ""
                }.onFailure { e ->
                    profileLoadError = "Failed to load profile: ${e.message}"
                }
            } catch (e: Exception) {
                profileLoadError = "Error loading profile: ${e.message}"
            } finally {
                isLoadingProfile = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadProfile()
    }

    // Show loading indicator while profile is loading
    if (isLoadingProfile) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CyberpunkTheme.PrimaryPurple)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading your profile...", color = CyberpunkTheme.LightGray)
            }
        }
    }

    // Show error if profile failed to load
    if (profileLoadError.isNotEmpty() && !isLoadingProfile) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Unable to load your profile",
                    color = Color.Red,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    profileLoadError,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { loadProfile() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
                ) {
                    Text("Retry")
                }
            }
        }
    }

    // Show profile content only if loaded successfully
    if (!isLoadingProfile && profileLoadError.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // MODERN AVATAR SECTION
            item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(3.dp, CyberpunkTheme.PrimaryPurple, CircleShape),
                            color = CyberpunkTheme.DarkGray
                        ) {
                            val resolvedAvatarUrl = apiService.resolveAvatarUrl(profileImageUrl)
                            if (!resolvedAvatarUrl.isNullOrEmpty() && resolvedAvatarUrl != "null") {
                                AsyncImage(
                                    model = resolvedAvatarUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = "Profile",
                                    tint = CyberpunkTheme.LightGray,
                                    modifier = Modifier.size(60.dp).padding(30.dp)
                                )
                            }
                        }
                        
                        // Edit overlay button
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberpunkTheme.PrimaryPurple)
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Change photo",
                                tint = CyberpunkTheme.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        if (isUploadingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(120.dp),
                                color = CyberpunkTheme.PrimaryPurple,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "@$currentUsername",
                        color = CyberpunkTheme.PrimaryPurple,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                    ) {
                        Text(
                            userRole.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = CyberpunkTheme.PrimaryPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Profile Editor",
                color = CyberpunkTheme.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Display Name Editor
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp, 
                        if (displayNameError.isNotEmpty()) Color.Red else CyberpunkTheme.DarkGray, 
                        RoundedCornerShape(12.dp)
                    ),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Display Name", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                        if (!canChangeDisplayName()) {
                            Text(
                                "Locked for ${getDaysUntilDisplayNameChange()} days",
                                color = Color(0xFFFFD93D),
                                fontSize = 10.sp
                            )
                        }
                    }

                    TextField(
                        value = newDisplayName,
                        onValueChange = { 
                            newDisplayName = it
                            displayNameError = ""
                            isEditingProfile = true
                            hasChanges = true
                        },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White
                        ),
                        enabled = canChangeDisplayName(),
                        singleLine = true,
                        isError = displayNameError.isNotEmpty()
                    )
                }
            }
        }

        // Username Editor
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp, 
                        if (usernameError.isNotEmpty()) Color.Red else CyberpunkTheme.DarkGray, 
                        RoundedCornerShape(12.dp)
                    ),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Username", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                        if (!canChangeUsername()) {
                            Text(
                                "Locked for ${getDaysUntilUsernameChange()} days",
                                color = Color(0xFFFFD93D),
                                fontSize = 10.sp
                            )
                        }
                    }
                    TextField(
                        value = newUsername,
                        onValueChange = { 
                            newUsername = it
                            usernameError = ""
                            isEditingProfile = true
                            hasChanges = true
                        },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White
                        ),
                        enabled = canChangeUsername(),
                        singleLine = true,
                        isError = usernameError.isNotEmpty()
                    )
                }
            }
        }

        // Bio Editor
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Bio", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                    TextField(
                        value = newBio,
                        onValueChange = { 
                            newBio = it
                            isEditingProfile = true
                            hasChanges = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White
                        )
                    )
                }
            }
        }

        // Pronouns Editor
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Pronouns", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                    TextField(
                        value = pronouns,
                        onValueChange = {
                            pronouns = it
                            isEditingProfile = true
                            hasChanges = true
                        },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        placeholder = { Text("e.g. he/him, she/her, they/them", color = CyberpunkTheme.LightGray.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Tags Display (Read-Only)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tags", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Tags are assigned by administrators",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp
                    )

                    // Display tags as read-only chips
                    if (userTags.isNotEmpty()) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            userTags.forEach { tag ->
                                Surface(
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = CyberpunkTheme.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "No tags assigned yet",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }

        // Profile Picture Upload
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Profile Picture", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, CyberpunkTheme.PrimaryPurple, CircleShape),
                        color = CyberpunkTheme.MediumGray
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!profileImageUrl.isNullOrEmpty() && profileImageUrl != "null") {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(profileImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = "Profile",
                                    tint = CyberpunkTheme.LightGray,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                            if (isUploadingImage) {
                                CircularProgressIndicator(color = CyberpunkTheme.PrimaryPurple)
                            }
                        }
                    }
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple),
                        enabled = !isUploadingImage
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change Photo")
                    }
                }
            }
        }

        // Accept/Cancel Buttons
        if (isEditingProfile || hasChanges) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedButton(
                        onClick = {
                            scope.launch {
                                // ===== CRITICAL: Validate profile is loaded before saving =====
                                if (profileLoadError.isNotEmpty()) {
                                    android.util.Log.e("FREETIME_SETTINGS", "Cannot save: profile load failed - $profileLoadError")
                                    Toast.makeText(context, "Cannot save: Profile failed to load. Please refresh.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                if (currentUsername.isEmpty() || currentDisplayName.isEmpty()) {
                                    android.util.Log.e("FREETIME_SETTINGS", "Cannot save: profile data not initialized (username='$currentUsername', displayName='$currentDisplayName')")
                                    Toast.makeText(context, "Cannot save: Profile data not fully loaded. Please refresh.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                
                                isLoadingProfile = true
                                val errors = mutableListOf<String>()
                                try {
                                    if (newDisplayName != currentDisplayName && newDisplayName.isNotEmpty()) {
                                        apiService.updateDisplayName(newDisplayName)
                                            .exceptionOrNull()?.let { errors.add("Display name: ${it.message}") }
                                    }
                                    if (newBio != bio) {


                                
                                        apiService.updateBio(newBio)
                                            .exceptionOrNull()?.let { errors.add("Bio: ${it.message}") }
                                    }
                                    if (newUsername != currentUsername && newUsername.isNotEmpty()) {
                                        val usernameResult = apiService.updateUsername(newUsername)
                                        usernameResult.exceptionOrNull()?.let { errors.add("Username: ${it.message}") }
                                        if (usernameResult.isSuccess) prefs.setUsername(newUsername)
                                    }
                                    // Save pronouns via profile update (tags are admin-only)
                                    if (pronouns != originalPronouns) {
                                        apiService.updateUserProfile(pronouns = pronouns)
                                            .exceptionOrNull()?.let { errors.add("Pronouns: ${it.message}") }
                                    }

                                    if (errors.isEmpty()) {
                                        Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                                        isEditingProfile = false
                                        hasChanges = false
                                        loadProfile()
                                    } else {
                                        Toast.makeText(context, errors.first(), Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoadingProfile = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple,
                            contentColor = CyberpunkTheme.White
                        ),
                        enabled = !isLoadingProfile && profileLoadError.isEmpty()
                    ) {
                        if (isLoadingProfile) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberpunkTheme.White)
                        } else {
                            Text("Save Changes")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isEditingProfile = false
                            hasChanges = false
                            newDisplayName = currentDisplayName
                            newUsername = currentUsername
                            newBio = bio
                            userTags = originalTags
                            pronouns = originalPronouns
                        },
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
        
        item {
            SettingsSectionHeader("Preferences")
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.Notifications,
                title = "Push Notifications",
                description = "Enable real-time message alerts",
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsChange
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Photo Upload Confirmation Dialog
    if (showPhotoCropDialog && selectedPhotoUri != null) {
        AlertDialog(
            onDismissRequest = {
                showPhotoCropDialog = false
                selectedPhotoUri = null
            },
            title = {
                Text("Upload Profile Photo?", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Confirm to upload this image as your profile photo",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 13.sp
                    )
                    // Preview of selected image
                    if (selectedPhotoUri != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = CyberpunkTheme.DarkGray
                        ) {
                            AsyncImage(
                                model = selectedPhotoUri,
                                contentDescription = "Photo Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Read image bytes from URI and upload
                        selectedPhotoUri?.let { uri ->
                            scope.launch {
                                isUploadingImage = true
                                try {
                                    // Read image file from URI
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val imageBytes = inputStream?.readBytes() ?: return@launch
                                    inputStream.close()
                                    
                                    // Determine MIME type from URI
                                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                                    
                                    // Upload to server
                                    val result = apiService.uploadProfileImage(imageBytes, mimeType)
                                    result.onSuccess { imageUrl ->
                                        profileImageUrl = imageUrl
                                        Toast.makeText(context, "Photo updated successfully!", Toast.LENGTH_SHORT).show()
                                        showPhotoCropDialog = false
                                        selectedPhotoUri = null
                                        // Refresh profile to sync with server
                                        loadProfile()
                                    }.onFailure { error ->
                                        val msg = error.message ?: "Failed to update the photo!"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isUploadingImage = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple),
                    enabled = !isUploadingImage
                ) {
                    if (isUploadingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyberpunkTheme.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoCropDialog = false
                        selectedPhotoUri = null
                    }
                ) {
                    Text("Cancel", color = CyberpunkTheme.LightGray)
                }
            },
            containerColor = CyberpunkTheme.DarkGray,
            textContentColor = CyberpunkTheme.White
        )
        }
    } // End of if (!isLoadingProfile && profileLoadError.isEmpty())
}

// ===== PRIVACY SETTINGS CONTENT =====
@Composable
fun PrivacySettingsContent() {
    val context = LocalContext.current
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    val apiService = remember { FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()

    var lastSeenEnabled by remember { mutableStateOf(true) }
    var onlineStatusEnabled by remember { mutableStateOf(true) }
    var bioEnabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    fun privacyValue(enabled: Boolean): String = if (enabled) "friends" else "nobody"

    // Load settings from server
    LaunchedEffect(Unit) {
        try {
            val userId = prefs.getUserId()
            if (userId != null) {
                val result = apiService.getPrivacySettings(userId)
                result.onSuccess { settings ->
                    lastSeenEnabled = settings["lastSeen"] != "nobody"
                    onlineStatusEnabled = settings["onlineStatus"] != "nobody"
                    bioEnabled = settings["bio"] != "nobody"
                }
            }
            isLoading = false
        } catch (e: Exception) {
            Log.e("PRIVACY", "Error: ${e.message}")
            isLoading = false
        }
    }

    fun savePrivacySetting(key: String, enabled: Boolean) {
        scope.launch {
            isSaving = true
            try {
                val userId = prefs.getUserId()
                if (userId != null) {
                    val updateData = mapOf(key to privacyValue(enabled))
                    if (key == "bio") {
                        apiService.updatePrivacySettings(userId, mapOf("bio" to privacyValue(enabled), "aboutBio" to privacyValue(enabled)))
                    } else {
                        apiService.updatePrivacySettings(userId, updateData)
                    }
                }
            } catch (e: Exception) {
                Log.e("PRIVACY", "Error saving privacy settings: ${e.message}")
                Toast.makeText(context, "Failed to save privacy setting", Toast.LENGTH_SHORT).show()
            } finally {
                isSaving = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(32.dp))
                }
            }
        } else {
            item {
                SettingsSectionHeader("Who Can See My Info")
            }

            item {
                SettingsToggleItem(
                    icon = Icons.Filled.AccessTime,
                    title = "Last Seen",
                    description = "Let friends see when you were last online",
                    checked = lastSeenEnabled,
                    onCheckedChange = { enabled ->
                        lastSeenEnabled = enabled
                        savePrivacySetting("lastSeen", enabled)
                    }
                )
            }

            item {
                SettingsToggleItem(
                    icon = Icons.Filled.Person,
                    title = "Online Status",
                    description = "Let friends see when you're online",
                    checked = onlineStatusEnabled,
                    onCheckedChange = { enabled ->
                        onlineStatusEnabled = enabled
                        savePrivacySetting("onlineStatus", enabled)
                    }
                )
            }

            item {
                SettingsToggleItem(
                    icon = Icons.Filled.Description,
                    title = "Bio / About",
                    description = "Let friends see your profile bio",
                    checked = bioEnabled,
                    onCheckedChange = { enabled ->
                        bioEnabled = enabled
                        savePrivacySetting("bio", enabled)
                    }
                )
            }

            if (isSaving) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PrivacyDropdownItem(
    icon: ImageVector,
    title: String,
    description: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = true }
            .border(1.dp, CyberpunkTheme.MediumGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberpunkTheme.White)
                    Text(description, fontSize = 11.sp, color = CyberpunkTheme.LightGray)
                }
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                ) {
                    Text(
                        options.getOrNull(selectedIndex) ?: "Select",
                        fontSize = 10.sp,
                        color = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (index == selectedIndex) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsContent(
    onLogoutClick: () -> Unit = {}
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var accountDeletionReason by remember { mutableStateOf("") }
    var isLoadingPasswordChange by remember { mutableStateOf(false) }
    var isLoadingLogout by remember { mutableStateOf(false) }
    var isLoadingDelete by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsSectionHeader("Change Password")
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Enter your current password and new password. You must provide your 2FA code to confirm.",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    TextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White,
                            focusedIndicatorColor = CyberpunkTheme.PrimaryPurple,
                            unfocusedIndicatorColor = CyberpunkTheme.DarkGray
                        ),
                        placeholder = {
                            Text("Current password", color = CyberpunkTheme.LightGray)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    TextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White,
                            focusedIndicatorColor = CyberpunkTheme.PrimaryPurple,
                            unfocusedIndicatorColor = CyberpunkTheme.DarkGray
                        ),
                        placeholder = {
                            Text("New password", color = CyberpunkTheme.LightGray)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    TextField(
                        value = totpCode,
                        onValueChange = { if (it.length <= 6) totpCode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White,
                            focusedIndicatorColor = CyberpunkTheme.PrimaryPurple,
                            unfocusedIndicatorColor = CyberpunkTheme.DarkGray
                        ),
                        placeholder = {
                            Text("2FA Code (6 digits)", color = CyberpunkTheme.LightGray)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    ElevatedButton(
                        onClick = {
                            if (oldPassword.isNotEmpty() && newPassword.isNotEmpty() && totpCode.length == 6) {
                                scope.launch {
                                    isLoadingPasswordChange = true
                                    try {
                                        val result = apiService.changePassword(oldPassword, newPassword, totpCode)
                                        result.onSuccess {
                                            Toast.makeText(context, "Password changed successfully!", Toast.LENGTH_SHORT).show()
                                            oldPassword = ""
                                            newPassword = ""
                                            totpCode = ""
                                        }.onFailure { e ->
                                            Toast.makeText(context, "Failed to change password: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to update the photo!", Toast.LENGTH_SHORT).show()
                                } finally {
                                        isLoadingPasswordChange = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please fill all fields correctly", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple,
                            contentColor = CyberpunkTheme.White
                        ),
                        enabled = oldPassword.isNotEmpty() && newPassword.isNotEmpty() && totpCode.length == 6 && !isLoadingPasswordChange
                    ) {
                        if (isLoadingPasswordChange) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CyberpunkTheme.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Password")
                    }
                }
            }
        }

        item {
            SettingsSectionHeader("Session", isDanger = false)
        }

        item {
            SettingsDangerItem(
                icon = Icons.Filled.Logout,
                title = "Logout",
                description = "Sign out from this device",
                onClick = {
                    scope.launch {
                        isLoadingLogout = true
                        try {
                            apiService.logout()
                        } catch (e: Exception) {
                            android.util.Log.e("SecuritySettings", "Logout API error: ${e.message}")
                        } finally {
                            isLoadingLogout = false
                            // Always clear local data and navigate to login regardless of API result
                            val prefs = com.freetime.app.data.local.SharedPreferencesHelper(context)
                            prefs.clearAuthData()
                            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                            onLogoutClick()
                        }
                    }
                }
            )
        }

        item {
            SettingsSectionHeader("Danger Zone", isDanger = true)
        }

        item {
            SettingsDangerItem(
                icon = Icons.Filled.Delete,
                title = "Delete Account",
                description = "Permanently delete your account and all data",
                onClick = {
                    showDeleteConfirmation = true
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Delete Account dialog — must be OUTSIDE LazyColumn to render reliably
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Account", color = CyberpunkTheme.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This action is permanent. Please tell us why you're leaving.",
                        color = CyberpunkTheme.LightGray
                    )
                    TextField(
                        value = accountDeletionReason,
                        onValueChange = { accountDeletionReason = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CyberpunkTheme.Black,
                            unfocusedContainerColor = CyberpunkTheme.Black,
                            focusedTextColor = CyberpunkTheme.White,
                            unfocusedTextColor = CyberpunkTheme.White,
                            focusedIndicatorColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                            unfocusedIndicatorColor = CyberpunkTheme.DarkGray
                        ),
                        placeholder = { Text("Why are you leaving?", color = CyberpunkTheme.LightGray) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (accountDeletionReason.isNotEmpty()) {
                            scope.launch {
                                isLoadingDelete = true
                                try {
                                    val result = apiService.deleteAccount(accountDeletionReason)
                                    result.onSuccess {
                                        Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                                        showDeleteConfirmation = false
                                        accountDeletionReason = ""
                                        onLogoutClick()
                                    }.onFailure { e ->
                                        Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoadingDelete = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please provide a reason", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = accountDeletionReason.isNotEmpty() && !isLoadingDelete
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.DarkGray)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CyberpunkTheme.DarkGray,
            textContentColor = CyberpunkTheme.White
        )
    }
}

@Composable
fun NotificationsContent() {
    val context = LocalContext.current
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    val scope = rememberCoroutineScope()

    // Notification toggle states from prefs
    var notifyMessages by remember { mutableStateOf(prefs.isNotifyMessagesEnabled()) }
    var notifyFriendRequests by remember { mutableStateOf(prefs.isNotifyFriendRequestsEnabled()) }
    var notifyGroupUpdates by remember { mutableStateOf(prefs.isNotifyGroupUpdatesEnabled()) }
    var notifyVibration by remember { mutableStateOf(prefs.isNotifyVibrationEnabled()) }
    var notifySound by remember { mutableStateOf(prefs.isNotifySoundEnabled()) }

    // Reload preferences when tab regains focus
    LaunchedEffect(Unit) {
        notifyMessages = prefs.isNotifyMessagesEnabled()
        notifyFriendRequests = prefs.isNotifyFriendRequestsEnabled()
        notifyGroupUpdates = prefs.isNotifyGroupUpdatesEnabled()
        notifyVibration = prefs.isNotifyVibrationEnabled()
        notifySound = prefs.isNotifySoundEnabled()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Notification Type Toggles
        item {
            SettingsSectionHeader("Notification Types")
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.Chat,
                title = "Message Notifications",
                description = "Get notified for incoming messages",
                checked = notifyMessages,
                onCheckedChange = { enabled ->
                    notifyMessages = enabled
                    prefs.setNotifyMessages(enabled)
                    com.freetime.app.notifications.NotificationHelper.recreateNotificationChannels(context)
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Message notifications enabled" else "Message notifications disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.PersonAdd,
                title = "Friend Requests",
                description = "Get notified for new friend requests",
                checked = notifyFriendRequests,
                onCheckedChange = { enabled ->
                    notifyFriendRequests = enabled
                    prefs.setNotifyFriendRequests(enabled)
                    com.freetime.app.notifications.NotificationHelper.recreateNotificationChannels(context)
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Friend request notifications enabled" else "Friend request notifications disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.VolumeUp,
                title = "Notification Sound",
                description = "Play sound for incoming notifications",
                checked = notifySound,
                onCheckedChange = { enabled ->
                    notifySound = enabled
                    prefs.setNotifySound(enabled)
                    com.freetime.app.notifications.NotificationHelper.recreateNotificationChannels(context)
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Notification sound enabled" else "Notification sound disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.Groups,
                title = "Group Updates",
                description = "Get notified for group messages and events",
                checked = notifyGroupUpdates,
                onCheckedChange = { enabled ->
                    notifyGroupUpdates = enabled
                    prefs.setNotifyGroupUpdates(enabled)
                    com.freetime.app.notifications.NotificationHelper.recreateNotificationChannels(context)
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Group notifications enabled" else "Group notifications disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            SettingsToggleItem(
                icon = Icons.Filled.Vibration,
                title = "Vibration",
                description = "Vibrate for incoming notifications",
                checked = notifyVibration,
                onCheckedChange = { enabled ->
                    notifyVibration = enabled
                    prefs.setNotifyVibration(enabled)
                    com.freetime.app.notifications.NotificationHelper.recreateNotificationChannels(context)
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Vibration enabled" else "Vibration disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FriendRequestNotificationCard(
    request: FriendRequest,
    apiService: FreeTimeApiService,
    onAcceptedOrDeclined: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF9D4EDD), RoundedCornerShape(12.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // User Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    color = Color(0xFF9D4EDD).copy(alpha = 0.3f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "User",
                        tint = Color(0xFF9D4EDD),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }

                // User Name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        request.senderUsername,
                        color = CyberpunkTheme.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Sent at " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(request.createdAt)),
                        color = CyberpunkTheme.LightGray,
                        fontSize = 10.sp
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    val result = apiService.acceptFriendRequest(request.senderId)
                                    if (result.isSuccess) {
                                        onAcceptedOrDeclined()
                                    } else {
                                        Log.e("FriendRequestCard", "Failed to accept: ${result.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("FriendRequestCard", "Error accepting request", e)
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FFFF),
                        disabledContainerColor = Color(0xFF00FFFF).copy(alpha = 0.5f),
                        contentColor = CyberpunkTheme.Black
                    ),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isProcessing
                ) {
                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch {
                                try {
                                    val result = apiService.declineFriendRequest(request.senderId)
                                    if (result.isSuccess) {
                                        onAcceptedOrDeclined()
                                    } else {
                                        Log.e("FriendRequestCard", "Failed to decline: ${result.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("FriendRequestCard", "Error declining request", e)
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF00FF).copy(alpha = 0.3f),
                        disabledContainerColor = Color(0xFFFF00FF).copy(alpha = 0.15f),
                        contentColor = Color(0xFFFF00FF)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isProcessing
                ) {
                    Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AboutContent() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsSectionHeader("About FreeTime")
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "FreeTime",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = CyberpunkTheme.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column {
                            Text("Version", color = CyberpunkTheme.LightGray, fontSize = 11.sp)
                            Text("1.0.0", color = CyberpunkTheme.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Build", color = CyberpunkTheme.LightGray, fontSize = 11.sp)
                            Text("Build 2026", color = CyberpunkTheme.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        "A modern secure communication app with end-to-end encryption, built for privacy-conscious users.",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            SettingsSectionHeader("Creator")
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Developed by",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Coder",
                        color = CyberpunkTheme.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Passionate about creating secure, modern communication tools. Available 24/7 for support.",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            SettingsSectionHeader("Legal & Support")
        }

        item {
            SettingsClickableItem(
                icon = Icons.Filled.Description,
                title = "Terms of Service",
                description = "Read our terms and conditions",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://freetime-official.org/terms"))
                    context.startActivity(intent)
                }
            )
        }

        item {
            SettingsClickableItem(
                icon = Icons.Filled.Public,
                title = "Visit Website",
                description = "Learn more about FreeTime",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://freetime-official.org/"))
                    context.startActivity(intent)
                }
            )
        }

        item {
            SettingsClickableItem(
                icon = Icons.Filled.Email,
                title = "Send Feedback",
                description = "Email us at your-email@example.com",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("your-email@example.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "FreeTime Feedback & Suggestions")
                    }
                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ShareContent(context: android.content.Context) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsSectionHeader("Share FreeTime")
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.08f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Spread the Word",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = CyberpunkTheme.White
                    )

                    Text(
                        "Share FreeTime with your friends and help us build a privacy-first community.",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareOption(
                    icon = Icons.Filled.Share,
                    title = "Share App",
                    description = "Send to friends via messaging apps",
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out FreeTime - A secure communication app! Download it now.")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share FreeTime"))
                    }
                )

                ShareOption(
                    icon = Icons.Filled.OpenInBrowser,
                    title = "Visit Website",
                    description = "Learn more about FreeTime",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://freetime-official.org/"))
                        context.startActivity(intent)
                    }
                )

                ShareOption(
                    icon = Icons.Filled.Code,
                    title = "View Source Code",
                    description = "FreeTime source code",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/coderguy787/FreeTime"))
                        context.startActivity(intent)
                    }
                )

            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ===== HELPER COMPOSABLES =====

@Composable
fun ThemeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.MediumGray,
                shape = RoundedCornerShape(10.dp)
            ),
        color = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f) else CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (label == "Dark") CyberpunkTheme.DarkGray
                        else if (label == "Light") Color(0xFFF0F0F0) // Light grey instead of white
                        else CyberpunkTheme.MediumGray,
                shadowElevation = 4.dp
            ) {}

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = CyberpunkTheme.PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ColorOption(
    label: String,
    colorCode: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colorCode else CyberpunkTheme.MediumGray,
                shape = RoundedCornerShape(10.dp)
            ),
        color = if (isSelected) colorCode.copy(alpha = 0.15f) else CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = colorCode,
                shadowElevation = 4.dp
            ) {}

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = colorCode,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ShareOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    color = CyberpunkTheme.LightGray
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ===== HELPER FUNCTIONS FOR COMPOSABLES =====

@Composable
fun SettingsSectionHeader(
    title: String,
    isDanger: Boolean = false
) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = if (isDanger) Color(0xFFFF4444) else CyberpunkTheme.PrimaryPurple,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    fontSizeMultiplier: Float = 1.0f,
    spacingMultiplier: Float = 1.0f,
    accentColor: androidx.compose.ui.graphics.Color = CyberpunkTheme.PrimaryPurple
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CyberpunkTheme.MediumGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp * spacingMultiplier),
            horizontalArrangement = Arrangement.spacedBy(12.dp * spacingMultiplier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp * fontSizeMultiplier,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
                Text(
                    description,
                    fontSize = 11.sp * fontSizeMultiplier,
                    color = CyberpunkTheme.LightGray
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(48.dp, 24.dp)
            )
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.MediumGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    color = CyberpunkTheme.LightGray
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Navigate",
                tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SettingsDangerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberpunkTheme.White
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    color = CyberpunkTheme.LightGray
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Navigate",
                tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}



