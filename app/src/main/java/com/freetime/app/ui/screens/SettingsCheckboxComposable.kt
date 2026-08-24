package com.freetime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme

@Composable
fun SettingsCheckbox(
    label: String,
    description: String = "",
    value: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                color = CyberpunkTheme.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (description.isNotEmpty()) {
                Text(
                    description,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp
                )
            }
        }
        Checkbox(
            checked = value,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CyberpunkTheme.CyberCyan,
                uncheckedColor = CyberpunkTheme.MediumGray
            )
        )
    }
}
