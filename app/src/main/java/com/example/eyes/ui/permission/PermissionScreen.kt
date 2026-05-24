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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.eyes.R
import com.example.eyes.ui.blind.blindFocusable

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
    val screenDescription = stringResource(R.string.permission_screen_description)
    val summaryDescription = stringResource(R.string.permission_summary_description, grantedCount, REQUIRED_PERMISSIONS.size)
    val summaryText = stringResource(R.string.permission_summary_text, grantedCount, REQUIRED_PERMISSIONS.size)
    val cameraLabel = stringResource(R.string.permission_camera_label)
    val microphoneLabel = stringResource(R.string.permission_microphone_label)
    val locationLabel = stringResource(R.string.permission_location_label)
    val requestDescription = stringResource(R.string.permission_request_description)
    val requestText = stringResource(R.string.permission_request_text)

    Column(
        modifier = modifier.semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = summaryDescription
                    liveRegion = LiveRegionMode.Polite
                }
                .blindFocusable(
                    id = "permission_summary",
                    label = summaryDescription,
                    onActivate = {}
                ),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.titleMedium
                )
                PermissionLine(
                    label = cameraLabel,
                    granted = statuses[Manifest.permission.CAMERA] == true
                )
                PermissionLine(
                    label = microphoneLabel,
                    granted = statuses[Manifest.permission.RECORD_AUDIO] == true
                )
                PermissionLine(
                    label = locationLabel,
                    granted = statuses[Manifest.permission.ACCESS_FINE_LOCATION] == true
                )
            }
        }

        Button(
            onClick = { permissionsLauncher.launch(REQUIRED_PERMISSIONS.toTypedArray()) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .semantics {
                    contentDescription = requestDescription
                }
                .blindFocusable(
                    id = "permission_request",
                    label = requestDescription,
                    onActivate = { permissionsLauncher.launch(REQUIRED_PERMISSIONS.toTypedArray()) }
                )
        ) {
            Text(requestText)
        }
    }
}

@Composable
private fun PermissionLine(
    label: String,
    granted: Boolean
) {
    val grantedText = stringResource(R.string.permission_granted_text)
    val deniedText = stringResource(R.string.permission_denied_text)
    val lineText = stringResource(R.string.permission_line_text, label, if (granted) grantedText else deniedText)
    val grantedDescription = stringResource(R.string.permission_granted_description, label)
    val deniedDescription = stringResource(R.string.permission_denied_description, label)
    Text(
        text = lineText,
        style = MaterialTheme.typography.bodyMedium,
        color = if (granted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.semantics {
            contentDescription = if (granted) grantedDescription else deniedDescription
        }
    )
}
