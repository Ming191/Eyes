package com.example.eyes.ai

import com.example.eyes.i18n.AppLanguage

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
    /**
     * Fuse a YOLO detection and a depth-based hazard into a single fused hazard alert.
     *
     * When both inputs are null, returns null. If a YOLO detection is present it is chosen
     * as the primary source; the returned alert's speech text uses the YOLO label and zone.
     * If a depth hazard is present alongside YOLO and its severity is `HazardSeverity.HIGH`
     * and its zone differs from the YOLO zone, that depth zone is included as
     * `secondaryHapticZone`. If YOLO is absent but a depth hazard is present, the depth
     * hazard becomes the primary source and the speech text indicates a nearby obstacle.
     *
     * @param yoloDetection The object detection result from the YOLO model, or null if none.
     * @param depthHazard The depth-based hazard detection, or null if none.
     * @return A `FusedHazardAlert` representing the chosen primary source/zone, optional
     * secondary haptic zone, and generated speech text, or `null` if both inputs are null.
     */
    fun fuse(
        yoloDetection: Detection?,
        depthHazard: DepthHazard?,
        language: AppLanguage = AppLanguage.VI
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
                speechText = when (language) {
                    AppLanguage.VI -> "Phát hiện ${yoloDetection.labelVi} ở ${yoloDetection.zone.label(language)}"
                    AppLanguage.EN -> "Detected ${yoloDetection.labelEn} on the ${yoloDetection.zone.label(language)}"
                },
                secondaryHapticZone = secondary
            )
        }

        if (depthHazard == null) return null

        return FusedHazardAlert(
            primarySource = AlertSource.DEPTH,
            primaryZone = depthHazard.zone,
            speechText = when (language) {
                AppLanguage.VI -> "Có vật cản gần ở ${depthHazard.zone.label(language)}"
                AppLanguage.EN -> "Nearby obstacle on the ${depthHazard.zone.label(language)}"
            },
            secondaryHapticZone = null
        )
    }
}
