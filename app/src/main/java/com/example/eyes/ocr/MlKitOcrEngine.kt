package com.example.eyes.ocr

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.eyes.application.ports.OcrEnginePort
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MlKitOcrEngine : OcrEnginePort {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @ExperimentalGetImage
    override suspend fun recognize(imageProxy: ImageProxy): OcrResult =
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

    override suspend fun recognize(bitmap: Bitmap): OcrResult =
        suspendCoroutine { cont ->
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
