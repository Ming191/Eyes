package com.example.eyes.ui.camera

import com.example.eyes.domain.ocr.OcrGuidanceStatus

internal fun CameraUiState.resetOcrRuntime(
    cameraText: CameraText,
    guidanceMessage: String = cameraText.ocrGuidanceSearch
): CameraUiState = copy(
    isOcrScanning = false,
    ocrSentences = emptyList(),
    ocrCurrentIndex = 0,
    canTranslateCurrentOcrDocument = false,
    ocrCapturedBitmap = null,
    ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
    ocrGuidanceMessage = guidanceMessage,
    isOcrReadyToCapture = false,
    ocrGuidanceBounds = null
)

internal fun CameraUiState.resetDetectionAndCurrency(): CameraUiState = copy(
    objectDetections = emptyList(),
    currencyDisplay = "",
    currencyConfidence = 0f,
    isCurrencyScanning = false
)
