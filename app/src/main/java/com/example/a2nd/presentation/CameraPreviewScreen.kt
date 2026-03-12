package com.example.a2nd.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.a2nd.data.network.HazardReport
import com.example.a2nd.data.network.RetrofitClient
import com.example.a2nd.domain.vision.PotholeDetector
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// Move throttle state OUT of Compose scope to prevent permanent lock
private var globalLastReportTime = 0L

@Composable
fun CameraPreviewScreen(
    onHazardReported: () -> Unit = {},
    onLocationUpdated: (Location) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            permissionsGranted = permissions.values.all { it }
        }
    )

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            launcher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    if (permissionsGranted) {
        CameraContainer(context, lifecycleOwner, coroutineScope, onHazardReported, onLocationUpdated)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Permissions required for AI and GPS", color = Color.White)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraContainer(
    context: Context, 
    lifecycleOwner: LifecycleOwner, 
    scope: CoroutineScope,
    onHazardReported: () -> Unit,
    onLocationUpdated: (Location) -> Unit
) {
    val previewView = remember { PreviewView(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // FIX: Using MutableState object directly to ensure background lambda captures the reference, not a stale value
    val lastLocationState = remember { mutableStateOf<Location?>(null) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentOnHazardReported = rememberUpdatedState(onHazardReported)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Update location continuously
    DisposableEffect(Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLocationState.value = location
                    onLocationUpdated(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("GRIIN", "GPS: Error starting updates: ${e.message}")
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    val detector = remember {
        PotholeDetector(context) { results ->
            val currentTime = System.currentTimeMillis()
            results.forEach { result ->
                // Stricter threshold check
                if (result.score >= 0.80f) {
                    Log.d("GRIIN", "DETECTION: ${result.label} (${result.score})")
                    
                    if (currentTime - globalLastReportTime > 5000) {
                        globalLastReportTime = currentTime
                        
                        // FIX: Retrieve REAL coordinates from the state object reference
                        val currentLocation = lastLocationState.value
                        val lat = currentLocation?.latitude ?: 0.0
                        val lon = currentLocation?.longitude ?: 0.0
                        
                        if (currentLocation == null) {
                            Log.w("GRIIN_NETWORK", "GPS: Warning - Location still null during detection!")
                        }

                        scope.launch(Dispatchers.IO) {
                            try {
                                val report = HazardReport(
                                    latitude = lat,
                                    longitude = lon,
                                    severity = result.score.toDouble()
                                )
                                Log.d("GRIIN_NETWORK", "Attempting to send payload to server with GPS: [$lat, $lon]")
                                
                                RetrofitClient.apiService.reportHazard(report)
                                
                                Log.d("GRIIN_NETWORK", "SUCCESS: Server received data!")
                                scope.launch(Dispatchers.Main) {
                                    currentOnHazardReported.value()
                                }
                            } catch (e: Exception) {
                                Log.e("GRIIN_NETWORK", "FAILED: " + e.message)
                            }
                        }
                    } else {
                        Log.d("GRIIN", "THROTTLE: Skipping report, cooldown active.")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (detector.isReady()) {
                                detector.analyze(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.d("GRIIN", "CAMERA: Bound successfully with AI and GPS.")
            } catch (e: Exception) {
                Log.e("GRIIN", "CAMERA: Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        Text(
            text = "AI ACTIVE | GPS: ${if (lastLocationState.value != null) "LOCKED" else "SEARCHING"}",
            color = Color.Green,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        )
    }
}
