package com.example.planit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

/**
 * ScheduledBlockItem composable for the timeline canvas.
 *
 * Phase 3.8: The Butter Update:
 * - Fluid Dragging: Visual offsetY tracks exact pixel movement without lag.
 *   On release, it smoothly animates into the snapped position via spring physics.
 * - Spring Physics: Drop shadow, border width, and scale animate with spring(dampingRatio = MediumBouncy, stiffness = Low).
 *   On long-press lift: springs to scale 1.02f and shadow 8.dp.
 * - Click Squish: Tapping squishes the block down to 0.95f, bouncing back on release.
 * - Deletion triggers via standard tap (simple click) on the card body.
 * - Isolated bottom resize handle allows immediate resize dragging for flexible activities.
 */
@Composable
fun ScheduledBlockItem(
    blockWithActivity: ScheduledBlockWithActivity,
    hourHeight: Dp,
    timeColumnWidth: Dp,
    onUpdate: (ScheduledBlock) -> Unit,
    onDelete: (ScheduledBlock) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChanged: (Boolean) -> Unit = {}
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

    // 2. Transient local drag states & spring animatables for fluid movement
    var dragOffsetY by remember(block.id, initialYOffsetPx) { mutableFloatStateOf(initialYOffsetPx) }
    val animOffsetY = remember(block.id) { Animatable(initialYOffsetPx) }
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

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

    // Spring physics states for shadow, border, and squish/lift scale
    val shadowOffset by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 3.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shadowOffset"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isDragging || isResizing) 2.5.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "borderWidth"
    )

    val targetScale = when {
        isDragging -> 1.02f
        isPressed -> 0.95f
        else -> 1.0f
    }

    val blockScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "blockScale"
    )

    // Sync external block changes smoothly when not dragging
    LaunchedEffect(initialYOffsetPx) {
        if (!isDragging && dragOffsetY != initialYOffsetPx) {
            animOffsetY.snapTo(dragOffsetY)
            animOffsetY.animateTo(
                targetValue = initialYOffsetPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                dragOffsetY = value
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(x = 0, y = dragOffsetY.roundToInt()) }
            .height(visualHeightDp)
            .padding(start = timeColumnWidth + 10.dp, end = 16.dp)
            .graphicsLayer {
                scaleX = blockScale
                scaleY = blockScale
            }
    ) {
        // 1. Hard Drop Shadow (springs between 3.dp and 8.dp on lift)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(Color.Black, shape = cardShape)
        )

        // 2. Main Card Body: Long-press to drag; tap triggers delete confirmation with click squish
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cardBgColor, shape = cardShape)
                .border(
                    border = BorderStroke(
                        width = borderWidth,
                        color = Color.Black
                    ),
                    shape = cardShape
                )
                .clip(cardShape)
                .pointerInput(block.id) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
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
                            onDraggingChanged(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Fluid dragging: visually tracks exact pixel movement
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
                            onDraggingChanged(false)
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

                            val targetSnappedY = with(density) {
                                (hourHeight * (snappedStartMinutes / 60f)).toPx()
                            }

                            // Smoothly animate the block into its final 15-minute snapped position
                            coroutineScope.launch {
                                animOffsetY.snapTo(dragOffsetY)
                                animOffsetY.animateTo(
                                    targetValue = targetSnappedY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) {
                                    dragOffsetY = value
                                }
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
                            onDraggingChanged(false)
                            coroutineScope.launch {
                                animOffsetY.snapTo(dragOffsetY)
                                animOffsetY.animateTo(
                                    targetValue = initialYOffsetPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) {
                                    dragOffsetY = value
                                }
                            }
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
