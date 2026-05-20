package com.example.eyes.map.data

data class ComputeRoutesRequest(
    val origin: RoutesWaypoint,
    val destination: RoutesWaypoint,
    val travelMode: String = "WALK",
    val polylineQuality: String = "OVERVIEW",
    val polylineEncoding: String = "ENCODED_POLYLINE",
    val languageCode: String = "vi-VN",
    val units: String = "METRIC"
)

data class RoutesWaypoint(
    val location: RoutesLocation? = null,
    val address: String? = null
)

data class RoutesLocation(
    val latLng: RoutesLatLng
)

data class RoutesLatLng(
    val latitude: Double,
    val longitude: Double
)

data class ComputeRoutesResponse(
    val routes: List<RoutesRoute> = emptyList()
)

data class RoutesRoute(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val polyline: RoutesPolyline? = null,
    val legs: List<RoutesLeg> = emptyList()
)

data class RoutesPolyline(
    val encodedPolyline: String? = null
)

data class RoutesLeg(
    val steps: List<RoutesStep> = emptyList()
)

data class RoutesStep(
    val distanceMeters: Int? = null,
    val staticDuration: String? = null,
    val navigationInstruction: RoutesNavigationInstruction? = null
)

data class RoutesNavigationInstruction(
    val instructions: String? = null
)
