package com.example.planit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.planit.data.local.converter.Converters
import com.example.planit.data.local.dao.ActivityDao
import com.example.planit.data.local.dao.ScheduledBlockDao
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room Database for PlanIT application.
 * Manages [Activity] and [ScheduledBlock] tables.
 *
 * Implements thread-safe singleton pattern and database initialization callback.
 */
@Database(
    entities = [
        Activity::class,
        ScheduledBlock::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PlanItDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao

    companion object {
        private const val DATABASE_NAME = "planit_database"

        @Volatile
        private var INSTANCE: PlanItDatabase? = null

        fun getInstance(
            context: Context,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
        ): PlanItDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlanItDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabasePrepopulateCallback(coroutineScope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Database callback that seeds default activities upon initial database creation
     * and enforces foreign key constraints.
     */
    private class DatabasePrepopulateCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON;")
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateInitialData(database.activityDao())
                }
            }
        }

        private suspend fun populateInitialData(
            activityDao: ActivityDao
        ) {
            val defaultActivities = listOf(
                Activity(
                    name = "Deep Work",
                    icon = "code",
                    color = 0xFFBAE6FD, // Sky Blue
                    defaultDuration = 90,
                    isFlexible = true
                ),
                Activity(
                    name = "Exercise",
                    icon = "fitness",
                    color = 0xFFA7F3D0, // Mint
                    defaultDuration = 45,
                    isFlexible = false
                ),
                Activity(
                    name = "Reading",
                    icon = "book",
                    color = 0xFFFED7AA, // Peach
                    defaultDuration = 30,
                    isFlexible = true
                ),
                Activity(
                    name = "Meeting",
                    icon = "people",
                    color = 0xFFDDD6FE, // Lavender
                    defaultDuration = 60,
                    isFlexible = false
                )
            )
            activityDao.insertAll(defaultActivities)
            // Scheduled blocks are intentionally NOT seeded; timeline days default to completely empty.
        }
    }
}
