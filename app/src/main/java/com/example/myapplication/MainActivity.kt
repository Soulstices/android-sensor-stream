package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.core.content.edit

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

    // SharedPreferences for settings
    val sharedPrefs = remember {
        context.getSharedPreferences("sensor_service_prefs", Context.MODE_PRIVATE)
    }

    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var accelData by remember { mutableStateOf(FloatArray(3)) }
    var gyroData by remember { mutableStateOf(FloatArray(3)) }
    var magData by remember { mutableStateOf(FloatArray(3)) }
    var orientationAngles by remember { mutableStateOf(FloatArray(3)) }
    var serviceStatus by remember { mutableStateOf("🟢 Service Running") }
    var isServiceRunning by remember { mutableStateOf(true) }
    var loggingEnabled by remember { mutableStateOf(false) }

    // Settings
    var targetIp by remember {
        mutableStateOf(sharedPrefs.getString("target_ip", "127.0.0.1") ?: "127.0.0.1")
    }
    var targetPort by remember {
        mutableIntStateOf(sharedPrefs.getInt("target_port", 9999))
    }
    var updateInterval by remember {
        mutableLongStateOf(sharedPrefs.getLong("update_interval", 5L))
    }
    var showNetworkDialog by remember { mutableStateOf(false) }

    // Function to handle service status changes
    val onServiceStatusChange: (String, Boolean) -> Unit = { status, running ->
        serviceStatus = status
        isServiceRunning = running
    }

    // Function to restart service with current settings
    val restartServiceWithSettings: () -> Unit = {
        if (isServiceRunning) {
            val intent = Intent(context, SensorService::class.java).apply {
                putExtra("logging_enabled", loggingEnabled)
                putExtra("target_ip", targetIp)
                putExtra("target_port", targetPort)
                putExtra("update_interval", updateInterval)
            }
            context.stopService(intent)
            context.startForegroundService(intent)
        }
    }

    // Function to update settings
    val updateSettings: (String, Int, Long) -> Unit = { ip, port, interval ->
        targetIp = ip
        targetPort = port
        updateInterval = interval
        // Save to SharedPreferences
        sharedPrefs.edit {
            putString("target_ip", ip)
            putInt("target_port", port)
            putLong("update_interval", interval)
        }
        restartServiceWithSettings()
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

    // Network Settings Dialog
    if (showNetworkDialog) {
        NetworkSettingsDialog(
            currentIp = targetIp,
            currentPort = targetPort,
            currentUpdateInterval = updateInterval,
            onDismiss = { showNetworkDialog = false },
            onConfirm = { ip, port, interval ->
                updateSettings(ip, port, interval)
                showNetworkDialog = false
            }
        )
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F23),
                        Color(0xFF000000)
                    ),
                    radius = 1200f
                )
            )
    ) {
        // Animated background particles
        AnimatedBackgroundParticles()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced Header Card
                EnhancedHeaderCard(
                    targetIp = targetIp,
                    targetPort = targetPort,
                    updateInterval = updateInterval,
                    serviceStatus = serviceStatus,
                    isServiceRunning = isServiceRunning,
                    context = context,
                    onServiceStatusChange = onServiceStatusChange,
                    loggingEnabled = loggingEnabled,
                    onLoggingToggle = { enabled ->
                        loggingEnabled = enabled
                        restartServiceWithSettings()
                    },
                    onNetworkSettingsClick = { showNetworkDialog = true }
                )

                // Enhanced Orientation Card
                if (rotationVector != null) {
                    EnhancedOrientationCard(orientationAngles)
                }

                // Enhanced Motion Sensors Card
                EnhancedMotionSensorsCard(accelData, gyroData, magData)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun NetworkSettingsDialog(
    currentIp: String,
    currentPort: Int,
    currentUpdateInterval: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long) -> Unit
) {
    var ipText by remember { mutableStateOf(currentIp) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var intervalText by remember { mutableStateOf(currentUpdateInterval.toString()) }
    var ipError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }
    var intervalError by remember { mutableStateOf(false) }
    var ipErrorMessage by remember { mutableStateOf("") }

    // Function to validate IP address format
    fun isValidIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false

        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255 && part == num.toString() // Ensures no leading zeros like "01"
            } catch (_: NumberFormatException) {
                false
            }
        }
    }

    // Function to filter IP input - only allow digits and dots
    fun filterIpInput(input: String): String {
        return input.filter { char -> char.isDigit() || char == '.' }
            .let { filtered ->
                // Prevent multiple consecutive dots
                var result = ""
                var lastWasDot = false
                for (char in filtered) {
                    if (char == '.') {
                        if (!lastWasDot && result.isNotEmpty()) {
                            result += char
                            lastWasDot = true
                        }
                    } else {
                        result += char
                        lastWasDot = false
                    }
                }
                result
            }
            .let { filtered ->
                // Limit each octet to 3 digits and validate range
                val parts = filtered.split(".")
                if (parts.size <= 4) {
                    parts.mapIndexed { index, part ->
                        if (part.length <= 3) {
                            val num = part.toIntOrNull()
                            if (num != null && num <= 255) part else part.dropLast(1)
                        } else {
                            part.take(3)
                        }
                    }.joinToString(".")
                } else {
                    // If more than 4 parts, take only first 4
                    parts.take(4).joinToString(".")
                }
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Streaming Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF74C0FC)
                )

                // IP Address Field
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { input ->
                        val filtered = filterIpInput(input)
                        ipText = filtered
                        ipError = false
                        ipErrorMessage = ""
                    },
                    label = { Text("IP Address", color = Color(0xFFADB5BD)) },
                    placeholder = { Text("127.0.0.1", color = Color(0xFF6C757D)) },
                    isError = ipError,
                    supportingText = if (ipError) {
                        { Text(ipErrorMessage, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                    } else {
                        { Text("Format: 192.168.1.1", color = Color(0xFF6C757D), fontSize = 11.sp) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (ipError) Color(0xFFFF6B6B) else Color(0xFF74C0FC),
                        unfocusedBorderColor = if (ipError) Color(0xFFFF6B6B) else Color(0xFF495057),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF74C0FC),
                        errorBorderColor = Color(0xFFFF6B6B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Port Field
                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        // Filter to only allow digits and limit to 5 characters (max port is 65535)
                        val filtered = input.filter { char -> char.isDigit() }.take(5)
                        portText = filtered
                        portError = false
                    },
                    label = { Text("Port", color = Color(0xFFADB5BD)) },
                    placeholder = { Text("9999", color = Color(0xFF6C757D)) },
                    supportingText = if (portError) {
                        { Text("Port must be between 1 and 65535", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                    } else {
                        { Text("Range: 1-65535", color = Color(0xFF6C757D), fontSize = 11.sp) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = portError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (portError) Color(0xFFFF6B6B) else Color(0xFF74C0FC),
                        unfocusedBorderColor = if (portError) Color(0xFFFF6B6B) else Color(0xFF495057),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF74C0FC),
                        errorBorderColor = Color(0xFFFF6B6B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Update Interval Field
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { input ->
                        // Filter to only allow digits and limit to reasonable length
                        val filtered = input.filter { char -> char.isDigit() }.take(6)
                        intervalText = filtered
                        intervalError = false
                    },
                    label = { Text("Update Interval (ms)", color = Color(0xFFADB5BD)) },
                    placeholder = { Text("5", color = Color(0xFF6C757D)) },
                    supportingText = if (intervalError) {
                        { Text("Interval must be between 1 and 10000 ms", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                    } else {
                        { Text("Range: 1-10000 ms (lower = faster)", color = Color(0xFF6C757D), fontSize = 11.sp) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = intervalError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (intervalError) Color(0xFFFF6B6B) else Color(0xFF74C0FC),
                        unfocusedBorderColor = if (intervalError) Color(0xFFFF6B6B) else Color(0xFF495057),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF74C0FC),
                        errorBorderColor = Color(0xFFFF6B6B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFADB5BD)
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val port = portText.toIntOrNull()
                            val interval = intervalText.toLongOrNull()
                            val isIpValid = isValidIpAddress(ipText)
                            val isPortValid = port != null && port in 1..65535
                            val isIntervalValid = interval != null && interval in 1..10000

                            when {
                                !isIpValid -> {
                                    ipError = true
                                    ipErrorMessage = when {
                                        ipText.isBlank() -> "IP address cannot be empty"
                                        ipText.split(".").size != 4 -> "IP must have 4 parts separated by dots"
                                        else -> "Invalid IP address format"
                                    }
                                }
                                !isPortValid -> {
                                    portError = true
                                }
                                !isIntervalValid -> {
                                    intervalError = true
                                }
                                else -> {
                                    onConfirm(ipText, port, interval)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF74C0FC)
                        )
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedBackgroundParticles() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    repeat(8) { index ->
        val animatedOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (8000 + index * 1000),
                    easing = LinearEasing
                )
            ),
            label = "particle_$index"
        )

        Box(
            modifier = Modifier
                .offset(
                    x = (50 + index * 40).dp,
                    y = (100 + index * 80).dp
                )
                .size((4 + index % 3).dp)
                .rotate(animatedOffset)
                .alpha(0.1f + (index % 3) * 0.05f)
                .background(
                    Color.White,
                    CircleShape
                )
        )
    }
}

@Composable
fun EnhancedHeaderCard(
    targetIp: String,
    targetPort: Int,
    updateInterval: Long,
    serviceStatus: String,
    isServiceRunning: Boolean,
    context: Context,
    onServiceStatusChange: (String, Boolean) -> Unit,
    loggingEnabled: Boolean,
    onLoggingToggle: (Boolean) -> Unit,
    onNetworkSettingsClick: () -> Unit
) {
    val pulsatingScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isServiceRunning) pulsatingScale else 1f)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E).copy(alpha = 0.95f)
        )
    ) {
        Box {
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF667EEA).copy(alpha = 0.1f),
                                Color(0xFF764BA2).copy(alpha = 0.1f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                "Android Sensor Stream",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF74C0FC)
                            )
                        }
                    }
                }

                // Network endpoint row with settings button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🌐 Endpoint",
                        fontSize = 14.sp,
                        color = Color(0xFFADB5BD),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "$targetIp:$targetPort",
                            fontSize = 14.sp,
                            color = Color(0xFF74C0FC),
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = onNetworkSettingsClick,
                            modifier = Modifier
                                .height(32.dp)
                                .width(60.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF74C0FC),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Edit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Update interval row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⏱️ Interval",
                        fontSize = 14.sp,
                        color = Color(0xFFADB5BD),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${updateInterval}ms",
                        fontSize = 14.sp,
                        color = Color(0xFFFFD43B),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status row with toggle button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚡ Status",
                        fontSize = 14.sp,
                        color = Color(0xFFADB5BD),
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            serviceStatus,
                            fontSize = 14.sp,
                            color = when {
                                serviceStatus.contains("Running") -> Color(0xFF51CF66)
                                serviceStatus.contains("Stopped") -> Color(0xFFFF6B6B)
                                else -> Color(0xFFFFD43B)
                            },
                            fontWeight = FontWeight.Bold
                        )

                        ServiceToggleButton(
                            isServiceRunning = isServiceRunning,
                            context = context,
                            onServiceStatusChange = onServiceStatusChange,
                            loggingEnabled = loggingEnabled,
                            targetIp = targetIp,
                            targetPort = targetPort,
                            updateInterval = updateInterval
                        )
                    }
                }

                // Logging toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📝 Logging",
                        fontSize = 14.sp,
                        color = Color(0xFFADB5BD),
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (loggingEnabled) "Enabled" else "Disabled",
                            fontSize = 14.sp,
                            color = if (loggingEnabled) Color(0xFF51CF66) else Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold
                        )

                        Switch(
                            checked = loggingEnabled,
                            onCheckedChange = onLoggingToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF51CF66),
                                checkedTrackColor = Color(0xFF51CF66).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color(0xFFFF6B6B),
                                uncheckedTrackColor = Color(0xFFFF6B6B).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedOrientationCard(orientationAngles: FloatArray) {
    val animatedRotation by rememberInfiniteTransition(label = "compass").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D1B69).copy(alpha = 0.9f)
        )
    ) {
        Box {
            // Animated background pattern
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9775FA).copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            radius = 300f
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .rotate(animatedRotation)
                            .background(
                                Color(0xFF9775FA).copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧭", fontSize = 20.sp)
                    }

                    Text(
                        "Device Orientation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF9775FA)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val axisData = listOf(
                        Pair("🧭 Yaw", orientationAngles[0]),
                        Pair("↕️ Pitch", orientationAngles[1]),
                        Pair("🔄 Roll", orientationAngles[2])
                    )

                    axisData.forEach { (label, value) ->
                        EnhancedOrientationItem(
                            label = label,
                            value = value
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun EnhancedOrientationItem(
    label: String,
    value: Float
) {
    Card(
        modifier = Modifier.size(width = 100.dp, height = 90.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A3E).copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFADB5BD),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${String.format("%.1f", value)}°",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun EnhancedMotionSensorsCard(
    accelData: FloatArray,
    gyroData: FloatArray,
    magData: FloatArray
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D4A2D).copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(0xFF51CF66).copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊", fontSize = 20.sp)
                }

                Text(
                    "Motion Sensors",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF51CF66)
                )
            }

            EnhancedSensorDataTable(accelData, gyroData, magData)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun EnhancedSensorDataTable(accelData: FloatArray, gyroData: FloatArray, magData: FloatArray) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Header Row
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF212529)
            ),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TableHeaderCell("Axis", Color(0xFFADB5BD), weight = 0.5f)
                TableHeaderCell("🚀 Accel\n(m/s²)", Color(0xFF74C0FC), weight = 1f)
                TableHeaderCell("🌪️ Gyro\n(rad/s)", Color(0xFF9775FA), weight = 1f)
                TableHeaderCell("🧲 Mag\n(μT)", Color(0xFFFFD43B), weight = 1f)
            }
        }

        // Data Rows
        listOf("X", "Y", "Z").forEachIndexed { index, axis ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (index % 2 == 0)
                        Color(0xFF2D3748).copy(alpha = 0.4f)
                    else
                        Color(0xFF1A202C).copy(alpha = 0.4f)
                ),
                shape = if (index == 2)
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                else
                    RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TableDataCell(axis, Color.White, FontWeight.Bold, weight = 0.5f)
                    TableDataCell(String.format("%.3f", accelData[index]), Color(0xFF74C0FC), weight = 1f)
                    TableDataCell(String.format("%.3f", gyroData[index]), Color(0xFF9775FA), weight = 1f)
                    TableDataCell(String.format("%.3f", magData[index]), Color(0xFFFFD43B), weight = 1f)
                }
            }
        }
    }
}

