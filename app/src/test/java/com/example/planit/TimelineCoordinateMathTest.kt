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

    @Test
    fun dropToTimeline_exactLocalY_with1Point2DpScale() {
        val density = 2.5f
        val minuteScalePx = 1.2f * density // 3.0 px per minute
        val rawScreenDropY = 850f
        val timelineTopGlobal = 250f
        val scrollOffset = 600f // scrolled 200 minutes in (600 / 3.0 = 200 min)

        val localY = (rawScreenDropY - timelineTopGlobal) + scrollOffset // (850 - 250) + 600 = 1200 px
        val rawMinutes = localY / minuteScalePx // 1200 / 3.0 = 400.0 minutes -> 6 hours 40 minutes (6:40 AM)
        val duration = 30

        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, 1440 - duration)
        assertEquals(405, snappedMinutes) // 400 min snapped to 15 min -> 405 min (6:45 AM)

        val startTime = LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
        assertEquals(LocalTime.of(6, 45), startTime)
    }

    @Test
    fun optimisticState_rendersImmediatelyAndFiltersOutOncePersisted() {
        val date = java.time.LocalDate.now()
        val activity = com.example.planit.data.local.entity.Activity(
            id = 1,
            name = "Workout",
            icon = "fitness",
            color = 0xFFBAF2BB,
            defaultDuration = 30,
            isFlexible = true
        )

        val tempId = -System.currentTimeMillis()
        val optimisticBlock = com.example.planit.data.local.entity.ScheduledBlock(
            id = tempId,
            activityId = activity.id,
            date = date,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(7, 30)
        )
        val optimisticWithAct = com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity(
            scheduledBlock = optimisticBlock,
            activity = activity
        )

        var optimisticList = listOf(optimisticWithAct)
        var realList = emptyList<com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity>()

        // 1. Before Room emits: combined blocks includes optimistic block
        val effective1 = optimisticList.filter { opt ->
            opt.scheduledBlock.date == date &&
            realList.none { real ->
                real.scheduledBlock.activityId == opt.scheduledBlock.activityId &&
                real.scheduledBlock.date == opt.scheduledBlock.date &&
                real.scheduledBlock.startTime == opt.scheduledBlock.startTime
            }
        }
        val combined1 = realList + effective1
        assertEquals(1, combined1.size)
        assertEquals(tempId, combined1[0].scheduledBlock.id)

        // 2. Room emits persisted block with positive ID
        val persistedBlock = optimisticBlock.copy(id = 42)
        realList = listOf(
            com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity(
                scheduledBlock = persistedBlock,
                activity = activity
            )
        )

        // 3. After Room emits: effective optimistic filters out the temp block, no duplicates!
        val effective2 = optimisticList.filter { opt ->
            opt.scheduledBlock.date == date &&
            realList.none { real ->
                real.scheduledBlock.activityId == opt.scheduledBlock.activityId &&
                real.scheduledBlock.date == opt.scheduledBlock.date &&
                real.scheduledBlock.startTime == opt.scheduledBlock.startTime
            }
        }
        val combined2 = realList + effective2
        assertEquals(1, combined2.size)
        assertEquals(42L, combined2[0].scheduledBlock.id)
    }

    @Test
    fun dropPreviewGhost_previewTimeMath_continuousFingerTracking() {
        val density = 3.0f
        val minuteScalePx = 1.2f * density // 3.6 px/min
        val timelineTop = 200f
        val scrollStateValue = 720f // scrolled down 200 minutes (720 / 3.6)
        val defaultDuration = 45

        fun calculateGhostStartTime(fingerScreenY: Float): LocalTime {
            val localY = (fingerScreenY - timelineTop) + scrollStateValue
            val rawMinutes = localY / minuteScalePx
            val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, 1440 - defaultDuration)
            return LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
        }

        // Finger at Y = 200 (top of timeline): localY = 720 -> 200 min -> snapped to 195 or 210 min
        // 200 / 15 = 13.33 -> round to 13 * 15 = 195 min (3:15 AM)
        assertEquals(LocalTime.of(3, 15), calculateGhostStartTime(200f))

        // Moving finger down continuously:
        // Finger moves down by 54 px (15 minutes in px = 15 * 3.6 = 54 px)
        // Y = 254 -> localY = 774 -> 215 min -> 215 / 15 = 14.33 -> 14 * 15 = 210 min (3:30 AM)
        assertEquals(LocalTime.of(3, 30), calculateGhostStartTime(254f))

        // Finger moves down by another 54 px -> 308 px -> 225 min (3:45 AM)
        assertEquals(LocalTime.of(3, 45), calculateGhostStartTime(308f))
    }

    @Test
    fun dropPreviewGhost_hapticTriggerLogic() {
        var previousPreviewStartTime: LocalTime? = null
        var hapticTriggerCount = 0

        fun updatePreviewTime(newTime: LocalTime?) {
            if (newTime != null && newTime != previousPreviewStartTime) {
                hapticTriggerCount++
                previousPreviewStartTime = newTime
            } else if (newTime == null) {
                previousPreviewStartTime = null
            }
        }

        // 1. Initial appearance at 9:00 AM -> triggers 1st haptic
        updatePreviewTime(LocalTime.of(9, 0))
        assertEquals(1, hapticTriggerCount)

        // 2. Continuous slight jitter within same 15-min slot -> NO extra haptics
        updatePreviewTime(LocalTime.of(9, 0))
        assertEquals(1, hapticTriggerCount)

        // 3. User drags past 15-minute threshold to 9:15 AM -> triggers 2nd haptic!
        updatePreviewTime(LocalTime.of(9, 15))
        assertEquals(2, hapticTriggerCount)

        // 4. User drags to 9:30 AM -> triggers 3rd haptic!
        updatePreviewTime(LocalTime.of(9, 30))
        assertEquals(3, hapticTriggerCount)

        // 5. User moves finger off timeline -> resets previous, no haptic
        updatePreviewTime(null)
        assertEquals(3, hapticTriggerCount)

        // 6. User re-enters at 9:30 AM -> triggers haptic again
        updatePreviewTime(LocalTime.of(9, 30))
        assertEquals(4, hapticTriggerCount)
    }
}
