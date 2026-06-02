package com.example.eyes.infrastructure.system

import android.content.Context
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eyes.domain.voice.SttErrorReason
import com.example.eyes.domain.voice.SttResult
import com.example.eyes.domain.voice.SttState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SttServiceInternalListenerTest {
    @Test
    fun listenerMapsSpeechCallbacksToStateAndResults() = runTest {
        val service = SttService(ApplicationProvider.getApplicationContext<Context>())
        val listener = newListener(service)
        val partials = mutableListOf<SttResult>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { service.results.first { it is SttResult.Partial }.also(partials::add) }

        listener.javaClass.getMethod("onReadyForSpeech", Bundle::class.java).invoke(listener, null)
        assertEquals(SttState.Listening, service.state.value)
        listener.javaClass.getMethod("onEndOfSpeech").invoke(listener)
        assertEquals(SttState.Processing, service.state.value)
        listener.javaClass.getMethod("onPartialResults", Bundle::class.java).invoke(listener, bundle(" một phần "))
        job.join()

        assertEquals(SttResult.Partial("một phần"), partials.single())
        service.release()
    }

    @Test
    fun listenerMapsFinalEmptyAndErrors() = runTest {
        val service = SttService(ApplicationProvider.getApplicationContext<Context>())
        val listener = newListener(service)

        listener.javaClass.getMethod("onResults", Bundle::class.java).invoke(listener, bundle(" xong "))
        assertEquals(SttState.Idle, service.state.value)

        listener.javaClass.getMethod("onResults", Bundle::class.java).invoke(listener, bundle("   "))
        assertEquals(SttState.Error(SttErrorReason.NoMatch), service.state.value)

        listener.javaClass.getMethod("onError", Int::class.javaPrimitiveType).invoke(listener, SpeechRecognizer.ERROR_NETWORK)
        assertEquals(SttState.Error(SttErrorReason.Network), service.state.value)
        service.release()
    }

    private fun newListener(service: SttService): Any {
        val klass = Class.forName("com.example.eyes.infrastructure.system.SttService\$InternalListener")
        val ctor = klass.getDeclaredConstructor(SttService::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(service)
    }

    private fun bundle(text: String): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }
}
