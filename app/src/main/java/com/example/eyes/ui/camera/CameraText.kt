package com.example.eyes.ui.camera

import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrGuidanceEvaluator.OcrGuidanceText
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.scene.SceneDescriptionError
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

data class CameraText(
    val ocrGuidanceSearch: String,
    val ocrGuidanceTooDark: String,
    val ocrGuidanceTooBright: String,
    val ocrGuidanceMoveCloser: String,
    val ocrGuidanceMoveBack: String,
    val ocrGuidanceTextClipped: String,
    val ocrGuidanceMoveLeft: String,
    val ocrGuidanceMoveRight: String,
    val ocrGuidanceMoveUp: String,
    val ocrGuidanceMoveDown: String,
    val ocrGuidanceReady: String,
    val ocrGuidanceHoldSteady: String,
    val initialStatus: String,
    val initialAnnouncement: String,
    val initialDebug: String,
    val ocrTitle: String,
    val ocrSummary: String,
    val sceneDescriptionTitle: String,
    val sceneDescriptionSummary: String,
    val currencyTitle: String,
    val currencySummary: String,
    val currencyInstruction: String,
    val currencyModelLoadError: String,
    val currencyModelInitError: String,
    val currencyDetectedStatusTemplate: String,
    val currencyDebugTemplate: String,
    val currencyDebugWaiting: String,
    val noCurrencyDetected: String,
    val waitingForClearTextFrame: String,
    val waitingForSceneFrame: String,
    val waitingForClearMoneyImage: String,
    val frameUnclearRetrying: String,
    val cannotProcessCapturedImage: String,
    val processingOcrImage: String,
    val processingCurrencyImage: String,
    val unknownReason: String,
    val unknownError: String,
    val cannotCaptureOcrTryAgain: String,
    val cannotCaptureCurrencyTryAgain: String,
    val imageMayBeUnclearCapturing: String,
    val readyToCaptureOcr: String,
    val readyForNewCapture: String,
    val endOfText: String,
    val startOfText: String,
    val switchedToReadTextMode: String,
    val ocrDebugWaiting: String,
    val enabledEnglishToVietnameseTranslation: String,
    val disabledEnglishToVietnameseTranslation: String,
    val cannotCaptureSceneTryAgain: String,
    val describingScenePleaseWait: String,
    val sceneDescriptionDone: String,
    val sceneDescriptionFailed: String,
    val sceneDescriptionErrorApiKeyMissing: String,
    val sceneDescriptionErrorUnauthorized: String,
    val sceneDescriptionErrorQuota: String,
    val sceneDescriptionErrorTimeout: String,
    val sceneDescriptionErrorGeneric: String,
    val sceneDescriptionOfflineFallback: String,
    val gptRefusedReason: String,
    val noTextDetectedTryAgain: String,
    val updatingOcrReading: String,
    val apiKeyReason: String,
    val modelPermissionReason: String,
    val quotaReason: String,
    val timeoutReason: String,
    val translationUnchangedReason: String,
    val cannotTranslateReadingOriginal: String,
    val ocrFailedTemplate: String,
    val accuracyOcrFallbackTemplate: String,
    val quickModeLabel: String,
    val accurateModeLabel: String,
    val switchedToOcrModeTemplate: String,
    val switchedToQuickMode: String,
    val switchedToAccurateMode: String,
    val gptFallbackStatusTemplate: String,
    val capturedParagraphsTemplate: String,
    val ocrSentencePositionTemplate: String,
    val objectDetectionWarmupDone: String,
    val objectDetectionWarmupFailed: String,
    val objectDetectionAnnouncementTemplate: String,
    val objectDetectionNoObjects: String,
    val objectDetectionDebugTemplate: String,
    val objectDetectionTitle: String,
    val objectDetectionSummary: String,
    val objectDetectionStatus: String,
    val switchedToSceneDescriptionMode: String,
    val switchedToObjectDetectionMode: String,
    val switchedToCurrencyMode: String
) {
    fun initialUiState(): CameraUiState = CameraUiState(
        activeMode = CameraMode.OBJECT_DETECTION,
        ocrGuidanceMessage = ocrGuidanceSearch,
        title = objectDetectionTitle,
        summary = objectDetectionSummary,
        statusMessage = objectDetectionStatus,
        lastAnnouncement = initialAnnouncement,
        debugMetrics = initialDebug
    )

    fun translateTitle(value: String, target: CameraText): String = when (value) {
        ocrTitle -> target.ocrTitle
        objectDetectionTitle -> target.objectDetectionTitle
        sceneDescriptionTitle -> target.sceneDescriptionTitle
        currencyTitle -> target.currencyTitle
        else -> value
    }

    fun translateSummary(value: String, target: CameraText): String = when (value) {
        ocrSummary -> target.ocrSummary
        objectDetectionSummary -> target.objectDetectionSummary
        sceneDescriptionSummary -> target.sceneDescriptionSummary
        currencySummary -> target.currencySummary
        else -> value
    }

    fun translateStatus(value: String, target: CameraText): String = translateKnownRuntimeText(value, target)

    fun translateAnnouncement(value: String, target: CameraText): String = translateKnownRuntimeText(value, target)

    fun translateOcrGuidance(value: String, target: CameraText): String = when (value) {
        ocrGuidanceSearch -> target.ocrGuidanceSearch
        ocrGuidanceTooDark -> target.ocrGuidanceTooDark
        ocrGuidanceTooBright -> target.ocrGuidanceTooBright
        ocrGuidanceMoveCloser -> target.ocrGuidanceMoveCloser
        ocrGuidanceMoveBack -> target.ocrGuidanceMoveBack
        ocrGuidanceTextClipped -> target.ocrGuidanceTextClipped
        ocrGuidanceMoveLeft -> target.ocrGuidanceMoveLeft
        ocrGuidanceMoveRight -> target.ocrGuidanceMoveRight
        ocrGuidanceMoveUp -> target.ocrGuidanceMoveUp
        ocrGuidanceMoveDown -> target.ocrGuidanceMoveDown
        ocrGuidanceReady -> target.ocrGuidanceReady
        ocrGuidanceHoldSteady -> target.ocrGuidanceHoldSteady
        processingOcrImage -> target.processingOcrImage
        processingCurrencyImage -> target.processingCurrencyImage
        else -> value
    }

    fun ocrGuidanceText(): OcrGuidanceText = OcrGuidanceText(
        searching = ocrGuidanceSearch,
        tooDark = ocrGuidanceTooDark,
        tooBright = ocrGuidanceTooBright,
        moveCloser = ocrGuidanceMoveCloser,
        moveBack = ocrGuidanceMoveBack,
        textClipped = ocrGuidanceTextClipped,
        moveLeft = ocrGuidanceMoveLeft,
        moveRight = ocrGuidanceMoveRight,
        moveUp = ocrGuidanceMoveUp,
        moveDown = ocrGuidanceMoveDown,
        ready = ocrGuidanceReady,
        holdSteady = ocrGuidanceHoldSteady
    )

    fun translateDebug(value: String, target: CameraText): String = when (value) {
        initialDebug -> target.initialDebug
        ocrDebugWaiting -> target.ocrDebugWaiting
        currencyDebugWaiting -> target.currencyDebugWaiting
        else -> value
    }

    private fun translateKnownRuntimeText(value: String, target: CameraText): String = when (value) {
        initialStatus -> target.initialStatus
        initialAnnouncement -> target.initialAnnouncement
        waitingForClearTextFrame -> target.waitingForClearTextFrame
        waitingForSceneFrame -> target.waitingForSceneFrame
        waitingForClearMoneyImage -> target.waitingForClearMoneyImage
        currencyInstruction -> target.currencyInstruction
        currencyModelLoadError -> target.currencyModelLoadError
        currencyModelInitError -> target.currencyModelInitError
        frameUnclearRetrying -> target.frameUnclearRetrying
        cannotProcessCapturedImage -> target.cannotProcessCapturedImage
        processingOcrImage -> target.processingOcrImage
        processingCurrencyImage -> target.processingCurrencyImage
        cannotCaptureOcrTryAgain -> target.cannotCaptureOcrTryAgain
        cannotCaptureCurrencyTryAgain -> target.cannotCaptureCurrencyTryAgain
        cannotCaptureSceneTryAgain -> target.cannotCaptureSceneTryAgain
        readyToCaptureOcr -> target.readyToCaptureOcr
        readyForNewCapture -> target.readyForNewCapture
        switchedToReadTextMode -> target.switchedToReadTextMode
        describingScenePleaseWait -> target.describingScenePleaseWait
        sceneDescriptionDone -> target.sceneDescriptionDone
        sceneDescriptionFailed -> target.sceneDescriptionFailed
        sceneDescriptionErrorApiKeyMissing -> target.sceneDescriptionErrorApiKeyMissing
        sceneDescriptionErrorUnauthorized -> target.sceneDescriptionErrorUnauthorized
        sceneDescriptionErrorQuota -> target.sceneDescriptionErrorQuota
        sceneDescriptionErrorTimeout -> target.sceneDescriptionErrorTimeout
        sceneDescriptionErrorGeneric -> target.sceneDescriptionErrorGeneric
        noTextDetectedTryAgain -> target.noTextDetectedTryAgain
        updatingOcrReading -> target.updatingOcrReading
        objectDetectionWarmupDone -> target.objectDetectionWarmupDone
        objectDetectionWarmupFailed -> target.objectDetectionWarmupFailed
        objectDetectionStatus -> target.objectDetectionStatus
        switchedToSceneDescriptionMode -> target.switchedToSceneDescriptionMode
        switchedToObjectDetectionMode -> target.switchedToObjectDetectionMode
        switchedToCurrencyMode -> target.switchedToCurrencyMode
        else -> value
    }

    fun ocrFailed(reason: String): String = ocrFailedTemplate.format(reason)

    fun sceneDescriptionError(error: SceneDescriptionError): String = when (error) {
        SceneDescriptionError.API_KEY_MISSING -> sceneDescriptionErrorApiKeyMissing
        SceneDescriptionError.UNAUTHORIZED -> sceneDescriptionErrorUnauthorized
        SceneDescriptionError.RATE_LIMIT -> sceneDescriptionErrorQuota
        SceneDescriptionError.TIMEOUT -> sceneDescriptionErrorTimeout
        SceneDescriptionError.OFFLINE,
        SceneDescriptionError.EMPTY_RESPONSE,
        SceneDescriptionError.UNKNOWN -> sceneDescriptionErrorGeneric
    }

    fun accuracyOcrFallback(reason: String): String = accuracyOcrFallbackTemplate.format(reason)

    fun ocrModeLabel(mode: OcrMode): String = when (mode) {
        OcrMode.QUICK -> quickModeLabel
        OcrMode.ACCURACY -> accurateModeLabel
    }

    fun switchedToOcrMode(modeLabel: String): String = switchedToOcrModeTemplate.format(modeLabel)

    fun switchedOcrMode(mode: OcrMode): String = if (mode == OcrMode.QUICK) {
        switchedToQuickMode
    } else {
        switchedToAccurateMode
    }

    fun gptFallbackStatus(count: Int): String = gptFallbackStatusTemplate.format(count)

    fun capturedParagraphs(count: Int): String = capturedParagraphsTemplate.format(count)

    fun ocrSentencePosition(index: Int, total: Int, sentence: String): String =
        ocrSentencePositionTemplate.format(index, total, sentence)

    fun objectDetectionAnnouncement(label: String, position: String): String =
        objectDetectionAnnouncementTemplate.format(label, position)

    fun objectDetectionDebug(count: Int): String = objectDetectionDebugTemplate.format(count)

    fun currencyDetectedStatus(display: String, confidence: String): String =
        currencyDetectedStatusTemplate.format(display, confidence)

    fun currencyDebug(confidence: String): String = currencyDebugTemplate.format(confidence)

    companion object {
        fun from(localizedTextProvider: LocalizedTextProvider, language: AppLanguage): CameraText {
            val resources = localizedTextProvider.localizedContext(language).resources
            return CameraText(
                ocrGuidanceSearch = resources.getString(R.string.camera_vm_ocr_guidance_search),
                ocrGuidanceTooDark = resources.getString(R.string.camera_vm_ocr_guidance_too_dark),
                ocrGuidanceTooBright = resources.getString(R.string.camera_vm_ocr_guidance_too_bright),
                ocrGuidanceMoveCloser = resources.getString(R.string.camera_vm_ocr_guidance_move_closer),
                ocrGuidanceMoveBack = resources.getString(R.string.camera_vm_ocr_guidance_move_back),
                ocrGuidanceTextClipped = resources.getString(R.string.camera_vm_ocr_guidance_text_clipped),
                ocrGuidanceMoveLeft = resources.getString(R.string.camera_vm_ocr_guidance_move_left),
                ocrGuidanceMoveRight = resources.getString(R.string.camera_vm_ocr_guidance_move_right),
                ocrGuidanceMoveUp = resources.getString(R.string.camera_vm_ocr_guidance_move_up),
                ocrGuidanceMoveDown = resources.getString(R.string.camera_vm_ocr_guidance_move_down),
                ocrGuidanceReady = resources.getString(R.string.camera_vm_ocr_guidance_ready),
                ocrGuidanceHoldSteady = resources.getString(R.string.camera_vm_ocr_guidance_hold_steady),
                initialStatus = resources.getString(R.string.camera_vm_initial_status),
                initialAnnouncement = resources.getString(R.string.camera_vm_initial_announcement),
                initialDebug = resources.getString(R.string.camera_vm_initial_debug),
                ocrTitle = resources.getString(R.string.camera_vm_ocr_title),
                ocrSummary = resources.getString(R.string.camera_vm_ocr_summary),
                sceneDescriptionTitle = resources.getString(R.string.camera_vm_scene_description_title),
                sceneDescriptionSummary = resources.getString(R.string.camera_vm_scene_description_summary),
                currencyTitle = resources.getString(R.string.camera_vm_currency_title),
                currencySummary = resources.getString(R.string.camera_vm_currency_summary),
                currencyInstruction = resources.getString(R.string.currency_instruction),
                currencyModelLoadError = resources.getString(R.string.currency_model_load_error),
                currencyModelInitError = resources.getString(R.string.currency_model_init_error),
                currencyDetectedStatusTemplate = resources.getString(R.string.currency_detected_status),
                currencyDebugTemplate = resources.getString(R.string.camera_vm_currency_debug_template),
                currencyDebugWaiting = resources.getString(R.string.camera_vm_currency_debug_waiting),
                noCurrencyDetected = resources.getString(R.string.camera_vm_no_currency_detected),
                waitingForClearTextFrame = resources.getString(R.string.camera_vm_waiting_for_clear_text_frame),
                waitingForSceneFrame = resources.getString(R.string.camera_vm_waiting_for_scene_frame),
                waitingForClearMoneyImage = resources.getString(R.string.camera_vm_waiting_for_clear_money_image),
                frameUnclearRetrying = resources.getString(R.string.camera_vm_frame_unclear_retrying),
                cannotProcessCapturedImage = resources.getString(R.string.camera_vm_cannot_process_captured_image),
                processingOcrImage = resources.getString(R.string.camera_vm_processing_ocr_image),
                processingCurrencyImage = resources.getString(R.string.camera_vm_processing_currency_image),
                unknownReason = resources.getString(R.string.camera_vm_unknown_reason),
                unknownError = resources.getString(R.string.camera_vm_unknown_error),
                cannotCaptureOcrTryAgain = resources.getString(R.string.camera_vm_cannot_capture_ocr_try_again),
                cannotCaptureCurrencyTryAgain = resources.getString(R.string.camera_vm_cannot_capture_currency_try_again),
                imageMayBeUnclearCapturing = resources.getString(R.string.camera_vm_image_may_be_unclear_capturing),
                readyToCaptureOcr = resources.getString(R.string.camera_vm_ready_to_capture_ocr),
                readyForNewCapture = resources.getString(R.string.camera_vm_ready_for_new_capture),
                endOfText = resources.getString(R.string.camera_vm_end_of_text),
                startOfText = resources.getString(R.string.camera_vm_start_of_text),
                switchedToReadTextMode = resources.getString(R.string.camera_vm_switched_to_read_text_mode),
                ocrDebugWaiting = resources.getString(R.string.camera_vm_ocr_debug_waiting),
                enabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_enabled_english_to_vietnamese_translation),
                disabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_disabled_english_to_vietnamese_translation),
                cannotCaptureSceneTryAgain = resources.getString(R.string.camera_vm_cannot_capture_scene_try_again),
                describingScenePleaseWait = resources.getString(R.string.camera_vm_describing_scene_please_wait),
                sceneDescriptionDone = resources.getString(R.string.camera_vm_scene_description_done),
                sceneDescriptionFailed = resources.getString(R.string.camera_vm_scene_description_failed),
                sceneDescriptionErrorApiKeyMissing = resources.getString(R.string.scene_description_error_api_key_missing),
                sceneDescriptionErrorUnauthorized = resources.getString(R.string.scene_description_error_unauthorized),
                sceneDescriptionErrorQuota = resources.getString(R.string.scene_description_error_quota),
                sceneDescriptionErrorTimeout = resources.getString(R.string.scene_description_error_timeout),
                sceneDescriptionErrorGeneric = resources.getString(R.string.scene_description_error_generic),
                sceneDescriptionOfflineFallback = resources.getString(R.string.scene_description_offline_fallback),
                gptRefusedReason = resources.getString(R.string.camera_vm_gpt_refused_reason),
                noTextDetectedTryAgain = resources.getString(R.string.camera_vm_no_text_detected_try_again),
                updatingOcrReading = resources.getString(R.string.camera_vm_updating_ocr_reading),
                apiKeyReason = resources.getString(R.string.camera_vm_api_key_reason),
                modelPermissionReason = resources.getString(R.string.camera_vm_model_permission_reason),
                quotaReason = resources.getString(R.string.camera_vm_quota_reason),
                timeoutReason = resources.getString(R.string.camera_vm_timeout_reason),
                translationUnchangedReason = resources.getString(R.string.camera_vm_translation_unchanged_reason),
                cannotTranslateReadingOriginal = resources.getString(R.string.camera_vm_cannot_translate_reading_original),
                ocrFailedTemplate = resources.getString(R.string.camera_vm_ocr_failed_template),
                accuracyOcrFallbackTemplate = resources.getString(R.string.camera_vm_accuracy_ocr_fallback_template),
                quickModeLabel = resources.getString(R.string.camera_vm_quick_mode_label),
                accurateModeLabel = resources.getString(R.string.camera_vm_accurate_mode_label),
                switchedToOcrModeTemplate = resources.getString(R.string.camera_vm_switched_to_ocr_mode_template),
                switchedToQuickMode = resources.getString(R.string.camera_vm_switched_to_quick_mode),
                switchedToAccurateMode = resources.getString(R.string.camera_vm_switched_to_accurate_mode),
                gptFallbackStatusTemplate = resources.getString(R.string.camera_vm_gpt_fallback_status_template),
                capturedParagraphsTemplate = resources.getString(R.string.camera_vm_captured_paragraphs_template),
                ocrSentencePositionTemplate = resources.getString(R.string.camera_vm_ocr_sentence_position_template),
                objectDetectionWarmupDone = resources.getString(R.string.camera_vm_object_detection_warmup_done),
                objectDetectionWarmupFailed = resources.getString(R.string.camera_vm_object_detection_warmup_failed),
                objectDetectionAnnouncementTemplate = resources.getString(R.string.camera_vm_object_detection_announcement_template),
                objectDetectionNoObjects = resources.getString(R.string.camera_vm_object_detection_no_objects),
                objectDetectionDebugTemplate = resources.getString(R.string.camera_vm_object_detection_debug_template),
                objectDetectionTitle = resources.getString(R.string.camera_vm_object_detection_title),
                objectDetectionSummary = resources.getString(R.string.camera_vm_object_detection_summary),
                objectDetectionStatus = resources.getString(R.string.camera_vm_object_detection_status),
                switchedToSceneDescriptionMode = resources.getString(R.string.camera_vm_switched_to_scene_description_mode),
                switchedToObjectDetectionMode = resources.getString(R.string.camera_vm_switched_to_object_detection_mode),
                switchedToCurrencyMode = resources.getString(R.string.camera_vm_switched_to_currency_mode)
            )
        }
    }
}
