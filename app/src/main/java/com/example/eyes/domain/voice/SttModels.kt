package com.example.eyes.domain.voice

/** State of the speech-to-text recognizer. */
sealed interface SttState {
    data object Idle : SttState
    data object Listening : SttState
    data object Processing : SttState
    data class Error(val reason: SttErrorReason) : SttState
}

/** Speech recognition result. */
sealed interface SttResult {
    data class Partial(val text: String) : SttResult
    data class Final(val text: String) : SttResult
    data class Error(val reason: SttErrorReason) : SttResult
}

/** Human-meaningful categorisation of recognition failures. */
sealed interface SttErrorReason {
    data object Network : SttErrorReason
    data object NoMatch : SttErrorReason
    data object Audio : SttErrorReason
    data object PermissionDenied : SttErrorReason
    data object NotAvailable : SttErrorReason
    data object TooManyRequests : SttErrorReason
    data class Unknown(val code: Int) : SttErrorReason
}
