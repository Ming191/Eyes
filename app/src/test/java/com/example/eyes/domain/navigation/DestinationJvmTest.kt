package com.example.eyes.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationJvmTest {

    @Test
    fun valuesRemainInExpectedOrder() {
        // WHEN
        val values = Destination.values().toList()

        // THEN
        assertEquals(
            listOf(
                Destination.HOME,
                Destination.CAMERA,
                Destination.SETTINGS,
            ),
            values,
        )
    }

    @Test
    fun valueOf_returnsExpectedDestination() {
        assertEquals(Destination.HOME, Destination.valueOf("HOME"))
        assertEquals(Destination.CAMERA, Destination.valueOf("CAMERA"))
        assertEquals(Destination.SETTINGS, Destination.valueOf("SETTINGS"))
    }
}
