package com.example.eyes.map.data

import com.example.eyes.map.RoutePreview
import com.example.eyes.map.RouteStep
import com.example.eyes.map.UserLocation

private val DurationPattern = Regex("""^(\d+)(?:\.\d+)?s$""")

fun UserLocation.toRoutesWaypoint(): RoutesWaypoint {
    return RoutesWaypoint(
        location = RoutesLocation(
            latLng = RoutesLatLng(
                latitude = latitude,
                longitude = longitude
            )
        )
    )
}

fun String.toAddressWaypoint(): RoutesWaypoint {
    return RoutesWaypoint(address = trim())
}

fun ComputeRoutesResponse.toRoutePreview(): RoutePreview {
    val route = routes.firstOrNull()
        ?: throw IllegalStateException("Không tìm thấy tuyến đi bộ")

    return RoutePreview(
        distanceMeters = route.distanceMeters ?: 0,
        durationSeconds = route.duration.parseDurationSeconds(),
        encodedPolyline = route.polyline?.encodedPolyline.orEmpty(),
        steps = route.legs.flatMap { leg ->
            leg.steps.mapIndexed { index, step ->
                RouteStep(
                    instruction = step.navigationInstruction?.instructions
                        ?.takeIf { it.isNotBlank() }
                        ?: "Tiếp tục đi bộ bước ${index + 1}",
                    distanceMeters = step.distanceMeters ?: 0,
                    durationSeconds = step.staticDuration.parseDurationSeconds()
                )
            }
        }
    )
}

fun String?.parseDurationSeconds(): Int {
    val seconds = this?.let { value ->
        DurationPattern.matchEntire(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    return seconds ?: 0
}
