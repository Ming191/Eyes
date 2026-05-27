package com.example.eyes.ui.blind

import androidx.compose.ui.geometry.Rect
import com.example.eyes.domain.speech.SpeechOutput
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BlindFocusManagerTest {

    private val speechOutput = FakeSpeechOutput()
    private val manager = BlindFocusManager(
        speechOutput = speechOutput,
        localeProvider = { Locale("vi", "VN") },
        noActionsLabelProvider = { "Không có hành động" }
    )

    @Test
    fun routeChangeFocusesSelectedBottomNavAndSkipsPreviousRouteItems() {
        // GIVEN
        manager.registerOrUpdate(item(id = "bottom_nav_HOME", routeKey = BlindFocusManager.GLOBAL_ROUTE_KEY, top = 900f))
        manager.registerOrUpdate(item(id = "bottom_nav_CAMERA", routeKey = BlindFocusManager.GLOBAL_ROUTE_KEY, top = 900f, left = 100f))
        manager.registerOrUpdate(item(id = "camera_item", routeKey = CAMERA_ROUTE, top = 10f))
        manager.registerOrUpdate(item(id = "home_item", routeKey = HOME_ROUTE, top = 10f))

        manager.setActiveRoute(CAMERA_ROUTE)
        manager.focusItem("camera_item", CAMERA_ROUTE)

        // WHEN
        manager.setActiveRoute(HOME_ROUTE)
        manager.focusItem("bottom_nav_HOME")

        // THEN
        assertEquals(bottomHomeBounds, manager.focusedBounds)
        assertEquals(listOf("Mục camera", "Trang chủ"), speechOutput.spokenTexts)
    }

    @Test
    fun focusNextOnlyCyclesGlobalAndActiveRouteItems() {
        // GIVEN
        manager.registerOrUpdate(item(id = "bottom_nav_HOME", routeKey = BlindFocusManager.GLOBAL_ROUTE_KEY, top = 900f))
        manager.registerOrUpdate(item(id = "bottom_nav_CAMERA", routeKey = BlindFocusManager.GLOBAL_ROUTE_KEY, top = 900f, left = 100f))
        manager.registerOrUpdate(item(id = "camera_item", routeKey = CAMERA_ROUTE, top = 10f))
        manager.registerOrUpdate(item(id = "settings_item", routeKey = SETTINGS_ROUTE, top = 20f))

        // WHEN
        manager.setActiveRoute(CAMERA_ROUTE)
        manager.focusNext()
        manager.focusNext()
        manager.focusNext()
        manager.focusNext()

        // THEN
        assertEquals(cameraBounds, manager.focusedBounds)
        assertEquals(
            listOf("Mục camera", "Trang chủ", "Camera", "Mục camera"),
            speechOutput.spokenTexts
        )
    }

    @Test
    fun focusedItemSurvivesResortAfterBoundsUpdate() {
        // GIVEN
        manager.setActiveRoute(CAMERA_ROUTE)
        manager.registerOrUpdate(item(id = "camera_mode_OBJECT_DETECTION", routeKey = CAMERA_ROUTE, top = 10f))
        manager.registerOrUpdate(item(id = "camera_mode_CURRENCY", routeKey = CAMERA_ROUTE, top = 20f))
        manager.focusItem("camera_mode_OBJECT_DETECTION", CAMERA_ROUTE)

        // WHEN bounds update moves focused item after currency in sorted order.
        manager.registerOrUpdate(item(id = "camera_mode_OBJECT_DETECTION", routeKey = CAMERA_ROUTE, top = 30f))

        // THEN focus stays on object button, not currency.
        assertEquals(objectModeMovedBounds, manager.focusedBounds)
        assertEquals(listOf("Nhận diện vật cản"), speechOutput.spokenTexts)
    }

    @Test
    fun activateFocusedKeepsSameItemWhenCallbackUpdatesFocusedItem() {
        // GIVEN
        manager.setActiveRoute(CAMERA_ROUTE)
        manager.registerOrUpdate(
            item(
                id = "camera_mode_CURRENCY",
                routeKey = CAMERA_ROUTE,
                top = 20f,
                onActivate = {
                    manager.registerOrUpdate(item(id = "camera_mode_CURRENCY", routeKey = CAMERA_ROUTE, top = 30f))
                }
            )
        )
        manager.registerOrUpdate(item(id = "camera_mode_OBJECT_DETECTION", routeKey = CAMERA_ROUTE, top = 10f))
        manager.focusItem("camera_mode_CURRENCY", CAMERA_ROUTE)

        // WHEN
        manager.activateFocused()

        // THEN focus stays on money button, not object or home.
        assertEquals(currencyModeMovedBounds, manager.focusedBounds)
        assertEquals(listOf("Nhận diện tiền", "Nhận diện tiền"), speechOutput.spokenTexts)
    }

    private fun item(
        id: String,
        routeKey: String,
        top: Float,
        left: Float = 0f,
        onActivate: () -> Unit = {}
    ): BlindFocusItem {
        val bounds = Rect(left = left, top = top, right = left + 50f, bottom = top + 50f)
        val label = when (id) {
            "bottom_nav_HOME" -> "Trang chủ"
            "bottom_nav_CAMERA" -> "Camera"
            "camera_item" -> "Mục camera"
            "home_item" -> "Mục trang chủ"
            "settings_item" -> "Mục cài đặt"
            "camera_mode_OBJECT_DETECTION" -> "Nhận diện vật cản"
            "camera_mode_CURRENCY" -> "Nhận diện tiền"
            else -> id
        }
        return BlindFocusItem(
            id = id,
            routeKey = routeKey,
            label = label,
            bounds = bounds,
            onActivate = onActivate
        )
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spokenTexts = mutableListOf<String>()

        override fun speak(text: String) {
            spokenTexts += text
        }

        override fun speak(text: String, locale: Locale) {
            spokenTexts += text
        }
    }

    private companion object {
        const val HOME_ROUTE = "home"
        const val CAMERA_ROUTE = "camera"
        const val SETTINGS_ROUTE = "settings"

        val bottomHomeBounds = Rect(left = 0f, top = 900f, right = 50f, bottom = 950f)
        val cameraBounds = Rect(left = 0f, top = 10f, right = 50f, bottom = 60f)
        val objectModeMovedBounds = Rect(left = 0f, top = 30f, right = 50f, bottom = 80f)
        val currencyModeMovedBounds = Rect(left = 0f, top = 30f, right = 50f, bottom = 80f)
    }
}
