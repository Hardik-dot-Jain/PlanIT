package com.example.planit.data.repository

import com.example.planit.data.local.dao.ActivityDao
import com.example.planit.data.local.dao.ScheduledBlockDao
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ActivityWithScheduledBlocks
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository mediating between Room DAOs and UI ViewModels.
 * Serves as the single source of truth for Activities and ScheduledBlocks.
 */
class PlanItRepository(
    private val activityDao: ActivityDao,
    private val scheduledBlockDao: ScheduledBlockDao
) {

    // --- Activities ---

    val allActivities: Flow<List<Activity>> = activityDao.getAllActivities()

    val allActivitiesWithScheduledBlocks: Flow<List<ActivityWithScheduledBlocks>> =
        activityDao.getAllActivitiesWithScheduledBlocks()

    fun getActivityById(id: Long): Flow<Activity?> =
        activityDao.getActivityById(id)

    suspend fun getActivityByIdOnce(id: Long): Activity? =
        activityDao.getActivityByIdOnce(id)

    fun searchActivities(query: String): Flow<List<Activity>> =
        activityDao.searchActivities(query)

    suspend fun insertActivity(activity: Activity): Long =
        activityDao.insert(activity)

    suspend fun insertActivities(activities: List<Activity>): List<Long> =
        activityDao.insertAll(activities)

    suspend fun updateActivity(activity: Activity) {
        activityDao.update(activity)
    }

    suspend fun deleteActivity(activity: Activity) {
        scheduledBlockDao.deleteBlocksByActivityId(activity.id)
        activityDao.delete(activity)
    }

    suspend fun deleteActivityById(id: Long) {
        scheduledBlockDao.deleteBlocksByActivityId(id)
        activityDao.deleteById(id)
    }

    // --- Scheduled Blocks ---

    val allScheduledBlocks: Flow<List<ScheduledBlock>> =
        scheduledBlockDao.getAllScheduledBlocks()

    val allScheduledBlocksWithActivity: Flow<List<ScheduledBlockWithActivity>> =
        scheduledBlockDao.getAllScheduledBlocksWithActivity()

    suspend fun getAllScheduledBlocksWithActivityOnce(): List<ScheduledBlockWithActivity> =
        scheduledBlockDao.getAllScheduledBlocksWithActivityOnce()

    fun getScheduledBlockById(id: Long): Flow<ScheduledBlock?> =
        scheduledBlockDao.getScheduledBlockById(id)

    suspend fun getScheduledBlockWithActivityByIdOnce(id: Long): ScheduledBlockWithActivity? =
        scheduledBlockDao.getScheduledBlockWithActivityByIdOnce(id)

    suspend fun getScheduledBlocksForActivityOnce(activityId: Long): List<ScheduledBlock> =
        scheduledBlockDao.getScheduledBlocksForActivityOnce(activityId)

    fun getScheduledBlocksForDate(date: LocalDate): Flow<List<ScheduledBlock>> =
        scheduledBlockDao.getScheduledBlocksForDate(date)

    fun getScheduledBlocksWithActivityForDate(date: LocalDate): Flow<List<ScheduledBlockWithActivity>> =
        scheduledBlockDao.getScheduledBlocksWithActivityForDate(date)

    fun getScheduledBlocksBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<ScheduledBlock>> =
        scheduledBlockDao.getScheduledBlocksBetweenDates(startDate, endDate)

    fun getScheduledBlocksForActivity(activityId: Long): Flow<List<ScheduledBlock>> =
        scheduledBlockDao.getScheduledBlocksForActivity(activityId)

    suspend fun insertScheduledBlock(block: ScheduledBlock): Long =
        scheduledBlockDao.insert(block)

    suspend fun insertScheduledBlocks(blocks: List<ScheduledBlock>): List<Long> =
        scheduledBlockDao.insertAll(blocks)

    suspend fun updateScheduledBlock(block: ScheduledBlock) {
        scheduledBlockDao.update(block)
    }

    suspend fun deleteScheduledBlock(block: ScheduledBlock) {
        scheduledBlockDao.delete(block)
    }

    suspend fun deleteScheduledBlockById(id: Long) {
        scheduledBlockDao.deleteById(id)
    }

    suspend fun deleteBlocksForDate(date: LocalDate) {
        scheduledBlockDao.deleteBlocksForDate(date)
    }
}
