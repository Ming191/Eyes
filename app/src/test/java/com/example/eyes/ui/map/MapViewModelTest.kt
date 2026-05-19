package com.example.eyes.ui.map

import com.example.eyes.map.LocationProvider
import com.example.eyes.map.MapUiState
import com.example.eyes.map.RoutePreview
import com.example.eyes.map.RouteRepository
import com.example.eyes.map.UserLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewModelTest {

    @Test
    fun onMapOpened_withoutLocationPermission_setsPermissionRequired() {
        // GIVEN
        val viewModel = MapViewModel(
            locationProvider = FakeLocationProvider(),
            routeRepository = FakeRouteRepository(),
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        viewModel.onMapOpened(hasLocationPermission = false)

        // THEN
        assertEquals(MapUiState.LocationPermissionRequired, viewModel.uiState.value)
    }

    @Test
    fun onMapOpened_withLocationPermission_setsLocatingWhileProviderRuns() {
        // GIVEN
        val pendingLocation = CompletableDeferred<Result<UserLocation>>()
        val viewModel = MapViewModel(
            locationProvider = FakeLocationProvider(pendingLocation),
            routeRepository = FakeRouteRepository(),
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        viewModel.onMapOpened(hasLocationPermission = true)

        // THEN
        assertEquals(MapUiState.Locating, viewModel.uiState.value)
    }

    @Test
    fun onMapOpened_withLocationPermission_setsReadyWhenProviderReturnsLocation() = runBlocking {
        // GIVEN
        val location = UserLocation(latitude = 10.7769, longitude = 106.7009)
        val pendingLocation = CompletableDeferred<Result<UserLocation>>()
        val viewModel = MapViewModel(
            locationProvider = FakeLocationProvider(pendingLocation),
            routeRepository = FakeRouteRepository(),
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        viewModel.onMapOpened(hasLocationPermission = true)
        pendingLocation.complete(Result.success(location))
        yield()

        // THEN
        assertEquals(MapUiState.Ready(location), viewModel.uiState.value)
    }

    private class FakeLocationProvider(
        private val result: CompletableDeferred<Result<UserLocation>> =
            CompletableDeferred(Result.failure(IllegalStateException("Không dùng trong test")))
    ) : LocationProvider {
        override suspend fun currentLocation(): Result<UserLocation> {
            return result.await()
        }
    }

    private class FakeRouteRepository : RouteRepository {
        override suspend fun previewWalkingRoute(
            origin: UserLocation,
            destination: String
        ): Result<RoutePreview> {
            return Result.failure(IllegalStateException("Không dùng trong test"))
        }
    }
}
