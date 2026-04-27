package com.example.eyes.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analysisUseCase: ImageAnalysis? = null
    private var previewUseCase: Preview? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit
    ) {
        bindInternal(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onFrame = onFrame
        )
    }

    fun bindAnalysisToLifecycle(
        lifecycleOwner: LifecycleOwner,
        onFrame: (ImageProxy) -> Unit
    ) {
        bindInternal(
            lifecycleOwner = lifecycleOwner,
            previewView = null,
            onFrame = onFrame
        )
    }

    fun unbindAll() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                providerFuture.get().unbindAll()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun bindInternal(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?,
        onFrame: (ImageProxy) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()

                previewUseCase = previewView?.let { view ->
                    Preview.Builder().build().also { preview ->
                        preview.surfaceProvider = view.surfaceProvider
                    }
                }

                analysisUseCase = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            onFrame(imageProxy)
                        }
                    }

                cameraProvider.unbindAll()
                val analysis = analysisUseCase ?: return@addListener
                val preview = previewUseCase
                if (preview != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        analysis
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}
