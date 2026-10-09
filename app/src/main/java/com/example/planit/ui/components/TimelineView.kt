package com.example.planit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import com.example.planit.ui.theme.CanvasBackground
import com.example.planit.ui.theme.ClashDisplay
import com.example.planit.ui.theme.DottedGridColor
import com.example.planit.ui.theme.Satoshi
import com.example.planit.ui.theme.TimeMarkerText
import kotlinx.coroutines.delay

// Hardcoded vertical scale: 1 hour = 72.dp -> 1.2.dp per minute
val HourHeight: Dp = 72.dp
val MinuteScale: Dp = 1.2.dp
val TimeColumnWidth: Dp = 68.dp
const val TotalHours: Int = 24

/**
 * Vertically scrollable 24-hour timeline area with a dotted background grid (#E0E0D4)
 * and time markers from 12 AM to 11 PM along the left edge.
 *
 * Renders movable, resizable, and deletable [ScheduledBlockItem] cards.
 * Also renders a translucent, spring-animated ghost preview block when an activity is dragged.
 * Vertical scale is hardcoded at 1 hour = 72.dp.
 */
@Composable
fun TimelineView(
    scheduledBlocks: List<ScheduledBlockWithActivity> = emptyList(),
    selectedDate: LocalDate = LocalDate.now(),
    scrollState: ScrollState = rememberScrollState(),
    onUpdateBlock: (ScheduledBlock) -> Unit = {},
    onDeleteBlock: (ScheduledBlock) -> Unit = {},
    modifier: Modifier = Modifier,
    initialScrollHour: Int = 8, // Scrolls automatically to 8 AM on load
    draggingActivity: Activity? = null,
    dragPosition: Offset = Offset.Zero
) {
    val density = LocalDensity.current
    val totalHeight = HourHeight * TotalHours
    val haptic = LocalHapticFeedback.current
    var timelineBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    // Calculate preview start time when an activity is dragged over this timeline viewport
    val isFingerOverTimeline = draggingActivity != null &&
        timelineBoundsInRoot.width > 0f &&
        dragPosition.x in timelineBoundsInRoot.left..timelineBoundsInRoot.right &&
        dragPosition.y in timelineBoundsInRoot.top..timelineBoundsInRoot.bottom

    val previewStartTime = if (isFingerOverTimeline) {
        val minuteScalePx = with(density) { MinuteScale.toPx() }
        val localY = (dragPosition.y - timelineBoundsInRoot.top) + scrollState.value
        val rawMinutes = localY / minuteScalePx
        val duration = draggingActivity!!.defaultDuration
        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15)
            .coerceIn(0, (1440 - duration).coerceAtLeast(0))
        LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
    } else null

    // Live haptic feedback on snapping to each new 15-minute slot
    var previousPreviewStartTime by remember { mutableStateOf<LocalTime?>(null) }
    LaunchedEffect(previewStartTime) {
        if (previewStartTime != null && previewStartTime != previousPreviewStartTime) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            previousPreviewStartTime = previewStartTime
        } else if (previewStartTime == null) {
            previousPreviewStartTime = null
        }
    }

    // Automatically scroll to morning hours on initial appearance
    LaunchedEffect(Unit) {
        val targetScrollY = with(density) { (HourHeight * initialScrollHour).roundToPx() }
        scrollState.scrollTo(targetScrollY)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasBackground)
            .onGloballyPositioned { coordinates ->
                timelineBoundsInRoot = coordinates.boundsInRoot()
            }
            .verticalScroll(scrollState)
    ) {
        // Container with fixed height equal to 24 hours * 72.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
        ) {
            // 1. Dotted Background Grid & Hour Divider Lines
            TimelineGridCanvas(
                modifier = Modifier.fillMaxSize(),
                timeColumnWidth = TimeColumnWidth,
                hourHeight = HourHeight,
                totalHours = TotalHours
            )

            // 2. Time Markers Column (12 AM to 11 PM)
            TimeMarkersColumn(
                modifier = Modifier
                    .width(TimeColumnWidth)
                    .fillMaxHeight(),
                hourHeight = HourHeight
            )

            // 3. Drop Preview "Ghost" Block (magnetic 15-minute snap preview)
            AnimatedVisibility(
                visible = previewStartTime != null && draggingActivity != null,
                enter = fadeIn(animationSpec = tween(80)),
                exit = fadeOut(animationSpec = tween(80))
            ) {
                if (previewStartTime != null && draggingActivity != null) {
                    TimelineGhostBlock(
                        activity = draggingActivity,
                        startTime = previewStartTime,
                        minuteScale = MinuteScale,
                        timeColumnWidth = TimeColumnWidth
                    )
                }
            }

            // 4. Interactive Scheduled Blocks on the Timeline
            var draggingBlockId by remember { mutableStateOf<Long?>(null) }

            scheduledBlocks.forEach { blockWithActivity ->
                val blockId = blockWithActivity.scheduledBlock.id
                key(blockId) {
                    var isVisible by remember { mutableStateOf(true) }
                    var isDeleted by remember { mutableStateOf(false) }

                    val isThisBlockDragging = draggingBlockId == blockId

                    AnimatedVisibility(
                        visible = isVisible && !isDeleted,
                        enter = fadeIn(animationSpec = tween(50)) + scaleIn(
                            animationSpec = tween(100),
                            initialScale = 0.95f
                        ),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.zIndex(if (isThisBlockDragging) 10f else 1f)
                    ) {
                        ScheduledBlockItem(
                            blockWithActivity = blockWithActivity,
                            hourHeight = HourHeight,
                            timeColumnWidth = TimeColumnWidth,
                            onUpdate = onUpdateBlock,
                            onDelete = {
                                isDeleted = true
                            },
                            onDraggingChanged = { dragging ->
                                draggingBlockId = if (dragging) blockId else null
                            }
                        )
                    }

                    LaunchedEffect(isDeleted) {
                        if (isDeleted) {
                            delay(300)
                            onDeleteBlock(blockWithActivity.scheduledBlock)
                        }
                    }
                }
            }

            // 4. Current Time Indicator (if selectedDate is today)
            if (selectedDate == LocalDate.now()) {
                CurrentTimeIndicator(
                    hourHeight = HourHeight,
                    timeColumnWidth = TimeColumnWidth
                )
            }
        }
    }
}

