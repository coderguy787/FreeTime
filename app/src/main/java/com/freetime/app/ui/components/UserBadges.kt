package com.freetime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout

/**
 * Badge model from server
 */
data class UserBadge(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val description: String,
    val assignedAt: String,
    val assignedBy: String
)

/**
 * Display a single badge with icon and name
 */
@Composable
fun UserBadgeChip(
    badge: UserBadge,
    modifier: Modifier = Modifier
) {
    val badgeColor = try {
        Color(android.graphics.Color.parseColor(badge.color))
    } catch (e: Exception) {
        com.freetime.app.ui.components.CyberpunkTheme.PrimaryMagenta
    }

    Row(
        modifier = modifier
            .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            badge.icon,
            fontSize = 14.sp,
            modifier = Modifier.padding(2.dp)
        )
        Text(
            badge.name,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Display multiple badges in a horizontal scrollable row
 */
@Composable
fun UserBadgesRow(
    badges: List<UserBadge>,
    modifier: Modifier = Modifier
) {
    if (badges.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.take(6).forEach { badge ->
            UserBadgeChip(badge = badge)
        }
        
        // Show "+N more" if more than 6 badges
        if (badges.size > 6) {
            Text(
                "+${badges.size - 6} more",
                color = com.freetime.app.ui.components.CyberpunkTheme.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * Display badges grid for profile page
 */
@Composable
fun UserBadgesGrid(
    badges: List<UserBadge>,
    modifier: Modifier = Modifier
) {
    if (badges.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Badges & Achievements",
            color = com.freetime.app.ui.components.CyberpunkTheme.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            badges.forEach { badge ->
                UserBadgeChip(badge = badge)
            }
        }
    }
}

/**
 * Flow layout - arrange items in rows
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        var yPosition = 0
        var xPosition = 0
        var maxHeightInRow = 0
        val rows = mutableListOf<MutableList<Pair<Int, Int>>>()
        var currentRow = mutableListOf<Pair<Int, Int>>()

        placeables.forEach { placeable ->
            if (xPosition + placeable.width > constraints.maxWidth) {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                xPosition = 0
                yPosition += maxHeightInRow
                maxHeightInRow = 0
            }

            currentRow.add(xPosition to yPosition)
            xPosition += placeable.width + 8.dp.roundToPx() // 8.dp spacing
            maxHeightInRow = maxOf(maxHeightInRow, placeable.height)
        }

        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        layout(constraints.maxWidth, yPosition + maxHeightInRow) {
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { itemIndex, (x, y) ->
                    placeables[rows.take(rowIndex).sumOf { it.size } + itemIndex].place(x, y)
                }
            }
        }
    }
}
