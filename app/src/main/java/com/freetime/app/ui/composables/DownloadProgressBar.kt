package com.freetime.app.ui.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme

@Composable
fun DownloadProgressBar(
    progress: Float,
    fileName: String = "",
    modifier: Modifier = Modifier,
    trackColor: Color = CyberpunkTheme.DarkGray,
    progressColor: Color = CyberpunkTheme.PrimaryPurple,
    textColor: Color = CyberpunkTheme.GhostGray
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "downloadProgress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (fileName.isNotEmpty()) {
            Text(
                text = fileName,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = trackColor
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
