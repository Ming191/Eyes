package com.example.eyes.infrastructure.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class HapticServiceTest {
    @Test
    fun feedbackMethodsDoNotCrashOnJvm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = HapticService(context)

        service.confirm()
        service.loading()
        service.error()
    }
}
