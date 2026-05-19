package com.example.eyes.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun readPermissionStatuses(
    context: Context,
    permissions: List<String>
): Map<String, Boolean> = permissions.associateWith { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var statuses by remember {
        mutableStateOf(readPermissionStatuses(context, REQUIRED_PERMISSIONS))
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Dùng trực tiếp result từ hệ thống, không gọi lại checkSelfPermission
        // để tránh double recomposition gây nhấp nháy
        statuses = statuses + result
    }

    val grantedCount = statuses.values.count { it }

    Column(
        modifier = modifier.semantics { contentDescription = "Khu vực cấp quyền" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Tổng quan quyền truy cập. Đã cấp $grantedCount trên ${REQUIRED_PERMISSIONS.size} quyền."
                    liveRegion = LiveRegionMode.Polite
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Đã cấp $grantedCount/${REQUIRED_PERMISSIONS.size} quyền",
                    style = MaterialTheme.typography.titleMedium
                )
                PermissionLine(
                    label = "Camera",
                    granted = statuses[Manifest.permission.CAMERA] == true
                )
                PermissionLine(
                    label = "Micro",
                    granted = statuses[Manifest.permission.RECORD_AUDIO] == true
                )
                PermissionLine(
                    label = "Vị trí chính xác",
                    granted = statuses[Manifest.permission.ACCESS_FINE_LOCATION] == true
                )
                PermissionLine(
                    label = "Vị trí gần đúng",
                    granted = statuses[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                )
            }
        }

        Button(
            onClick = { permissionsLauncher.launch(REQUIRED_PERMISSIONS.toTypedArray()) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .semantics {
                    contentDescription = "Nút yêu cầu cấp quyền camera, micro, vị trí chính xác và vị trí gần đúng"
                }
        ) {
            Text("Yêu cầu quyền")
        }
    }
}

@Composable
private fun PermissionLine(
    label: String,
    granted: Boolean
) {
    Text(
        text = "$label: ${if (granted) "Đã cấp" else "Chưa cấp"}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (granted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.semantics {
            contentDescription = "$label ${if (granted) "đã cấp quyền" else "chưa cấp quyền"}"
        }
    )
}
