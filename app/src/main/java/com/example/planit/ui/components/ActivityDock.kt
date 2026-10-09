package com.example.planit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.planit.data.local.entity.Activity
import com.example.planit.ui.theme.ClashDisplay
import com.example.planit.ui.theme.Satoshi
import com.example.planit.ui.theme.SoftPastelPalette

/**
 * Bottom dock displaying available activities as 'Soft Neo-Brutalism' chips.
 *
 * Each chip features:
 * - RoundedCornerShape(16.dp)
 * - Solid BorderStroke(2.dp, Color.Black)
 * - Hard drop shadow: solid black shape offset by (x = 4.dp, y = 4.dp)
 * - Soft pastel backgrounds
 */
@Composable
fun ActivityDock(
    activities: List<Activity>,
    modifier: Modifier = Modifier,
    onActivityClick: (Activity) -> Unit = {},
    onDragStarted: (Activity, Offset) -> Unit = { _, _ -> },
    onDragDelta: (Offset) -> Unit = {},
    onDragEnded: () -> Unit = {},
    onDragCanceled: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFDFDF7))
            .border(
                border = BorderStroke(2.dp, Color.Black),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        // Dock Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ACTIVITIES",
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    color = Color.Black
                )

                // Counter pill
                Box(
                    modifier = Modifier
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${activities.size}",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "DRAG TO SCHEDULE",
                fontFamily = Satoshi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = Color(0xFF71716A)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Activity Chips Row
        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No activities available. Create one to get started.",
                    fontFamily = Satoshi,
                    fontSize = 13.sp,
                    color = Color(0xFF71716A)
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(activities, key = { _, act -> act.id }) { _, activity ->
                    val chipColor = Color(activity.color.toInt())
                    NeoBrutalistActivityChip(
                        activity = activity,
                        backgroundColor = chipColor,
                        onClick = { onActivityClick(activity) },
                        onDragStarted = { startPos -> onDragStarted(activity, startPos) },
                        onDragDelta = onDragDelta,
                        onDragEnded = onDragEnded,
                        onDragCanceled = onDragCanceled
                    )
                }
            }
        }
    }
}

/**
 * Individual Activity UI Chip rendered in Soft Neo-Brutalism aesthetic.
 * - Tap opens Edit Activity dialog.
 * - Long-press & drag initiates global drag overlay to drop onto timeline.
 */
@Composable
fun NeoBrutalistActivityChip(
    activity: Activity,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDragStarted: (Offset) -> Unit = {},
    onDragDelta: (Offset) -> Unit = {},
    onDragEnded: () -> Unit = {},
    onDragCanceled: () -> Unit = {}
) {
    val cornerShape = RoundedCornerShape(16.dp)
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .onGloballyPositioned { coordinates ->
                positionInRoot = coordinates.positionInRoot()
            }
            .clickable(onClick = onClick)
            .pointerInput(activity.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        onDragStarted(positionInRoot + localOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = {
                        onDragEnded()
                    },
                    onDragCancel = {
                        onDragCanceled()
                    }
                )
            }
    ) {
        // 1. Hard drop shadow: solid black shape offset by x = 4.dp, y = 4.dp behind the card
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(
                    color = Color.Black,
                    shape = cornerShape
                )
        )

        // 2. Foreground Card: 16.dp rounded corners, 2.dp solid black border, pastel fill
        Box(
            modifier = Modifier
                .background(
                    color = backgroundColor,
                    shape = cornerShape
                )
                .border(
                    border = BorderStroke(2.dp, Color.Black),
                    shape = cornerShape
                )
                .clip(cornerShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Icon avatar container
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White, shape = CircleShape)
                        .border(BorderStroke(1.5.dp, Color.Black), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resolveActivityIcon(activity.icon),
                        fontSize = 18.sp
                    )
                }

                Column {
                    Text(
                        text = activity.name,
                        fontFamily = ClashDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        maxLines = 1
                    )
                    Text(
                        text = "${activity.defaultDuration}m • " + (if (activity.isFlexible) "Flexible" else "Rigid"),
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Maps icon name/identifier to an expressive glyph or emoji.
 */
fun resolveActivityIcon(iconName: String): String {
    return when (iconName.lowercase()) {
        "code", "laptop", "dev" -> "💻"
        "fitness", "gym", "workout" -> "⚡"
        "book", "read", "study" -> "📖"
        "meeting", "people", "call" -> "👥"
        "star", "priority" -> "⭐"
        "coffee", "break" -> "☕"
        "design", "art" -> "🎨"
        "write", "pen" -> "✍️"
        "music" -> "🎵"
        "walk", "run" -> "🏃"
        else -> if (iconName.length in 1..2) iconName else "✦"
    }
}
