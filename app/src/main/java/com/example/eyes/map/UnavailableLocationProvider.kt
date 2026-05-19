package com.example.eyes.map

class UnavailableLocationProvider : LocationProvider {
    override suspend fun currentLocation(): Result<UserLocation> {
        return Result.failure(IllegalStateException("Nhà cung cấp vị trí chưa được triển khai"))
    }
}
