package com.example.eyes.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class AndroidAudioRouteProviderTest {
    @Test
    fun isHeadsetConnectedReturnsFalseWhenNoLegacyRoutesEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(AndroidAudioRouteProvider(context).isHeadsetConnected())
    }

    @Test
    @Config(sdk = [35])
    fun isHeadsetConnectedReturnsFalseWhenNoModernOutputDevicesMatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(AndroidAudioRouteProvider(context).isHeadsetConnected())
    }
}
