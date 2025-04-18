package com.example.lab1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private val CHANNEL_ID = "work_manager_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestNotificationPermission()

        createNotificationChannel()

        val button = findViewById<Button>(R.id.startWorkButton)
        button.setOnClickListener {
            startBackgroundWork()
        }
    }
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Разрешение уже есть
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Показать объяснение пользователю
                    showPermissionExplanationDialog()
                }
                else -> {
                    // Запросить разрешение
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("This app needs notification permission to alert you about important updates")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WorkManager Notifications"
            val descriptionText = "Notifications for background work"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startBackgroundWork() {
        val data = Data.Builder()
            .putString("message", "Пример сообщения")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(data)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        // Observe work status
        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED ->
                        Toast.makeText(this, "Задача успешно выполнена", Toast.LENGTH_SHORT).show()
                    WorkInfo.State.FAILED ->
                        Toast.makeText(this, "Ошибка выполнения задачи", Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }

        Toast.makeText(this, "Фоновая задача запущена", Toast.LENGTH_SHORT).show()
    }
}

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            delay(5000) // Ждем 5 секунд

            sendNotification()

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun sendNotification() {
        val notificationId = 1
        val notification = NotificationCompat.Builder(applicationContext, "work_manager_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Changed to system icon
            .setContentTitle("Фоновая задача")
            .setContentText("Задача успешно выполнена за 5 секунд!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

}