/**
 * Renders a distinct, 2.dp thick horizontal red line with a small red dot on the left axis
 * corresponding to the current actual time (LocalTime.now()).
 */
@Composable
fun CurrentTimeIndicator(
    hourHeight: Dp,
    timeColumnWidth: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEF4444)
) {
    val now = remember { LocalTime.now() }
    val currentMinutes = now.hour * 60 + now.minute
    val indicatorYDp = hourHeight * (currentMinutes / 60f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = indicatorYDp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .offset(y = (-7).dp)
        ) {
            val startXPx = timeColumnWidth.toPx()
            val centerXPx = startXPx
            val centerYPx = 7.dp.toPx()

            // 2.dp thick horizontal red line
            drawLine(
                color = color,
                start = Offset(startXPx, centerYPx),
                end = Offset(size.width, centerYPx),
                strokeWidth = 2.dp.toPx()
            )

            // Small red dot on the left axis
            drawCircle(
                color = color,
                radius = 4.5.dp.toPx(),
                center = Offset(centerXPx, centerYPx)
            )
        }
    }
}

/**
 * Canvas drawing the dotted background grid (#E0E0D4), the time column separator,
 * and dashed horizontal hour dividers.
 */
@Composable
private fun TimelineGridCanvas(
    timeColumnWidth: Dp,
    hourHeight: Dp,
    totalHours: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val startXPx = timeColumnWidth.toPx()
        val hourHeightPx = hourHeight.toPx()
        val gridColor = DottedGridColor

        // Subtle vertical separator line between time markers and schedule grid
        drawLine(
            color = gridColor,
            start = Offset(startXPx, 0f),
            end = Offset(startXPx, size.height),
            strokeWidth = 1.5.dp.toPx()
        )

        // Dotted grid dots across the timeline canvas
        // Spaced at 24.dp horizontally and 18.dp vertically (18.dp = 15 minutes of an hour)
        val dotSpacingX = 24.dp.toPx()
        val dotSpacingY = 18.dp.toPx()
        val dotRadius = 1.25.dp.toPx()

        var y = 0f
        while (y <= size.height) {
            var x = startXPx + dotSpacingX
            while (x <= size.width) {
                drawCircle(
                    color = gridColor,
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                x += dotSpacingX
            }
            y += dotSpacingY
        }

        // Horizontal dashed dividers at each hour line
        val dashEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
            phase = 0f
        )

        for (hour in 0..totalHours) {
            val lineY = hour * hourHeightPx
            drawLine(
                color = gridColor,
                start = Offset(startXPx, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }
    }
}

/**
 * Column rendering time markers from 12 AM to 11 PM on the left edge.
 */
@Composable
private fun TimeMarkersColumn(
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        for (hour in 0 until TotalHours) {
            val timeLabel = formatHour(hour)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight)
                    .offset(y = hourHeight * hour)
                    .padding(end = 10.dp, top = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = timeLabel,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = TimeMarkerText,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Formats a 0-23 hour into a 12-hour AM/PM label.
 */
fun formatHour(hour: Int): String {
    return when (hour) {
        0 -> "12 AM"
        12 -> "12 PM"
        in 1..11 -> "$hour AM"
        else -> "${hour - 12} PM"
    }
}

/**
 * Translucent preview 'ghost' block rendered on the timeline during drag-and-drop.
 * Displays where the dock activity will snap to (15-minute grid), with live spring
 * animations, dashed border, and Modifier.alpha(0.4f).
 */
@Composable
fun TimelineGhostBlock(
    activity: Activity,
    startTime: LocalTime,
    minuteScale: Dp,
    timeColumnWidth: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val startMinutes = startTime.hour * 60 + startTime.minute
    val durationMinutes = activity.defaultDuration
    val endTime = if (activity.isFlexible) {
        if (startMinutes + durationMinutes >= 1440) {
            LocalTime.of(23, 59)
        } else {
            startTime.plusMinutes(durationMinutes.toLong())
        }
    } else {
        startTime.plusMinutes(activity.defaultDuration.toLong())
    }

    val targetYOffsetPx = with(density) { (minuteScale * startMinutes).toPx() }
    val animatedYOffsetPx by animateFloatAsState(
        targetValue = targetYOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ghostYOffset"
    )

    val visualHeightDp = maxOf(36.dp, minuteScale * durationMinutes)
    val cardShape = RoundedCornerShape(14.dp)
    val activityColor = remember(activity.color) { Color(activity.color.toInt()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val timeLabel = "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(5f)
            .offset { IntOffset(x = 0, y = animatedYOffsetPx.roundToInt()) }
            .height(visualHeightDp)
            .padding(start = timeColumnWidth + 10.dp, end = 16.dp)
            .alpha(0.4f)
    ) {
        // 1. Ghost drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black.copy(alpha = 0.2f), shape = cardShape)
        )

        // 2. Ghost Main Card Body with Dashed Border
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(activityColor, shape = cardShape)
                .drawBehind {
                    val strokeWidth = 2.dp.toPx()
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    drawRoundRect(
                        color = Color.Black,
                        size = size,
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = strokeWidth, pathEffect = dashEffect)
                    )
                }
                .clip(cardShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Activity Icon Pill
                Box(
                    modifier = Modifier
                        .size(if (durationMinutes < 45) 22.dp else 26.dp)
                        .background(Color.White, shape = CircleShape)
                        .border(BorderStroke(1.2.dp, Color.Black), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resolveActivityIcon(activity.icon),
                        fontSize = if (durationMinutes < 45) 12.sp else 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (durationMinutes < 45) {
                    Text(
                        text = activity.name,
                        fontFamily = ClashDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
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
                            text = timeLabel,
                            fontFamily = Satoshi,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color.Black.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
