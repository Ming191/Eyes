package com.example.eyes.infrastructure.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.eyes.application.ports.SpeechOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsService(context: Context) : SpeechOutput {
    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private enum class InitState { PENDING, READY, FAILED }

    private data class PendingUtterance(
        val text: String,
        val locale: Locale?,
        val sequence: Long,
        val completion: CompletableDeferred<Unit>? = null
    )

    private val lock = Any()
    private val pendingUtterances = mutableListOf<PendingUtterance>()
    private val inFlightUtteranceIds = mutableSetOf<String>()
    private val completionWaiters = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val utteranceTexts = mutableMapOf<String, String>()
    private var nextSequence = 0L
    private var initState: InitState = InitState.PENDING
    private var currentLocale: Locale? = null
    private val _currentSpokenText = MutableStateFlow<String?>(null)
    override val currentSpokenText: StateFlow<String?> = _currentSpokenText.asStateFlow()

    private val tts: TextToSpeech
    private var speechRate: Float = DEFAULT_SPEECH_RATE

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusHeld = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            synchronized(lock) {
                if (status != TextToSpeech.SUCCESS) {
                    initState = InitState.FAILED
                    clearPendingLocked()
                    Log.w(TAG, "TTS initialization failed with status=$status")
                    return@TextToSpeech
                }

                val localeResult = tts.setLanguage(VIETNAMESE_LOCALE)
                if (isLocaleUnsupported(localeResult)) {
                    Log.w(TAG, "vi-VN locale unavailable on this device; using engine default locale")
                    currentLocale = tts.voice?.locale ?: tts.defaultVoice?.locale
                } else {
                    currentLocale = VIETNAMESE_LOCALE
                }
                tts.setSpeechRate(speechRate)

                setupProgressListener()

                initState = InitState.READY
                flushPendingLocked()
            }
        }
    }

    private fun setupProgressListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Bắt đầu đọc: $utteranceId")
                synchronized(lock) {
                    _currentSpokenText.value = utteranceId?.let(utteranceTexts::get)
                }
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Đã đọc xong: $utteranceId")
                handleUtteranceFinished(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Lỗi khi đọc: $utteranceId")
                handleUtteranceFinished(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Lỗi khi đọc: $utteranceId, code=$errorCode")
                handleUtteranceFinished(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                Log.d(TAG, "Bị ngắt ngang: $utteranceId")
                handleUtteranceFinished(utteranceId)
            }
        })
    }

    override fun speak(text: String) {
        speakInternal(text = text, locale = null)
    }

    override fun speak(text: String, locale: Locale) {
        speakInternal(text = text, locale = locale)
    }

    override suspend fun speakAndAwait(text: String) {
        speakAndAwaitInternal(text = text, locale = null)
    }

    override suspend fun speakAndAwait(text: String, locale: Locale) {
        speakAndAwaitInternal(text = text, locale = locale)
    }

    private suspend fun speakAndAwaitInternal(text: String, locale: Locale?) {
        val normalizedText = preprocessText(text, locale)
        if (normalizedText.isBlank()) return
        if (isScreenReaderLikelyEnabled()) {
            stop()
            return
        }

        val completion = CompletableDeferred<Unit>()
        synchronized(lock) {
            when (initState) {
                InitState.PENDING -> enqueuePendingLocked(normalizedText, locale, completion)
                InitState.FAILED -> {
                    Log.w(TAG, "TTS unavailable; dropping utterance")
                    completion.complete(Unit)
                }
                InitState.READY -> enqueueAndDispatchLocked(normalizedText, locale, completion)
            }
        }
        completion.await()
    }

    private fun speakInternal(text: String, locale: Locale?) {
        val normalizedText = preprocessText(text, locale)
        if (normalizedText.isBlank()) return
        if (isScreenReaderLikelyEnabled()) {
            stop()
            return
        }

        synchronized(lock) {
            when (initState) {
                InitState.PENDING -> {
                    clearPendingLocked()
                    enqueuePendingLocked(normalizedText, locale)
                }
                InitState.FAILED -> Log.w(TAG, "TTS unavailable; dropping utterance")
                InitState.READY -> {
                    interruptActiveSpeechLocked()
                    enqueueAndDispatchLocked(normalizedText, locale)
                }
            }
        }
    }

    override fun setSpeechRate(rate: Float) {
        synchronized(lock) {
            speechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
            if (initState == InitState.READY) {
                tts.setSpeechRate(speechRate)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            clearPendingLocked()
            completionWaiters.values.forEach { it.complete(Unit) }
            completionWaiters.clear()
            utteranceTexts.clear()
            inFlightUtteranceIds.clear()
            if (initState == InitState.READY) {
                tts.stop()
            }
            _currentSpokenText.value = null
            abandonAudioFocusLocked()
        }
    }

    private fun enqueuePendingLocked(
        text: String,
        locale: Locale?,
        completion: CompletableDeferred<Unit>? = null
    ) {
        enqueueAndDispatchLocked(text, locale, completion, allowDispatch = false)
    }

    private fun flushPendingLocked() {
        dispatchNextIfIdleLocked()
    }

    private fun clearPendingLocked() {
        pendingUtterances.forEach { it.completion?.complete(Unit) }
        pendingUtterances.clear()
    }

    private fun interruptActiveSpeechLocked() {
        clearPendingLocked()
        completionWaiters.values.forEach { it.complete(Unit) }
        completionWaiters.clear()
        utteranceTexts.clear()
        inFlightUtteranceIds.clear()
        _currentSpokenText.value = null
        if (initState == InitState.READY) {
            tts.stop()
        }
    }

    override fun warmupLocale(locale: Locale) {
        synchronized(lock) {
            if (initState != InitState.READY) return
            if (currentLocale == locale) return
            val result = tts.setLanguage(locale)
            if (isLocaleUnsupported(result)) {
                Log.w(TAG, "TTS locale warmup unsupported: $locale")
                return
            }
            currentLocale = locale
        }
    }

    private fun enqueueAndDispatchLocked(
        text: String,
        locale: Locale?,
        completion: CompletableDeferred<Unit>? = null,
        allowDispatch: Boolean = true
    ) {
        pendingUtterances.add(
            PendingUtterance(
                text = text,
                locale = locale,
                sequence = nextSequence++,
                completion = completion
            )
        )

        if (allowDispatch) {
            dispatchNextIfIdleLocked()
        }
    }

    private fun dispatchNextIfIdleLocked() {
        if (inFlightUtteranceIds.isNotEmpty()) return
        val nextItem = pendingUtterances.removeFirstOrNull() ?: run {
            _currentSpokenText.value = null
            abandonAudioFocusLocked()
            return
        }
        speakInternalLocked(nextItem)
    }

    private fun speakInternalLocked(item: PendingUtterance) {
        requestAudioFocusLocked()
        tts.setSpeechRate(speechRate)
        val targetLocale = item.locale ?: VIETNAMESE_LOCALE
        if (currentLocale != targetLocale) {
            val localeResult = tts.setLanguage(targetLocale)
            if (isLocaleUnsupported(localeResult)) {
                Log.w(TAG, "Requested locale unavailable: $targetLocale, fallback to vi-VN")
                val fallbackResult = tts.setLanguage(VIETNAMESE_LOCALE)
                if (!isLocaleUnsupported(fallbackResult)) {
                    currentLocale = VIETNAMESE_LOCALE
                } else {
                    currentLocale = tts.voice?.locale ?: tts.defaultVoice?.locale
                }
            } else {
                currentLocale = targetLocale
            }
        }

        val utteranceId = UUID.randomUUID().toString()
        utteranceTexts[utteranceId] = item.text
        val result = tts.speak(item.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            inFlightUtteranceIds.add(utteranceId)
            item.completion?.let { completionWaiters[utteranceId] = it }
        } else {
            Log.w(TAG, "TTS speak failed with result=$result")
            utteranceTexts.remove(utteranceId)
            item.completion?.complete(Unit)
            dispatchNextIfIdleLocked()
        }
    }

    private fun handleUtteranceFinished(utteranceId: String?) {
        synchronized(lock) {
            if (utteranceId != null) {
                inFlightUtteranceIds.remove(utteranceId)
                utteranceTexts.remove(utteranceId)
                completionWaiters.remove(utteranceId)?.complete(Unit)
            }

            dispatchNextIfIdleLocked()
        }
    }

    private fun requestAudioFocusLocked() {
        if (audioFocusHeld) return

        if (audioFocusRequest == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> stop()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Log.d(
                            TAG,
                            "TTS audio focus transient loss; continue current utterance"
                        )
                    }
                }
                .build()
        }

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!audioFocusHeld) {
            Log.w(TAG, "Audio focus not granted, continue TTS with best effort")
        }
    }

    private fun abandonAudioFocusLocked() {
        if (!audioFocusHeld) return
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusHeld = false
    }

    private fun preprocessText(raw: String, locale: Locale?): String {
        return TtsTextPreprocessor.preprocess(raw, locale)
    }

    private fun isLocaleUnsupported(result: Int): Boolean {
        return result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun isScreenReaderLikelyEnabled(): Boolean =
        accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled

    private companion object {
        private const val TAG = "TtsService"
        private const val DEFAULT_SPEECH_RATE = 1.2f
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.0f
        private val VIETNAMESE_LOCALE = Locale.Builder()
            .setLanguage("vi")
            .setRegion("VN")
            .build()
    }
}

