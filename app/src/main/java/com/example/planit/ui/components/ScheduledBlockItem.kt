package com.example.planit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import com.example.planit.ui.theme.ClashDisplay
import com.example.planit.ui.theme.Satoshi
import com.example.planit.ui.theme.SoftPastelPalette
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ScheduledBlockItem composable for the timeline canvas.
 *
 * Requirements:
 * - Deletion triggers via standard tap (simple click) on the card body.
 * - Long-press to drag: Moving the card requires holding the card body for a moment before dragging up/down.
 * - Visual Lift State: When dragging, drop shadow increases to 6.dp and border to 2.5.dp for a lifted effect.
 * - Resize Handle Isolation: Bottom resize handle allows immediate drag resize for flexible activities.
 * - If duration_in_minutes < 45: ONLY shows a horizontal Row with Activity Icon + Title (with Ellipsis).
 * - If duration_in_minutes >= 45: Shows full layout with Icon, Title, and time subtitle.
 * - Transient drag state for zero-lag 120fps movement.
 * - Snapped update on release, strictly preserving duration when moved.
 */
@Composable
fun ScheduledBlockItem(
    blockWithActivity: ScheduledBlockWithActivity,
    hourHeight: Dp,
    timeColumnWidth: Dp,
    onUpdate: (ScheduledBlock) -> Unit,
    onDelete: (ScheduledBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val block = blockWithActivity.scheduledBlock
    val activity = blockWithActivity.activity

    // 1. Time calculations
    val originalStartMinutes = block.startTime.hour * 60 + block.startTime.minute
    val originalEndMinutes = block.endTime.hour * 60 + block.endTime.minute
    val originalDurationMinutes = (originalEndMinutes - originalStartMinutes).coerceAtLeast(0)

    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val minHeightPx = with(density) { 36.dp.toPx() }

    val initialYOffsetPx = with(density) { (hourHeight * (originalStartMinutes / 60f)).toPx() }
    val initialHeightPx = with(density) {
        maxOf(36.dp, hourHeight * (originalDurationMinutes / 60f)).toPx()
    }

    // 2. Transient local drag states for zero-lag movement
    var dragOffsetY by remember(block.id, initialYOffsetPx) { mutableFloatStateOf(initialYOffsetPx) }
    var isDragging by remember { mutableStateOf(false) }

    var currentHeightPx by remember(block.id, initialHeightPx) { mutableFloatStateOf(initialHeightPx) }
    var isResizing by remember { mutableStateOf(false) }

    val visualHeightDp = with(density) { currentHeightPx.toDp() }

    // Live display values
    val liveStartMinutes = ((dragOffsetY / hourHeightPx) * 60f).roundToInt().coerceIn(0, 1439)
    val liveDurationMinutes = ((currentHeightPx / hourHeightPx) * 60f).roundToInt().coerceAtLeast(0)
    val displayStartTime = minutesToLocalTime(liveStartMinutes)
    val displayEndTime = minutesToLocalTime(liveStartMinutes + liveDurationMinutes)
    val timeLabel = "${displayStartTime.format(timeFormatter)} - ${displayEndTime.format(timeFormatter)}"

    val cardBgColor = remember(activity.color) {
        Color(activity.color.toInt())
    }

    val haptic = LocalHapticFeedback.current
    var lastSnappedStartMinutes by remember(block.id, originalStartMinutes) {
        mutableIntStateOf(originalStartMinutes)
    }
    var lastSnappedDurationMinutes by remember(block.id, originalDurationMinutes) {
        mutableIntStateOf(originalDurationMinutes)
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val cornerRadius = 14.dp
    val cardShape = RoundedCornerShape(cornerRadius)

    val maxYPx = with(density) {
        (hourHeight * ((1440 - originalDurationMinutes.coerceAtLeast(15)) / 60f)).toPx()
    }

    val shadowOffset = if (isDragging) 6.dp else 3.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(x = 0, y = dragOffsetY.roundToInt()) }
            .height(visualHeightDp)
            .padding(start = timeColumnWidth + 10.dp, end = 16.dp)
    ) {
        // 1. Hard Drop Shadow (offset x = 3.dp, y = 3.dp; or x = 6.dp, y = 6.dp when lifted)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(Color.Black, shape = cardShape)
        )

        // 2. Main Card Body: Long-press to drag; tap triggers delete confirmation
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cardBgColor, shape = cardShape)
                .border(
                    border = BorderStroke(
                        width = if (isDragging || isResizing) 2.5.dp else 2.dp,
                        color = Color.Black
                    ),
                    shape = cardShape
                )
                .clip(cardShape)
                .pointerInput(block.id) {
                    detectTapGestures(
                        onTap = {
                            if (!isDragging) {
                                showDeleteConfirmationDialog = true
                            }
                        }
                    )
                }
                .pointerInput(block.id, originalStartMinutes, originalDurationMinutes) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, maxYPx)
                            val rawMinutes = (dragOffsetY / hourHeightPx) * 60f
                            val snappedStartMinutes = ((rawMinutes / 15.0).roundToInt() * 15)
                                .coerceIn(0, (1440 - originalDurationMinutes).coerceAtLeast(0))
                            if (snappedStartMinutes != lastSnappedStartMinutes) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastSnappedStartMinutes = snappedStartMinutes
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val rawMinutes = (dragOffsetY / hourHeightPx) * 60f
                            val snappedStartMinutes = ((rawMinutes / 15.0).roundToInt() * 15)
                                .coerceIn(0, (1440 - originalDurationMinutes).coerceAtLeast(0))
                            val newStartTime = minutesToLocalTime(snappedStartMinutes)
                            // Strictly preserve duration
                            val newEndTime = if (activity.isFlexible) {
                                newStartTime.plusMinutes(originalDurationMinutes.toLong())
                            } else {
                                newStartTime.plusMinutes(activity.defaultDuration.toLong())
                            }

                            dragOffsetY = with(density) {
                                (hourHeight * (snappedStartMinutes / 60f)).toPx()
                            }

                            onUpdate(
                                block.copy(
                                    startTime = newStartTime,
                                    endTime = newEndTime
                                )
                            )
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetY = initialYOffsetPx
                        }
                    )
                }
        ) {
            // Content Row on the left and center
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 2.dp,
                        bottom = 2.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Activity Icon
                Box(
                    modifier = Modifier
                        .size(if (liveDurationMinutes < 45) 22.dp else 26.dp)
                        .background(Color.White, shape = CircleShape)
                        .border(BorderStroke(1.2.dp, Color.Black), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resolveIconEmoji(activity.icon),
                        fontSize = if (liveDurationMinutes < 45) 12.sp else 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Layout conditional:
                // If duration < 45 min: ONLY show Activity Title (single row, no subtitle)
                // If duration >= 45 min: Show full layout (Title + time subtitle)
                if (liveDurationMinutes < 45) {
                    Text(
                        text = activity.name,
                        fontFamily = ClashDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = activity.name,
                            fontFamily = ClashDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$timeLabel • ${liveDurationMinutes}m",
                            fontFamily = Satoshi,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = Color.Black.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Resize Handle: Pinned to BottomCenter (Height = 12.dp) - Only enabled if activity is flexible
        if (activity.isFlexible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .pointerInput(block.id, originalStartMinutes, originalDurationMinutes) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentHeightPx = maxOf(minHeightPx, currentHeightPx + dragAmount.y)
                                val rawDurationMin = ((currentHeightPx / hourHeightPx) * 60f).roundToInt()
                                val snappedDurationMin = maxOf(15, ((rawDurationMin / 15.0).roundToInt() * 15))
                                if (snappedDurationMin != lastSnappedDurationMinutes) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastSnappedDurationMinutes = snappedDurationMin
                                }
                            },
                            onDragEnd = {
                                isResizing = false
                                val rawDurationMin = ((currentHeightPx / hourHeightPx) * 60f).roundToInt()
                                val snappedDurationMin = maxOf(15, ((rawDurationMin / 15.0).roundToInt() * 15))
                                val newEndTime = block.startTime.plusMinutes(snappedDurationMin.toLong())

                                currentHeightPx = with(density) {
                                    maxOf(36.dp, hourHeight * (snappedDurationMin / 60f)).toPx()
                                }

                                onUpdate(
                                    block.copy(
                                        endTime = newEndTime
                                    )
                                )
                            },
                            onDragCancel = {
                                isResizing = false
                                currentHeightPx = initialHeightPx
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(
                            color = if (isResizing) Color.Black else Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }

    // Neo-Brutalist Confirmation Dialog on Long-Press
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            containerColor = Color(0xFFFDFDF7),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(16.dp)),
            title = {
                Text(
                    text = "Remove from today?",
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            },
            text = {
                Text(
                    text = "Delete \"${activity.name}\" from today's timeline?",
                    fontFamily = Satoshi,
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        onDelete(block)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Delete",
                        fontFamily = ClashDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun minutesToLocalTime(totalMinutes: Int): LocalTime {
    val clamped = totalMinutes.coerceIn(0, 1439)
    val hour = clamped / 60
    val minute = clamped % 60
    return LocalTime.of(hour, minute)
}

private fun resolveIconEmoji(iconName: String): String {
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
