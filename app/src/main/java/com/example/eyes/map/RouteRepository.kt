package com.example.eyes.map

interface RouteRepository {
    suspend fun previewWalkingRoute(
        origin: UserLocation,
        destination: String
    ): Result<RoutePreview>
}
