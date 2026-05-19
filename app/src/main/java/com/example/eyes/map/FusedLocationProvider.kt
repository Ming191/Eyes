package com.example.eyes.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FusedLocationProvider(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Result<UserLocation> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Chưa có quyền truy cập vị trí"))
        }

        return runCatching {
            val cancellationTokenSource = CancellationTokenSource()
            val location = fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                .await(onCancel = cancellationTokenSource::cancel)
                ?: throw IllegalStateException("Không lấy được vị trí hiện tại")

            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }
}

private suspend fun <T> Task<T>.await(
    onCancel: () -> Unit = {}
): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        continuation.resume(value)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel(CancellationException("Tác vụ lấy vị trí đã bị hủy"))
    }
    continuation.invokeOnCancellation {
        onCancel()
    }
}
