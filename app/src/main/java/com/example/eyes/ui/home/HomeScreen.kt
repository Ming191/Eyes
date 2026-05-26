package com.example.eyes.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.theme.EyesTheme
import com.example.eyes.ui.voice.VoiceCommandViewModel
import com.example.eyes.ui.voice.VoiceNavigationTarget
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenOcrQuick: () -> Unit,
    onOpenOcrAccuracy: () -> Unit,
    onOpenCameraMode: (CameraMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEmergency: (String?) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    voiceCommandViewModel: VoiceCommandViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastVoiceStartAtMs = remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && text.isNotBlank()) {
            voiceCommandViewModel.handleRecognizedText(text)
        } else {
            voiceCommandViewModel.handleRecognitionCancelled()
        }
    }
    fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            voiceCommandViewModel.handleRecognitionUnavailable()
            return
        }
        try {
            voiceInputLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceCommandViewModel.handleRecognitionUnavailable()
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            voiceCommandViewModel.handleRecognitionUnavailable()
        }
    }

    fun requestMicrophoneOrStart() {
        val now = System.currentTimeMillis()
        if (now - lastVoiceStartAtMs.longValue < 1_500L) return
        lastVoiceStartAtMs.longValue = now
        if (!hasRecordAudioPermission()) {
            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoiceRecognition()
    }

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(voiceCommandViewModel) {
        voiceCommandViewModel.navigation.collect { target ->
            when (target) {
                VoiceNavigationTarget.Camera -> onOpenCameraMode(voiceCommandViewModel.uiState.value.lastCommand.cameraMode())
                VoiceNavigationTarget.Home -> Unit
            }
        }
    }

    HomeContent(
        uiState = uiState,
        onActionSelected = { action ->
            when (action) {
                HomeActionType.ReadTextQuick -> onOpenOcrQuick()
                HomeActionType.ReadTextAccuracy -> onOpenOcrAccuracy()
                HomeActionType.DescribeScene -> onOpenCameraMode(CameraMode.SCENE_DESCRIPTION)
                HomeActionType.DetectObjects -> onOpenCameraMode(CameraMode.OBJECT_DETECTION)
                HomeActionType.RecognizeCurrency -> onOpenCameraMode(CameraMode.CURRENCY)
                HomeActionType.EmergencyCall -> onOpenEmergency(null)
                HomeActionType.Voice -> requestMicrophoneOrStart()
                HomeActionType.Settings -> onOpenSettings()
            }
        },
        onEmergencyNumberSelected = { number ->
            onOpenEmergency(number)
        }
    )
}

private fun VoiceCommand?.cameraMode(): CameraMode = when (this) {
    VoiceCommand.DescribeScene -> CameraMode.SCENE_DESCRIPTION
    VoiceCommand.RecognizeCurrency -> CameraMode.CURRENCY
    else -> CameraMode.OCR
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onActionSelected: (HomeActionType) -> Unit,
    onEmergencyNumberSelected: (String) -> Unit,
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
            onActionSelected = onActionSelected,
            onEmergencyNumberSelected = onEmergencyNumberSelected
        )
    }
}

@Composable
private fun CompactActionGrid(
    actions: List<HomeAction>,
    onActionSelected: (HomeActionType) -> Unit,
    onEmergencyNumberSelected: (String) -> Unit,
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
                            secondaryActions = action.emergencyActions(onEmergencyNumberSelected),
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

@Composable
private fun HomeAction.emergencyActions(onEmergencyNumberSelected: (String) -> Unit): List<BlindAction> {
    if (type != HomeActionType.EmergencyCall) return emptyList()
    return listOf(
        BlindAction(
            label = stringResource(R.string.emergency_police),
            activateLabel = stringResource(R.string.emergency_open_dialer_113),
            onActivate = { onEmergencyNumberSelected("113") }
        ),
        BlindAction(
            label = stringResource(R.string.emergency_fire),
            activateLabel = stringResource(R.string.emergency_open_dialer_114),
            onActivate = { onEmergencyNumberSelected("114") }
        ),
        BlindAction(
            label = stringResource(R.string.emergency_medical),
            activateLabel = stringResource(R.string.emergency_open_dialer_115),
            onActivate = { onEmergencyNumberSelected("115") }
        ),
        BlindAction(
            label = stringResource(R.string.emergency_general),
            activateLabel = stringResource(R.string.emergency_open_dialer_112),
            onActivate = { onEmergencyNumberSelected("112") }
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    EyesTheme(dynamicColor = false) {
        HomeContent(
            uiState = HomeUiState(),
            onActionSelected = {},
            onEmergencyNumberSelected = {}
        )
    }
}
