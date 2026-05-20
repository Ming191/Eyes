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
import java.io.IOException

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

    @Test
    fun previewRoute_withDestination_setsRouteReadyWhenRepositorySucceeds() = runBlocking {
        // GIVEN
        val location = UserLocation(latitude = 10.7769, longitude = 106.7009)
        val route = RoutePreview(
            distanceMeters = 1200,
            durationSeconds = 900,
            encodedPolyline = "abc123",
            steps = emptyList()
        )
        val viewModel = MapViewModel(
            locationProvider = FakeLocationProvider(CompletableDeferred(Result.success(location))),
            routeRepository = FakeRouteRepository(Result.success(route)),
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        viewModel.onMapOpened(hasLocationPermission = true)
        viewModel.onDestinationChanged("Nhà thờ Đức Bà")
        viewModel.previewRoute()
        yield()

        // THEN
        assertEquals(
            MapUiState.RouteReady(location, "Nhà thờ Đức Bà", route),
            viewModel.uiState.value
        )
    }

    @Test
    fun previewRoute_withNetworkFailure_setsErrorState() = runBlocking {
        // GIVEN
        val location = UserLocation(latitude = 10.7769, longitude = 106.7009)
        val viewModel = MapViewModel(
            locationProvider = FakeLocationProvider(CompletableDeferred(Result.success(location))),
            routeRepository = FakeRouteRepository(Result.failure(IOException("Mất kết nối mạng"))),
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        viewModel.onMapOpened(hasLocationPermission = true)
        viewModel.onDestinationChanged("Nhà thờ Đức Bà")
        viewModel.previewRoute()
        yield()

        // THEN
        assertEquals(MapUiState.Error("Mất kết nối mạng"), viewModel.uiState.value)
    }

    private class FakeLocationProvider(
        private val result: CompletableDeferred<Result<UserLocation>> =
            CompletableDeferred(Result.failure(IllegalStateException("Không dùng trong test")))
    ) : LocationProvider {
        override suspend fun currentLocation(): Result<UserLocation> {
            return result.await()
        }
    }

    private class FakeRouteRepository(
        private val result: Result<RoutePreview> =
            Result.failure(IllegalStateException("Không dùng trong test"))
    ) : RouteRepository {
        override suspend fun previewWalkingRoute(
            origin: UserLocation,
            destination: String
        ): Result<RoutePreview> {
            return result
        }
    }
}
