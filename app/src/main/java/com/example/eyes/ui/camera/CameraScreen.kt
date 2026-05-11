package com.example.eyes.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.camera.CameraManager
import com.example.eyes.camera.FrameThrottle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = koinViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager: CameraManager = koinInject()
    val frameThrottle = remember { FrameThrottle() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(viewModel) {
                detectTapGestures(
                    onLongPress = {
                        viewModel.describeScene()
                    }
                )
            }
            .semantics {
                contentDescription = "Màn hình camera ở chế độ ${uiState.activeMode.descriptionVi}. Nhấn giữ để mô tả cảnh xung quanh."
                onLongClick(label = "Mô tả cảnh xung quanh") {
                    viewModel.describeScene()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                    cameraManager.bindToLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView
                    ) { imageProxy ->
                        val shouldProcess = frameThrottle.shouldProcess(System.currentTimeMillis())
                        if (shouldProcess) {
                            viewModel.processFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        CameraBoundingBoxOverlay(
            boxes = uiState.boundingBoxes,
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isStatusCardVisible) {
            CameraStatusPanel(
                uiState = uiState,
                onDismiss = viewModel::toggleStatusCardVisibility,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        uiState.depthPreviewBitmap?.let { depthBitmap ->
            DepthPreviewPanel(
                depthBitmap = depthBitmap.asImageBitmap(),
                aspectRatio = depthBitmap.width.toFloat() / depthBitmap.height.toFloat(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 144.dp
                    )
            )
        }

        CameraModeSelector(
            activeMode = uiState.activeMode,
            onModeSelected = viewModel::selectMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )

        if (!uiState.isStatusCardVisible) {
            FilledTonalIconButton(
                onClick = viewModel::toggleStatusCardVisibility,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Hiện bảng trạng thái camera"
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 132.dp)
            .semantics {
                contentDescription = "Bảng trạng thái camera"
                stateDescription = "${uiState.title}. ${uiState.statusMessage}. ${uiState.lastAnnouncement}."
                liveRegion = LiveRegionMode.Polite
            },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Tiêu đề và nút ẩn bảng trạng thái" },
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
                    modifier = Modifier.semantics {
                        contentDescription = "Ẩn bảng trạng thái camera"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null
                    )
                }
            }
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = uiState.statusMessage
                }
            )
            Text(
                text = uiState.lastAnnouncement,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = uiState.lastAnnouncement
                }
            )
        }
    }
}

@Composable
private fun DepthPreviewPanel(
    depthBitmap: ImageBitmap,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Khung xem bản đồ độ sâu MiDaS"
            },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Depth map (MiDaS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = "Bản đồ độ sâu MiDaS"
                }
            )
            Image(
                bitmap = depthBitmap,
                contentDescription = "Bản đồ độ sâu MiDaS, vùng sáng là gần và vùng tối là xa",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .heightIn(max = 360.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraModeSelector(
    activeMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = remember { CameraMode.entries }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Chọn chế độ camera"
            },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics {
                    contentDescription = "Thanh chọn chế độ camera"
                }
        ) {
            modes.forEachIndexed { index, mode ->
                val selected = mode == activeMode
                SegmentedButton(
                    selected = selected,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    modifier = Modifier
                        .heightIn(min = 88.dp)
                        .semantics {
                            contentDescription = "Chuyển sang chế độ ${mode.descriptionVi}"
                            stateDescription = if (selected) "Đang chọn" else "Chưa chọn"
                        },
                    label = { Text(text = mode.labelVi) }
                )
            }
        }
    }
}