@Composable
fun RowScope.TableHeaderCell(text: String, color: Color, weight: Float = 1f) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        fontSize = 11.sp
    )
}

@Composable
fun RowScope.TableDataCell(
    text: String,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    weight: Float = 1f
) {
    Text(
        text,
        color = color,
        fontWeight = fontWeight,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        fontSize = 12.sp
    )
}

@Composable
fun ServiceToggleButton(
    isServiceRunning: Boolean,
    context: Context,
    onServiceStatusChange: (String, Boolean) -> Unit,
    loggingEnabled: Boolean,
    targetIp: String,
    targetPort: Int,
    updateInterval: Long
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isServiceRunning) Color(0xFFFF6B6B) else Color(0xFF51CF66),
        animationSpec = tween(300),
        label = "button_color"
    )

    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_scale"
    )

    Button(
        onClick = {
            val intent = Intent(context, SensorService::class.java).apply {
                putExtra("logging_enabled", loggingEnabled)
                putExtra("target_ip", targetIp)
                putExtra("target_port", targetPort)
                putExtra("update_interval", updateInterval)
            }
            if (isServiceRunning) {
                context.stopService(intent)
                onServiceStatusChange("⏹️ Service Stopped", false)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                onServiceStatusChange("🟢 Service Running", true)
            }
        },
        modifier = Modifier
            .scale(animatedScale)
            .height(36.dp)
            .width(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Text(
            if (isServiceRunning) "Stop" else "Start",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}