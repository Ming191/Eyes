package com.example.eyes.ai

enum class VerticalBand {
    GROUND,
    TORSO,
    HEAD
}

enum class HazardSeverity {
    MEDIUM,
    HIGH
}

data class DepthHazard(
    val zone: Zone,
    val band: VerticalBand,
    val severity: HazardSeverity,
    val score: Float
)

class DepthHazardDetector(
    private val nearThresholdMedium: Float = 0.80f,
    private val nearThresholdHigh: Float = 0.88f,
    private val persistenceFrames: Int = 1,
    private val percentile: Float = 0.8f
) {
    private val streakByZoneBand = mutableMapOf<Pair<Zone, VerticalBand>, Int>()

    fun detect(depthMap: DepthMap): DepthHazard? {
        require(depthMap.values.size == depthMap.width * depthMap.height) {
            "DepthMap shape does not match values size"
        }

        val candidates = mutableListOf<DepthHazard>()
        Zone.entries.forEach { zone ->
            VerticalBand.entries.forEach { band ->
                val stats = regionStats(depthMap, zone, band)
                val score = stats.score
                val severity = when {
                    score >= nearThresholdHigh && passesHighPolicy(zone, band, stats.highRatio) -> HazardSeverity.HIGH
                    score >= nearThresholdMedium && passesMediumPolicy(zone, band, stats.mediumRatio) -> HazardSeverity.MEDIUM
                    else -> null
                }

                val key = zone to band
                if (severity != null) {
                    val streak = (streakByZoneBand[key] ?: 0) + 1
                    streakByZoneBand[key] = streak
                    if (streak >= persistenceFrames) {
                        candidates += DepthHazard(zone = zone, band = band, severity = severity, score = score)
                    }
                } else {
                    streakByZoneBand[key] = 0
                }
            }
        }

        return candidates.maxByOrNull { it.score }
    }

    private data class RegionStats(
        val score: Float,
        val mediumRatio: Float,
        val highRatio: Float
    )

    private fun regionStats(depthMap: DepthMap, zone: Zone, band: VerticalBand): RegionStats {
        if (band == VerticalBand.HEAD) return RegionStats(0f, 0f, 0f)
        val xRange = zoneXRange(depthMap.width, zone)
        val yRange = bandYRange(depthMap.height, band)
        val samples = ArrayList<Float>((xRange.last - xRange.first + 1) * (yRange.last - yRange.first + 1))

        for (y in yRange) {
            val rowOffset = y * depthMap.width
            for (x in xRange) {
                samples += depthMap.values[rowOffset + x].coerceIn(0f, 1f)
            }
        }

        if (samples.isEmpty()) return RegionStats(0f, 0f, 0f)
        val total = samples.size.toFloat()
        val mediumCount = samples.count { it >= nearThresholdMedium }
        val highCount = samples.count { it >= nearThresholdHigh }
        samples.sort()
        val p = percentile.coerceIn(0f, 1f)
        val idx = ((samples.lastIndex) * p).toInt().coerceIn(0, samples.lastIndex)
        return RegionStats(
            score = samples[idx],
            mediumRatio = mediumCount / total,
            highRatio = highCount / total
        )
    }

    private fun passesHighPolicy(zone: Zone, band: VerticalBand, highRatio: Float): Boolean {
        return when {
            band == VerticalBand.HEAD -> false
            zone == Zone.CENTER && band == VerticalBand.GROUND -> highRatio >= 0.25f
            zone == Zone.CENTER && band == VerticalBand.TORSO -> highRatio >= 0.35f
            (zone == Zone.LEFT || zone == Zone.RIGHT) && band == VerticalBand.GROUND -> highRatio >= 0.25f
            (zone == Zone.LEFT || zone == Zone.RIGHT) && band == VerticalBand.TORSO -> highRatio >= 0.30f
            else -> false
        }
    }

    private fun passesMediumPolicy(zone: Zone, band: VerticalBand, mediumRatio: Float): Boolean {
        return when {
            band == VerticalBand.HEAD -> false
            zone == Zone.CENTER && band == VerticalBand.GROUND -> mediumRatio >= 0.55f
            zone == Zone.CENTER && band == VerticalBand.TORSO -> mediumRatio >= 0.60f
            (zone == Zone.LEFT || zone == Zone.RIGHT) && band == VerticalBand.GROUND -> mediumRatio >= 0.45f
            (zone == Zone.LEFT || zone == Zone.RIGHT) && band == VerticalBand.TORSO -> mediumRatio >= 0.50f
            else -> false
        }
    }

    private fun zoneXRange(width: Int, zone: Zone): IntRange {
        val leftEnd = (width / 3) - 1
        val centerEnd = ((2 * width) / 3) - 1
        return when (zone) {
            Zone.LEFT -> 0..leftEnd.coerceAtLeast(0)
            Zone.CENTER -> (leftEnd + 1).coerceAtMost(width - 1)..centerEnd.coerceAtLeast((leftEnd + 1).coerceAtMost(width - 1))
            Zone.RIGHT -> (centerEnd + 1).coerceAtMost(width - 1)..(width - 1)
        }
    }

    private fun bandYRange(height: Int, band: VerticalBand): IntRange {
        val headEnd = (height / 3) - 1
        val torsoEnd = ((2 * height) / 3) - 1
        return when (band) {
            VerticalBand.HEAD -> 0..headEnd.coerceAtLeast(0)
            VerticalBand.TORSO -> (headEnd + 1).coerceAtMost(height - 1)..torsoEnd.coerceAtLeast((headEnd + 1).coerceAtMost(height - 1))
            VerticalBand.GROUND -> (torsoEnd + 1).coerceAtMost(height - 1)..(height - 1)
        }
    }
}
