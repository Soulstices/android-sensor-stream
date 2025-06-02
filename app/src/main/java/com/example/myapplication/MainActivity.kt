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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var serviceIntent: Intent? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serviceIntent = Intent(this, SensorService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SensorUdpStreamer()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
    val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var accelData by remember { mutableStateOf(FloatArray(3)) }
    var gyroData by remember { mutableStateOf(FloatArray(3)) }
    var magData by remember { mutableStateOf(FloatArray(3)) }
    var orientationAngles by remember { mutableStateOf(FloatArray(3)) }
    var rotation by remember { mutableStateOf("Unknown") }
    var serviceStatus by remember { mutableStateOf("🟢 Background Service Running") }

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
                Surface.ROTATION_0 -> "🔄 ROTATION_0"
                Surface.ROTATION_90 -> "🔄 ROTATION_90"
                Surface.ROTATION_180 -> "🔄 ROTATION_180"
                Surface.ROTATION_270 -> "🔄 ROTATION_270"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            rotation = "❌ Error getting rotation"
        }
    }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelData = event.values.copyOf()
                    Sensor.TYPE_GYROSCOPE -> gyroData = event.values.copyOf()
                    Sensor.TYPE_MAGNETIC_FIELD -> magData = event.values.copyOf()
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val tempOrientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, tempOrientation)
                        for (i in 0..2) {
                            tempOrientation[i] = Math.toDegrees(tempOrientation[i].toDouble()).toFloat()
                        }
                        orientationAngles = tempOrientation
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        listOf(accelerometer, gyroscope, magnetometer, rotationVector).forEach { sensor ->
            sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }

    val scrollState = rememberScrollState()
    val targetIp = "10.47.80.118"
    val targetPort = 9999

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "📱 Android Sensor Stream",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF74C0FC)
                        )
                        Text(
                            "🌐 Target: $targetIp:$targetPort",
                            fontSize = 14.sp,
                            color = Color(0xFFADB5BD)
                        )
                        Text(
                            "🛠️ Status: $serviceStatus",
                            fontSize = 14.sp,
                            color = Color(0xFF51CF66)
                        )
                        Text(
                            "🔒 Note: Service runs even when screen is off",
                            fontSize = 12.sp,
                            color = Color(0xFF868E96)
                        )
                    }
                }

                // Orientation Card
                if (rotationVector != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2D1B69).copy(alpha = 0.8f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "🎯 Device Orientation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF9775FA)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OrientationItem("🧭\nYaw", orientationAngles[0], "°")
                                OrientationItem("↕️\nPitch", orientationAngles[1], "°")
                                OrientationItem("🔄\nRoll", orientationAngles[2], "°")
                            }

                            Text(
                                "Yaw: Left/Right • Pitch: Forward/Backward • Roll: Side tilt",
                                fontSize = 11.sp,
                                color = Color(0xFFADB5BD),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Sensor Data Table Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0D4A2D).copy(alpha = 0.8f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "📊 Motion Sensors",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF51CF66)
                        )

                        SensorDataTable(accelData, gyroData, magData)
                    }
                }

                // Screen Rotation Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF495057).copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔄 Screen Rotation: $rotation",
                            fontSize = 16.sp,
                            color = Color(0xFFF8F9FA),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(context, SensorService::class.java)
                            context.startForegroundService(intent)
                            serviceStatus = "🟢 Service Started"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF51CF66)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("▶️ Start Service", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(context, SensorService::class.java)
                            context.stopService(intent)
                            serviceStatus = "⏹️ Service Stopped"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("⏹️ Stop Service", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun OrientationItem(label: String, value: Float, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFADB5BD),
            textAlign = TextAlign.Center
        )
        Text(
            "${String.format("%.1f", value)}$unit",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SensorDataTable(accelData: FloatArray, gyroData: FloatArray, magData: FloatArray) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF212529),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                "Axis",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFADB5BD),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "🧭 Accel\n(m/s²)",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF74C0FC),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
            Text(
                "🌀 Gyro\n(rad/s)",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9775FA),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
            Text(
                "🧲 Mag\n(μT)",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD43B),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }

        // Data Rows
        listOf("X", "Y", "Z").forEachIndexed { index, axis ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) Color(0xFF2D3748).copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    axis,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    String.format("%.3f", accelData[index]),
                    color = Color(0xFF74C0FC),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Text(
                    String.format("%.3f", gyroData[index]),
                    color = Color(0xFF9775FA),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Text(
                    String.format("%.3f", magData[index]),
                    color = Color(0xFFFFD43B),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}