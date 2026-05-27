package com.example.eyes.infrastructure.ocr

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.infrastructure.camera.toBitmap
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MlKitOcrEngine : OcrEnginePort {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @ExperimentalGetImage
    suspend fun recognize(imageProxy: ImageProxy): OcrResult =
        suspendCoroutine { cont ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                cont.resume(OcrResult.EMPTY)
                return@suspendCoroutine
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    cont.resume(OcrPostProcessor.process(visionText.text))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

    override suspend fun recognize(imageFrame: ImageFrame): OcrResult = recognize(imageFrame.toBitmap())

    suspend fun recognize(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    cont.resume(OcrPostProcessor.process(visionText.text))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    override fun close() {
        recognizer.close()
    }
}
