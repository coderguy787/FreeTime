package com.freetime.app.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.util.Log
import android.widget.Toast
import com.freetime.app.api.FreeTimeApiService
import kotlinx.coroutines.launch

/**
 * Report User Dialog
 * Allows users to report another user for abuse/harassment
 * Shows reason selection, optional description, and reports to backend
 */
@Composable
fun ReportUserDialog(
    userId: String,
    userName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { FreeTimeApiService(context) }
    
    var selectedReason by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val reportReasons = listOf(
        "Harassment or bullying" to "The user is harassing, insulting, or bullying me or others",
        "Hate speech" to "The user is using hate speech or discriminatory language",
        "Spam" to "The user is sending spam or unwanted messages",
        "Inappropriate content" to "The user is sharing inappropriate or offensive content",
        "Scam or fraud" to "The user is attempting to scam or defraud me",
        "Impersonation" to "The user is impersonating someone else",
        "Other" to "Another reason not listed above"
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = !isSubmitting
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Report User",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // User info
                Text(
                    "Reporting: $userName",
                    fontSize = 14.sp,
                    color = Color(0xFFB0B0B0)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Error message if any
                if (error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF662A2A))
                    ) {
                        Text(
                            error ?: "Unknown error",
                            color = Color(0xFFFF6B6B),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Reason selection
                Text(
                    "Select Reason *",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    reportReasons.forEach { (reason, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                enabled = !isSubmitting,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFF00FF),
                                    unselectedColor = Color.Gray
                                )
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    reason,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    description,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB0B0B0),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Additional details
                Text(
                    "Additional Details (Optional)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text("Please provide any additional details...", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF00FF),
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    enabled = !isSubmitting
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A3A)
                        ),
                        enabled = !isSubmitting
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            if (selectedReason.isEmpty()) {
                                error = "Please select a reason"
                                return@Button
                            }
                            
                            isSubmitting = true
                            error = null
                            
                            scope.launch {
                                try {
                                    Log.d("REPORT_USER", "Reporting user $userId for reason: $selectedReason")
                                    val result = apiService.reportUser(
                                        reportedUserId = userId,
                                        reason = selectedReason,
                                        description = description
                                    )
                                    
                                    result.fold(
                                        onSuccess = { reportId ->
                                            Log.d("REPORT_USER", "✅ User reported successfully: $reportId")
                                            Toast.makeText(
                                                context,
                                                "Report submitted successfully",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        },
                                        onFailure = { ex ->
                                            Log.e("REPORT_USER", "❌ Failed to report user: ${ex.message}")
                                            error = ex.message ?: "Failed to submit report"
                                        }
                                    )
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF00FF)
                        ),
                        enabled = !isSubmitting && selectedReason.isNotEmpty()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Report", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Info text
                Text(
                    "Your report helps us keep FreeTime safe. All reports are reviewed by our moderation team.",
                    fontSize = 12.sp,
                    color = Color(0xFF808080),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
