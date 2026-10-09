package com.example.planit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ActivityWithScheduledBlocks
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import com.example.planit.data.repository.PlanItRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * ViewModel managing UI state for activities and scheduled blocks.
 *
 * Utilizes Kotlin [StateFlow] converted from Room [Flow] using
 * [SharingStarted.WhileSubscribed] with a 5000ms stop timeout, adhering
 * to Google's official architecture guidelines for screen rotations and backgrounding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanItViewModel(
    private val repository: PlanItRepository
) : ViewModel() {

    // --- State: Activities ---

    val activities: StateFlow<List<Activity>> = repository.allActivities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val activitiesWithScheduledBlocks: StateFlow<List<ActivityWithScheduledBlocks>> =
        repository.allActivitiesWithScheduledBlocks
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    // --- State: Date Selection & Scheduled Blocks ---

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Reactively streams the scheduled blocks for the currently selected date.
     * Automatically triggers a new Room Flow emission whenever [selectedDate] changes.
     */
    val scheduledBlocksForSelectedDate: StateFlow<List<ScheduledBlockWithActivity>> =
        _selectedDate.flatMapLatest { date ->
            repository.getScheduledBlocksWithActivityForDate(date)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /**
     * All scheduled blocks across all dates with full activity details.
     */
    val allScheduledBlocks: StateFlow<List<ScheduledBlockWithActivity>> =
        repository.allScheduledBlocksWithActivity
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    // --- Actions: Date ---

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // --- Actions: Activity Management ---

    fun createActivity(
        name: String,
        icon: String = "circle",
        color: Long = 0xFFA7F3D0,
        defaultDuration: Int = 30,
        isFlexible: Boolean = true,
        onCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val activity = Activity(
                name = name,
                icon = icon,
                color = color,
                defaultDuration = defaultDuration,
                isFlexible = isFlexible
            )
            val newId = repository.insertActivity(activity)
            onCreated?.invoke(newId)
        }
    }

    fun updateActivity(activity: Activity) {
        viewModelScope.launch {
            repository.updateActivity(activity)
        }
    }

    fun deleteActivity(activity: Activity) {
        viewModelScope.launch {
            repository.deleteActivity(activity)
        }
    }

    fun deleteActivityById(id: Long) {
        viewModelScope.launch {
            repository.deleteActivityById(id)
        }
    }

    // --- Actions: Schedule Block Management ---

    fun scheduleBlock(
        activityId: Long,
        date: LocalDate = _selectedDate.value,
        startTime: LocalTime,
        endTime: LocalTime,
        onScheduled: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val block = ScheduledBlock(
                activityId = activityId,
                date = date,
                startTime = startTime,
                endTime = endTime
            )
            val id = repository.insertScheduledBlock(block)
            onScheduled?.invoke(id)
        }
    }

    fun updateScheduledBlock(block: ScheduledBlock) {
        viewModelScope.launch {
            repository.updateScheduledBlock(block)
        }
    }

    fun deleteScheduledBlock(block: ScheduledBlock) {
        viewModelScope.launch {
            repository.deleteScheduledBlock(block)
        }
    }

    fun deleteScheduledBlockById(id: Long) {
        viewModelScope.launch {
            repository.deleteScheduledBlockById(id)
        }
    }

    /**
     * Query scheduled blocks for any given date.
     */
    fun getScheduledBlocksForDate(date: LocalDate): Flow<List<ScheduledBlockWithActivity>> =
        repository.getScheduledBlocksWithActivityForDate(date)

    /**
     * Schedules a new block from the ActivityDock onto the timeline.
     * The start time snaps to the nearest 15-minute increment of the current actual time
     * (or defaults to 8:00 AM if placed early), with end_time = start_time + default_duration.
     */
    fun scheduleActivityFromDock(
        activity: Activity,
        targetDate: LocalDate = _selectedDate.value,
        currentTime: LocalTime = LocalTime.now()
    ) {
        viewModelScope.launch {
            val (startTime, endTime) = calculateDockScheduleTimes(currentTime, activity.defaultDuration)

            val block = ScheduledBlock(
                activityId = activity.id,
                date = targetDate,
                startTime = startTime,
                endTime = endTime
            )
            repository.insertScheduledBlock(block)
        }
    }

    /**
     * Helper to populate sample scheduled blocks for a date if none exist,
     * ensuring immediate interactive testability on the timeline.
     */
    fun seedSampleBlocksIfEmpty(date: LocalDate = _selectedDate.value) {
        // No-op: Timeline days default to completely empty as per Phase 3.75 requirement.
    }

    companion object {
        /**
         * Calculates start and end times for dock scheduling:
         * - Snaps current time to nearest 15-minute increment
         * - Defaults to 8:00 AM if before 8:00 AM
         * - Sets end_time = start_time + default_duration
         */
        fun calculateDockScheduleTimes(
            currentTime: LocalTime,
            defaultDuration: Int
        ): Pair<LocalTime, LocalTime> {
            val rawMinutes = currentTime.hour * 60 + currentTime.minute
            val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15)
            val duration = defaultDuration.coerceAtLeast(15)

            // Default to 8:00 AM (480 minutes) if placed early (before 8:00 AM)
            val maxStart = (1440 - duration).coerceAtLeast(0)
            val startMinutes = if (snappedMinutes < 8 * 60) {
                8 * 60
            } else {
                snappedMinutes.coerceIn(0, maxStart)
            }

            val startTime = LocalTime.of(startMinutes / 60, startMinutes % 60)
            val endTime = if (startMinutes + duration >= 1440) {
                LocalTime.of(23, 59)
            } else {
                startTime.plusMinutes(duration.toLong())
            }

            return Pair(startTime, endTime)
        }
    }

    // --- Factory ---

    class Factory(
        private val repository: PlanItRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlanItViewModel::class.java)) {
                return PlanItViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
