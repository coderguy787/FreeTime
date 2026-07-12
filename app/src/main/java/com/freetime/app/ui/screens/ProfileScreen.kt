package com.freetime.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.ui.utils.*
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogoutClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    val scope = rememberCoroutineScope()
    val prefs = SharedPreferencesHelper(context)
    val apiService = remember { FreeTimeApiService(context) }

    // Profile state
    var username by remember { mutableStateOf("CYBER_USER") }
    var userId by remember { mutableStateOf("") }
    var userStatus by remember { mutableStateOf("Online") }
    var userBio by remember { mutableStateOf("Welcome to FreeTime Network") }
    var userInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var selectedAvatar by remember { mutableStateOf(0) }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    var newUsername by remember { mutableStateOf("") }
    var newStatus by remember { mutableStateOf("Online") }
    var newBio by remember { mutableStateOf("") }
    var newInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf("") }
    var isUploadingImage by remember { mutableStateOf(false) }

    // Avatar picker states
    var showAvatarOptions by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    // Banner picker states
    var showBannerOptions by remember { mutableStateOf(false) }
    var pendingBannerUri by remember { mutableStateOf<Uri?>(null) }
    var profileBannerUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingBanner by remember { mutableStateOf(false) }
    var showBannerDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingBanner by remember { mutableStateOf(false) }

    // Account preferences state
    var statusMessage by remember { mutableStateOf("") }
    var availabilityStatus by remember { mutableStateOf("available") }  // available/away/busy/offline
    var preferredLanguage by remember { mutableStateOf("en") }  // en/es/it/de/fr
    var preferredTheme by remember { mutableStateOf("cyberpunk") }  // dark/light/cyberpunk
    var isPreferencesSaving by remember { mutableStateOf(false) }
    var preferencesError by remember { mutableStateOf("") }
    var preferencesSuccess by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        userId = prefs.getUserId() ?: "USR_UNKNOWN"
        username = prefs.getUsername() ?: "CYBER_USER"
        newUsername = username
        newStatus = userStatus
        newBio = userBio
        newInterests = userInterests
        // Load existing profile image from prefs as fallback
        profileImageUrl = prefs.getProfileImage()
        
        // Load full profile data from API
        try {
            val result = apiService.getCurrentUserProfile()
            result.onSuccess { profile ->
                username = profile.username
                userId = profile.userId
                userBio = profile.bio
                profileImageUrl = profile.avatar
                userStatus = profile.status.ifEmpty { "Online" }
                userInterests = profile.tags
                newUsername = profile.username
                newBio = profile.bio
                newStatus = userStatus
                newInterests = userInterests
                if (!profile.avatar.isNullOrEmpty()) {
                    prefs.saveProfileImage(profile.avatar)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileScreen", "Failed to load profile: ${e.message}")
        }
    }

    // Image picker — sets pendingImageUri for preview before confirming upload
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            showAvatarOptions = false
        }
    }

    // Confirm upload: actually upload the pending image
    val confirmUpload: suspend () -> Unit = {
        val uri = pendingImageUri
        if (uri != null) {
            isUploadingImage = true
            try {
                val result = apiService.uploadProfileImage(uri)
                result.fold(
                    onSuccess = { imageUrl ->
                        // ✅ FIX: Add cache buster to ensure fresh image loads
                        val cacheBustedUrl = if (imageUrl.contains("?")) {
                            "$imageUrl&t=${System.currentTimeMillis()}"
                        } else {
                            "$imageUrl?t=${System.currentTimeMillis()}"
                        }
                        
                        profileImageUrl = cacheBustedUrl
                        prefs.saveProfileImage(cacheBustedUrl)
                        pendingImageUri = null
                        updateError = ""
                        android.widget.Toast.makeText(context, "Photo updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // ✅ CRITICAL FIX: Reload profile from server to confirm database persistence
                        android.util.Log.d("ProfileScreen", "Image uploaded successfully (cache-busted), reloading profile from server...")
                        try {
                            val profileResult = apiService.getCurrentUserProfile()
                            profileResult.onSuccess { profile ->
                                // Verify the image URL was persisted on server
                                if (!profile.avatar.isNullOrEmpty()) {
                                    // ✅ Ensure absolute URL and add cache buster
                                    val finalImageUrl = apiService.resolveAvatarUrl(profile.avatar)
                                    val finalCacheBustedUrl = if (finalImageUrl?.contains("?") == true) {
                                        "$finalImageUrl&t=${System.currentTimeMillis()}"
                                    } else {
                                        "${finalImageUrl}?t=${System.currentTimeMillis()}"
                                    }
                                    profileImageUrl = finalCacheBustedUrl
                                    android.util.Log.d("ProfileScreen", "✓ Profile image confirmed on server (cache-busted): ${finalCacheBustedUrl}")
                                } else {
                                    android.util.Log.w("ProfileScreen", "⚠️ Profile image NOT found on server after upload!")
                                    updateError = "Warning: Image uploaded but not persisted to server database"
                                }
                            }
                            profileResult.onFailure { error ->
                                android.util.Log.w("ProfileScreen", "Could not verify image on server: ${error.message}")
                                // Image upload succeeded locally, so don't show error
                                // If profile fetch fails, image is still on server - retry will happen automatically
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ProfileScreen", "Error reloading profile: ${e.message}")
                            // Non-fatal: image upload is complete, profile sync will happen on next screen visit
                        }
                    },
                    onFailure = { error ->
                        updateError = error.message ?: "Failed to update the photo!"
                        android.util.Log.e("ProfileScreen", "Image upload failed: ${error.message}")
                        android.widget.Toast.makeText(context, error.message ?: "Failed to update the photo!", android.widget.Toast.LENGTH_LONG).show()
                        pendingImageUri = null
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateError = "Failed to update the photo!"
                android.util.Log.e("ProfileScreen", "Exception during image upload: ${e.message}", e)
                android.widget.Toast.makeText(context, "Failed to update the photo!", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                isUploadingImage = false
            }
        }
    }

    // Banner image picker — sets pendingBannerUri for preview before confirming upload
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingBannerUri = uri
            showBannerOptions = false
        }
    }

    // Confirm banner upload: actually upload the pending banner image
    val confirmBannerUpload: suspend () -> Unit = {
        val uri = pendingBannerUri
        if (uri != null) {
            isUploadingBanner = true
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val byteArray = inputStream.readBytes()
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val result = apiService.uploadProfileBanner(byteArray, mimeType)
                    result.fold(
                        onSuccess = { bannerUrl ->
                            val cacheBustedUrl = if (bannerUrl.contains("?")) {
                                "$bannerUrl&t=${System.currentTimeMillis()}"
                            } else {
                                "$bannerUrl?t=${System.currentTimeMillis()}"
                            }
                            profileBannerUrl = cacheBustedUrl
                            pendingBannerUri = null
                            updateError = ""

                            android.util.Log.d("ProfileScreen", "Banner uploaded successfully, reloading profile from server...")
                            try {
                                val profileResult = apiService.getCurrentUserProfile()
                                profileResult.onSuccess { profile ->
                                    if (!profile.banner.isNullOrEmpty()) {
                                        val finalBannerUrl = apiService.resolveAvatarUrl(profile.banner)
                                        val finalCacheBustedUrl = if (finalBannerUrl?.contains("?") == true) {
                                            "$finalBannerUrl&t=${System.currentTimeMillis()}"
                                        } else {
                                            "${finalBannerUrl}?t=${System.currentTimeMillis()}"
                                        }
                                        profileBannerUrl = finalCacheBustedUrl
                                        android.util.Log.d("ProfileScreen", "✓ Profile banner confirmed on server")
                                    } else {
                                        android.util.Log.w("ProfileScreen", "⚠️ Banner NOT found on server after upload!")
                                        updateError = "Warning: Banner uploaded but not persisted to server database"
                                    }
                                }
                                profileResult.onFailure { error ->
                                    android.util.Log.w("ProfileScreen", "Could not verify banner on server: ${error.message}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("ProfileScreen", "Error reloading profile after banner: ${e.message}")
                            }
                        },
                        onFailure = { error ->
                            updateError = "Banner upload failed: ${error.message}"
                            android.util.Log.e("ProfileScreen", "Banner upload failed: ${error.message}")
                            pendingBannerUri = null
                        }
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateError = "Error uploading banner: ${e.message}"
                android.util.Log.e("ProfileScreen", "Exception during banner upload: ${e.message}", e)
            } finally {
                isUploadingBanner = false
            }
        }
    }

    // ✅ NEW: Delete banner image
    val deleteBannerImage: suspend () -> Unit = {
        isDeletingBanner = true
        try {
            val result = apiService.deleteBanner()
            result.fold(
                onSuccess = {
                    profileBannerUrl = null
                    pendingBannerUri = null
                    showBannerDeleteConfirm = false
                    updateError = ""
                },
                onFailure = { error ->
                    updateError = "Failed to delete banner: ${error.message}"
                }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            updateError = "Error deleting banner: ${e.message}"
        } finally {
            isDeletingBanner = false
        }
    }

    // Preference-saving functions
    suspend fun saveAvailabilityStatus(status: String) {
        isPreferencesSaving = true
        preferencesError = ""
        val result = apiService.setAvailabilityStatus(status)
        result.fold(
            onSuccess = {
                availabilityStatus = status
                preferencesSuccess = "Status updated"
                android.util.Log.d("ProfileScreen", "Availability status saved: $status")
            },
            onFailure = { error ->
                preferencesError = "Failed to update status: ${error.message}"
                android.util.Log.e("ProfileScreen", "Error saving status: ${error.message}")
            }
        )
        isPreferencesSaving = false
    }

    suspend fun saveStatusMessage(message: String) {
        isPreferencesSaving = true
        preferencesError = ""
        val result = apiService.setStatusMessage(message)
        result.fold(
            onSuccess = {
                statusMessage = message
                preferencesSuccess = "Status message updated"
                android.util.Log.d("ProfileScreen", "Status message saved: $message")
            },
            onFailure = { error ->
                preferencesError = "Failed to update message: ${error.message}"
                android.util.Log.e("ProfileScreen", "Error saving message: ${error.message}")
            }
        )
        isPreferencesSaving = false
    }

    suspend fun saveLanguagePreference(language: String) {
        isPreferencesSaving = true
        preferencesError = ""
        val result = apiService.setLanguagePreference(language)
        result.fold(
            onSuccess = {
                preferredLanguage = language
                preferencesSuccess = "Language updated"
                android.util.Log.d("ProfileScreen", "Language preference saved: $language")
            },
            onFailure = { error ->
                preferencesError = "Failed to update language: ${error.message}"
                android.util.Log.e("ProfileScreen", "Error saving language: ${error.message}")
            }
        )
        isPreferencesSaving = false
    }

    suspend fun saveThemePreference(theme: String) {
        isPreferencesSaving = true
        preferencesError = ""
        val result = apiService.setThemePreference(theme)
        result.fold(
            onSuccess = {
                preferredTheme = theme
                preferencesSuccess = "Theme updated"
                android.util.Log.d("ProfileScreen", "Theme preference saved: $theme")
            },
            onFailure = { error ->
                preferencesError = "Failed to update theme: ${error.message}"
                android.util.Log.e("ProfileScreen", "Error saving theme: ${error.message}")
            }
        )
        isPreferencesSaving = false
    }

    // Auto-dismiss preference messages
    if (preferencesSuccess.isNotEmpty()) {
        LaunchedEffect(key1 = preferencesSuccess) {
            try {
                kotlinx.coroutines.delay(2000)
                preferencesSuccess = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("ProfileScreen", "Success message auto-dismiss cancelled")
            }
        }
    }

    if (preferencesError.isNotEmpty()) {
        LaunchedEffect(key1 = preferencesError) {
            try {
                kotlinx.coroutines.delay(3000)
                preferencesError = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("ProfileScreen", "Error message auto-dismiss cancelled")
            }
        }
    }

    // Animations
    val profileScale by animateFloatAsState(
        targetValue = if (isEditingProfile) 0.95f else 1f,
        animationSpec = tween(300), label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== CYBERPUNK HEADER =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CyberpunkTheme.PrimaryMagenta, CyberpunkTheme.DarkGray)
                        )
                    ),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "> PROFILE_CONFIG <",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = CyberpunkTheme.PrimaryPurple
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== AVATAR SECTION =====
            Box(
                modifier = Modifier.size(156.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .clip(CircleShape)
                        .background(
                            if (pendingImageUri != null)
                                CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.5f)
                            else
                                CyberpunkTheme.PrimaryPurple.copy(alpha = 0.35f)
                        )
                )

                // Main avatar circle
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(profileScale)
                        .clip(CircleShape)
                        .background(CyberpunkTheme.PrimaryMagenta)
                        .border(
                            width = if (pendingImageUri != null) 3.dp else 2.dp,
                            color = if (pendingImageUri != null) CyberpunkTheme.PrimaryMagenta else CyberpunkTheme.PrimaryPurple,
                            shape = CircleShape
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showAvatarOptions = true },
                    contentAlignment = Alignment.Center
                ) {
                    // Layer 1: initial letter — always visible as fallback
                    Text(
                        text = (username.firstOrNull() ?: '?').toString().uppercase(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CyberpunkTheme.PrimaryPurple
                    )

                    // Layer 2: actual photo (covers the letter when loaded successfully)
                    val imageData: Any? = when {
                        pendingImageUri != null -> pendingImageUri
                        !profileImageUrl.isNullOrEmpty() && profileImageUrl != "null" -> profileImageUrl
                        else -> null
                    }
                    if (imageData != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageData)
                                .crossfade(300)
                                .build(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Layer 3: uploading overlay
                    if (isUploadingImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = CyberpunkTheme.PrimaryPurple,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                }

                // Edit button (bottom-right, outside the circle)
                if (!isUploadingImage) {
                    SmallFloatingActionButton(
                        onClick = { showAvatarOptions = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(36.dp),
                        shape = CircleShape,
                        containerColor = CyberpunkTheme.PrimaryPurple,
                        contentColor = CyberpunkTheme.CyberBlack
                    ) {
                        Icon(
                            if (pendingImageUri != null) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (pendingImageUri != null) "Confirm photo" else "Change photo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Pending-image action bar (confirm / cancel)
            AnimatedVisibility(
                visible = pendingImageUri != null && !isUploadingImage,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { pendingImageUri = null },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.GhostGray),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CANCEL", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { scope.launch { confirmUpload() } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(16.dp), tint = CyberpunkTheme.CyberBlack)
                        Spacer(Modifier.width(4.dp))
                        Text("UPLOAD", fontSize = 12.sp, color = CyberpunkTheme.CyberBlack)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ===== PROFILE BANNER SECTION =====
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(12.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showBannerOptions = true },
                colors = CardDefaults.cardColors(containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Banner image display
                    val bannerImageData: Any? = when {
                        pendingBannerUri != null -> pendingBannerUri
                        !profileBannerUrl.isNullOrEmpty() && profileBannerUrl != "null" -> profileBannerUrl
                        else -> null
                    }
                    
                    if (bannerImageData != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(bannerImageData)
                                .crossfade(300)
                                .build(),
                            contentDescription = "Profile Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "Upload Banner",
                                modifier = Modifier.size(40.dp),
                                tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap to upload banner",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray
                            )
                        }
                    }

                    // Edit button (bottom-right)
                    SmallFloatingActionButton(
                        onClick = { showBannerOptions = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp),
                        shape = CircleShape,
                        containerColor = CyberpunkTheme.PrimaryMagenta,
                        contentColor = CyberpunkTheme.CyberBlack
                    ) {
                        Icon(
                            if (pendingBannerUri != null) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = "Edit banner",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Uploading overlay
                    if (isUploadingBanner) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = CyberpunkTheme.PrimaryPurple,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                }
            }

            // Pending-banner action bar (confirm / cancel)
            AnimatedVisibility(
                visible = pendingBannerUri != null && !isUploadingBanner,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { pendingBannerUri = null },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.GhostGray),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CANCEL", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { scope.launch { confirmBannerUpload() } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryMagenta),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(16.dp), tint = CyberpunkTheme.CyberBlack)
                        Spacer(Modifier.width(4.dp))
                        Text("UPLOAD", fontSize = 12.sp, color = CyberpunkTheme.CyberBlack)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ===== USER INFO SECTION =====
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                // Username - Terminal style
                Text(
                    "[$username]",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = CyberpunkTheme.PrimaryPurple
                )

                Spacer(modifier = Modifier.height(4.dp))

                // User ID - Dimmed
                Text(
                    "ID: $userId",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberpunkTheme.GhostGray
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status badge - Pulsing
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(20.dp)),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyberpunkTheme.PrimaryPurple)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            userStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.PrimaryPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bio display
                if (userBio.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, CyberpunkTheme.PrimaryMagenta, RoundedCornerShape(8.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "// $userBio",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberpunkTheme.GhostGray,
                            maxLines = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ===== CUSTOMIZATION SECTION =====
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(12.dp))
                    .background(CyberpunkTheme.DarkGray.copy(alpha = 0.3f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "[CUSTOMIZATION_OPTIONS]",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = CyberpunkTheme.PrimaryPurple
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Avatar options (Discord-like)
                Text(
                    "Avatar Color Themes:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.GhostGray
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CyberpunkTheme.PrimaryMagenta,
                        CyberpunkTheme.PrimaryPurple,
                        0xFF00FF9F to "Neon",
                        0xFFFF006E to "Magenta",
                        0xFF00D9FF to "Cyan+"
                    ).forEachIndexed { index, item ->
                        val color = if (item is Color) {
                            item
                        } else if (item is Pair<*, *>) {
                            Color((item.first as Long))
                        } else {
                            Color(item as Long)
                        }
                        
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .clickable { selectedAvatar = index },
                            color = color,
                            border = if (selectedAvatar == index) {
                                androidx.compose.foundation.BorderStroke(3.dp, CyberpunkTheme.PrimaryPurple)
                            } else {
                                null
                            }
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick edit buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isEditingProfile = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryMagenta
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EDIT", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = { showAvatarOptions = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.AccountCircle, "Avatar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AVATAR", style = MaterialTheme.typography.labelSmall, color = CyberpunkTheme.CyberBlack)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== PREFERENCES SECTION =====
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "> ACCOUNT_PREFERENCES <",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.PrimaryPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Availability Status
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Availability Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("available", "away", "busy", "offline").forEach { status ->
                                Button(
                                    onClick = {
                                        scope.launch {
                                            saveAvailabilityStatus(status)
                                        }
                                    },
                                    enabled = !isPreferencesSaving,
                                    modifier = Modifier
                                        .height(28.dp)
                                        .wrapContentWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (availabilityStatus == status)
                                            CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                                        disabledContainerColor = CyberpunkTheme.DarkGray
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        status.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = if (availabilityStatus == status)
                                            CyberpunkTheme.CyberBlack else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Status Message
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Status Message (max 500 chars)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var messageInput by remember { mutableStateOf(statusMessage) }
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { if (it.length <= 500) messageInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp),
                                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberpunkTheme.PrimaryMagenta,
                                    unfocusedBorderColor = CyberpunkTheme.DarkGray,
                                    focusedLabelColor = CyberpunkTheme.PrimaryMagenta
                                ),
                                placeholder = { Text("Your status...", fontSize = 10.sp) },
                                singleLine = false,
                                maxLines = 2
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        saveStatusMessage(messageInput)
                                    }
                                },
                                enabled = !isPreferencesSaving && messageInput != statusMessage,
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberpunkTheme.CyberCyan,
                                    disabledContainerColor = CyberpunkTheme.DarkGray
                                ),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("SET", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = CyberpunkTheme.CyberBlack)
                            }
                        }
                    }

                    // Language & Theme Preferences
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Language
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Language",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray,
                                fontSize = 10.sp
                            )
                            var expandedLang by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedLang,
                                onExpandedChange = { expandedLang = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = preferredLanguage.uppercase(),
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberpunkTheme.PrimaryMagenta,
                                        unfocusedBorderColor = CyberpunkTheme.DarkGray
                                    ),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang) }
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedLang,
                                    onDismissRequest = { expandedLang = false }
                                ) {
                                    listOf("en", "es", "it", "de", "fr").forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (lang) {
                                                        "en" -> "English"
                                                        "es" -> "Spanish"
                                                        "it" -> "Italian"
                                                        "de" -> "German"
                                                        "fr" -> "French"
                                                        else -> lang.uppercase()
                                                    },
                                                    fontSize = 9.sp
                                                )
                                            },
                                            onClick = {
                                                scope.launch {
                                                    saveLanguagePreference(lang)
                                                }
                                                expandedLang = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Theme
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Theme",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray,
                                fontSize = 10.sp
                            )
                            var expandedTheme by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedTheme,
                                onExpandedChange = { expandedTheme = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = preferredTheme.uppercase(),
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberpunkTheme.PrimaryMagenta,
                                        unfocusedBorderColor = CyberpunkTheme.DarkGray
                                    ),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) }
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedTheme,
                                    onDismissRequest = { expandedTheme = false }
                                ) {
                                    listOf("dark", "light", "cyberpunk").forEach { theme ->
                                        DropdownMenuItem(
                                            text = { Text(theme.uppercase(), fontSize = 9.sp) },
                                            onClick = {
                                                scope.launch {
                                                    saveThemePreference(theme)
                                                }
                                                expandedTheme = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status messages
                    if (preferencesSuccess.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF00FF00).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = Color(0xFF00FF00),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                preferencesSuccess,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00FF00),
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (preferencesError.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                null,
                                tint = Color.Red,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                preferencesError,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== ACTION BUTTONS =====
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onNavigateToSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.DarkGray
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.GhostGray),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Filled.Security, contentDescription = "Security", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SECURITY_SETTINGS", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        prefs.clearAuthData()
                        onLogoutClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B0000)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOGOUT", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ===== AVATAR OPTIONS DIALOG =====
        if (showAvatarOptions) {
            Dialog(
                onDismissRequest = { showAvatarOptions = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(16.dp)),
                    color = CyberpunkTheme.DarkGray
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "[AVATAR_OPTIONS]",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberpunkTheme.PrimaryPurple
                        )
                        Spacer(Modifier.height(12.dp))

                        // Choose from gallery
                        TextButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                null,
                                tint = CyberpunkTheme.PrimaryMagenta,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Choose from Gallery",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                        }

                        HorizontalDivider(color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.25f))

                        // Remove photo (only when a photo exists)
                        if (!profileImageUrl.isNullOrEmpty() && profileImageUrl != "null") {
                            TextButton(
                                onClick = {
                                    showAvatarOptions = false
                                    showRemoveConfirm = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    null,
                                    tint = Color(0xFFFF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Remove Photo",
                                    color = Color(0xFFFF4444),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.weight(1f))
                            }
                            HorizontalDivider(color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.25f))
                        }

                        // Cancel
                        TextButton(
                            onClick = { showAvatarOptions = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = CyberpunkTheme.GhostGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Cancel",
                                color = CyberpunkTheme.GhostGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ===== BANNER OPTIONS DIALOG =====
        if (showBannerOptions) {
            Dialog(
                onDismissRequest = { showBannerOptions = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryMagenta, RoundedCornerShape(16.dp)),
                    color = CyberpunkTheme.DarkGray
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "[BANNER_OPTIONS]",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberpunkTheme.PrimaryMagenta
                        )
                        Spacer(Modifier.height(12.dp))

                        // Choose from gallery
                        TextButton(
                            onClick = { bannerPickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                null,
                                tint = CyberpunkTheme.PrimaryMagenta,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Choose from Gallery",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                        }

                        HorizontalDivider(color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.25f))

                        // ✅ NEW: Delete banner (only show if banner exists)
                        if (!profileBannerUrl.isNullOrEmpty() && profileBannerUrl != "null") {
                            TextButton(
                                onClick = {
                                    showBannerOptions = false
                                    showBannerDeleteConfirm = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    null,
                                    tint = Color(0xFFFF4757),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Delete Banner",
                                    color = Color(0xFFFF4757),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.weight(1f))
                            }

                            HorizontalDivider(color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.25f))
                        }

                        // Cancel
                        TextButton(
                            onClick = { showBannerOptions = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = CyberpunkTheme.GhostGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Cancel",
                                color = CyberpunkTheme.GhostGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ===== REMOVE PHOTO CONFIRM DIALOG =====
        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = {
                    Text(
                        "> REMOVE_PHOTO <",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF4444)
                    )
                },
                text = {
                    Text(
                        "Your profile photo will be removed and all other users will see your initial instead.",
                        color = CyberpunkTheme.GhostGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRemoveConfirm = false
                            scope.launch {
                                isUploadingImage = true
                                try {
                                    val result = apiService.deleteProfileImage()
                                    result.fold(
                                        onSuccess = {
                                            profileImageUrl = null
                                            prefs.saveProfileImage("")
                                            updateError = ""
                                        },
                                        onFailure = { err ->
                                            updateError = "Failed to remove photo: ${err.message}"
                                        }
                                    )
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    updateError = "Error: ${e.message}"
                                } finally {
                                    isUploadingImage = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                    ) {
                        Text("REMOVE", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showRemoveConfirm = false },
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.GhostGray)
                    ) {
                        Text("CANCEL", color = CyberpunkTheme.GhostGray)
                    }
                },
                containerColor = CyberpunkTheme.DarkGray,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ===== DELETE BANNER CONFIRM DIALOG =====
        if (showBannerDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBannerDeleteConfirm = false },
                title = {
                    Text(
                        "> DELETE_BANNER <",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF4757)
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to remove your banner? This action cannot be undone.",
                        color = CyberpunkTheme.GhostGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBannerDeleteConfirm = false
                            scope.launch {
                                isUploadingImage = true
                                try {
                                    val result = apiService.deleteBanner()
                                    result.fold(
                                        onSuccess = {
                                            profileBannerUrl = null
                                            prefs.saveBannerImage("")
                                            updateError = ""
                                        },
                                        onFailure = { err ->
                                            updateError = "Failed to delete banner: ${err.message}"
                                        }
                                    )
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    updateError = "Error: ${e.message}"
                                } finally {
                                    isUploadingImage = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                    ) {
                        Text("DELETE", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showBannerDeleteConfirm = false },
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.GhostGray)
                    ) {
                        Text("CANCEL", color = CyberpunkTheme.GhostGray)
                    }
                },
                containerColor = CyberpunkTheme.DarkGray,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Edit Profile Dialog
        if (isEditingProfile) {
            EditProfileDialogCyberpunk(
                currentUsername = username,
                currentStatus = userStatus,
                currentBio = userBio,
                currentInterests = userInterests,
                isSaving = isSaving,
                onDismiss = { isEditingProfile = false },
                onSave = { newUsername, newStatus, newBio, newInterests ->
                    scope.launch {
                        isSaving = true
                        updateError = ""
                        try {
                            // Username changes route through the dedicated endpoint (enforces 30-day cooldown)
                            val usernameChanged = newUsername.isNotEmpty() && newUsername != username
                            if (usernameChanged) {
                                val usernameResult = apiService.updateUsername(newUsername)
                                if (usernameResult.isFailure) {
                                    updateError = usernameResult.exceptionOrNull()?.message ?: "Username update failed"
                                    isSaving = false
                                    return@launch
                                }
                            }

                            // Update bio, status, and interests via /profile endpoint
                            val result = apiService.updateUserProfile(
                                bio = newBio,
                                status = newStatus,
                                interests = newInterests
                            )

                            if (result.isSuccess) {
                                if (usernameChanged) username = newUsername
                                userStatus = newStatus
                                userBio = newBio
                                userInterests = newInterests
                                android.util.Log.d("PROFILE_EDIT", "Profile updated successfully on server")
                                isSaving = false
                                isEditingProfile = false
                            } else {
                                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to update profile"
                                updateError = errorMsg
                                android.util.Log.e("PROFILE_EDIT", "Profile update failed: $errorMsg")
                                isSaving = false
                            }
                        } catch (e: Exception) {
                            updateError = "Error: ${e.message}"
                            android.util.Log.e("PROFILE_EDIT", "Exception during profile update: ${e.message}", e)
                            isSaving = false
                        }
                    }
                }
            )
        }
        
        // Error message display with proper auto-dismiss
        // Use a separate LaunchedEffect to handle auto-dismiss independently
        if (updateError.isNotEmpty()) {
            // Auto-dismiss error after 3 seconds
            LaunchedEffect(key1 = updateError) {
                try {
                    kotlinx.coroutines.delay(3000)
                    updateError = ""
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation - screen was navigated away
                    android.util.Log.d("ProfileScreen", "Error auto-dismiss cancelled")
                }
            }
            
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFff4444).copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFff4444)),
                shape = RoundedCornerShape(8.dp)
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
                        null,
                        tint = Color(0xFFff4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        updateError,
                        color = Color(0xFFff4444),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialogCyberpunk(
    currentUsername: String,
    currentStatus: String,
    currentBio: String,
    currentInterests: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>) -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }
    var status by remember { mutableStateOf(currentStatus) }
    var bio by remember { mutableStateOf(currentBio) }
    var interests by remember { mutableStateOf(currentInterests.joinToString(", ")) }
    var interestInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(16.dp)),
            color = CyberpunkTheme.DarkGray
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "> EDIT_PROFILE <",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CyberpunkTheme.PrimaryPurple
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("[USER_NAME]", color = CyberpunkTheme.GhostGray) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CyberpunkTheme.PrimaryPurple),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                        unfocusedBorderColor = CyberpunkTheme.DarkGray,
                        focusedLabelColor = CyberpunkTheme.PrimaryPurple
                    )
                )

                // Status dropdown
                var expandedStatus by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedStatus,
                    onExpandedChange = { expandedStatus = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        label = { Text("[STATUS]", color = CyberpunkTheme.GhostGray) },
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CyberpunkTheme.PrimaryPurple),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                            unfocusedBorderColor = CyberpunkTheme.DarkGray,
                            focusedLabelColor = CyberpunkTheme.PrimaryPurple
                        ),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatus) }
                    )
                    ExposedDropdownMenu(expanded = expandedStatus, onDismissRequest = { expandedStatus = false }) {
                        listOf("Online", "Available", "Away", "Do Not Disturb", "Invisible").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = CyberpunkTheme.PrimaryPurple) },
                                onClick = {
                                    status = option
                                    expandedStatus = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("[BIO]", color = CyberpunkTheme.GhostGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CyberpunkTheme.PrimaryPurple),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                        unfocusedBorderColor = CyberpunkTheme.DarkGray,
                        focusedLabelColor = CyberpunkTheme.PrimaryPurple
                    ),
                    maxLines = 4
                )

                // Interests/Tags section
                Text(
                    "[INTERESTS]",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.PrimaryPurple
                )
                
                OutlinedTextField(
                    value = interestInput,
                    onValueChange = { interestInput = it },
                    label = { Text("Add interest...", color = CyberpunkTheme.GhostGray) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CyberpunkTheme.PrimaryPurple),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberpunkTheme.PrimaryMagenta,
                        unfocusedBorderColor = CyberpunkTheme.DarkGray,
                        focusedLabelColor = CyberpunkTheme.PrimaryMagenta
                    ),
                    suffix = {
                        if (interestInput.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val newInterests = interests.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() } + interestInput.trim()
                                    interests = newInterests.takeLast(10).joinToString(", ")
                                    interestInput = ""
                                },
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberpunkTheme.PrimaryMagenta
                                )
                            ) {
                                Text("+", style = MaterialTheme.typography.labelSmall, fontSize = 12.sp)
                            }
                        }
                    }
                )

                // Display interests as tags
                if (interests.isNotEmpty()) {
                    val interestList = interests.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        interestList.forEach { interest ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, CyberpunkTheme.PrimaryMagenta, RoundedCornerShape(16.dp)),
                                color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "#$interest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberpunkTheme.PrimaryMagenta
                                    )
                                    IconButton(
                                        onClick = {
                                            interests = interestList.filter { it != interest }.joinToString(", ")
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(12.dp),
                                            tint = CyberpunkTheme.PrimaryMagenta
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.DarkGray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = CyberpunkTheme.PrimaryPurple)
                    }

                    Button(
                        onClick = {
                            val finalInterests = interests.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(username, status, bio, finalInterests)
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberpunkTheme.CyberBlack)
                        } else {
                            Text("SAVE", color = CyberpunkTheme.CyberBlack)
                        }
                    }
                }
            }
        }
    }
}


