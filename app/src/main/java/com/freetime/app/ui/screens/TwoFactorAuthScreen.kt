@file:Suppress("UNUSED_VARIABLE", "DEPRECATION")

package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme


@Composable
fun TwoFactorAuthScreenEnhanced(
    onBackClick: () -> Unit = {},
    onVerifyClick: (code: String) -> Unit = {}
) {
    var code1 by remember { mutableStateOf("") }
    var code2 by remember { mutableStateOf("") }
    var code3 by remember { mutableStateOf("") }
    var code4 by remember { mutableStateOf("") }
    var code5 by remember { mutableStateOf("") }
    var code6 by remember { mutableStateOf("") }

    val fullCode = "$code1$code2$code3$code4$code5$code6"
    val isComplete = fullCode.length == 6

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        "> 2FA_VERIFICATION <",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = CyberpunkTheme.PrimaryPurple
                    )

                    Box(modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ICON
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(12.dp)),
                color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.2f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = "2FA",
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // TITLE
            Text(
                "AUTHENTICATION_REQUIRED",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = CyberpunkTheme.PrimaryPurple,
                textAlign = TextAlign.Center
            )

            // DESCRIPTION
            Text(
                "Enter the 6-digit code from your authenticator app",
                style = MaterialTheme.typography.bodyMedium,
                color = CyberpunkTheme.GhostGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CODE INPUT FIELDS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TwoFADigitField(
                    value = code1,
                    onValueChange = { code1 = it.take(1) },
                    isFocused = code1.isNotEmpty()
                )
                TwoFADigitField(
                    value = code2,
                    onValueChange = { code2 = it.take(1) },
                    isFocused = code2.isNotEmpty()
                )
                TwoFADigitField(
                    value = code3,
                    onValueChange = { code3 = it.take(1) },
                    isFocused = code3.isNotEmpty()
                )
                TwoFADigitField(
                    value = code4,
                    onValueChange = { code4 = it.take(1) },
                    isFocused = code4.isNotEmpty()
                )
                TwoFADigitField(
                    value = code5,
                    onValueChange = { code5 = it.take(1) },
                    isFocused = code5.isNotEmpty()
                )
                TwoFADigitField(
                    value = code6,
                    onValueChange = { code6 = it.take(1) },
                    isFocused = code6.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // VERIFY BUTTON
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = isComplete) {
                        if (isComplete) {
                            onVerifyClick(fullCode)
                        }
                    }
                    .border(
                        2.dp,
                        if (isComplete) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                        RoundedCornerShape(8.dp)
                    ),
                color = if (isComplete) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f) else CyberpunkTheme.DarkGray.copy(alpha = 0.2f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Verify",
                            tint = if (isComplete) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "VERIFY_CODE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = if (isComplete) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ALTERNATIVE METHOD
            Text(
                "Can't access your authenticator?",
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkTheme.GhostGray
            )

            Text(
                "USE_BACKUP_CODES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.clickable { /* Show backup codes */ }
            )

            Spacer(modifier = Modifier.weight(1f))

            // INFO BOX
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.08f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Info",
                            tint = CyberpunkTheme.PrimaryMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Your authentication code changes every 30 seconds. Make sure to enter the current code.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TwoFADigitField(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean = false
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                shape = RoundedCornerShape(8.dp)
            ),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (value.isEmpty()) {
                Text(
                    "-",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = CyberpunkTheme.GhostGray,
                    fontSize = 20.sp
                )
            } else {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = CyberpunkTheme.PrimaryPurple,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun TwoFactorSetupScreenEnhanced(
    onSetupComplete: (secret: String) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=otpauth://totp/FreeTime"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "> SETUP_2FA <",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = CyberpunkTheme.PrimaryPurple
            )

            Text(
                "Secure your account with two-factor authentication",
                style = MaterialTheme.typography.bodyMedium,
                color = CyberpunkTheme.GhostGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SETUP STEPS
            SetupStep(
                number = "1",
                title = "DOWNLOAD_APP",
                description = "Use Google Authenticator, Authy, or Microsoft Authenticator"
            )

            SetupStep(
                number = "2",
                title = "SCAN_QR_CODE",
                description = "Scan this code with your authenticator app"
            )

            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(8.dp)),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[QR_CODE_PLACEHOLDER]", color = CyberpunkTheme.DarkGray)
                }
            }

            SetupStep(
                number = "3",
                title = "ENTER_CODE",
                description = "Enter the 6-digit code to verify setup"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // BACKUP CODES WARNING
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color(0xFFFFA500), RoundedCornerShape(8.dp)),
                color = Color(0xFFFFA500).copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFFA500),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                "SAVE_BACKUP_CODES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFA500)
                            )
                            Text(
                                "We'll provide backup codes. Store them safely - they can recover your account.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.GhostGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ACTION BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCancel() }
                        .border(1.dp, CyberpunkTheme.GhostGray.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "CANCEL",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSetupComplete("") }
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(8.dp)),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "CONTINUE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberpunkTheme.PrimaryPurple,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(50),
            color = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.PrimaryPurple
                )
            }
        }

        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CyberpunkTheme.PrimaryPurple
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = CyberpunkTheme.GhostGray
            )
        }
    }
}


