package com.example.planit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.planit.PlanItApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * BroadcastReceiver listening for [Intent.ACTION_BOOT_COMPLETED] to reschedule
 * all future scheduled alarms if the device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Device rebooted or package replaced ($action). Rescheduling future alarms...")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as PlanItApplication
                    val notificationService = NotificationService(context)
                    val blocksWithActivity = app.repository.getAllScheduledBlocksWithActivityOnce()
                    val now = LocalDateTime.now()

                    var scheduledCount = 0
                    for (item in blocksWithActivity) {
                        val block = item.scheduledBlock
                        val blockDateTime = LocalDateTime.of(block.date, block.startTime)
                        if (blockDateTime.isAfter(now)) {
                            notificationService.scheduleBlockAlarm(block, item.activity)
                            scheduledCount++
                        }
                    }

                    Log.d(TAG, "Successfully rescheduled $scheduledCount future alarms after boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
