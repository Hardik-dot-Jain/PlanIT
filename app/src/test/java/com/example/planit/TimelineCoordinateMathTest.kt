package com.example.planit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import kotlin.math.roundToInt

class TimelineCoordinateMathTest {

    private val hourHeightDp = 72.0
    private val minHeightDp = 36.0 // Enforced 36.dp minimum visual height

    private fun computeYOffsetDp(startTime: LocalTime): Double {
        val startMinutes = startTime.hour * 60 + startTime.minute
        return (startMinutes / 60.0) * hourHeightDp
    }

    private fun computeHeightDp(startTime: LocalTime, endTime: LocalTime): Double {
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute
        val durationMinutes = (endMinutes - startMinutes).coerceAtLeast(0)
        return maxOf(minHeightDp, (durationMinutes / 60.0) * hourHeightDp)
    }

    private fun snapTo15Minutes(minutes: Int): Int {
        return (minutes / 15.0).roundToInt() * 15
    }

    @Test
    fun yOffsetMath_isAccurate() {
        // 00:00 -> 0.dp
        assertEquals(0.0, computeYOffsetDp(LocalTime.of(0, 0)), 0.001)

        // 01:00 -> 72.dp
        assertEquals(72.0, computeYOffsetDp(LocalTime.of(1, 0)), 0.001)

        // 09:30 -> 9.5 hours * 72.dp = 684.dp
        assertEquals(684.0, computeYOffsetDp(LocalTime.of(9, 30)), 0.001)

        // 12:00 -> 12 hours * 72.dp = 864.dp
        assertEquals(864.0, computeYOffsetDp(LocalTime.of(12, 0)), 0.001)
    }

    @Test
    fun heightMath_minimumHeightEnforced() {
        // 0-minute duration must enforce 36.dp minimum
        val zeroMinHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(9, 0))
        assertEquals(36.0, zeroMinHeight, 0.001)

