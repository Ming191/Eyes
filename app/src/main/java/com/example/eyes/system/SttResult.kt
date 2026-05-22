package com.example.eyes.system

/**
 * State of the speech-to-text recognizer.
 *
 * State transitions:
 *   Idle → Listening    (user pressed mic)
 *   Listening → Processing (user stopped speaking, engine is transcribing)
 *   Processing → Idle   (final result emitted) or → Error
 *   Listening → Error / Idle → Error  (any failure)
 *   Error → Idle        (after error is consumed by UI)
 */
sealed interface SttState {
    /** Recognizer is ready but not actively listening. */
    data object Idle : SttState

    /** Microphone is open and capturing user speech. */
    data object Listening : SttState

    /** User finished speaking; engine is producing the final transcript. */
    data object Processing : SttState

    /** A recoverable error occurred. UI should display [reason] then call [SttService.reset]. */
    data class Error(val reason: SttErrorReason) : SttState
}

/**
 * Speech recognition results emitted to subscribers of [SttService.results].
 *
 * Subscribers should treat [Partial] as a hint (e.g. show as caption) and only
 * act on [Final]. [Error] is duplicated here (also in [SttState]) so consumers
 * that subscribe only to results can still observe failures.
 */
sealed interface SttResult {
    /** Interim transcription, may change as the user keeps speaking. */
    data class Partial(val text: String) : SttResult

    /** Final transcription after the user stopped speaking. */
    data class Final(val text: String) : SttResult

    /** Recognition failed. */
    data class Error(val reason: SttErrorReason) : SttResult
}

/**
 * Human-meaningful categorisation of recognition failures.
 * The mapping from raw Android error codes lives in [SttService].
 */
sealed interface SttErrorReason {
    /** Network unavailable, timeout, or server-side failure. */
    data object Network : SttErrorReason

    /** Engine heard the user but could not match anything, or heard nothing. */
    data object NoMatch : SttErrorReason

    /** Microphone problem or the recognizer is busy. */
    data object Audio : SttErrorReason

    /** RECORD_AUDIO permission missing or revoked. */
    data object PermissionDenied : SttErrorReason

    /** Speech recognition is not available on this device. */
    data object NotAvailable : SttErrorReason

    /** Any other error from the underlying engine. [code] is the raw Android code. */
    data class Unknown(val code: Int) : SttErrorReason
}