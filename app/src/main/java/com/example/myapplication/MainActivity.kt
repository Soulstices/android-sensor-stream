package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var serviceIntent: Intent? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the background service
        serviceIntent = Intent(this, SensorService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            MyApplicationTheme {
                SensorUdpStreamer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the service when activity is destroyed (optional)
         serviceIntent?.let { stopService(it) }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SensorUdpStreamer() {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    var accelData by remember { mutableStateOf(FloatArray(3)) }
    var gyroData by remember { mutableStateOf(FloatArray(3)) }
    var magData by remember { mutableStateOf(FloatArray(3)) }
    var rotation by remember { mutableStateOf("Unknown") }
    var serviceStatus by remember { mutableStateOf("Background Service Running") }

    // Get rotation using non-deprecated API
    LaunchedEffect(Unit) {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
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

    // Keep local sensor listeners for UI display
    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelData = event.values.copyOf()
                    Sensor.TYPE_GYROSCOPE -> gyroData = event.values.copyOf()
                    Sensor.TYPE_MAGNETIC_FIELD -> magData = event.values.copyOf()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Register listeners for UI display
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val targetIp = "10.47.80.118"
    val targetPort = 9999

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text("Sensor Data UDP Stream", fontSize = 20.sp, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Target: $targetIp:$targetPort", fontSize = 14.sp)
            Text("Status: $serviceStatus", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Green)
            Text("Note: Service continues running even when screen is locked", fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))

            if (accelerometer != null) {
                SensorSection("Accelerometer (m/s²)", accelData)
            } else {
                Text("Accelerometer not available", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (gyroscope != null) {
                SensorSection("Gyroscope (rad/s)", gyroData)
            } else {
                Text("Gyroscope not available", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (magnetometer != null) {
                SensorSection("Magnetometer (μT)", magData)
            } else {
                Text("Magnetometer not available", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Rotation: $rotation", fontSize = 16.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // Service control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val intent = Intent(context, SensorService::class.java)
                        context.startForegroundService(intent)
                        serviceStatus = "Service Started"
                    }
                ) {
                    Text("Start Service")
                }

                Button(
                    onClick = {
                        val intent = Intent(context, SensorService::class.java)
                        context.stopService(intent)
                        serviceStatus = "Service Stopped"
                    }
                ) {
                    Text("Stop Service")
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SensorSection(title: String, values: FloatArray) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("X: ${String.format("%.3f", values[0])}", style = MaterialTheme.typography.bodyMedium)
        Text("Y: ${String.format("%.3f", values[1])}", style = MaterialTheme.typography.bodyMedium)
        Text("Z: ${String.format("%.3f", values[2])}", style = MaterialTheme.typography.bodyMedium)
    }
}