package com.example.simplealarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") { // Some devices use this

            Log.d("BootCompletedReceiver", "Device boot completed, rescheduling alarms.")
            Toast.makeText(context, "Rescheduling alarms...", Toast.LENGTH_SHORT).show()


            val database = AppDatabase.getDatabase(context.applicationContext)
            val repository = AlarmRepository(database.alarmDao())
            val scheduler = AlarmScheduler(context)

            CoroutineScope(Dispatchers.IO).launch {
                // Fetch all alarms. LiveData is not suitable here as this is not a UI component.
                // So, we'd need a direct suspend fun in DAO or get all from repository non-LiveData way.
                // For simplicity, let's assume we add a suspend function to DAO and Repository:
                // In AlarmDao:
                // @Query("SELECT * FROM alarms WHERE isEnabled = 1") // Only enabled ones
                // suspend fun getAllEnabledAlarmsList(): List<Alarm>
                // In AlarmRepository:
                // suspend fun getAllEnabledAlarmsList(): List<Alarm> = alarmDao.getAllEnabledAlarmsList()

                // For now, let's observe once (less ideal for a receiver but works)
                // A better way is a direct suspend fun in DAO/Repository as commented above.
                // This observer approach is more for quick demonstration.
                // For a robust solution, use a direct suspend fun in DAO/Repository.
                // As LiveData might not emit immediately or outside main thread.
                // A simpler way for background task:
                val alarms = database.alarmDao().getAllAlarmsBlocking() // Add this method to DAO

                alarms.filter { it.isEnabled }.forEach { alarm ->
                    scheduler.schedule(alarm)
                    Log.d("BootCompletedReceiver", "Rescheduled: ${alarm.label}")
                }
            }
        }
    }
}