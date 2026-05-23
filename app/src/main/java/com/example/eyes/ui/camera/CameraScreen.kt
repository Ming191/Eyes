package com.example.eyes.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.camera.CameraManager
import com.example.eyes.camera.FrameThrottle
import com.example.eyes.ui.navigation.CameraMode
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.res.stringResource

@Composable
fun CameraScreen(
    mode: CameraMode = CameraMode.Navigation,
    viewModel: CameraViewModel = koinViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager: CameraManager = koinInject()
    val frameThrottle = remember { FrameThrottle() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenContentDescription = stringResource(R.string.camera_screen_content_description)
    val statusPanelContentDescription = stringResource(R.string.camera_status_panel_content_description)
    val modeSelectorContentDescription = stringResource(R.string.camera_mode_selector_content_description)

    LaunchedEffect(mode) {
        viewModel.setMode(mode)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = screenContentDescription },
        contentAlignment = Alignment.Center
    ) {
        // ── Camera preview ────────────────────────────────────────
        AndroidView(
            factory = { context ->
                PreviewView(context).also { previewView ->
                    cameraManager.bindToLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView
                    ) { imageProxy ->
                        if (frameThrottle.shouldProcess(System.currentTimeMillis())) {
                            viewModel.processFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Status card (top) ─────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = statusPanelContentDescription
                    stateDescription = "${uiState.title}. ${uiState.statusMessage}"
                    liveRegion = LiveRegionMode.Polite
                },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = uiState.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Currency result overlay ───────────────────────────────
        if (uiState.currentMode == CameraMode.Currency) {
            CurrencyOverlay(
                display    = uiState.currencyDisplay,
                confidence = uiState.currencyConfidence,
                modifier   = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)   // tránh đè lên mode selector
            )
        }

        // ── Mode selector (bottom) ────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                    .semantics { contentDescription = modeSelectorContentDescription },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    selected = uiState.currentMode == CameraMode.Navigation,
                    icon     = Icons.Rounded.Navigation,
                    label    = stringResource(R.string.camera_mode_navigation_short),
                    onClick  = { viewModel.setMode(CameraMode.Navigation) }
                )
                ModeButton(
                    selected = uiState.currentMode == CameraMode.OCR,
                    icon     = Icons.Rounded.TextFields,
                    label    = stringResource(R.string.camera_mode_ocr_short),
                    onClick  = { viewModel.setMode(CameraMode.OCR) }
                )
                ModeButton(
                    selected = uiState.currentMode == CameraMode.Currency,
                    icon     = Icons.Rounded.Payments,
                    label    = stringResource(R.string.camera_mode_currency_short),
                    onClick  = { viewModel.setMode(CameraMode.Currency) }
                )
            }
        }
    }
}

// ── Currency overlay component ────────────────────────────────────

@Composable
private fun CurrencyOverlay(
    display: String,
    confidence: Float,
    modifier: Modifier = Modifier,
) {
    val hasResult = display.isNotEmpty()
    val overlayContentDescription = stringResource(R.string.currency_overlay_content_description)
    val instruction = stringResource(R.string.currency_instruction)
    val resultContentDescription = stringResource(
        R.string.currency_overlay_result_content_description,
        display,
        (confidence * 100).toInt(),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = overlayContentDescription }
    ) {
        // Hướng dẫn khi chưa có kết quả
        AnimatedVisibility(
            visible = !hasResult,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    text      = instruction,
                    color     = Color.White,
                    fontSize  = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        // Kết quả nhận diện
        AnimatedVisibility(
            visible  = hasResult,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Surface(
                shape          = MaterialTheme.shapes.large,
                color          = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier       = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = resultContentDescription
                        liveRegion         = LiveRegionMode.Polite
                    }
            ) {
                Column(
                    modifier                = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement     = Arrangement.spacedBy(4.dp),
                    horizontalAlignment     = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text       = display,
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign  = TextAlign.Center,
                    )
                    Text(
                        text  = "${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ── Mode button component ─────────────────────────────────────────

@Composable
private fun ModeButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val selectedDescription = stringResource(R.string.camera_mode_selected_state)
    Button(
        onClick = onClick,
        colors  = if (selected) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )
        },
        modifier = Modifier
            .sizeIn(minWidth = if (selected) 88.dp else 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (selected) selectedDescription else ""
            }
    ) {
        Icon(icon, contentDescription = null)
        Text(text = label, modifier = Modifier.padding(start = 4.dp))
    }
}
