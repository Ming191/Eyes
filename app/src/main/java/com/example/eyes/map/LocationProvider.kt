package com.example.eyes.map

interface LocationProvider {
    suspend fun currentLocation(): Result<UserLocation>
}
