package com.example.planit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import com.example.planit.ui.theme.CanvasBackground
import com.example.planit.ui.theme.DottedGridColor
import com.example.planit.ui.theme.Satoshi
import com.example.planit.ui.theme.TimeMarkerText

// Hardcoded vertical scale: 1 hour = 72.dp
val HourHeight: Dp = 72.dp
val TimeColumnWidth: Dp = 68.dp
const val TotalHours: Int = 24

/**
 * Vertically scrollable 24-hour timeline area with a dotted background grid (#E0E0D4)
 * and time markers from 12 AM to 11 PM along the left edge.
 *
 * Renders movable, resizable, and deletable [ScheduledBlockItem] cards.
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
    initialScrollHour: Int = 8 // Scrolls automatically to 8 AM on load
) {
    val density = LocalDensity.current
    val totalHeight = HourHeight * TotalHours

    // Automatically scroll to morning hours on initial appearance
    LaunchedEffect(Unit) {
        val targetScrollY = with(density) { (HourHeight * initialScrollHour).roundToPx() }
        scrollState.scrollTo(targetScrollY)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasBackground)
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

            // 3. Interactive Scheduled Blocks on the Timeline
            scheduledBlocks.forEach { blockWithActivity ->
                key(blockWithActivity.scheduledBlock.id) {
                    ScheduledBlockItem(
                        blockWithActivity = blockWithActivity,
                        hourHeight = HourHeight,
                        timeColumnWidth = TimeColumnWidth,
                        onUpdate = onUpdateBlock,
                        onDelete = onDeleteBlock
                    )
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
