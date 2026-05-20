package com.example.eyes.map.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface RoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: ComputeRoutesRequest
    ): ComputeRoutesResponse
}
