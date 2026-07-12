package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.MediaDownloadRequestInfo
import kotlinx.coroutines.launch

data class MediaItem(
    val id: String,
    val name: String,
    val type: String, // image, video, document
    val size: Long,
    val date: String,
    val isSelected: Boolean = false,
    val thumbnail: String = "", // placeholder in a real app, this would be image path
    val duration: String? = null,  // for videos
    val isApproved: Boolean = true,  // NEW: Track if media has been approved for preview
    val isRequestingApproval: Boolean = false  // NEW: Track if approval is pending
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    onNavigateBack: () -> Unit,
    onSelectMedia: (List<MediaItem>) -> Unit = {}
) {
    var selectedMediaList by remember { mutableStateOf(listOf<MediaItem>()) }
    var viewMode by remember { mutableStateOf("grid") } // grid, list
    var filterType by remember { mutableStateOf("all") } // all, image, video, document
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf<MediaItem?>(null) }
    var showApprovalDialog by remember { mutableStateOf<String?>(null) }
    var showPendingRequests by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var isRequestingDownload by remember { mutableStateOf(false) }
    var pendingRequests by remember { mutableStateOf(listOf<MediaDownloadRequestInfo>()) }
    var isLoadingRequests by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load pending requests on screen open
    LaunchedEffect(Unit) {
        isLoadingRequests = true
        coroutineScope.launch {
            val result = apiService.getPendingMediaDownloadRequests()
            isLoadingRequests = false
            result.onSuccess { requests ->
                pendingRequests = requests
            }
        }
    }
    var showPreview by remember { mutableStateOf<MediaItem?>(null) }
    var showInfoDialog by remember { mutableStateOf<MediaItem?>(null) }

    val allMedia = remember {
        listOf(
            MediaItem(
                id = "1",
                name = "Project_Screenshot.png",
                type = "image",
                size = 2048000,
                date = "Today",
                thumbnail = "IMG1"
            ),
            MediaItem(
                id = "2",
                name = "Demo_Video.mp4",
                type = "video",
                size = 15728640,
                date = "Yesterday",
                duration = "2:45",
                thumbnail = "VID1"
            ),
            MediaItem(
                id = "3",
                name = "Design_Mockup.png",
                type = "image",
                size = 3145728,
                date = "Yesterday",
                thumbnail = "IMG2"
            ),
            MediaItem(
                id = "4",
                name = "Presentation.pdf",
                type = "document",
                size = 5242880,
                date = "2 days ago",
                thumbnail = "DOC1"
            ),
            MediaItem(
                id = "5",
                name = "Meeting_Recording.mp4",
                type = "video",
                size = 52428800,
                date = "3 days ago",
                duration = "45:20",
                thumbnail = "VID2"
            ),
            MediaItem(
                id = "6",
                name = "Wireframe.png",
                type = "image",
                size = 1572864,
                date = "1 week ago",
                thumbnail = "IMG3"
            ),
        )
    }

    val filteredMedia = remember(filterType) {
        if (filterType == "all") allMedia else allMedia.filter { it.type == filterType }
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
            MediaGalleryHeader(
                selectedCount = selectedMediaList.size,
                pendingRequestCount = pendingRequests.size,
                onNavigateBack = onNavigateBack,
                onSelectAll = {
                    selectedMediaList = if (selectedMediaList.size == filteredMedia.size) {
                        emptyList()
                    } else {
                        filteredMedia
                    }
                },
                onShowPendingRequests = { showPendingRequests = true },
                onConfirm = {
                    onSelectMedia(selectedMediaList)
                    onNavigateBack()
                }
            )
            
            // Pending Download Requests Bar (for senders)
            if (pendingRequests.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "You have ${pendingRequests.size} pending download request(s)",
                        color = CyberpunkTheme.PrimaryPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showPendingRequests = true },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Review", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Advanced Options Bar (when selection)
            if (selectedMediaList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberpunkTheme.Black.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = CyberpunkTheme.PrimaryPurple)
                    }
                    IconButton(onClick = { showPreview = selectedMediaList.firstOrNull() }) {
                        Icon(Icons.Default.Preview, contentDescription = "Preview", tint = CyberpunkTheme.CyberCyan)
                    }
                    IconButton(onClick = { showInfoDialog = selectedMediaList.firstOrNull() }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = CyberpunkTheme.LightGray)
                    }
                }
            }

            // Filter Tabs
            MediaFilterTabs(
                currentFilter = filterType,
                onFilterChange = { filterType = it }
            )

            // View Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFF0F0F1E))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { viewMode = "grid" },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.GridView,
                        null,
                        tint = if (viewMode == "grid") CyberpunkTheme.PrimaryPurple else CyberpunkTheme.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { viewMode = "list" },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ViewList,
                        null,
                        tint = if (viewMode == "list") CyberpunkTheme.PrimaryPurple else CyberpunkTheme.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Media List/Grid
            if (viewMode == "grid") {
                MediaGridView(
                    media = filteredMedia,
                    selectedMedia = selectedMediaList,
                    onMediaSelected = { item ->
                        selectedMediaList = if (selectedMediaList.contains(item)) {
                            selectedMediaList - item
                        } else {
                            selectedMediaList + item
                        }
                    }
                )
            } else {
                MediaListView(
                    media = filteredMedia,
                    selectedMedia = selectedMediaList,
                    onMediaSelected = { item ->
                        selectedMediaList = if (selectedMediaList.contains(item)) {
                            selectedMediaList - item
                        } else {
                            selectedMediaList + item
                        }
                    }
                )
            }
        }

        // Options Dropdown (Delete, Share, Download)
        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            modifier = Modifier.background(CyberpunkTheme.Black)
        ) {
            DropdownMenuItem(
                text = { Text("Delete", color = CyberpunkTheme.PrimaryPurple) },
                onClick = {
                    selectedMediaList = emptyList()
                    showOptionsMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CyberpunkTheme.PrimaryPurple) }
            )
            DropdownMenuItem(
                text = { Text("Share", color = CyberpunkTheme.CyberCyan) },
                onClick = { showOptionsMenu = false },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = CyberpunkTheme.CyberCyan) }
            )
            DropdownMenuItem(
                text = { Text("Download", color = CyberpunkTheme.LightGray) },
                onClick = {
                    showOptionsMenu = false
                    showDownloadDialog = selectedMediaList.firstOrNull()
                },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = CyberpunkTheme.LightGray) }
            )
        }

        // Download Request Dialog
        if (showDownloadDialog != null) {
            val media = showDownloadDialog
            AlertDialog(
                onDismissRequest = { showDownloadDialog = null; downloadStatus = null },
                title = { Text("Request Download") },
                text = {
                    Column {
                        Text("To download this media, you must request permission from the sender.")
                        if (downloadStatus != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(downloadStatus!!, color = if (downloadStatus!!.contains("approved")) CyberpunkTheme.CyberCyan else CyberpunkTheme.PrimaryPurple)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!isRequestingDownload && media != null) {
                                isRequestingDownload = true
                                coroutineScope.launch {
                                    val result = apiService.requestMediaDownload(media.id)
                                    isRequestingDownload = false
                                    result.onSuccess { (requestId, status) ->
                                        downloadStatus = if (status == "pending") {
                                            "Download request sent. Awaiting sender approval."
                                        } else if (status == "approved") {
                                            "Download approved! You may now download and decrypt this media."
                                        } else {
                                            "Request status: $status"
                                        }
                                    }.onFailure {
                                        downloadStatus = "Failed to request download: ${it.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isRequestingDownload
                    ) {
                        Text(if (isRequestingDownload) "Requesting..." else "Request Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = null; downloadStatus = null }) { Text("Cancel") }
                }
            )
        }

        // Download Dialog: Download and decrypt media to device gallery
        if (showPreview != null) {
            var isDownloading by remember { mutableStateOf(false) }
            var downloadError by remember { mutableStateOf<String?>(null) }
            var downloadSuccess by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { 
                    if (!isDownloading) {
                        showPreview = null
                        downloadError = null
                        downloadSuccess = false
                    }
                },
                title = { Text("${showPreview?.name}") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Media Info
                        Text("Type: ${showPreview?.type}", fontSize = 12.sp, color = CyberpunkTheme.LightGray)
                        Text("Size: ${showPreview?.size?.let { formatMediaFileSize(it) }}", fontSize = 12.sp, color = CyberpunkTheme.LightGray)
                        Text("Date: ${showPreview?.date}", fontSize = 12.sp, color = CyberpunkTheme.LightGray)
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Status Messages
                        if (downloadSuccess) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = CyberpunkTheme.SuccessGreen, modifier = Modifier.size(20.dp))
                                Text("Downloaded to gallery!", color = CyberpunkTheme.SuccessGreen, fontSize = 12.sp)
                            }
                        } else if (downloadError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF5D1B1B), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = CyberpunkTheme.ErrorRed, modifier = Modifier.size(20.dp))
                                Text(downloadError!!, color = CyberpunkTheme.ErrorRed, fontSize = 11.sp)
                            }
                        } else if (isDownloading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberpunkTheme.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = CyberpunkTheme.CyberCyan
                                )
                                Text("Decrypting and downloading...", color = CyberpunkTheme.CyberCyan, fontSize = 12.sp)
                            }
                        } else if (!showPreview!!.isApproved && !showPreview!!.isRequestingApproval) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF3D2514), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = CyberpunkTheme.WarningOrange, modifier = Modifier.size(20.dp))
                                Text("Request download permission first", color = CyberpunkTheme.WarningOrange, fontSize = 11.sp)
                            }
                        } else if (!showPreview!!.isApproved && showPreview!!.isRequestingApproval) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2B3A3A), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = CyberpunkTheme.WarningOrange, modifier = Modifier.size(20.dp))
                                Text("Your request is pending sender approval", color = CyberpunkTheme.WarningOrange, fontSize = 11.sp)
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1B3A3A), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(20.dp))
                                Text("Download approved by sender", color = CyberpunkTheme.CyberCyan, fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { 
                                if (!isDownloading) {
                                    showPreview = null
                                    downloadError = null
                                    downloadSuccess = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { 
                            Text("Close") 
                        }
                        
                        if (showPreview!!.isApproved && !downloadSuccess) {
                            Button(
                                onClick = {
                                    isDownloading = true
                                    downloadError = null
                                    coroutineScope.launch {
                                        try {
                                            // Decrypt and download to gallery
                                            val result = apiService.downloadMediaFile(
                                                mediaId = showPreview!!.id,
                                                fileName = showPreview!!.name,
                                                mediaType = showPreview!!.type,
                                                mediaKey = ""
                                            )
                                            isDownloading = false
                                            result.onSuccess {
                                                downloadSuccess = true
                                            }.onFailure { error ->
                                                downloadError = error.message ?: "Download failed"
                                            }
                                        } catch (e: Exception) {
                                            isDownloading = false
                                            downloadError = e.message ?: "Download failed"
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberpunkTheme.CyberCyan
                                )
                            ) { 
                                Text(
                                    if (isDownloading) "Downloading..." else "Download to Gallery",
                                    color = CyberpunkTheme.Black
                                ) 
                            }
                        } else if (!showPreview!!.isApproved && !showPreview!!.isRequestingApproval) {
                            Button(
                                onClick = { showDownloadDialog = showPreview },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberpunkTheme.PrimaryPurple
                                )
                            ) { 
                                Text("Request Permission") 
                            }
                        }
                    }
                }
            )
        }

        // Pending Download Requests Dialog (for senders to approve/deny)
        if (showPendingRequests) {
            AlertDialog(
                onDismissRequest = { showPendingRequests = false },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Download Requests (${pendingRequests.size})", fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { 
                                isLoadingRequests = true
                                coroutineScope.launch {
                                    val result = apiService.getPendingMediaDownloadRequests()
                                    isLoadingRequests = false
                                    result.onSuccess { requests ->
                                        pendingRequests = requests
                                    }
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = CyberpunkTheme.CyberCyan)
                        }
                    }
                },
                text = {
                    if (isLoadingRequests) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = CyberpunkTheme.PrimaryPurple)
                            Spacer(Modifier.height(8.dp))
                            Text("Loading requests...", color = CyberpunkTheme.LightGray)
                        }
                    } else if (pendingRequests.isEmpty()) {
                        Text("No pending download requests", color = CyberpunkTheme.LightGray)
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            pendingRequests.forEach { request ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            request.requesterName ?: "User",
                                            color = CyberpunkTheme.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            request.reason,
                                            color = CyberpunkTheme.LightGray,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "Requested: ${request.requestedAt.split("T")[0]}",
                                            color = CyberpunkTheme.LightGray.copy(alpha = 0.6f),
                                            fontSize = 9.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { showApprovalDialog = request.requestId },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                null,
                                                tint = CyberpunkTheme.CyberCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { 
                                                coroutineScope.launch {
                                                    apiService.denyMediaDownloadRequest(request.requestId)
                                                    pendingRequests = pendingRequests.filter { it.requestId != request.requestId }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                null,
                                                tint = CyberpunkTheme.PrimaryPurple,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPendingRequests = false }) { Text("Close") }
                }
            )
        }

        // Approval Confirmation Dialog
        if (showApprovalDialog != null) {
            AlertDialog(
                onDismissRequest = { showApprovalDialog = null },
                title = { Text("Confirm Approval") },
                text = { Text("Allow this user to download and decrypt this media?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                apiService.approveMediaDownloadRequest(showApprovalDialog!!)
                                pendingRequests = pendingRequests.filter { it.requestId != showApprovalDialog }
                                showApprovalDialog = null
                            }
                        }
                    ) {
                        Text("Approve")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApprovalDialog = null }) { Text("Cancel") }
                }
            )
        }

        // Info Dialog
        if (showInfoDialog != null) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = null },
                title = { Text("Media Info") },
                text = {
                    Column {
                        Text("Name: ${showInfoDialog?.name}")
                        Text("Type: ${showInfoDialog?.type}")
                        Text("Size: ${showInfoDialog?.size?.let { formatMediaFileSize(it) }}")
                        Text("Date: ${showInfoDialog?.date}")
                        if (showInfoDialog?.type == "video") {
                            Text("Duration: ${showInfoDialog?.duration}")
                        }
                        Text("ID: ${showInfoDialog?.id}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = null }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
fun MediaGalleryHeader(
    selectedCount: Int,
    pendingRequestCount: Int = 0,
    onNavigateBack: () -> Unit,
    onSelectAll: () -> Unit,
    onShowPendingRequests: () -> Unit = {},
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF0F0F1E),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                null,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Text(
            if (selectedCount > 0) "Select Media ($selectedCount)" else "Gallery",
            style = MaterialTheme.typography.titleLarge,
            color = CyberpunkTheme.White,
            fontWeight = FontWeight.Bold
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selectedCount > 0) {
                IconButton(onClick = onSelectAll, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.SelectAll,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (selectedCount > 0) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.PrimaryPurple
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MediaFilterTabs(
    currentFilter: String,
    onFilterChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xFF0F0F1E))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("all", "image", "video", "document").forEach { filter ->
            Button(
                onClick = { onFilterChange(filter) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentFilter == filter) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f) else Color.Transparent,
                    contentColor = if (currentFilter == filter) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.LightGray
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (currentFilter == filter) BorderStroke(1.5.dp, CyberpunkTheme.PrimaryPurple) else BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    filter.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MediaGridView(
    media: List<MediaItem>,
    selectedMedia: List<MediaItem>,
    onMediaSelected: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFF0F0F1E)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(media) { item ->
            MediaGridItem(
                item = item,
                isSelected = selectedMedia.contains(item),
                onClick = { onMediaSelected(item) }
            )
        }
    }
}

@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f) else Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Media type icon/thumbnail
        when (item.type) {
            "image" -> {
                Icon(
                    Icons.Default.Image,
                    null,
                    tint = CyberpunkTheme.CyberCyan,
                    modifier = Modifier.size(36.dp)
                )
            }
            "video" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF000000).copy(alpha = 0.5f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            item.duration ?: "0:00",
                            color = CyberpunkTheme.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            "document" -> {
                Icon(
                    Icons.Default.Description,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        // Selection checkbox
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(
                        color = CyberpunkTheme.PrimaryPurple,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = CyberpunkTheme.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun MediaListView(
    media: List<MediaItem>,
    selectedMedia: List<MediaItem>,
    onMediaSelected: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFF0F0F1E)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(media) { item ->
            MediaListItem(
                item = item,
                isSelected = selectedMedia.contains(item),
                onClick = { onMediaSelected(item) }
            )
        }
    }
}

@Composable
fun MediaListItem(
    item: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f) else Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail box
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = when (item.type) {
                        "image" -> CyberpunkTheme.CyberCyan.copy(alpha = 0.2f)
                        "video" -> CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                        else -> CyberpunkTheme.LightGray.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            when (item.type) {
                "image" -> Icon(Icons.Default.Image, null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(28.dp))
                "video" -> Icon(Icons.Default.Videocam, null, tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(28.dp))
                "document" -> Icon(Icons.Default.Description, null, tint = CyberpunkTheme.LightGray, modifier = Modifier.size(28.dp))
            }
        }
        
        // Item info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.name,
                color = CyberpunkTheme.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatMediaFileSize(item.size),
                    color = CyberpunkTheme.LightGray,
                    fontSize = 11.sp
                )
                Text(
                    "•",
                    color = CyberpunkTheme.LightGray.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                Text(
                    item.date,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 11.sp
                )
            }
        }
        
        // Selection checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { },
            colors = CheckboxDefaults.colors(
                checkedColor = CyberpunkTheme.PrimaryPurple,
                uncheckedColor = CyberpunkTheme.LightGray.copy(alpha = 0.5f)
            )
        )
    }
}

fun formatMediaFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024 * 1024).toDouble())
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
