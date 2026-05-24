package com.example.eyes.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.camera.CameraManager
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.blindFocusable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun CameraScreen(
    requestedMode: CameraMode? = null,
    onRequestedModeConsumed: () -> Unit = {},
    viewModel: CameraViewModel = koinViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager: CameraManager = koinInject()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenDescription = stringResource(
        R.string.camera_screen_description,
        uiState.activeMode.localizedDescription()
    )
    val describeSceneDescription = stringResource(R.string.camera_describe_scene_description)
    val capturedOcrDescription = stringResource(R.string.camera_captured_ocr_description)
    val analyzingImageText = stringResource(R.string.camera_analyzing_image)
    val ocrSwipeHint = stringResource(R.string.camera_ocr_swipe_hint)
    val captureAnotherDescription = stringResource(R.string.camera_capture_another_description)
    val captureAnotherText = stringResource(R.string.camera_capture_another_text)
    val showStatusDescription = stringResource(R.string.camera_show_status_description)
    fun captureOcrIfReady() {
        if (
            uiState.activeMode == CameraMode.OCR &&
            !uiState.isOcrScanning &&
            !uiState.isOcrDocumentMode
        ) {
            viewModel.onOcrCaptureRequested()
            cameraManager.takePictureAfterCenterFocus(
                onCaptured = viewModel::processCapturedOcrImage,
                onError = { viewModel.onOcrCaptureError() }
            )
        }
    }

    LaunchedEffect(requestedMode) {
        if (requestedMode != null) {
            viewModel.selectMode(requestedMode)
            onRequestedModeConsumed()
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.onScreenDisposed() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(uiState.activeMode, uiState.isOcrDocumentMode, uiState.isOcrScanning) {
                detectTapGestures(
                    onDoubleTap = {
                        captureOcrIfReady()
                    },
                    onLongPress = { viewModel.describeScene() }
                )
            }
            .pointerInput(uiState.activeMode, uiState.isOcrDocumentMode) {
                if (uiState.activeMode == CameraMode.OCR && uiState.isOcrDocumentMode) {
                    var accumulatedDrag = 0f
                    var handledInThisGesture = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                            handledInThisGesture = false
                        }
                    ) { _, dragAmount ->
                        if (handledInThisGesture) return@detectHorizontalDragGestures
                        accumulatedDrag += dragAmount
                        when {
                            accumulatedDrag > 120f -> {
                                handledInThisGesture = true
                                viewModel.prevOcrSentence()
                            }

                            accumulatedDrag < -120f -> {
                                handledInThisGesture = true
                                viewModel.nextOcrSentence()
                            }
                        }
                    }
                }
            }
            .semantics {
                contentDescription = screenDescription
                onLongClick(label = describeSceneDescription) {
                    viewModel.describeScene()
                    true
                }
            }
            .blindFocusable(
                id = "camera_viewfinder",
                label = screenDescription,
                onActivate = { captureOcrIfReady() },
                actions = listOf(
                    BlindAction(label = describeSceneDescription, onActivate = { viewModel.describeScene() }),
                    BlindAction(label = captureAnotherDescription, onActivate = { captureOcrIfReady() })
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                    cameraManager.bindToLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        onFrame = viewModel::processFrame
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.activeMode == CameraMode.OCR && uiState.ocrCapturedBitmap == null) {
            OcrGuidanceOverlay(
                bounds = uiState.ocrGuidanceBounds,
                isReady = uiState.isOcrReadyToCapture,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (uiState.activeMode == CameraMode.OBJECT_DETECTION) {
            ObjectDetectionOverlay(
                detections = uiState.objectDetections,
                sourceAspectRatio = uiState.objectDetections.firstOrNull()?.sourceAspectRatio,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (uiState.activeMode == CameraMode.CURRENCY) {
            CurrencyResultOverlay(
                display = uiState.currencyDisplay,
                confidence = uiState.currencyConfidence,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 212.dp)
            )
        }
        uiState.ocrCapturedBitmap?.let { capturedBitmap ->
            Image(
                bitmap = capturedBitmap.asImageBitmap(),
                contentDescription = capturedOcrDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (uiState.isStatusCardVisible) {
            CameraStatusPanel(
                uiState = uiState,
                onDismiss = viewModel::toggleStatusCardVisibility,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        CameraModeSelector(
            mode = uiState.activeMode,
            onModeSelected = viewModel::selectMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )

        if (uiState.activeMode == CameraMode.OCR) {
            OcrEngineModeSelector(
                mode = uiState.ocrMode,
                onModeSelected = viewModel::selectOcrMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 132.dp)
            )
        }

        if (uiState.isOcrScanning) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = analyzingImageText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        if (uiState.isOcrDocumentMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 210.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${uiState.ocrCurrentIndex + 1} / ${uiState.ocrSentences.size}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = uiState.currentOcrSentence,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = ocrSwipeHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.activeMode == CameraMode.OCR && uiState.isOcrDocumentMode && !uiState.isOcrScanning) {
            Button(
                onClick = { viewModel.prepareForNextOcrCapture() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 88.dp)
                    .padding(bottom = 164.dp)
                    .semantics { contentDescription = captureAnotherDescription }
                    .blindFocusable(
                        id = "camera_capture_another",
                        label = captureAnotherDescription,
                        onActivate = { viewModel.prepareForNextOcrCapture() }
                    )
            ) {
                Text(captureAnotherText)
            }
        }

        if (!uiState.isStatusCardVisible) {
            FilledTonalIconButton(
                onClick = viewModel::toggleStatusCardVisibility,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = showStatusDescription }
                    .blindFocusable(
                        id = "camera_show_status",
                        label = showStatusDescription,
                        onActivate = viewModel::toggleStatusCardVisibility
                    )
            ) {
                Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraModeSelector(
    mode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = remember { CameraMode.entries }
    val selectorDescription = stringResource(R.string.camera_mode_selector_description)
    val selectedDescription = stringResource(R.string.camera_selected_description)
    val unselectedDescription = stringResource(R.string.camera_unselected_description)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = selectorDescription
            },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            modes.forEachIndexed { index, item ->
                val selected = item == mode
                val itemLabel = item.localizedLabel()
                val itemDescription = item.localizedDescription()
                SegmentedButton(
                    selected = selected,
                    onClick = { onModeSelected(item) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = itemDescription
                            stateDescription = if (selected) selectedDescription else unselectedDescription
                        }
                        .blindFocusable(
                            id = "camera_mode_${item.name}",
                            label = itemDescription,
                            onActivate = { onModeSelected(item) }
                        ),
                    label = { Text(itemLabel) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrEngineModeSelector(
    mode: OcrMode,
    onModeSelected: (OcrMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = remember { OcrMode.entries }
    val selectorDescription = stringResource(R.string.camera_ocr_mode_selector_description)
    val quickDescription = stringResource(R.string.camera_ocr_mode_quick_description)
    val accurateDescription = stringResource(R.string.camera_ocr_mode_accurate_description)
    val selectedDescription = stringResource(R.string.camera_selected_description)
    val unselectedDescription = stringResource(R.string.camera_unselected_description)
    val quickLabel = stringResource(R.string.camera_ocr_mode_quick_label)
    val accurateLabel = stringResource(R.string.camera_ocr_mode_accurate_label)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = selectorDescription },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            modes.forEachIndexed { index, item ->
                val selected = item == mode
                SegmentedButton(
                    selected = selected,
                    onClick = { onModeSelected(item) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = if (item == OcrMode.QUICK) quickDescription else accurateDescription
                            stateDescription = if (selected) selectedDescription else unselectedDescription
                        }
                        .blindFocusable(
                            id = "camera_ocr_mode_${item.name}",
                            label = if (item == OcrMode.QUICK) quickDescription else accurateDescription,
                            onActivate = { onModeSelected(item) }
                        ),
                    label = { Text(if (item == OcrMode.QUICK) quickLabel else accurateLabel) }
                )
            }
        }
    }
}

@Composable
private fun CurrencyResultOverlay(
    display: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val hasResult = display.isNotBlank()
    val confidencePercent = (confidence * 100f).toInt().coerceIn(0, 100)
    val overlayDescription = stringResource(R.string.currency_overlay_content_description)
    val instruction = stringResource(R.string.currency_instruction)
    val resultDescription = stringResource(
        R.string.currency_overlay_result_content_description,
        display,
        confidencePercent
    )
    val confidenceLabel = stringResource(R.string.currency_overlay_confidence_text, confidencePercent)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (hasResult) resultDescription else overlayDescription
                liveRegion = LiveRegionMode.Polite
            }
            .blindFocusable(
                id = "camera_currency_result",
                label = if (hasResult) resultDescription else overlayDescription,
                onActivate = {}
            ),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (hasResult) display else instruction,
                style = if (hasResult) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = if (hasResult) resultDescription else instruction
                }
            )
            if (hasResult) {
                Text(
                    text = confidenceLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = confidenceLabel }
                )
            }
        }
    }
}

@Composable
private fun CameraStatusPanel(
    uiState: CameraUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelDescription = stringResource(R.string.camera_status_panel_description)
    val titleRowDescription = stringResource(R.string.camera_status_title_row_description)
    val hideStatusDescription = stringResource(R.string.camera_hide_status_description)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 132.dp)
            .semantics {
                contentDescription = panelDescription
                stateDescription = "${uiState.title}. ${uiState.statusMessage}. ${uiState.lastAnnouncement}."
                liveRegion = LiveRegionMode.Polite
            }
            .blindFocusable(
                id = "camera_status_panel",
                label = "${uiState.title}. ${uiState.statusMessage}. ${uiState.lastAnnouncement}.",
                onActivate = {},
                actions = listOf(
                    BlindAction(label = hideStatusDescription, onActivate = { onDismiss() })
                )
            ),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = titleRowDescription },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = uiState.title
                            heading()
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .semantics { contentDescription = hideStatusDescription }
                        .blindFocusable(
                            id = "camera_hide_status",
                            label = hideStatusDescription,
                            onActivate = onDismiss
                        )
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = null)
                }
            }
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = uiState.statusMessage }
            )
            Text(
                text = uiState.lastAnnouncement,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = uiState.lastAnnouncement }
            )
        }
    }
}

@Composable
private fun CameraMode.localizedLabel(): String = when (this) {
    CameraMode.OCR -> stringResource(R.string.camera_mode_ocr_label)
    CameraMode.OBJECT_DETECTION -> stringResource(R.string.camera_mode_object_detection_label)
    CameraMode.CURRENCY -> stringResource(R.string.camera_mode_currency_label)
}

@Composable
private fun CameraMode.localizedDescription(): String = when (this) {
    CameraMode.OCR -> stringResource(R.string.camera_mode_ocr_description)
    CameraMode.OBJECT_DETECTION -> stringResource(R.string.camera_mode_object_detection_description)
    CameraMode.CURRENCY -> stringResource(R.string.camera_mode_currency_description)
}
