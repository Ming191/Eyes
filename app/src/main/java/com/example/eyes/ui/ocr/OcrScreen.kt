package com.example.eyes.ui.ocr

import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.camera.CameraManager
import com.example.eyes.ocr.OcrMode
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun OcrScreen(
    viewModel: OcrViewModel = koinViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager: CameraManager = koinInject()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ocrMode by viewModel.ocrMode.collectAsStateWithLifecycle()
    val currentUiState by rememberUpdatedState(uiState)
    var previewViewReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Màn hình đọc văn bản OCR" }
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).also { previewView ->
                    previewViewReady = true
                    cameraManager.bindToLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (currentUiState is OcrUiState.DocumentMode) return@detectTapGestures
                            cameraManager.takePicture(
                                onCaptured = { imageProxy ->
                                    viewModel.processCapturedImage(imageProxy)
                                },
                                onError = {
                                    viewModel.onCaptureError()
                                }
                            )
                        }
                    )
                }
                .pointerInput(uiState) {
                    if (currentUiState is OcrUiState.DocumentMode) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            when {
                                dragAmount > 60f -> viewModel.prevSentence()
                                dragAmount < -60f -> viewModel.nextSentence()
                            }
                        }
                    }
                }
        )

        when (val state = uiState) {
            is OcrUiState.DocumentMode -> DocumentOverlay(state = state, onExit = viewModel::exitDocumentMode)
            is OcrUiState.Scanning -> ScanningOverlay(mode = ocrMode)
            is OcrUiState.Error -> ErrorOverlay(message = state.message)
            is OcrUiState.Idle -> IdleOverlay(previewReady = previewViewReady)
        }

        OcrModeSelector(
            mode = ocrMode,
            onModeSelected = viewModel::setOcrMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun OcrModeSelector(
    mode: OcrMode,
    onModeSelected: (OcrMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = when (mode) {
                    OcrMode.QUICK -> "Chế độ OCR hiện tại là Quick mode, dùng ML Kit"
                    OcrMode.ACCURACY -> "Chế độ OCR hiện tại là Accuracy mode, dùng GPT-4o"
                }
            },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Chế độ OCR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (mode) {
                    OcrMode.QUICK -> "Quick mode · ML Kit"
                    OcrMode.ACCURACY -> "Accuracy mode · GPT-4o"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when (mode) {
                    OcrMode.QUICK -> "Nhanh hơn, phù hợp khi cần đọc sơ bộ."
                    OcrMode.ACCURACY -> "Chính xác hơn cho tiếng Việt, chấp nhận chậm hơn một chút."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Bộ chọn chế độ OCR" }
            ) {
                FilterChip(
                    selected = mode == OcrMode.QUICK,
                    onClick = { onModeSelected(OcrMode.QUICK) },
                    label = { Text("Quick mode") },
                    modifier = Modifier.semantics {
                        contentDescription = "Chọn Quick mode để dùng ML Kit"
                    }
                )
                FilterChip(
                    selected = mode == OcrMode.ACCURACY,
                    onClick = { onModeSelected(OcrMode.ACCURACY) },
                    label = { Text("Accuracy mode") },
                    modifier = Modifier.semantics {
                        contentDescription = "Chọn Accuracy mode để dùng GPT-4o"
                    }
                )
            }
        }
    }
}

@Composable
private fun DocumentOverlay(
    state: OcrUiState.DocumentMode,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.End)
                .semantics { contentDescription = "Thoát chế độ đọc tài liệu" }
        ) {
            Text("Thoát đọc tài liệu")
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Câu ${state.currentIndex + 1} trên ${state.sentences.size}: ${state.currentSentence}"
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${state.currentIndex + 1} / ${state.sentences.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentSentence,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vuốt trái / phải để chuyển câu",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ScanningOverlay(mode: OcrMode) {
    val scanningMessage = when (mode) {
        OcrMode.QUICK -> "Đang nhận dạng nhanh bằng ML Kit"
        OcrMode.ACCURACY -> "Đang nhận dạng chính xác bằng GPT-4o, vui lòng chờ lâu hơn một chút"
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = scanningMessage }
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    text = scanningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = scanningMessage
                        }
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { contentDescription = "Lỗi OCR: $message" }
        )
    }
}

@Composable
private fun IdleOverlay(previewReady: Boolean) {
    if (!previewReady) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Hướng camera vào văn bản. Double tap để chụp ảnh và đọc.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
