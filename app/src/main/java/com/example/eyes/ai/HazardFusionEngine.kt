package com.example.eyes.ai

enum class AlertSource {
    YOLO,
    DEPTH
}

data class FusedHazardAlert(
    val primarySource: AlertSource,
    val primaryZone: Zone,
    val speechText: String?,
    val secondaryHapticZone: Zone?
)

class HazardFusionEngine {
    fun fuse(
        yoloDetection: Detection?,
        depthHazard: DepthHazard?
    ): FusedHazardAlert? {
        if (yoloDetection == null && depthHazard == null) return null

        if (yoloDetection != null) {
            val secondary = if (
                depthHazard?.severity == HazardSeverity.HIGH &&
                depthHazard.zone != yoloDetection.zone
            ) {
                depthHazard.zone
            } else {
                null
            }

            return FusedHazardAlert(
                primarySource = AlertSource.YOLO,
                primaryZone = yoloDetection.zone,
                speechText = "Phát hiện ${yoloDetection.labelVi} ở ${yoloDetection.zone.labelVi}",
                secondaryHapticZone = secondary
            )
        }

        if (depthHazard == null) return null

        return FusedHazardAlert(
            primarySource = AlertSource.DEPTH,
            primaryZone = depthHazard.zone,
            speechText = "Có vật cản gần ở ${depthHazard.zone.labelVi}",
            secondaryHapticZone = null
        )
    }
}
