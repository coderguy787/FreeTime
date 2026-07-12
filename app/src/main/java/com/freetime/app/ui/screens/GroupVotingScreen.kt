package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.GroupDeletionVote
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.theme.LocalDisplaySettings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Group Deletion Voting Screen
 * Shows active deletion votes for a group and allows members to approve or reject.
 * Admins can also start a new deletion vote from here.
 */
@Composable
fun GroupVotingScreen(
    groupId: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()
    val displaySettings = LocalDisplaySettings.current
    val accentColor = displaySettings.getAccentColor()

    var votes by remember { mutableStateOf(listOf<GroupDeletionVote>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isVoting by remember { mutableStateOf(false) }

    fun loadVotes() {
        scope.launch {
            isLoading = true
            errorMessage = ""
            try {
                val result = apiService.getActiveGroupVotes(groupId)
                result.onSuccess { activeVotes -> votes = activeVotes }
                    .onFailure { e -> errorMessage = e.message ?: "Failed to load votes" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage = e.message ?: "Error loading votes"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(groupId) { loadVotes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.Black)
            .systemBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = accentColor)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.HowToVote, null, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Group Deletion Votes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text("Democratic group deletion requires >50% approval", fontSize = 11.sp, color = CyberpunkTheme.LightGray)
            }
            IconButton(onClick = { loadVotes() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", tint = CyberpunkTheme.CyberCyan)
            }
        }

        // Status messages
        if (successMessage.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFF1B5E20),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(successMessage, color = Color.White, fontSize = 13.sp)
                }
            }
        }
        if (errorMessage.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFF7F0000),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (votes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.HowToVote, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Text("No Active Votes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("There are no active deletion votes\nfor this group right now.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(votes) { vote ->
                    DeletionVoteCard(
                        vote = vote,
                        isProcessing = isVoting,
                        accentColor = accentColor,
                        onApprove = {
                            isVoting = true
                            errorMessage = ""
                            successMessage = ""
                            scope.launch {
                                try {
                                    val result = apiService.castGroupDeletionVote(groupId, vote.voteId, approve = true)
                                    result.onSuccess {
                                        successMessage = "Your approval was recorded."
                                        loadVotes()
                                    }.onFailure { e ->
                                        errorMessage = e.message ?: "Failed to cast vote"
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    errorMessage = e.message ?: "Error"
                                } finally {
                                    isVoting = false
                                }
                            }
                        },
                        onReject = {
                            isVoting = true
                            errorMessage = ""
                            successMessage = ""
                            scope.launch {
                                try {
                                    val result = apiService.castGroupDeletionVote(groupId, vote.voteId, approve = false)
                                    result.onSuccess {
                                        successMessage = "Your rejection was recorded."
                                        loadVotes()
                                    }.onFailure { e ->
                                        errorMessage = e.message ?: "Failed to cast vote"
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    errorMessage = e.message ?: "Error"
                                } finally {
                                    isVoting = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletionVoteCard(
    vote: GroupDeletionVote,
    isProcessing: Boolean,
    accentColor: Color = CyberpunkTheme.PrimaryPurple,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val approvePercent = vote.approvalPercentage.coerceIn(0f, 100f)
    val rejectPercent = if (vote.totalMembers > 0)
        ((vote.rejectingVotes.toFloat() / vote.totalMembers) * 100).toInt().coerceIn(0, 100)
    else 0
    
    // Dynamic timer that updates every minute
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vote.expiresAt) {
        while (true) {
            currentTime = System.currentTimeMillis()
            val remaining = vote.expiresAt - currentTime
            if (remaining <= 0) break
            // Update every minute, or every second if under 5 minutes
            val delayMs = if (remaining < 300_000) 1_000L else 60_000L
            kotlinx.coroutines.delay(delayMs)
        }
    }
    val timeLeft = run {
        val remaining = vote.expiresAt - currentTime
        if (remaining <= 0) "Expired"
        else {
            val hours = remaining / 3_600_000
            val minutes = (remaining % 3_600_000) / 60_000
            if (hours > 0) "${hours}h ${minutes}m left"
            else "${minutes}m left"
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        color = Color(0xFF0F0F23)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Group Deletion Vote", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF6B35))
                    Text("Initiated by @${vote.initiatedByUsername}", fontSize = 11.sp, color = CyberpunkTheme.LightGray)
                }
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1A1A2E)) {
                    Text(timeLeft, fontSize = 10.sp, color = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Divider(color = accentColor.copy(alpha = 0.2f))

            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                VoteStat("Total Members", vote.totalMembers.toString(), CyberpunkTheme.LightGray)
                VoteStat("Approved", vote.approvingVotes.toString(), Color(0xFF4CAF50))
                VoteStat("Rejected", vote.rejectingVotes.toString(), Color(0xFFEF5350))
                VoteStat("Approval", "${approvePercent}%", accentColor)
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress to 50% threshold", fontSize = 11.sp, color = CyberpunkTheme.LightGray)
                    Text("${approvePercent}% / 50% needed", fontSize = 11.sp, color = accentColor)
                }
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A2E))) {
                    Box(modifier = Modifier.fillMaxWidth(approvePercent / 100f).fillMaxHeight()
                        .background(if (approvePercent > 50) Color(0xFF4CAF50) else accentColor))
                    // 50% threshold line
                    Box(modifier = Modifier.offset(x = 0.dp).fillMaxWidth(0.5f).fillMaxHeight())
                }
            }

            // Voting buttons
            if (vote.hasUserVoted) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You have already voted", fontSize = 13.sp, color = CyberpunkTheme.CyberCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedButton(
                        onClick = onApprove,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        else {
                            Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    ElevatedButton(
                        onClick = onReject,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF7F0000))
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        else {
                            Icon(Icons.Default.ThumbDown, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reject", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 10.sp, color = CyberpunkTheme.LightGray)
    }
}

@Composable
fun GroupVotingScreenEnhanced(
    onBackClick: () -> Unit = {},
    onVoteClick: (optionId: String) -> Unit = {}
) {
    // Legacy composable — kept for reference.
    // The live implementation is GroupVotingScreen(groupId, onBackClick)
    GroupVotingScreen(groupId = "", onBackClick = onBackClick)
}

