package com.example.myapplication

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
import android.os.PowerManager
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SensorService : Service(), SensorEventListener {
    private companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "SensorServiceChannel"
        const val TARGET_IP = "10.47.80.118"
        const val TARGET_PORT = 9999
        const val UPDATE_INTERVAL = 100L // milliseconds
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private var accelData = FloatArray(3) { 0f }
    private var gyroData = FloatArray(3) { 0f }
    private var magData = FloatArray(3) { 0f }
    private var rotation = "Unknown"

    private var wakeLock: PowerManager.WakeLock? = null
    private var udpSocket: DatagramSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Initialize sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Get device rotation
        getDeviceRotation()

        // Acquire wake lock to keep CPU active
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SensorService::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes, will be renewed

        // Register sensor listeners
        registerSensorListeners()

        // Start UDP streaming
        startUdpStreaming()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY // Restart service if killed
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup
        sensorManager.unregisterListener(this)
        wakeLock?.release()
        udpSocket?.close()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Streaming sensor data in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Streaming Active")
            .setContentText("Streaming sensor data to $TARGET_IP:$TARGET_PORT")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun registerSensorListeners() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun getDeviceRotation() {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            val rotConst = display?.rotation ?: Surface.ROTATION_0
            rotation = when (rotConst) {
                Surface.ROTATION_0 -> "ROTATION_0"
                Surface.ROTATION_90 -> "ROTATION_90"
                Surface.ROTATION_180 -> "ROTATION_180"
                Surface.ROTATION_270 -> "ROTATION_270"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            rotation = "Error getting rotation"
        }
    }

    private fun startUdpStreaming() {
        serviceScope.launch {
            try {
                udpSocket = DatagramSocket()

                while (isActive) {
                    try {
                        val json = JSONObject().apply {
                            put("accelerometer", JSONObject().apply {
                                put("x", accelData[0])
                                put("y", accelData[1])
                                put("z", accelData[2])
                            })
                            put("gyroscope", JSONObject().apply {
                                put("x", gyroData[0])
                                put("y", gyroData[1])
                                put("z", gyroData[2])
                            })
                            put("magnetometer", JSONObject().apply {
                                put("x", magData[0])
                                put("y", magData[1])
                                put("z", magData[2])
                            })
                            put("rotation", rotation)
                            put("timestamp", System.currentTimeMillis())
                        }

                        val message = json.toString()
                        val data = message.toByteArray()

                        val packet = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName(TARGET_IP),
                            TARGET_PORT
                        )

                        udpSocket?.send(packet)

                        // Renew wake lock periodically
                        if (System.currentTimeMillis() % 300000 < UPDATE_INTERVAL) { // Every 5 minutes
                            wakeLock?.let { wl ->
                                if (wl.isHeld) {
                                    wl.release()
                                }
                                wl.acquire(10 * 60 * 1000L)
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue streaming even if one packet fails
                    }

                    delay(UPDATE_INTERVAL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // SensorEventListener methods
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelData = event.values.copyOf()
            Sensor.TYPE_GYROSCOPE -> gyroData = event.values.copyOf()
            Sensor.TYPE_MAGNETIC_FIELD -> magData = event.values.copyOf()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
}