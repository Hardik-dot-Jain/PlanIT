package com.example.planit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for [ScheduledBlock] entities.
 * Supports date filtering, range queries, composite relations with [Activity],
 * and reactive [Flow] streams.
 */
@Dao
interface ScheduledBlockDao {

    @Query("SELECT * FROM scheduled_blocks ORDER BY date ASC, start_time ASC")
    fun getAllScheduledBlocks(): Flow<List<ScheduledBlock>>

    @Query("SELECT * FROM scheduled_blocks WHERE id = :id")
    fun getScheduledBlockById(id: Long): Flow<ScheduledBlock?>

    @Query("SELECT * FROM scheduled_blocks WHERE id = :id")
    suspend fun getScheduledBlockByIdOnce(id: Long): ScheduledBlock?

    @Query("SELECT * FROM scheduled_blocks WHERE date = :date ORDER BY start_time ASC")
    fun getScheduledBlocksForDate(date: LocalDate): Flow<List<ScheduledBlock>>

    @Query("SELECT * FROM scheduled_blocks WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, start_time ASC")
    fun getScheduledBlocksBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<ScheduledBlock>>

    @Query("SELECT * FROM scheduled_blocks WHERE activity_id = :activityId ORDER BY date ASC, start_time ASC")
    fun getScheduledBlocksForActivity(activityId: Long): Flow<List<ScheduledBlock>>

    @Transaction
    @Query("SELECT * FROM scheduled_blocks WHERE date = :date ORDER BY start_time ASC")
    fun getScheduledBlocksWithActivityForDate(date: LocalDate): Flow<List<ScheduledBlockWithActivity>>

    @Transaction
    @Query("SELECT * FROM scheduled_blocks ORDER BY date ASC, start_time ASC")
    fun getAllScheduledBlocksWithActivity(): Flow<List<ScheduledBlockWithActivity>>

    @Transaction
    @Query("SELECT * FROM scheduled_blocks ORDER BY date ASC, start_time ASC")
    suspend fun getAllScheduledBlocksWithActivityOnce(): List<ScheduledBlockWithActivity>

    @Transaction
    @Query("SELECT * FROM scheduled_blocks WHERE id = :id")
    fun getScheduledBlockWithActivityById(id: Long): Flow<ScheduledBlockWithActivity?>

    @Transaction
    @Query("SELECT * FROM scheduled_blocks WHERE id = :id")
    suspend fun getScheduledBlockWithActivityByIdOnce(id: Long): ScheduledBlockWithActivity?

    @Query("SELECT * FROM scheduled_blocks WHERE activity_id = :activityId ORDER BY date ASC, start_time ASC")
    suspend fun getScheduledBlocksForActivityOnce(activityId: Long): List<ScheduledBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledBlock: ScheduledBlock): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduledBlock>): List<Long>

    @Update
    suspend fun update(scheduledBlock: ScheduledBlock): Int

    @Delete
    suspend fun delete(scheduledBlock: ScheduledBlock): Int

    @Query("DELETE FROM scheduled_blocks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM scheduled_blocks WHERE date = :date")
    suspend fun deleteBlocksForDate(date: LocalDate): Int

    @Query("DELETE FROM scheduled_blocks WHERE activity_id = :activityId")
    suspend fun deleteBlocksByActivityId(activityId: Long): Int
}
