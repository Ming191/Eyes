package com.example.eyes.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToLong

@LargeTest
@RunWith(AndroidJUnit4::class)
class MiDasDepthEstimatorBenchmarkTest {

    @Test
    fun estimateDepth_reportsOnDeviceLatency() {
        // GIVEN
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val warmupIterations = args.getString(ARG_WARMUP)?.toIntOrNull() ?: DEFAULT_WARMUP_ITERATIONS
        val measuredIterations = args.getString(ARG_ITERATIONS)?.toIntOrNull() ?: DEFAULT_MEASURED_ITERATIONS
        val context = instrumentation.targetContext
        val estimator = MiDasDepthEstimator(context)
        val bitmap = benchmarkBitmap()

        repeat(warmupIterations) {
            estimator.estimateDepth(bitmap)
        }

        // WHEN
        val timingsMs = LongArray(measuredIterations)
        repeat(measuredIterations) { index ->
            val startedAtNs = SystemClock.elapsedRealtimeNanos()
            val depthMap = estimator.estimateDepth(bitmap)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAtNs) / NANOS_PER_MILLI

            timingsMs[index] = elapsedMs
            assertTrue(depthMap.width > 0)
            assertTrue(depthMap.height > 0)
            assertEquals(depthMap.width * depthMap.height, depthMap.values.size)
        }

        // THEN
        val summary = timingsMs.summary()
        Log.i(TAG, summary)
        instrumentation.sendStatus(0, Bundle().apply {
            putString(TAG, summary)
        })
    }

    private fun benchmarkBitmap(): Bitmap {
        val pixels = IntArray(BENCHMARK_WIDTH * BENCHMARK_HEIGHT)
        for (y in 0 until BENCHMARK_HEIGHT) {
            for (x in 0 until BENCHMARK_WIDTH) {
                val red = ((x * 255) / BENCHMARK_WIDTH).coerceIn(0, 255)
                val green = ((y * 255) / BENCHMARK_HEIGHT).coerceIn(0, 255)
                val blue = if (x in 300..620 && y in 180..760) 220 else 80
                pixels[y * BENCHMARK_WIDTH + x] = Color.rgb(red, green, blue)
            }
        }

        return Bitmap.createBitmap(
            pixels,
            BENCHMARK_WIDTH,
            BENCHMARK_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun LongArray.summary(): String {
        val sorted = sorted()
        val avg = average().roundToLong()
        val min = sorted.first()
        val p50 = sorted.percentile(0.50f)
        val p90 = sorted.percentile(0.90f)
        val p95 = sorted.percentile(0.95f)
        val max = sorted.last()
        val fpsAtP50 = if (p50 > 0L) 1_000f / p50 else 0f
        return "MiDaS latency: n=$size min=${min}ms avg=${avg}ms p50=${p50}ms p90=${p90}ms p95=${p95}ms max=${max}ms approxP50Fps=${"%.2f".format(fpsAtP50)}"
    }

    private fun List<Long>.percentile(percentile: Float): Long {
        val index = ((lastIndex) * percentile.coerceIn(0f, 1f)).roundToLong().toInt()
            .coerceIn(0, lastIndex)
        return this[index]
    }

    private companion object {
        private const val TAG = "MiDasBench"
        private const val ARG_WARMUP = "depthWarmup"
        private const val ARG_ITERATIONS = "depthIterations"
        private const val DEFAULT_WARMUP_ITERATIONS = 2
        private const val DEFAULT_MEASURED_ITERATIONS = 10
        private const val BENCHMARK_WIDTH = 720
        private const val BENCHMARK_HEIGHT = 1280
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