internal object TtsTextPreprocessor {
    fun preprocess(raw: String, locale: Locale?): String {
        val isEnglish = locale?.language.equals(Locale.ENGLISH.language, ignoreCase = true)
        return raw
            .replace(URL_REGEX, "link")
            .let { if (isEnglish) preprocessEnglishText(it) else preprocessVietnameseText(it) }
            .replace(SYMBOL_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun preprocessVietnameseText(raw: String): String = raw
        .replace(EN_TO_VI_REGEX, "tiếng Anh sang tiếng Việt")
        .replace(GPT_4O_REGEX, "GPT bốn ô")
        .replace(ML_KIT_REGEX, "em eo kít")
        .replace(OCR_REGEX, "ô xê e rờ")
        .replace(API_REGEX, "ây pi ai")
        .replace(TALKBACK_REGEX, "Talk Back")
        .replace(DOUBLE_TAP_REGEX, "chạm hai lần")
        .replace(DEBUG_REGEX, "gỡ lỗi")
        .replace(FALLBACK_REGEX, "chuyển dự phòng")
        .replace(MODE_REGEX, "chế độ")

    private fun preprocessEnglishText(raw: String): String = raw
        .replace(EN_TO_VI_REGEX, "English to Vietnamese")
        .replace(GPT_4O_REGEX, "G P T four O")
        .replace(ML_KIT_REGEX, "M L Kit")
        .replace(OCR_REGEX, "O C R")
        .replace(API_REGEX, "A P I")
        .replace(TALKBACK_REGEX, "TalkBack")
        .replace(DOUBLE_TAP_REGEX, "double tap")

    private val URL_REGEX = Regex("https?://\\S+")
    private val EN_TO_VI_REGEX = Regex("\\bEN\\s*[-–—]*>\\s*VI\\b", RegexOption.IGNORE_CASE)
    private val GPT_4O_REGEX = Regex("\\bGPT[- ]?4o\\b", RegexOption.IGNORE_CASE)
    private val ML_KIT_REGEX = Regex("\\bML Kit\\b", RegexOption.IGNORE_CASE)
    private val OCR_REGEX = Regex("\\bOCR\\b", RegexOption.IGNORE_CASE)
    private val API_REGEX = Regex("\\bAPI\\b", RegexOption.IGNORE_CASE)
    private val TALKBACK_REGEX = Regex("\\bTalkBack\\b", RegexOption.IGNORE_CASE)
    private val DOUBLE_TAP_REGEX = Regex("\\bDouble tap\\b", RegexOption.IGNORE_CASE)
    private val DEBUG_REGEX = Regex("\\bDebug\\b", RegexOption.IGNORE_CASE)
    private val FALLBACK_REGEX = Regex("\\bfallback\\b", RegexOption.IGNORE_CASE)
    private val MODE_REGEX = Regex("\\bmode\\b", RegexOption.IGNORE_CASE)
    private val SYMBOL_REGEX = Regex("[|■▪►]")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
