package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
        const val UPDATE_INTERVAL = 5L // milliseconds
        const val EXTRA_LOGGING_ENABLED = "logging_enabled"
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rotationVector: Sensor? = null

    private var accelData = FloatArray(3)
    private var gyroData = FloatArray(3)
    private var magData = FloatArray(3)
    private var orientationAngles = FloatArray(3)
    private var loggingEnabled = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var udpSocket: DatagramSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SensorService::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)

        registerSensorListeners()
        startUdpStreaming()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            loggingEnabled = it.getBooleanExtra(EXTRA_LOGGING_ENABLED, false)
        }

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Streaming Active")
                .setContentText("Streaming sensor data to $TARGET_IP:$TARGET_PORT")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Sensor Streaming Active")
                .setContentText("Streaming sensor data to $TARGET_IP:$TARGET_PORT")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
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
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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
                            put("orientation", JSONObject().apply {
                                put("yaw", orientationAngles[0])
                                put("pitch", orientationAngles[1])
                                put("roll", orientationAngles[2])
                            })
                            put("logging", loggingEnabled)
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

                        if (System.currentTimeMillis() % 300000 < UPDATE_INTERVAL) {
                            wakeLock?.let { wl ->
                                if (wl.isHeld) wl.release()
                                wl.acquire(10 * 60 * 1000L)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(UPDATE_INTERVAL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelData = event.values.copyOf()
            Sensor.TYPE_GYROSCOPE -> gyroData = event.values.copyOf()
            Sensor.TYPE_MAGNETIC_FIELD -> magData = event.values.copyOf()
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                for (i in 0..2) {
                    orientationAngles[i] = Math.toDegrees(orientationAngles[i].toDouble()).toFloat()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}