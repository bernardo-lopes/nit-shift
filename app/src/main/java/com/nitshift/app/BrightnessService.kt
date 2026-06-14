package com.nitshift.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.roundToInt

class BrightnessService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private val channelId = "LuxOffsetServiceChannel"
    private val notificationId = 1995

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastRecordedLux: Float = -1f
    private var targetBrightness: Float = -1f
    private var currentAppliedBrightnessFloat: Float = -1f

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Fetch current system brightness if possible to make initial start transitions silky smooth
        val initialSysBrightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
        } catch (e: Exception) {
            128f
        }
        currentAppliedBrightnessFloat = initialSysBrightness

        // Load the saved settings initially
        val prefs = getSharedPreferences("BrightnessPrefs", Context.MODE_PRIVATE)
        val initialOffset = prefs.getInt("brightness_offset", 0)
        BrightnessState.setUserOffset(initialOffset)

        val initialServiceEnabled = prefs.getBoolean("service_enabled", false)
        BrightnessState.setServiceEnabled(initialServiceEnabled)

        val initialApplyBg = prefs.getBoolean("apply_in_background", true)
        BrightnessState.setApplyInBackground(initialApplyBg)

        // Register light sensor listener
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Listen for real-time offset changes initiated from the UI
        serviceScope.launch {
            BrightnessState.userOffset.collect { offset ->
                if (lastRecordedLux >= 0f) {
                    applyCustomBrightness(lastRecordedLux, offset)
                }
                prefs.edit().putInt("brightness_offset", offset).apply()
            }
        }

        // Real-time smooth interpolation loop running 60 times per second (~16ms interval)
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(16)
                smoothUpdateLoop()
            }
        }

        BrightnessState.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // Setup notification tapping behavior to open MainActivity
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Custom Brightness Active")
            .setContentText("Monitoring ambient lighting dynamically")
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(notificationId, notification)

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            lastRecordedLux = lux
            BrightnessState.setCurrentLux(lux)

            val currentOffset = BrightnessState.userOffset.value
            applyCustomBrightness(lux, currentOffset)
        }
    }

    private fun applyCustomBrightness(lux: Float, offset: Int) {
        // Smooth logarithmic baseline mapping from lux to screen brightness (10 to 255)
        val calculatedBase = if (lux <= 0f) {
            10f
        } else {
            val logLux = log10(lux.coerceAtLeast(1f))
            // Maps 1 lux to ~15, 10000 lux to ~255
            val base = 15f + logLux * 60f
            base.coerceIn(10f, 255f)
        }

        BrightnessState.setCalculatedBaseBrightness(calculatedBase)

        // Combine base and interactive slider percentage offset (-100% to +100%)
        // Linear scaling: offset / 100f * 255f
        val offsetFraction = offset / 100f
        val finalBrightnessFloat = (calculatedBase + offsetFraction * 255f).coerceIn(10f, 255f)
        targetBrightness = finalBrightnessFloat
    }

    private fun smoothUpdateLoop() {
        if (targetBrightness < 0f) return

        if (currentAppliedBrightnessFloat < 0f) {
            currentAppliedBrightnessFloat = targetBrightness
        }

        val diff = targetBrightness - currentAppliedBrightnessFloat
        // Threshold check to avoid jitter when extremely close
        if (kotlin.math.abs(diff) > 0.05f) {
            // Slower, extremely smooth, cinematic exponential interpolation (0.8% adjustment per frame)
            // Perfectly seamless, flicker-free, and natural
            val step = diff * 0.008f
            currentAppliedBrightnessFloat += step

            val finalApplied = currentAppliedBrightnessFloat.roundToInt().coerceIn(10, 255)
            BrightnessState.setAppliedBrightness(finalApplied)
            writeSystemBrightness(finalApplied)
        } else if (currentAppliedBrightnessFloat != targetBrightness) {
            currentAppliedBrightnessFloat = targetBrightness
            val finalApplied = currentAppliedBrightnessFloat.roundToInt().coerceIn(10, 255)
            BrightnessState.setAppliedBrightness(finalApplied)
            writeSystemBrightness(finalApplied)
        }
    }

    private fun writeSystemBrightness(brightnessValue: Int) {
        if (Settings.System.canWrite(this)) {
            try {
                // Keep auto settings manual to prevent interference
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessValue
                )
            } catch (e: Exception) {
                // System level setting error
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        BrightnessState.setServiceRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Custom Brightness Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active state of the background lux sensor offset calculation service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
