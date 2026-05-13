package com.example.eyes.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
    private var imageCapture: ImageCapture? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var previewUseCase: Preview? = null

    /**
     * Binds a camera preview and image-analysis use case to the given lifecycle owner.
     *
     * Configures a Preview displayed in the provided PreviewView and an ImageAnalysis that invokes
     * `onFrame` for each captured ImageProxy.
     *
     * @param lifecycleOwner Owner whose lifecycle controls the camera binding.
     * @param previewView View that will display the camera preview.
     * @param onFrame Callback invoked with each captured ImageProxy for frame-by-frame analysis.
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: ((ImageProxy) -> Unit)? = null
    ) {
        bindInternal(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onFrame = onFrame
        )
    }

    /**
     * Bind an image-analysis use case to the given lifecycle owner without attaching a preview.
     *
     * @param lifecycleOwner The LifecycleOwner used to bind the camera use case.
     * @param onFrame Callback invoked for each captured `ImageProxy`; the callback is responsible for closing the `ImageProxy` when finished.
     */
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

    /**
     * Requests the CameraX ProcessCameraProvider to unbind all currently bound use cases on the main thread.
     *
     * The actual `unbindAll()` call is executed on the application's main executor once the provider becomes available.
     */
    fun unbindAll() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                providerFuture.get().unbindAll()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Binds camera use cases (preview and image analysis) to the provided lifecycle owner.
     *
     * Obtains a ProcessCameraProvider on the main executor, clears any existing bindings, and
     * then creates and binds an ImageAnalysis use case configured for YUV_420_888 with
     * STRATEGY_KEEP_ONLY_LATEST. If a non-null `previewView` is provided, a Preview use case
     * is created and its surface provider is set from the view; otherwise only analysis is bound.
     *
     * @param lifecycleOwner The LifecycleOwner to which camera use cases will be bound.
     * @param previewView If non-null, a Preview use case will be created using this view's surface provider; if null, no preview is bound.
     * @param onFrame Callback invoked for each delivered `ImageProxy`.
     */
    private fun bindInternal(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?,
        onFrame: ((ImageProxy) -> Unit)?
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

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                analysisUseCase = onFrame?.let { frameCallback ->
                    ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                frameCallback(imageProxy)
                            }
                        }
                }

                cameraProvider.unbindAll()
                val preview = previewUseCase
                val analysis = analysisUseCase
                if (preview != null && analysis != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        analysis
                    )
                } else if (preview != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } else if (analysis != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageCapture,
                        analysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageCapture
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun takePicture(
        onCaptured: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit = {}
    ) {
        val capture = imageCapture ?: return
        capture.takePicture(
            analysisExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    onCaptured(imageProxy)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun shutdown() {
        analysisExecutor.shutdown()
    }
}
