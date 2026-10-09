package com.example.planit.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.planit.MainActivity
import com.example.planit.R
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Service managing exact alarms via [AlarmManager] and notifications via [NotificationManagerCompat].
 *
 * Requirements:
 * - Uses [AlarmManager.setExactAndAllowWhileIdle] to fire notifications exactly when a [ScheduledBlock] starts.
 * - Manages custom notification channel with high priority.
 * - Handles notification layout with activity name, emoji icon, and timeline duration.
 * - Provides graceful fallback and cancellation support.
 */
class NotificationService(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "planit_scheduled_activities"
        const val CHANNEL_NAME = "Scheduled Activities"
        const val CHANNEL_DESC = "Exact alarms and start notifications for scheduled activities"
        private const val TAG = "NotificationService"

        private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        fun resolveIconEmoji(iconName: String): String {
            return when (iconName.lowercase()) {
                "code", "laptop", "dev" -> "💻"
                "fitness", "gym", "workout" -> "⚡"
                "book", "read", "study" -> "📖"
                "meeting", "people", "call" -> "👥"
                "star", "priority" -> "⭐"
                "coffee", "break" -> "☕"
                "design", "art" -> "🎨"
                "write", "pen" -> "✍️"
                "music" -> "🎵"
                "walk", "run" -> "🏃"
                else -> if (iconName.length in 1..2) iconName else "✦"
            }
        }
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the custom notification channel for scheduled activity alerts.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.BLACK
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if exact alarms are allowed on Android 12+ (API 31+).
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Schedules an exact alarm via [AlarmManager.setExactAndAllowWhileIdle]
     * to trigger right when the scheduled block starts.
     */
    fun scheduleBlockAlarm(block: ScheduledBlock, activity: Activity) {
        val blockDateTime = LocalDateTime.of(block.date, block.startTime)
        val triggerAtMillis = blockDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Do not schedule alarms in the past
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Block ${block.id} is in the past ($blockDateTime), skipping alarm")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_BLOCK_ID, block.id)
            putExtra(AlarmReceiver.EXTRA_ACTIVITY_ID, activity.id)
            putExtra(AlarmReceiver.EXTRA_ACTIVITY_NAME, activity.name)
            putExtra(AlarmReceiver.EXTRA_ACTIVITY_ICON, activity.icon)
            putExtra(AlarmReceiver.EXTRA_START_TIME, block.startTime.toString())
            putExtra(AlarmReceiver.EXTRA_END_TIME, block.endTime.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            block.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled EXACT alarm for block ${block.id} ('${activity.name}') at $blockDateTime")
            } else {
                Log.w(TAG, "Exact alarm permission denied; falling back to inexact idle alarm")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm for block ${block.id}", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling", fallbackEx)
            }
        }
    }

    /**
     * Cancels any pending alarm for the specified [blockId].
     */
    fun cancelBlockAlarm(blockId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            blockId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for block $blockId")
        }
    }

    /**
     * Displays the notification for the scheduled block with the activity name and emoji icon.
     */
    fun showNotification(
        blockId: Long,
        activityName: String,
        activityIcon: String,
        startTimeStr: String,
        endTimeStr: String
    ) {
        // Android 13+ runtime permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot display notification.")
                return
            }
        }

        val emoji = resolveIconEmoji(activityIcon)
        val title = "$emoji $activityName"

        val timeSub = if (startTimeStr.isNotBlank() && endTimeStr.isNotBlank()) {
            try {
                val start = LocalTime.parse(startTimeStr).format(timeFormatter)
                val end = LocalTime.parse(endTimeStr).format(timeFormatter)
                "$start - $end"
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }

        val contentText = if (timeSub.isNotBlank()) {
            "Scheduled block: $timeSub"
        } else {
            "Your scheduled activity is starting now!"
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_BLOCK_ID, blockId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            blockId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\nTime to focus on $activityName.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(context).notify(blockId.toInt(), notification)
        Log.d(TAG, "Fired notification for block $blockId: $title ($contentText)")
    }
}
