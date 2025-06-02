package com.example.simplealarms

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.simplealarms.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var alarmViewModel: AlarmModel
    private lateinit var alarmScheduler: AlarmScheduler

    // Lưu danh sách trước đó để so sánh khi cập nhật
    private var previousList: List<Alarm>? = null

    // Launcher cho Add/Edit Alarm
    private val addEditAlarmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("MainActivity", "Alarm saved or updated.")
                // LiveData sẽ tự cập nhật
            }
        }

    // Launcher xin permission (API 33+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied. Alarms may not show notifications.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmScheduler = AlarmScheduler(this)

        val database = AppDatabase.getDatabase(applicationContext)
        val alarmRepository = AlarmRepository(database.alarmDao())
        val viewModelFactory = AlarmViewModelFactory(alarmRepository)
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmModel::class.java]

        setupRecyclerView()

        binding.fabAddAlarm.setOnClickListener {
            val intent = Intent(this, AddAlarm::class.java)
            addEditAlarmLauncher.launch(intent)
        }

        observeAlarms()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    try {
                        startActivity(intent)
                        Toast.makeText(this, "Please grant permission to schedule alarms.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Could not open exact alarm settings", e)
                        Toast.makeText(this, "Could not open settings. Please grant 'Alarms & Reminders' permission manually.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(
            onAlarmClicked = { alarm ->
                val intent = Intent(this, AddAlarm::class.java)
                intent.putExtra(AddAlarm.EXTRA_ALARM_ID, alarm.id)
                addEditAlarmLauncher.launch(intent)
            },
            onAlarmToggled = { alarm, isEnabled ->
                alarm.isEnabled = isEnabled
                alarmViewModel.updateAlarm(alarm)
                if (isEnabled) {
                    alarmScheduler.schedule(alarm)
                } else {
                    alarmScheduler.cancel(alarm)
                }
            }
        )

        binding.recyclerViewAlarms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = alarmAdapter
            itemAnimator = DefaultItemAnimator() // ✅ Bật animation mặc định
        }
    }

    private fun observeAlarms() {
        alarmViewModel.allAlarms.observe(this) { newList ->
            newList?.let {
                val isFirstItemNew = previousList?.firstOrNull()?.id != it.firstOrNull()?.id

                alarmAdapter.submitList(it) {
                    if (isFirstItemNew) {
                        binding.recyclerViewAlarms.scrollToPosition(0) // ✅ Scroll lên đầu
                    }
                }

                previousList = it

                // Reschedule các alarm đang bật
                it.filter { alarm -> alarm.isEnabled }.forEach { enabledAlarm ->
                    alarmScheduler.schedule(enabledAlarm)
                }
            }
        }
    }
}
