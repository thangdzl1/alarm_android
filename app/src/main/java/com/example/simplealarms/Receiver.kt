package com.example.simplealarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Receiver : BroadcastReceiver() {

    companion object {
        const val ALARM_ID = "ALARM_ID_EXTRA"
        const val ALARM_LABEL = "ALARM_LABEL_EXTRA"
        const val NOTIFICATION_CHANNEL_ID = "alarm_channel_custom_sound"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm received!")
        val alarmId = intent.getIntExtra(ALARM_ID, -1)
        val alarmLabel = intent.getStringExtra(ALARM_LABEL) ?: "Alarm"

        if (alarmId == -1) {
            Log.e("AlarmReceiver", "Invalid alarm ID received.")
            return
        }

        showNotification(context, alarmId, alarmLabel)

        // ✅ Thay vì playAlarmSound, mở AlarmRingActivity
        val ringIntent = Intent(context, AlarmRing::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ALARM_LABEL, alarmLabel)
        }
        context.startActivity(ringIntent)

        // Reschedule hoặc tắt nếu là one-time alarm
        val database = AppDatabase.getDatabase(context.applicationContext)
        val repository = AlarmRepository(database.alarmDao())
        val scheduler = AlarmScheduler(context)

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = repository.getAlarmById(alarmId)
            alarm?.let {
                if (it.isRecurring && it.isEnabled) {
                    scheduler.schedule(it)
                    Log.d("AlarmReceiver", "Rescheduled repeating alarm: ${it.label}")
                } else if (!it.isRecurring && it.isEnabled) {
                    it.isEnabled = false
                    repository.update(it)
                    Log.d("AlarmReceiver", "Disabled one-time alarm: ${it.label}")
                }
            }
        }
    }

    private fun showNotification(context: Context, alarmId: Int, alarmLabel: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Alarm Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance).apply {
                description = "Channel for alarm clock notifications"
                enableLights(true)
                enableVibration(true)
                setSound(null, audioAttributes) // ❌ Tắt âm báo mặc định
            }

            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_placeholder)
            .setContentTitle("Alarm Ringing!")
            .setContentText(alarmLabel)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setSound(null) // ❌ Không dùng âm báo hệ thống

        notificationManager.notify(alarmId, notificationBuilder.build())
    }
}
