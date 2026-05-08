package com.example.eyes.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TtsService(context: Context) : SpeechOutput {

    private enum class InitState { PENDING, READY, FAILED }

    private data class PendingUtterance(
        val text: String,
        val priority: SpeechOutput.Priority,
        val locale: Locale?,
        val sequence: Long
    )

    private val lock = Any()
    private val pendingUtterances = mutableListOf<PendingUtterance>()
    private val inFlightUtteranceIds = mutableSetOf<String>()
    private var nextSequence = 0L
    private var initState: InitState = InitState.PENDING
    private var currentLocale: Locale? = null

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
                    pendingUtterances.clear()
                    Log.w(TAG, "TTS initialization failed with status=$status")
                    return@TextToSpeech
                }

                val localeResult = tts.setLanguage(VIETNAMESE_LOCALE)
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "vi-VN locale unavailable on this device; using engine default locale")
                }
                currentLocale = VIETNAMESE_LOCALE
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
        speak(text, SpeechOutput.Priority.NORMAL)
    }

    override fun speak(text: String, priority: SpeechOutput.Priority) {
        speak(text = text, priority = priority, locale = null)
    }

    fun speak(
        text: String,
        priority: SpeechOutput.Priority = SpeechOutput.Priority.NORMAL,
        locale: Locale? = null
    ) {
        val normalizedText = preprocessText(text)
        if (normalizedText.isBlank()) return

        synchronized(lock) {
            when (initState) {
                InitState.PENDING -> enqueuePendingLocked(normalizedText, priority, locale)
                InitState.FAILED -> Log.w(TAG, "TTS unavailable; dropping utterance")
                InitState.READY -> speakInternalLocked(normalizedText, priority, locale)
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
            pendingUtterances.clear()
            inFlightUtteranceIds.clear()
            if (initState == InitState.READY) {
                tts.stop()
            }
            abandonAudioFocusLocked()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            pendingUtterances.clear()
            inFlightUtteranceIds.clear()
            if (initState == InitState.READY) {
                tts.stop()
            }
            tts.shutdown()
            initState = InitState.FAILED
            abandonAudioFocusLocked()
        }
    }

    private fun enqueuePendingLocked(
        text: String,
        priority: SpeechOutput.Priority,
        locale: Locale?
    ) {
        val item = PendingUtterance(
            text = text,
            priority = priority,
            locale = locale,
            sequence = nextSequence++
        )

        when (priority) {
            SpeechOutput.Priority.URGENT -> {
                pendingUtterances.clear()
                pendingUtterances.add(item)
            }
            SpeechOutput.Priority.HIGH -> {
                val firstNormalIndex = pendingUtterances.indexOfFirst { it.priority == SpeechOutput.Priority.NORMAL }
                if (firstNormalIndex == -1) {
                    pendingUtterances.add(item)
                } else {
                    pendingUtterances.add(firstNormalIndex, item)
                }
            }
            SpeechOutput.Priority.NORMAL -> pendingUtterances.add(item)
        }
    }

    private fun flushPendingLocked() {
        val sortedPending = pendingUtterances
            .sortedWith(compareBy({ it.priority.rank }, { it.sequence }))

        sortedPending.forEach { item ->
            speakInternalLocked(item.text, item.priority, item.locale)
        }
        pendingUtterances.clear()
    }

    fun warmupLocale(locale: Locale) {
        synchronized(lock) {
            if (initState != InitState.READY) return
            if (currentLocale == locale) return
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS locale warmup unsupported: $locale")
                return
            }
            currentLocale = locale
        }
    }

    private fun speakInternalLocked(
        text: String,
        priority: SpeechOutput.Priority,
        locale: Locale?
    ) {
        val queueMode = when (priority) {
            SpeechOutput.Priority.URGENT -> {
                pendingUtterances.clear()
                inFlightUtteranceIds.clear()
                TextToSpeech.QUEUE_FLUSH
            }
            SpeechOutput.Priority.HIGH, SpeechOutput.Priority.NORMAL -> TextToSpeech.QUEUE_ADD
        }

        requestAudioFocusLocked()
        tts.setSpeechRate(speechRate)
        val targetLocale = locale ?: VIETNAMESE_LOCALE
        if (currentLocale != targetLocale) {
            val localeResult = tts.setLanguage(targetLocale)
            if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Requested locale unavailable: $targetLocale, fallback to vi-VN")
                val fallbackResult = tts.setLanguage(VIETNAMESE_LOCALE)
                if (fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                    currentLocale = VIETNAMESE_LOCALE
                }
            } else {
                currentLocale = targetLocale
            }
        }

        val utteranceId = UUID.randomUUID().toString()
        val result = tts.speak(text, queueMode, null, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            inFlightUtteranceIds.add(utteranceId)
        } else {
            Log.w(TAG, "TTS speak failed with result=$result")
            if (inFlightUtteranceIds.isEmpty()) {
                abandonAudioFocusLocked()
            }
        }
    }

    private fun handleUtteranceFinished(utteranceId: String?) {
        synchronized(lock) {
            if (utteranceId != null) {
                inFlightUtteranceIds.remove(utteranceId)
            }

            if (inFlightUtteranceIds.isEmpty()) {
                abandonAudioFocusLocked()
            }
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
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stop()
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

    private fun preprocessText(raw: String): String {
        return raw
            .replace(URL_REGEX, "link")
            .replace(SYMBOL_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private companion object {
        private const val TAG = "TtsService"
        private const val DEFAULT_SPEECH_RATE = 1.2f
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.0f
        private val VIETNAMESE_LOCALE = Locale.Builder()
            .setLanguage("vi")
            .setRegion("VN")
            .build()
        private val URL_REGEX = Regex("https?://\\S+")
        private val SYMBOL_REGEX = Regex("[|■▪►]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    private val SpeechOutput.Priority.rank: Int
        get() = when (this) {
            SpeechOutput.Priority.URGENT -> 0
            SpeechOutput.Priority.HIGH -> 1
            SpeechOutput.Priority.NORMAL -> 2
        }
}
