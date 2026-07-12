package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import kotlinx.coroutines.launch

/**
 * JoinGroupRedirectScreen
 * Automatically joins a group via ID or invite code and redirects to the group chat.
 * This screen is used for handling deep links and web invitation links.
 */
@Composable
fun JoinGroupRedirectScreen(
    idOrCode: String,
    onNavigateToGroup: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    var statusMessage by remember { mutableStateOf("Joining group...") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(idOrCode) {
        scope.launch {
            try {
                android.util.Log.d("JoinGroup", "Attempting to join group with: $idOrCode")
                
                // 1. Try to join by code first (common for web links)
                val codeResult = apiService.joinGroupByCode(idOrCode)
                if (codeResult.isSuccess) {
                    val groupId = codeResult.getOrNull()
                    if (!groupId.isNullOrEmpty()) {
                        android.util.Log.d("JoinGroup", "Successfully joined via code, groupId: $groupId")
                        onNavigateToGroup(groupId)
                        return@launch
                    }
                }

                // 2. If code fails, try joining by ID (common for internal deep links)
                val inviteResult = apiService.joinGroupByInvite(idOrCode)
                if (inviteResult.isSuccess) {
                    val groupId = inviteResult.getOrNull()
                    if (!groupId.isNullOrEmpty()) {
                        android.util.Log.d("JoinGroup", "Successfully joined via ID, groupId: $groupId")
                        onNavigateToGroup(groupId)
                        return@launch
                    }
                }

                // 3. If both fail, maybe the user is already a member? Try to get details.
                val detailResult = apiService.getGroupDetails(idOrCode)
                if (detailResult.isSuccess) {
                    android.util.Log.d("JoinGroup", "Already a member or group exists, navigating to chat")
                    onNavigateToGroup(idOrCode)
                } else {
                    // Everything failed
                    val error = detailResult.exceptionOrNull()?.message ?: "Unknown error"
                    android.util.Log.e("JoinGroup", "Failed to join group: $error")
                    statusMessage = "Failed to join group: $error"
                    isError = true
                }
            } catch (e: Exception) {
                android.util.Log.e("JoinGroup", "Error in JoinGroupRedirectScreen: ${e.message}")
                statusMessage = "Error: ${e.message}"
                isError = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            if (!isError) {
                CircularProgressIndicator(
                    color = CyberpunkTheme.PrimaryPurple,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = statusMessage,
                color = if (isError) Color.Red else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (isError) {
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(
                    onClick = onNavigateBack,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.PrimaryPurple
                    )
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}
