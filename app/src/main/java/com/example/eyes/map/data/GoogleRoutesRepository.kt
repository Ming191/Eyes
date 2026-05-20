package com.example.eyes.map.data

import com.example.eyes.map.RoutePreview
import com.example.eyes.map.RouteRepository
import com.example.eyes.map.UserLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RoutesFieldMask = "routes.distanceMeters," +
    "routes.duration," +
    "routes.polyline.encodedPolyline," +
    "routes.legs.steps.distanceMeters," +
    "routes.legs.steps.staticDuration," +
    "routes.legs.steps.navigationInstruction.instructions"

class GoogleRoutesRepository(
    private val routesApi: RoutesApi,
    private val apiKey: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RouteRepository {

    override suspend fun previewWalkingRoute(
        origin: UserLocation,
        destination: String
    ): Result<RoutePreview> = withContext(dispatcher) {
        runCatching {
            if (apiKey.isBlank()) {
                throw IllegalStateException("Chưa cấu hình khóa Google Routes API")
            }

            val cleanDestination = destination.trim()
            if (cleanDestination.isBlank()) {
                throw IllegalArgumentException("Vui lòng nhập điểm đến")
            }

            val request = ComputeRoutesRequest(
                origin = origin.toRoutesWaypoint(),
                destination = cleanDestination.toAddressWaypoint()
            )

            routesApi.computeRoutes(
                apiKey = apiKey,
                fieldMask = RoutesFieldMask,
                request = request
            ).toRoutePreview()
        }
    }
}
