package com.example.eyes.map

class UnavailableRouteRepository : RouteRepository {
    override suspend fun previewWalkingRoute(
        origin: UserLocation,
        destination: String
    ): Result<RoutePreview> {
        return Result.failure(IllegalStateException("Kho tuyến đi bộ chưa được triển khai"))
    }
}
