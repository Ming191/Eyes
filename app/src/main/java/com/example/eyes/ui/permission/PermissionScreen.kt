package com.example.eyes.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION
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
        Text(text = "Quyền đã cấp: $grantedCount/${REQUIRED_PERMISSIONS.size}")
        Text(text = "Camera: ${if (statuses[Manifest.permission.CAMERA] == true) "Đã cấp" else "Chưa cấp"}")
        Text(text = "Micro: ${if (statuses[Manifest.permission.RECORD_AUDIO] == true) "Đã cấp" else "Chưa cấp"}")
        Text(text = "Vị trí: ${if (statuses[Manifest.permission.ACCESS_FINE_LOCATION] == true) "Đã cấp" else "Chưa cấp"}")

        Button(
            onClick = { permissionsLauncher.launch(REQUIRED_PERMISSIONS.toTypedArray()) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Nút yêu cầu cấp quyền" }
        ) {
            Text("Yêu cầu quyền")
        }
    }
}
