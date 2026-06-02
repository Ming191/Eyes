package com.example.eyes.infrastructure.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eyes.domain.i18n.AppLanguage
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MiniLmSemanticVoiceCommandMatcherTest {
    @Test
    fun matchReturnsNullWhenModelFilesMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matcher = MiniLmSemanticVoiceCommandMatcher(context)

        assertNull(matcher.match("đọc văn bản", AppLanguage.VI))
    }
}
