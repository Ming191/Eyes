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

    /**
     * Detects the most severe persistent depth hazard region present in the provided depth map.
     *
     * @param depthMap The depth map to evaluate. Must satisfy `depthMap.values.size == depthMap.width * depthMap.height`.
     * @return The `DepthHazard` candidate with the highest region score after applying configured thresholds and persistence, or `null` if no region qualifies.
     * @throws IllegalArgumentException If `depthMap.values.size` does not equal `depthMap.width * depthMap.height`.
     */
    fun detect(depthMap: DepthMap): DepthHazard? {
        require(depthMap.width > 0 && depthMap.height > 0) {
            "DepthMap dimensions must be positive"
        }

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

    /**
     * Compute depth-derived statistics for the specified horizontal zone and vertical band.
     *
     * Samples depthMap values inside the region defined by `zone` and `band` and summarizes them as a
     * percentile-based score plus two ratios indicating the fraction of samples meeting configured
     * near thresholds.
     *
     * @param depthMap The depth map to sample.
     * @param zone The horizontal zone to evaluate.
     * @param band The vertical band to evaluate.
     * @return A [RegionStats] containing:
     *   - `score`: the configured-percentile value from the sampled depths (0..1),
     *   - `mediumRatio`: fraction of samples >= `nearThresholdMedium`,
     *   - `highRatio`: fraction of samples >= `nearThresholdHigh`.
     */
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

    /**
     * Determines whether a region satisfies the "high" proximity policy for its zone and vertical band.
     *
     * The HEAD band never qualifies. Required `highRatio` thresholds:
     * - CENTER + GROUND: >= 0.25
     * - CENTER + TORSO: >= 0.35
     * - LEFT/RIGHT + GROUND: >= 0.25
     * - LEFT/RIGHT + TORSO: >= 0.30
     *
     * @param zone The horizontal zone being evaluated.
     * @param band The vertical band being evaluated.
     * @param highRatio Fraction of region samples with depth >= the high threshold (0.0–1.0).
     * @return `true` if `highRatio` meets the threshold for the given `zone` and `band`, `false` otherwise.
     */
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

    /**
     * Determines whether a region satisfies the medium-severity policy for its zone and vertical band.
     *
     * The decision compares `mediumRatio` against zone-and-band-specific thresholds; regions in the
     * `HEAD` band never satisfy the medium policy.
     *
     * @param zone The horizontal zone being evaluated (LEFT, CENTER, RIGHT).
     * @param band The vertical band being evaluated (GROUND, TORSO, HEAD).
     * @param mediumRatio Fraction of region samples that meet or exceed the medium depth threshold.
     * @return `true` if the region's `mediumRatio` meets or exceeds the configured threshold for the
     * specified `zone` and `band`, `false` otherwise.
     */
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

    /**
     * Computes the inclusive horizontal pixel range for a zone by splitting the image width into thirds.
     *
     * The returned range is clamped to valid column indices [0, width - 1] and may be empty if width is small.
     *
     * @param width The image width in pixels.
     * @param zone The horizontal zone (LEFT, CENTER, RIGHT) to map to an X range.
     * @return The inclusive X coordinate range for the specified zone, clamped to [0, width - 1].
     */
    private fun zoneXRange(width: Int, zone: Zone): IntRange {
        val leftEnd = (width / 3) - 1
        val centerEnd = ((2 * width) / 3) - 1
        return when (zone) {
            Zone.LEFT -> 0..leftEnd.coerceAtLeast(0)
            Zone.CENTER -> (leftEnd + 1).coerceAtMost(width - 1)..centerEnd.coerceAtLeast((leftEnd + 1).coerceAtMost(width - 1))
            Zone.RIGHT -> (centerEnd + 1).coerceAtMost(width - 1)..(width - 1)
        }
    }

    /**
     * Computes the inclusive row index range corresponding to the given vertical band by dividing the image height into three horizontal bands.
     *
     * The height is split into HEAD, TORSO, and GROUND thirds; returned ranges are coerced to valid row indices so they stay within 0..(height-1).
     *
     * @param height The total number of rows (image height).
     * @param band The vertical band to map to a row range.
     * @return An inclusive IntRange of row indices for the requested band, adjusted to valid bounds. 
     */
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
