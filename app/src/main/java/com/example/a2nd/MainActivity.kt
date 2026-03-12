package com.example.a2nd

import android.Manifest
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.a2nd.presentation.CameraPreviewScreen
import com.example.a2nd.presentation.DashboardScreen
import com.example.a2nd.presentation.GRIINViewModel
import com.example.a2nd.ui.theme._2ndTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        
        setContent {
            val viewModel: GRIINViewModel = viewModel()
            val isReady by viewModel.isReady.collectAsState()
            
            splashScreen.setKeepOnScreenCondition { !isReady }

            _2ndTheme {
                MainScreen(viewModel)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Default.PhotoCamera)
    object Map : Screen("map", "Map", Icons.Default.Place)
}

@Composable
fun MainScreen(viewModel: GRIINViewModel) {
    val navController = rememberNavController()
    val totalHazards by viewModel.totalHazardsDetected.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val detectedHazards = viewModel.detectedHazards
    val context = LocalContext.current

    // Handle Notification Permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController, 
            startDestination = Screen.Dashboard.route, 
            Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { 
                // Dashboard Update: Sync data on resume
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    viewModel.fetchAllHazards()
                    onPauseOrDispose { }
                }
                DashboardScreen(totalHazards = totalHazards, logs = logs) 
            }
            composable(Screen.Scanner.route) { 
                CameraPreviewScreen(
                    onHazardReported = { 
                        viewModel.incrementHazardCount()
                        viewModel.showNotification(context)
                    },
                    onLocationUpdated = { viewModel.updateLocation(it) }
                ) 
            }
            composable(Screen.Map.route) { 
                MapScreen(
                    currentLocation = currentLocation,
                    detectedHazards = detectedHazards,
                    onRefresh = { viewModel.fetchAllHazards() }
                ) 
            }
        }
    }
}

private val items = listOf(
    Screen.Dashboard,
    Screen.Scanner,
    Screen.Map
)

@Composable
fun MapScreen(currentLocation: GeoPoint?, detectedHazards: List<GeoPoint>, onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                    locationOverlay.enableMyLocation()
                    locationOverlay.enableFollowLocation()
                    overlays.add(locationOverlay)
                }
            },
            update = { mapView ->
                if (currentLocation != null && mapView.overlays.none { it is MyLocationNewOverlay && it.isFollowLocationEnabled }) {
                    mapView.controller.animateTo(currentLocation)
                }

                mapView.overlays.removeAll { it is Marker && it.title == "hazard" }

                val context = mapView.context
                detectedHazards.forEach { hazardPoint ->
                    val marker = Marker(mapView).apply {
                        position = hazardPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "hazard"
                        subDescription = "Severe Pothole Detected"
                        
                        val icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                        icon?.let {
                            it.mutate()
                            it.colorFilter = PorterDuffColorFilter(android.graphics.Color.RED, PorterDuff.Mode.SRC_IN)
                        }
                        this.icon = icon
                    }
                    mapView.overlays.add(marker)
                }
                
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        FloatingActionButton(
            onClick = onRefresh,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Hazards")
        }
    }
}
