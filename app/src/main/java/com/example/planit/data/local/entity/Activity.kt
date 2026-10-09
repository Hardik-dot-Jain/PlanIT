package com.example.planit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Entity representing an Activity template (e.g., "Deep Work", "Exercise", "Reading").
 *
 * @property id Unique auto-generated identifier.
 * @property name Human-readable title of the activity.
 * @property icon Name/identifier of the icon representing the activity.
 * @property color 32-bit ARGB color value (e.g., 0xFF4CAF50).
 * @property defaultDuration Default duration in minutes for scheduling this activity.
 */
@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String,

    @ColumnInfo(name = "color")
    val color: Long,

    @ColumnInfo(name = "default_duration")
    val defaultDuration: Int,

    @ColumnInfo(name = "is_flexible", defaultValue = "1")
    val isFlexible: Boolean = true
) {
    // Convenience accessor matching snake_case naming
    @get:Ignore
    val default_duration: Int
        get() = defaultDuration

    @get:Ignore
    val is_flexible: Boolean
        get() = isFlexible
}
