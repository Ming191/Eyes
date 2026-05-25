package com.example.eyes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.theme.EyesTheme
import com.example.eyes.ui.voice.VoiceCommandScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenOcrQuick: () -> Unit,
    onOpenCameraMode: (CameraMode) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showVoiceCommand by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    if (showVoiceCommand) {
        VoiceCommandScreen(
            onNavigateToCamera = { onOpenCameraMode(CameraMode.OCR) },
            onNavigateBackHome = { showVoiceCommand = false }
        )
    } else {
        HomeContent(
            uiState = uiState,
            onActionSelected = { action ->
                when (action) {
                    HomeActionType.ReadTextQuick -> onOpenOcrQuick()
                    HomeActionType.ReadTextAccuracy -> Unit
                    HomeActionType.DescribeScene -> onOpenCameraMode(CameraMode.SCENE_DESCRIPTION)
                    HomeActionType.DetectObjects -> onOpenCameraMode(CameraMode.OBJECT_DETECTION)
                    HomeActionType.RecognizeCurrency -> onOpenCameraMode(CameraMode.CURRENCY)
                    HomeActionType.Voice -> showVoiceCommand = true
                    HomeActionType.Settings -> onOpenSettings()
                }
            }
        )
    }
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onActionSelected: (HomeActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenDescription = stringResource(R.string.home_screen_description)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompactActionGrid(
            actions = uiState.actions,
            onActionSelected = onActionSelected
        )
    }
}

@Composable
private fun CompactActionGrid(
    actions: List<HomeAction>,
    onActionSelected: (HomeActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth < 600.dp) 2 else 4
        val rows = actions.chunked(columnCount)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        HomeActionCard(
                            action = action,
                            onClick = { onActionSelected(action.type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columnCount - rowActions.size) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    EyesTheme(dynamicColor = false) {
        HomeContent(
            uiState = HomeUiState(),
            onActionSelected = {}
        )
    }
}
