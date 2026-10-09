package com.example.planit.data.local.entity.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock

/**
 * Composite data class representing a [ScheduledBlock] paired with its parent [Activity].
 * Useful for displaying schedule views with full activity metadata (name, icon, color).
 */
data class ScheduledBlockWithActivity(
    @Embedded
    val scheduledBlock: ScheduledBlock,

    @Relation(
        parentColumn = "activity_id",
        entityColumn = "id"
    )
    val activity: Activity
)
