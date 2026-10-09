package com.example.planit.data.local.entity.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock

/**
 * 1-to-N relation representing an [Activity] along with all of its [ScheduledBlock]s.
 */
data class ActivityWithScheduledBlocks(
    @Embedded
    val activity: Activity,

    @Relation(
        parentColumn = "id",
        entityColumn = "activity_id"
    )
    val scheduledBlocks: List<ScheduledBlock>
)
