package com.example.planit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by [android.app.AlarmManager] when a scheduled block starts.
 * Receives the alarm broadcast and triggers high-priority notification via [NotificationService].
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_BLOCK_ID = "extra_block_id"
        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_ACTIVITY_NAME = "extra_activity_name"
        const val EXTRA_ACTIVITY_ICON = "extra_activity_icon"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(EXTRA_BLOCK_ID, -1L)
        if (blockId == -1L) {
            Log.w(TAG, "Received alarm intent without a valid block ID")
            return
        }

        val activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME) ?: "Scheduled Activity"
        val activityIcon = intent.getStringExtra(EXTRA_ACTIVITY_ICON) ?: ""
        val startTimeStr = intent.getStringExtra(EXTRA_START_TIME) ?: ""
        val endTimeStr = intent.getStringExtra(EXTRA_END_TIME) ?: ""

        Log.d(TAG, "Alarm received for block $blockId ('$activityName')")

        val notificationService = NotificationService(context)
        notificationService.showNotification(
            blockId = blockId,
            activityName = activityName,
            activityIcon = activityIcon,
            startTimeStr = startTimeStr,
            endTimeStr = endTimeStr
        )
    }
}