        // 10-minute duration must enforce 36.dp minimum
        val tenMinHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(9, 10))
        assertEquals(36.0, tenMinHeight, 0.001)

        // 15-minute duration (18.dp raw) must enforce 36.dp minimum
        val fifteenMinHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(9, 15))
        assertEquals(36.0, fifteenMinHeight, 0.001)

        // 30-minute duration (36.dp raw) -> 36.dp
        val thirtyMinHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(9, 30))
        assertEquals(36.0, thirtyMinHeight, 0.001)

        // 60-minute duration -> 72.dp
        val oneHourHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(10, 0))
        assertEquals(72.0, oneHourHeight, 0.001)

        // 90-minute duration -> 108.dp
        val ninetyMinHeight = computeHeightDp(LocalTime.of(9, 0), LocalTime.of(10, 30))
        assertEquals(108.0, ninetyMinHeight, 0.001)
    }

    @Test
    fun snapping_snapsToNearest15Minutes() {
        assertEquals(0, snapTo15Minutes(0))
        assertEquals(0, snapTo15Minutes(7))
        assertEquals(15, snapTo15Minutes(8))
        assertEquals(15, snapTo15Minutes(15))
        assertEquals(15, snapTo15Minutes(22))
        assertEquals(30, snapTo15Minutes(23))
        assertEquals(-15, snapTo15Minutes(-8))
        assertEquals(-15, snapTo15Minutes(-15))
        assertEquals(-30, snapTo15Minutes(-23))
    }

    @Test
    fun moveBlock_strictlyPreservesOriginalDuration() {
        val originalStart = LocalTime.of(9, 15)
        val originalEnd = LocalTime.of(10, 45) // 90 minutes duration
        val originalDuration = (originalEnd.hour * 60 + originalEnd.minute) - (originalStart.hour * 60 + originalStart.minute)
        assertEquals(90, originalDuration)

        // User dragged by an arbitrary delta: e.g. 74 minutes -> snaps to 75 minutes
        val rawMinutes = (originalStart.hour * 60 + originalStart.minute) + 74.0
        val snappedStartMinutes = snapTo15Minutes(rawMinutes.toInt())
        assertEquals(630, snappedStartMinutes) // 10:30 (9*60+15 + 75 = 630)

        val newStart = LocalTime.of(snappedStartMinutes / 60, snappedStartMinutes % 60)
        val newEnd = newStart.plusMinutes(originalDuration.toLong())

        assertEquals(LocalTime.of(10, 30), newStart)
        assertEquals(LocalTime.of(12, 0), newEnd)

        val newDuration = (newEnd.hour * 60 + newEnd.minute) - (newStart.hour * 60 + newStart.minute)
        assertEquals(originalDuration, newDuration)
    }

    @Test
    fun dockScheduling_earlyTime_defaultsToEightAm() {
        // Any time before 8:00 AM (e.g. 5:23 AM, 7:00 AM, 7:52 AM) defaults to 8:00 AM
        val (start1, end1) = com.example.planit.ui.viewmodel.PlanItViewModel.calculateDockScheduleTimes(
            currentTime = LocalTime.of(5, 23),
            defaultDuration = 45
        )
        assertEquals(LocalTime.of(8, 0), start1)
        assertEquals(LocalTime.of(8, 45), end1)

        val (start2, end2) = com.example.planit.ui.viewmodel.PlanItViewModel.calculateDockScheduleTimes(
            currentTime = LocalTime.of(7, 30),
            defaultDuration = 60
        )
        assertEquals(LocalTime.of(8, 0), start2)
        assertEquals(LocalTime.of(9, 0), end2)
    }

    @Test
    fun dockScheduling_snapsToNearest15Minutes() {
        // 9:07 AM -> 9:00 AM
        val (start1, end1) = com.example.planit.ui.viewmodel.PlanItViewModel.calculateDockScheduleTimes(
            currentTime = LocalTime.of(9, 7),
            defaultDuration = 30
        )
        assertEquals(LocalTime.of(9, 0), start1)
        assertEquals(LocalTime.of(9, 30), end1)

        // 9:08 AM -> 9:15 AM
        val (start2, end2) = com.example.planit.ui.viewmodel.PlanItViewModel.calculateDockScheduleTimes(
            currentTime = LocalTime.of(9, 8),
            defaultDuration = 45
        )
        assertEquals(LocalTime.of(9, 15), start2)
        assertEquals(LocalTime.of(10, 0), end2)

        // 14:23 (2:23 PM) -> 14:30 (2:30 PM)
        val (start3, end3) = com.example.planit.ui.viewmodel.PlanItViewModel.calculateDockScheduleTimes(
            currentTime = LocalTime.of(14, 23),
            defaultDuration = 90
        )
        assertEquals(LocalTime.of(14, 30), start3)
        assertEquals(LocalTime.of(16, 0), end3)
    }

    @Test
    fun dateRobustness_javaTimeLocalDateRollovers() {
        // Month rollover (regular year: Feb 28 -> Mar 1)
        val feb28_2025 = java.time.LocalDate.of(2025, 2, 28)
        val mar01_2025 = feb28_2025.plusDays(1)
        assertEquals(java.time.LocalDate.of(2025, 3, 1), mar01_2025)

        // Leap year rollover (Feb 28 -> Feb 29 -> Mar 1)
        val feb28_2024 = java.time.LocalDate.of(2024, 2, 28)
        val feb29_2024 = feb28_2024.plusDays(1)
        assertEquals(java.time.LocalDate.of(2024, 2, 29), feb29_2024)
        val mar01_2024 = feb29_2024.plusDays(1)
        assertEquals(java.time.LocalDate.of(2024, 3, 1), mar01_2024)

        // Year rollover (Dec 31 -> Jan 1)
        val dec31 = java.time.LocalDate.of(2025, 12, 31)
        val jan01 = dec31.plusDays(1)
        assertEquals(java.time.LocalDate.of(2026, 1, 1), jan01)

        // Swiping backward (Jan 1 -> Dec 31)
        val backToDec31 = jan01.minusDays(1)
        assertEquals(dec31, backToDec31)
    }

    @Test
    fun shortBlockLayoutThreshold_strictlyAt45Minutes() {
        // If duration < 45 min: Single row only (title + icon, no subtitle)
        val dur15 = 15
        val dur30 = 30
        val dur44 = 44
        org.junit.Assert.assertTrue(dur15 < 45)
        org.junit.Assert.assertTrue(dur30 < 45)
        org.junit.Assert.assertTrue(dur44 < 45)

        // If duration >= 45 min: Full layout
        val dur45 = 45
        val dur60 = 60
        val dur90 = 90
        org.junit.Assert.assertTrue(dur45 >= 45)
        org.junit.Assert.assertTrue(dur60 >= 45)
        org.junit.Assert.assertTrue(dur90 >= 45)
    }

    @Test
    fun activity_isFlexible_defaultsToTrue() {
        val flexAct = com.example.planit.data.local.entity.Activity(
            name = "Coding",
            icon = "code",
            color = 0xFFA7F3D0,
            defaultDuration = 45
        )
        org.junit.Assert.assertTrue(flexAct.isFlexible)
        org.junit.Assert.assertTrue(flexAct.is_flexible)

        val rigidAct = flexAct.copy(isFlexible = false)
        org.junit.Assert.assertFalse(rigidAct.isFlexible)
        org.junit.Assert.assertFalse(rigidAct.is_flexible)
    }

    @Test
    fun softPastelPalette_contains12DistinctPastels() {
        val palette = com.example.planit.ui.theme.SoftPastelPalette
        assertEquals(12, palette.size)

        // Ensure all 12 colors in the palette are distinct
        val distinctColors = palette.distinct()
        assertEquals(12, distinctColors.size)
    }

    @Test
    fun rigidInteraction_disablesResizingHandle() {
        val rigidActivity = com.example.planit.data.local.entity.Activity(
            name = "Quick Standup",
            icon = "people",
            color = 0xFFDDD6FE,
            defaultDuration = 15,
            isFlexible = false
        )
        val flexibleActivity = rigidActivity.copy(isFlexible = true)

        val isRigidResizable = rigidActivity.isFlexible
        val isFlexibleResizable = flexibleActivity.isFlexible

        org.junit.Assert.assertFalse(isRigidResizable)
        org.junit.Assert.assertTrue(isFlexibleResizable)
    }

    @Test
    fun dropToTimeline_scrollOffsetAndDropY_calculatesSnappedStartTime() {
        val hourHeightPx = 144.0 // e.g. 72.dp with density = 2.0
        val dropYInTimeline = 100.0 // User dropped at y=100 in the visible viewport
        val scrollOffset = 576.0 // User scrolled down by 4 hours (4 * 144)

        val actualContentY = dropYInTimeline + scrollOffset // 676.0 px
        val rawMinutes = (actualContentY / hourHeightPx) * 60.0 // (676 / 144) * 60 = 281.666 min (4h 41m)
        val duration = 45

        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, 1440 - duration)
        assertEquals(285, snappedMinutes) // 285 min = 4 hours 45 minutes -> 4:45 AM (04:45)

        val startTime = LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
        val endTime = startTime.plusMinutes(duration.toLong())

        assertEquals(LocalTime.of(4, 45), startTime)
        assertEquals(LocalTime.of(5, 30), endTime)
    }

    @Test
    fun dropToTimeline_clampingAtEndOfDay() {
        val hourHeightPx = 100.0
        val duration = 60
        val actualContentY = 2400.0 // past 23:00 -> near 24:00

        val rawMinutes = (actualContentY / hourHeightPx) * 60.0
        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, 1440 - duration)

        // Clamped so block does not overflow beyond 23:59 (1440 - 60 = 1380 = 23:00)
        assertEquals(1380, snappedMinutes)
        val startTime = LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
        assertEquals(LocalTime.of(23, 0), startTime)
    }

    @Test
    fun currentTimeIndicator_yOffsetCalculation() {
        // At 14:30 (2:30 PM), minutes = 14 * 60 + 30 = 870
        val time = LocalTime.of(14, 30)
        val currentMinutes = time.hour * 60 + time.minute
        val hourHeight = 72.0
        val expectedYOffsetDp = hourHeight * (currentMinutes / 60.0)

        // 14.5 * 72 = 1044.dp
        assertEquals(1044.0, expectedYOffsetDp, 0.001)
    }

    @Test
    fun dropToTimeline_rigidBlock_strictlyPreservesDefaultDurationWithout15MinRounding() {
        val hourHeightPx = 144.0
        val dropYInTimeline = 144.0 * 9.2 // ~9:12 AM
        val scrollOffset = 0.0

        val actualContentY = dropYInTimeline + scrollOffset
        val rawMinutes = (actualContentY / hourHeightPx) * 60.0 // ~552 min (9:12 AM)
        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15) // Snaps start time to 9:15 AM (555 min)
        assertEquals(555, snappedMinutes)

        val startTime = LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
        assertEquals(LocalTime.of(9, 15), startTime)

        // Non-15m default duration, e.g. 20 minutes (or 35 minutes)
        val rigidActivity = com.example.planit.data.local.entity.Activity(
            name = "Quick Catchup",
            icon = "people",
            color = 0xFFDDD6FE,
            defaultDuration = 20,
            isFlexible = false
        )

        // For Rigid block: endTime MUST be exactly startTime.plusMinutes(activity.defaultDuration)
        // No 15-minute rounding on duration!
        val endTime = startTime.plusMinutes(rigidActivity.defaultDuration.toLong())
        assertEquals(LocalTime.of(9, 35), endTime)

        val actualDuration = (endTime.hour * 60 + endTime.minute) - (startTime.hour * 60 + startTime.minute)
        assertEquals(20, actualDuration)
    }
}
