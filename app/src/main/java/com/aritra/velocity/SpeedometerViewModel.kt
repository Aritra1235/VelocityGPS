package com.aritra.velocity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

const val METERS_PER_SECOND_TO_KMH = 3.6f

enum class DisplayStyle(val label: String) {
    DIGITAL("Digital"),
    ANALOG("Analog"),
    HYBRID("Hybrid")
}

enum class SpeedUnit(val label: String, val shortLabel: String, val multiplier: Float) {
    KMH("Kilometres/hour", "km/h", 3.6f),
    MPH("Miles/hour", "mph", 2.2369363f),
    KNOTS("Knots", "kn", 1.9438445f)
}

enum class SmoothingMode(val label: String, val alpha: Float) {
    RESPONSIVE("Responsive", 0.68f),
    BALANCED("Balanced", 0.38f),
    SMOOTH("Smooth", 0.20f)
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class AppSettings(
    val displayStyle: DisplayStyle = DisplayStyle.HYBRID,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val smoothingMode: SmoothingMode = SmoothingMode.BALANCED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val dialMaxKmh: Int = 240
)

data class SpeedUiState(
    val rawSpeedMps: Float = 0f,
    val speedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val distanceMeters: Float = 0f,
    val elapsedMillis: Long = 0L,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val hasFix: Boolean = false,
    val isTracking: Boolean = false
) {
    val averageSpeedMps: Float
        get() = if (elapsedMillis > 1_000L) distanceMeters / (elapsedMillis / 1000f) else 0f
}

class SpeedometerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("velocity_settings", 0)
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState.asStateFlow()

    private var tracking = false
    private var previousLocation: Location? = null
    private var filteredSpeed = 0f
    private var tripStartedElapsed: Long? = null
    private var tickerJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::consumeLocation)
        }
    }

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        700L
    )
        .setMinUpdateIntervalMillis(250L)
        .setMaxUpdateDelayMillis(1_000L)
        .setWaitForAccurateLocation(false)
        .build()

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (tracking || !hasLocationPermission()) return
        tracking = true
        if (tripStartedElapsed == null) tripStartedElapsed = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(isTracking = true)
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        startTicker()
    }

    fun stopTracking() {
        if (!tracking) return
        tracking = false
        fusedClient.removeLocationUpdates(locationCallback)
        tickerJob?.cancel()
        tickerJob = null
        _uiState.value = _uiState.value.copy(isTracking = false)
    }

    fun resetTrip() {
        previousLocation = null
        filteredSpeed = 0f
        tripStartedElapsed = if (tracking) SystemClock.elapsedRealtime() else null
        _uiState.value = _uiState.value.copy(
            rawSpeedMps = 0f,
            speedMps = 0f,
            maxSpeedMps = 0f,
            distanceMeters = 0f,
            elapsedMillis = 0L
        )
    }

    fun setDisplayStyle(value: DisplayStyle) = updateSettings { it.copy(displayStyle = value) }
    fun setSpeedUnit(value: SpeedUnit) = updateSettings { it.copy(speedUnit = value) }
    fun setSmoothing(value: SmoothingMode) = updateSettings { it.copy(smoothingMode = value) }
    fun setTheme(value: ThemeMode) = updateSettings { it.copy(themeMode = value) }
    fun setDynamicColor(value: Boolean) = updateSettings { it.copy(dynamicColor = value) }
    fun setKeepScreenOn(value: Boolean) = updateSettings { it.copy(keepScreenOn = value) }
    fun setDialMax(value: Int) = updateSettings { it.copy(dialMaxKmh = value) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("display", next.displayStyle.name)
            .putString("unit", next.speedUnit.name)
            .putString("smoothing", next.smoothingMode.name)
            .putString("theme", next.themeMode.name)
            .putBoolean("dynamic", next.dynamicColor)
            .putBoolean("keep_on", next.keepScreenOn)
            .putInt("dial_max", next.dialMaxKmh)
            .apply()
    }

    private fun loadSettings(): AppSettings = AppSettings(
        displayStyle = enumValueOrDefault(prefs.getString("display", null), DisplayStyle.HYBRID),
        speedUnit = enumValueOrDefault(prefs.getString("unit", null), SpeedUnit.KMH),
        smoothingMode = enumValueOrDefault(prefs.getString("smoothing", null), SmoothingMode.BALANCED),
        themeMode = enumValueOrDefault(prefs.getString("theme", null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic", true),
        keepScreenOn = prefs.getBoolean("keep_on", true),
        dialMaxKmh = prefs.getInt("dial_max", 240).coerceIn(180, 300)
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return runCatching { enumValueOf<T>(value ?: "") }.getOrDefault(default)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (tracking) {
                updateElapsed()
                delay(1_000L)
            }
        }
    }

    private fun updateElapsed() {
        val started = tripStartedElapsed ?: return
        _uiState.value = _uiState.value.copy(
            elapsedMillis = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        )
    }

    private fun consumeLocation(location: Location) {
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val rawSpeed = calculateRawSpeed(location).coerceAtLeast(0f)
        val alpha = _settings.value.smoothingMode.alpha
        val stationaryThreshold = if ((accuracy ?: 100f) <= 20f) 0.35f else 0.65f
        val sanitized = if (rawSpeed < stationaryThreshold) 0f else rawSpeed
        filteredSpeed = if (!_uiState.value.hasFix) sanitized else {
            alpha * sanitized + (1f - alpha) * filteredSpeed
        }
        if (sanitized == 0f && filteredSpeed < 0.35f) filteredSpeed = 0f

        var distance = _uiState.value.distanceMeters
        val prev = previousLocation
        if (prev != null && isGoodLocationPair(prev, location)) {
            val segment = prev.distanceTo(location)
            if (segment >= 0.8f) distance += segment
        }
        previousLocation = location

        if (tripStartedElapsed == null) tripStartedElapsed = SystemClock.elapsedRealtime()
        val elapsed = tripStartedElapsed?.let { SystemClock.elapsedRealtime() - it } ?: 0L

        _uiState.value = _uiState.value.copy(
            rawSpeedMps = rawSpeed,
            speedMps = filteredSpeed,
            maxSpeedMps = max(_uiState.value.maxSpeedMps, filteredSpeed),
            distanceMeters = distance,
            elapsedMillis = elapsed,
            accuracyMeters = accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            hasFix = true,
            isTracking = tracking
        )
    }

    private fun calculateRawSpeed(location: Location): Float {
        if (location.hasSpeed()) return location.speed
        val prev = previousLocation ?: return 0f
        val dtSeconds = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000f
        if (dtSeconds <= 0.15f || dtSeconds > 5f) return 0f
        return prev.distanceTo(location) / dtSeconds
    }

    private fun isGoodLocationPair(previous: Location, current: Location): Boolean {
        val currentAccuracy = if (current.hasAccuracy()) current.accuracy else 100f
        val previousAccuracy = if (previous.hasAccuracy()) previous.accuracy else 100f
        if (currentAccuracy > 45f || previousAccuracy > 45f) return false

        val dt = (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000f
        if (dt <= 0f || dt > 6f) return false

        val distance = previous.distanceTo(current)
        val plausibleMax = max(80f, max(current.speed, previous.speed) * dt * 3f + 25f)
        return distance <= plausibleMax
    }

    private fun hasLocationPermission(): Boolean {
        val app = getApplication<Application>()
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}
