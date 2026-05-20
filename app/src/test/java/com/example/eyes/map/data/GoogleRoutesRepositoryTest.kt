package com.example.eyes.map.data

import com.example.eyes.map.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GoogleRoutesRepositoryTest {

    @Test
    fun previewWalkingRoute_mapsSuccessfulResponseToRoutePreview() = runBlocking {
        // GIVEN
        val api = FakeRoutesApi(
            response = ComputeRoutesResponse(
                routes = listOf(
                    RoutesRoute(
                        distanceMeters = 1200,
                        duration = "900s",
                        polyline = RoutesPolyline(encodedPolyline = "abc123"),
                        legs = listOf(
                            RoutesLeg(
                                steps = listOf(
                                    RoutesStep(
                                        distanceMeters = 300,
                                        staticDuration = "180s",
                                        navigationInstruction = RoutesNavigationInstruction(
                                            instructions = "Đi thẳng"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        val repository = GoogleRoutesRepository(
            routesApi = api,
            apiKey = "test-key",
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        val result = repository.previewWalkingRoute(
            origin = UserLocation(latitude = 10.7769, longitude = 106.7009),
            destination = "Nhà thờ Đức Bà"
        )

        // THEN
        val route = result.getOrThrow()
        assertEquals(1200, route.distanceMeters)
        assertEquals(900, route.durationSeconds)
        assertEquals("abc123", route.encodedPolyline)
        assertEquals("Đi thẳng", route.steps.single().instruction)
        assertEquals("WALK", api.lastRequest?.travelMode)
        assertEquals("vi-VN", api.lastRequest?.languageCode)
    }

    @Test
    fun previewWalkingRoute_returnsFailureWhenRoutesApiHasZeroResult() = runBlocking {
        // GIVEN
        val repository = GoogleRoutesRepository(
            routesApi = FakeRoutesApi(response = ComputeRoutesResponse(routes = emptyList())),
            apiKey = "test-key",
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        val result = repository.previewWalkingRoute(
            origin = UserLocation(latitude = 10.7769, longitude = 106.7009),
            destination = "Không có tuyến"
        )

        // THEN
        assertTrue(result.isFailure)
        assertEquals("Không tìm thấy tuyến đi bộ", result.exceptionOrNull()?.message)
    }

    @Test
    fun previewWalkingRoute_returnsFailureWhenNetworkFails() = runBlocking {
        // GIVEN
        val repository = GoogleRoutesRepository(
            routesApi = FakeRoutesApi(error = IOException("Mất kết nối mạng")),
            apiKey = "test-key",
            dispatcher = Dispatchers.Unconfined
        )

        // WHEN
        val result = repository.previewWalkingRoute(
            origin = UserLocation(latitude = 10.7769, longitude = 106.7009),
            destination = "Nhà thờ Đức Bà"
        )

        // THEN
        assertTrue(result.isFailure)
        assertEquals("Mất kết nối mạng", result.exceptionOrNull()?.message)
    }

    private class FakeRoutesApi(
        private val response: ComputeRoutesResponse = ComputeRoutesResponse(),
        private val error: Throwable? = null
    ) : RoutesApi {
        var lastRequest: ComputeRoutesRequest? = null

        override suspend fun computeRoutes(
            apiKey: String,
            fieldMask: String,
            request: ComputeRoutesRequest
        ): ComputeRoutesResponse {
            lastRequest = request
            error?.let { throw it }
            return response
        }
    }
}
