package com.example.eyes.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.map.MapUiState
import com.example.eyes.map.UserLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.koin.androidx.compose.koinViewModel

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val DefaultCameraLocation = LatLng(10.762622, 106.660172)

@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result.hasLocationPermissionGrant() || context.hasLocationPermission()
        viewModel.onMapOpened(hasLocationPermission)
    }

    LaunchedEffect(hasLocationPermission) {
        viewModel.onMapOpened(hasLocationPermission)
    }

    MapScreenContent(
        uiState = uiState,
        hasLocationPermission = hasLocationPermission,
        onRequestLocationPermission = {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        },
        onRetryLocation = {
            viewModel.retryLocation(hasLocationPermission)
        }
    )
}

@Composable
fun MapScreenContent(
    uiState: MapUiState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRetryLocation: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (UserLocation?, Boolean, Modifier) -> Unit = { location, locationEnabled, mapModifier ->
        CurrentLocationMap(
            location = location,
            locationEnabled = locationEnabled,
            modifier = mapModifier
        )
    }
) {
    val location = uiState.currentLocationOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = "Màn hình bản đồ và dẫn đường" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bản đồ",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Tiêu đề bản đồ"
            }
        )

        MapStatusPanel(
            uiState = uiState,
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            onRetryLocation = onRetryLocation
        )

        if (uiState == MapUiState.LocationPermissionRequired || !hasLocationPermission) {
            PermissionRequiredPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            mapContent(
                location,
                hasLocationPermission,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun MapStatusPanel(
    uiState: MapUiState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRetryLocation: () -> Unit
) {
    val statusText = when {
        !hasLocationPermission || uiState == MapUiState.LocationPermissionRequired ->
            "Cần quyền vị trí để mở bản đồ."
        uiState == MapUiState.Idle ->
            "Bản đồ đang chuẩn bị."
        uiState == MapUiState.Locating ->
            "Đang lấy vị trí hiện tại."
        uiState is MapUiState.Ready ->
            "Đã tìm thấy vị trí hiện tại."
        uiState is MapUiState.RouteLoading ->
            "Đang chuẩn bị tuyến đi bộ."
        uiState is MapUiState.RouteReady ->
            "Đã sẵn sàng xem tuyến đi bộ."
        uiState is MapUiState.Error ->
            uiState.message
        else ->
            "Bản đồ đang chuẩn bị."
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Trạng thái bản đồ: ${statusText.lowercase()}"
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                !hasLocationPermission || uiState == MapUiState.LocationPermissionRequired -> {
                    Button(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp)
                            .semantics {
                                contentDescription = "Nút cấp quyền vị trí chính xác và vị trí gần đúng"
                            }
                    ) {
                        Text("Cấp quyền vị trí")
                    }
                }
                uiState is MapUiState.Error -> {
                    Button(
                        onClick = onRetryLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp)
                            .semantics {
                                contentDescription = "Nút thử lấy lại vị trí hiện tại"
                            }
                    ) {
                        Text("Thử lại")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredPanel(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 240.dp)
            .semantics { contentDescription = "Cần quyền vị trí để mở bản đồ" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Bản đồ sẽ xuất hiện sau khi có quyền vị trí.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics {
                contentDescription = "Bản đồ sẽ xuất hiện sau khi có quyền vị trí"
            }
        )
    }
}

@Composable
private fun CurrentLocationMap(
    location: UserLocation?,
    locationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCameraLocation, 14f)
    }
    val currentLatLng = location?.let {
        LatLng(it.latitude, it.longitude)
    }

    LaunchedEffect(currentLatLng) {
        currentLatLng?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = 280.dp)
            .semantics { contentDescription = "Bản đồ Google hiển thị vị trí hiện tại" }
    ) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Lớp bản đồ Google" },
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationEnabled),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = locationEnabled,
                zoomControlsEnabled = false
            )
        ) {
            currentLatLng?.let {
                Marker(
                    state = rememberUpdatedMarkerState(position = it),
                    title = "Vị trí hiện tại",
                    snippet = "Bạn đang ở đây"
                )
            }
        }

        if (location == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Đang lấy vị trí trên bản đồ" },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Đang tải vị trí"
                    }
                )
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean {
    return LOCATION_PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Map<String, Boolean>.hasLocationPermissionGrant(): Boolean {
    return LOCATION_PERMISSIONS.any { permission -> this[permission] == true }
}

private fun MapUiState.currentLocationOrNull(): UserLocation? {
    return when (this) {
        is MapUiState.Ready -> location
        is MapUiState.RouteLoading -> location
        is MapUiState.RouteReady -> location
        else -> null
    }
}
