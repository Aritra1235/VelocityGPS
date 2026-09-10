package com.aritra.velocity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aritra.velocity.ui.theme.VelocityTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SpeedometerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[SpeedometerViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            VelocityTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                KeepScreenAwake(settings.keepScreenOn)
                VelocityApp(viewModel, settings, uiState)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission(this)) viewModel.startTracking()
    }

    override fun onStop() {
        viewModel.stopTracking()
        super.onStop()
    }
}

private enum class AppTab(val label: String) {
    SPEED("Speed"),
    SETTINGS("Settings")
}

@Composable
private fun VelocityApp(
    viewModel: SpeedometerViewModel,
    settings: AppSettings,
    uiState: SpeedUiState
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.SPEED) }
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(hasLocationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (permissionGranted) viewModel.startTracking()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            VelocityBottomBar(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
                    initialOffsetX = { direction * it / 3 }
                ) + scaleIn(initialScale = 0.97f)) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 330f),
                        targetOffsetX = { -direction * it / 4 }
                    ) + scaleOut(targetScale = 0.98f))
            },
            label = "tab-transition"
        ) { tab ->
            when (tab) {
                AppTab.SPEED -> SpeedScreen(
                    settings = settings,
                    uiState = uiState,
                    permissionGranted = permissionGranted,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onResetTrip = viewModel::resetTrip
                )

                AppTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun SpeedScreen(
    settings: AppSettings,
    uiState: SpeedUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onResetTrip: () -> Unit
) {
    val displayedSpeed = uiState.speedMps * settings.speedUnit.multiplier
    val displayedMax = uiState.maxSpeedMps * settings.speedUnit.multiplier
    val displayedAverage = uiState.averageSpeedMps * settings.speedUnit.multiplier
    val dialMax = settings.dialMaxKmh / METERS_PER_SECOND_TO_KMH * settings.speedUnit.multiplier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        HeaderStatus(uiState)
        Spacer(Modifier.height(12.dp))

        if (!permissionGranted) {
            LocationPermissionCard(onRequestPermission)
            Spacer(Modifier.height(16.dp))
        }

        AnimatedContent(
            targetState = settings.displayStyle,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 230f),
                    initialOffsetX = { direction * it / 4 }
                ) + scaleIn(initialScale = 0.92f)) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                        targetOffsetX = { -direction * it / 5 }
                    ) + scaleOut(targetScale = 0.94f))
            },
            label = "meter-style"
        ) { style ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (style) {
                    DisplayStyle.DIGITAL -> DigitalMeter(
                        speed = displayedSpeed,
                        maxSpeed = dialMax,
                        unit = settings.speedUnit.shortLabel,
                        hasFix = uiState.hasFix
                    )
                    DisplayStyle.ANALOG -> AnalogMeter(
                        speed = displayedSpeed,
                        maxSpeed = dialMax,
                        unit = settings.speedUnit.shortLabel,
                        showDigital = false
                    )
                    DisplayStyle.HYBRID -> AnalogMeter(
                        speed = displayedSpeed,
                        maxSpeed = dialMax,
                        unit = settings.speedUnit.shortLabel,
                        showDigital = true
                    )
                }
            }
        }

        TripStats(
            distanceMeters = uiState.distanceMeters,
            unit = settings.speedUnit,
            average = displayedAverage,
            max = displayedMax,
            elapsedMillis = uiState.elapsedMillis,
            bearing = uiState.bearingDegrees,
            onReset = onResetTrip
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeaderStatus(uiState: SpeedUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Velocity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                "GPS speedometer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val accuracy = uiState.accuracyMeters
        val statusText = when {
            !uiState.isTracking -> "Paused"
            !uiState.hasFix -> "Finding GPS"
            accuracy == null -> "GPS locked"
            accuracy <= 8f -> "Excellent ±${accuracy.roundToInt()} m"
            accuracy <= 20f -> "Good ±${accuracy.roundToInt()} m"
            else -> "Weak ±${accuracy.roundToInt()} m"
        }
        val icon = if (uiState.hasFix) Icons.Rounded.GpsFixed else Icons.Outlined.GpsNotFixed
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(statusText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun LocationPermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Location needed", fontWeight = FontWeight.Bold)
                Text(
                    "Velocity reads GPS speed only while the app is open.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onRequestPermission) { Text("Allow") }
        }
    }
}

@Composable
private fun DigitalMeter(
    speed: Float,
    maxSpeed: Float,
    unit: String,
    hasFix: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (speed / maxSpeed).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f),
        label = "digital-progress"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val speedInt = speed.roundToInt().coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = speedInt,
                transitionSpec = {
                    val goingUp = targetState >= initialState
                    if (goingUp) {
                        (slideInVertically(
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
                            initialOffsetY = { it / 2 }
                        ) + scaleIn(initialScale = 0.82f)) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(190),
                                targetOffsetY = { -it / 2 }
                            ) + scaleOut(targetScale = 0.88f))
                    } else {
                        (slideInVertically(
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
                            initialOffsetY = { -it / 2 }
                        ) + scaleIn(initialScale = 0.82f)) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(190),
                                targetOffsetY = { it / 2 }
                            ) + scaleOut(targetScale = 0.88f))
                    }
                },
                label = "speed-number"
            ) { value ->
                Text(
                    text = if (hasFix) value.toString() else "—",
                    fontSize = 92.sp,
                    lineHeight = 92.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-4).sp
                )
            }
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AnalogMeter(
    speed: Float,
    maxSpeed: Float,
    unit: String,
    showDigital: Boolean
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed.coerceIn(0f, maxSpeed),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 145f),
        label = "needle"
    )
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.42f
            val startAngle = 135f
            val sweep = 270f
            val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)

            drawArc(
                color = track,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = primary,
                startAngle = startAngle,
                sweepAngle = sweep * progress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onSurface.toArgb()
                textSize = 12.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            val ticks = 12
            for (i in 0..ticks) {
                val fraction = i / ticks.toFloat()
                val angleDeg = startAngle + sweep * fraction
                val angle = angleDeg * PI.toFloat() / 180f
                val isMajor = i % 2 == 0
                val outer = radius * 0.92f
                val inner = radius * if (isMajor) 0.78f else 0.84f
                val p1 = Offset(
                    center.x + cos(angle) * inner,
                    center.y + sin(angle) * inner
                )
                val p2 = Offset(
                    center.x + cos(angle) * outer,
                    center.y + sin(angle) * outer
                )
                drawLine(
                    color = onSurface.copy(alpha = if (isMajor) 0.92f else 0.42f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                if (isMajor) {
                    val labelRadius = radius * 0.66f
                    val x = center.x + cos(angle) * labelRadius
                    val y = center.y + sin(angle) * labelRadius + paint.textSize / 3f
                    drawContext.canvas.nativeCanvas.drawText(
                        (maxSpeed * fraction).roundToInt().toString(),
                        x,
                        y,
                        paint
                    )
                }
            }

            val needleAngleDeg = startAngle + sweep * progress
            val needleAngle = needleAngleDeg * PI.toFloat() / 180f
            val needleStart = Offset(
                center.x - cos(needleAngle) * radius * 0.08f,
                center.y - sin(needleAngle) * radius * 0.08f
            )
            val needleEnd = Offset(
                center.x + cos(needleAngle) * radius * 0.69f,
                center.y + sin(needleAngle) * radius * 0.69f
            )
            drawLine(
                color = primary,
                start = needleStart,
                end = needleEnd,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(primary, radius = 10.dp.toPx(), center = center)
            drawCircle(onSurface, radius = 4.dp.toPx(), center = center)
        }

        Column(
            modifier = Modifier.offset(y = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showDigital,
                enter = expandVertically(expandFrom = Alignment.CenterVertically) + scaleIn(initialScale = 0.7f),
                exit = shrinkVertically(shrinkTowards = Alignment.CenterVertically) + scaleOut(targetScale = 0.7f)
            ) {
                Text(
                    speed.roundToInt().coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TripStats(
    distanceMeters: Float,
    unit: SpeedUnit,
    average: Float,
    max: Float,
    elapsedMillis: Long,
    bearing: Float?,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = onReset, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset trip", modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    title = "Distance",
                    value = formatDistance(distanceMeters, unit),
                    icon = Icons.Outlined.Route,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Average",
                    value = "${average.roundToInt()} ${unit.shortLabel}",
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    title = "Maximum",
                    value = "${max.roundToInt()} ${unit.shortLabel}",
                    icon = Icons.Outlined.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Heading",
                    value = bearing?.let { "${headingName(it)} ${it.roundToInt()}°" } ?: "—",
                    icon = Icons.Outlined.Explore,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatDuration(elapsedMillis), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, viewModel: SpeedometerViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Make the speedometer feel exactly how you want.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            SettingsCard(title = "Speedometer", icon = Icons.Rounded.Speed) {
                Text("Style", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    values = DisplayStyle.entries,
                    selected = settings.displayStyle,
                    label = { it.label },
                    onSelect = viewModel::setDisplayStyle
                )
                Spacer(Modifier.height(18.dp))
                Text("Units", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    values = SpeedUnit.entries,
                    selected = settings.speedUnit,
                    label = { it.shortLabel },
                    onSelect = viewModel::setSpeedUnit
                )
                Spacer(Modifier.height(18.dp))
                Text("Analog dial range", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    values = listOf(180, 240, 300),
                    selected = settings.dialMaxKmh,
                    label = { "$it km/h" },
                    onSelect = viewModel::setDialMax
                )
            }
        }

        item {
            SettingsCard(title = "GPS response", icon = Icons.Rounded.GpsFixed) {
                Text(
                    "Smoothing reduces GPS jitter. Responsive reacts faster; Smooth gives a calmer needle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ChoiceRow(
                    values = SmoothingMode.entries,
                    selected = settings.smoothingMode,
                    label = { it.label },
                    onSelect = viewModel::setSmoothing
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    title = "Keep screen awake",
                    subtitle = "Useful while driving or cycling",
                    checked = settings.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn
                )
            }
        }

        item {
            SettingsCard(title = "Appearance", icon = Icons.Rounded.Palette) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    values = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.label },
                    onSelect = viewModel::setTheme
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    title = "Dynamic color",
                    subtitle = "Use your device's Material You palette on Android 12+",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("GPS notes", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Speed comes from the phone's location sensors. Accuracy is usually best outdoors with a clear sky. Velocity does not request background location and does not need an internet connection for speed readings.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(15.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        label(value),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VelocityBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 7.dp,
        shadowElevation = 5.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 330f),
                    label = "nav-scale"
                )
                val yOffset by animateDpAsState(
                    targetValue = if (isSelected) (-1).dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f),
                    label = "nav-y"
                )
                val icon = when (tab) {
                    AppTab.SPEED -> if (isSelected) Icons.Rounded.Speed else Icons.Outlined.Speed
                    AppTab.SETTINGS -> if (isSelected) Icons.Rounded.Settings else Icons.Outlined.Settings
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = if (isSelected) 18.dp else 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = tab.label,
                            modifier = Modifier
                                .offset(y = yOffset)
                                .scale(iconScale)
                                .size(23.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + slideInHorizontally { -it / 2 },
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + slideOutHorizontally { -it / 2 }
                        ) {
                            Text(
                                tab.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context as? Activity
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun formatDistance(meters: Float, unit: SpeedUnit): String {
    return when (unit) {
        SpeedUnit.KMH -> if (meters < 1_000f) "${meters.roundToInt()} m" else String.format("%.2f km", meters / 1_000f)
        SpeedUnit.MPH -> String.format("%.2f mi", meters / 1_609.344f)
        SpeedUnit.KNOTS -> String.format("%.2f nm", meters / 1_852f)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun headingName(degrees: Float): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((degrees % 360f) + 360f) % 360f
    return names[((normalized + 22.5f) / 45f).toInt() % 8]
}
