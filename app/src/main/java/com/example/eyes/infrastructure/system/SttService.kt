package com.example.eyes.infrastructure.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.eyes.domain.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Speech-to-text wrapper around [android.speech.SpeechRecognizer].
 *
 * Exposes a [state] StateFlow (Idle / Listening / Processing / Error) and a
 * [results] SharedFlow that emits partial and final transcriptions plus errors.
 *
 * Threading: SpeechRecognizer is a Main-thread API. All public methods are
 * safe to call from any thread; this class internally posts work to the Main
 * looper.
 *
 * Lifecycle: this service is registered as a singleton in Koin. The recognizer
 * itself is lazily created on first [startListening]. Call [release] before
 * the application is torn down (typically only in tests; the OS cleans up at
 * process death).
 */
class SttService(
    context: Context
) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReleased = false

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    val state: StateFlow<SttState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<SttResult>(
        replay = 0,
        extraBufferCapacity = RESULTS_BUFFER_CAPACITY
    )
    val results: SharedFlow<SttResult> = _results.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null

    /**
     * Quick check whether the device has any RecognitionService available.
     * Note: requires the `<queries>` block in AndroidManifest on Android 11+
     * to return accurate results.
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Start listening for Vietnamese speech. No-op if already listening.
     * Errors (missing permission, unavailable engine, etc.) are emitted via
     * [state] and [results] rather than thrown.
     */
    fun startListening(language: AppLanguage = AppLanguage.VI) {
        runOnMain {
            if (isReleased) {
                Log.w(TAG, "startListening called after release() — ignoring")
                emitError(SttErrorReason.NotAvailable)
                return@runOnMain
            }
            if (_state.value is SttState.Listening || _state.value is SttState.Processing) {
                Log.d(TAG, "startListening ignored — already in state ${_state.value}")
                return@runOnMain
            }

            if (!hasRecordAudioPermission()) {
                emitError(SttErrorReason.PermissionDenied)
                return@runOnMain
            }

            if (!isAvailable()) {
                emitError(SttErrorReason.NotAvailable)
                return@runOnMain
            }

            val r = recognizer ?: createRecognizer().also { recognizer = it }
            try {
                r.startListening(buildIntent(language))
                _state.value = SttState.Listening
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException from SpeechRecognizer", e)
                emitError(SttErrorReason.PermissionDenied)
            }
        }
    }

    /**
     * Manually stop listening (e.g. user tapped a "stop" button).
     * The recognizer will still emit a Final result for whatever was captured.
     */
    fun stopListening() {
        runOnMain {
            recognizer?.stopListening()
        }
    }

    /**
     * Cancel any in-flight recognition without producing a final result.
     */
    fun cancel() {
        runOnMain {
            recognizer?.cancel()
            _state.value = SttState.Idle
        }
    }

    /**
     * Reset the state machine after an error has been shown to the user.
     * Does not destroy the underlying recognizer.
     */
    fun reset() {
        runOnMain {
            _state.value = SttState.Idle
        }
    }

    /**
     * Destroy the underlying SpeechRecognizer. After this call the service is
     * unusable. Intended for tests or app teardown.
     */
    fun release() {
        runOnMain {
            isReleased = true
            recognizer?.destroy()
            recognizer = null
            _state.value = SttState.Idle
        }
    }

    // ----- Internal -----

    private fun createRecognizer(): SpeechRecognizer {
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(InternalListener())
        return r
    }

    private fun buildIntent(language: AppLanguage) = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.sttLanguageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.sttLanguageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun emitError(reason: SttErrorReason) {
        _state.value = SttState.Error(reason)
        _results.tryEmit(SttResult.Error(reason))
    }

    private fun mapErrorCode(code: Int): SttErrorReason = when (code) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> SttErrorReason.Network

        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttErrorReason.NoMatch

        SpeechRecognizer.ERROR_AUDIO,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttErrorReason.Audio

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
            if (hasRecordAudioPermission()) SttErrorReason.Audio else SttErrorReason.PermissionDenied
        }

        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SttErrorReason.TooManyRequests

        else -> SttErrorReason.Unknown(code)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Internal listener that translates [RecognitionListener] callbacks into
     * [SttState] and [SttResult] emissions.
     */
    private inner class InternalListener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _state.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            _state.value = SttState.Processing
        }

        override fun onError(error: Int) {
            val reason = mapErrorCode(error)
            Log.w(TAG, "onError code=$error reason=$reason")
            recognizer?.destroy()
            recognizer = null
            emitError(reason)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                Log.d(TAG, "onPartialResults: $text")
                _results.tryEmit(SttResult.Partial(text))
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            Log.d(TAG, "onResults: $text")
            if (text.isNotEmpty()) {
                _results.tryEmit(SttResult.Final(text))
            } else {
                _results.tryEmit(SttResult.Error(SttErrorReason.NoMatch))
                _state.value = SttState.Error(SttErrorReason.NoMatch)
                return
            }
            _state.value = SttState.Idle
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        private const val TAG = "SttService"
        private const val RESULTS_BUFFER_CAPACITY = 64
    }
}
