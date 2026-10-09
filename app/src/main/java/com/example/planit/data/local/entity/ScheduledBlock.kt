package com.example.planit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Entity representing a scheduled block on the calendar/timeline.
 *
 * @property id Unique auto-generated identifier.
 * @property activityId Foreign key referencing the associated [Activity].
 * @property date The scheduled date (ISO-8601 YYYY-MM-DD).
 * @property startTime Start time of the block (ISO-8601 HH:mm:ss).
 * @property endTime End time of the block (ISO-8601 HH:mm:ss).
 */
@Entity(
    tableName = "scheduled_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Activity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activity_id"], name = "index_scheduled_blocks_activity_id"),
        Index(value = ["date"], name = "index_scheduled_blocks_date")
    ]
)
data class ScheduledBlock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "activity_id")
    val activityId: Long,

    @ColumnInfo(name = "date")
    val date: LocalDate,

    @ColumnInfo(name = "start_time")
    val startTime: LocalTime,

    @ColumnInfo(name = "end_time")
    val endTime: LocalTime
) {
    // Convenience accessors matching snake_case naming
    @get:Ignore
    val activity_id: Long
        get() = activityId

    @get:Ignore
    val start_time: LocalTime
        get() = startTime

    @get:Ignore
    val end_time: LocalTime
        get() = endTime
}
