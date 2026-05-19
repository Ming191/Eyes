package com.example.eyes.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.map.LocationProvider
import com.example.eyes.map.MapUiState
import com.example.eyes.map.RouteRepository
import com.example.eyes.map.UserLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val locationProvider: LocationProvider,
    private val routeRepository: RouteRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _destinationText = MutableStateFlow("")
    val destinationText: StateFlow<String> = _destinationText.asStateFlow()

    fun onMapOpened(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            _uiState.value = MapUiState.LocationPermissionRequired
            return
        }

        loadCurrentLocation()
    }

    fun onDestinationChanged(text: String) {
        _destinationText.value = text
    }

    fun previewRoute() {
        val origin = currentLocationOrNull() ?: return
        val destination = _destinationText.value.trim()
        if (destination.isBlank()) {
            _uiState.value = MapUiState.Error("Vui lòng nhập điểm đến")
            return
        }

        viewModelScope.launch(dispatcher) {
            _uiState.value = MapUiState.RouteLoading(origin, destination)
            routeRepository.previewWalkingRoute(origin, destination)
                .onSuccess { route ->
                    _uiState.value = MapUiState.RouteReady(origin, destination, route)
                }
                .onFailure { error ->
                    _uiState.value = MapUiState.Error(
                        error.message ?: "Không thể xem trước tuyến đi"
                    )
                }
        }
    }

    fun retryLocation(hasLocationPermission: Boolean) {
        onMapOpened(hasLocationPermission)
    }

    private fun loadCurrentLocation() {
        viewModelScope.launch(dispatcher) {
            _uiState.value = MapUiState.Locating
            locationProvider.currentLocation()
                .onSuccess { location ->
                    _uiState.value = MapUiState.Ready(location)
                }
                .onFailure { error ->
                    _uiState.value = MapUiState.Error(
                        error.message ?: "Không thể lấy vị trí hiện tại"
                    )
                }
        }
    }

    private fun currentLocationOrNull(): UserLocation? {
        return when (val state = _uiState.value) {
            is MapUiState.Ready -> state.location
            is MapUiState.RouteLoading -> state.location
            is MapUiState.RouteReady -> state.location
            else -> null
        }
    }
}
