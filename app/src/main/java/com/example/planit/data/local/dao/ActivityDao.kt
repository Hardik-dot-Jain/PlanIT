package com.example.planit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.relation.ActivityWithScheduledBlocks
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Activity] entities.
 * Follows modern Room best practices:
 * - Exposes observable data streams as [Flow].
 * - Uses `suspend` functions for asynchronous write operations.
 */
@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities ORDER BY name ASC")
    fun getAllActivities(): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    fun getActivityById(id: Long): Flow<Activity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getActivityByIdOnce(id: Long): Activity?

    @Query("SELECT * FROM activities WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchActivities(query: String): Flow<List<Activity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: Activity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<Activity>): List<Long>

    @Update
    suspend fun update(activity: Activity): Int

    @Delete
    suspend fun delete(activity: Activity): Int

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :id")
    fun getActivityWithScheduledBlocks(id: Long): Flow<ActivityWithScheduledBlocks?>

    @Transaction
    @Query("SELECT * FROM activities ORDER BY name ASC")
    fun getAllActivitiesWithScheduledBlocks(): Flow<List<ActivityWithScheduledBlocks>>
}
