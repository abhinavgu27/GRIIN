package com.example.a2nd.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a2nd.data.network.HazardReport
import com.example.a2nd.data.network.RetrofitClient
import com.example.a2nd.domain.vision.DetectionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VerifiedHazard(
    val detection: DetectionResult,
    val timestamp: Long
)

data class DetectionLog(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

class GRIINViewModel : ViewModel() {

    private val CHANNEL_ID = "griin_notifications"

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            delay(1500)
            _isReady.value = true
            fetchAllHazards()
        }
    }

    private val _totalHazardsDetected = MutableStateFlow(0)
    val totalHazardsDetected: StateFlow<Int> = _totalHazardsDetected.asStateFlow()

    private val _totalDistanceMovedMeters = MutableStateFlow(0.0)
    val totalDistanceMovedMeters: StateFlow<Double> = _totalDistanceMovedMeters.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<DetectionLog>>(emptyList())
    val recentLogs: StateFlow<List<DetectionLog>> = _recentLogs.asStateFlow()

    val detectedHazards = mutableStateListOf<GeoPoint>()

    private var lastLocationRaw: Location? = null

    private val lastVisualDetections = mutableListOf<Pair<DetectionResult, Long>>()
    private val detectionWindowMs = 500L

    fun fetchAllHazards() {
        viewModelScope.launch {
            try {
                val reports = RetrofitClient.apiService.getAllHazards()
                _totalHazardsDetected.value = reports.size
                
                detectedHazards.clear()
                val newLogs = mutableListOf<DetectionLog>()
                
                reports.forEach { report ->
                    detectedHazards.add(GeoPoint(report.latitude, report.longitude))
                    val logMsg = "Pothole: [${String.format("%.4f", report.latitude)}, ${String.format("%.4f", report.longitude)}] | Sev: ${String.format("%.2f", report.severity)}"
                    newLogs.add(DetectionLog(message = logMsg))
                }
                
                // Update logs, sorted by ID (latest first)
                _recentLogs.value = newLogs.sortedByDescending { it.id }
                
                Log.d("GRIIN_MAP", "FETCH SUCCESS: ${reports.size} hazards synced")
            } catch (e: Exception) {
                Log.e("GRIIN_MAP", "FETCH FAILED: ${e.message}")
            }
        }
    }

    fun updateLocation(newLocation: Location) {
        val newGeoPoint = GeoPoint(newLocation.latitude, newLocation.longitude)
        _currentLocation.value = newGeoPoint

        if (newLocation.accuracy < 20) {
            lastLocationRaw?.let { last ->
                val distance = last.distanceTo(newLocation)
                _totalDistanceMovedMeters.update { it + distance }
            }
            lastLocationRaw = newLocation
        }
    }

    fun incrementHazardCount() {
        val newPoint = _currentLocation.value ?: return
        
        val isDuplicate = detectedHazards.any { existingPoint ->
            val results = FloatArray(1)
            Location.distanceBetween(
                newPoint.latitude, newPoint.longitude,
                existingPoint.latitude, existingPoint.longitude,
                results
            )
            results[0] < 5
        }

        if (isDuplicate) {
            Log.d("GRIIN_MAP", "THROTTLE: Duplicate hazard ignored.")
            return
        }

        _totalHazardsDetected.update { it + 1 }
        detectedHazards.add(newPoint)
        
        val logMsg = "Pothole Detected at [${String.format("%.4f", newPoint.latitude)}, ${String.format("%.4f", newPoint.longitude)}]"
        val newLog = DetectionLog(message = logMsg)
        _recentLogs.update { listOf(newLog) + it }
    }

    fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GRIIN Hazard Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GRIIN Network")
            .setContentText("Hazard Reported Successfully!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun reportVerifiedHazard(detection: DetectionResult) {
        viewModelScope.launch {
            try {
                val lat = lastLocationRaw?.latitude ?: 0.0
                val lon = lastLocationRaw?.longitude ?: 0.0
                
                val report = HazardReport(
                    latitude = lat,
                    longitude = lon,
                    severity = detection.score.toDouble()
                )
                
                RetrofitClient.apiService.reportHazard(report)
                incrementHazardCount()
            } catch (e: Exception) {
                Log.e("GRIINViewModel", "Failed to report hazard", e)
                val errorLog = DetectionLog(message = "Network Error: Failed to report hazard")
                _recentLogs.update { listOf(errorLog) + it }
            }
        }
    }
}
