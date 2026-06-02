package com.example.eyes.infrastructure.system

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsServicePendingUtteranceTest {
    @Test
    fun pendingUtteranceDataClassMembersWorkViaReflection() {
        val type = Class.forName("com.example.eyes.infrastructure.system.TtsService\$PendingUtterance")
        val constructor = type.declaredConstructors.single { it.parameterTypes.size == 4 }
        constructor.isAccessible = true
        val completion = CompletableDeferred<Unit>()
        val first = constructor.newInstance("hello", Locale.US, 7L, completion)
        val same = constructor.newInstance("hello", Locale.US, 7L, completion)
        val other = constructor.newInstance("bye", Locale.US, 8L, null)

        assertEquals(first, same)
        assertNotEquals(first, other)
        assertEquals(first.hashCode(), same.hashCode())
        assertTrue(first.toString().contains("hello"))
    }
}
