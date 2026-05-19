package com.example.eyes.ui.map

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.eyes.map.MapUiState
import com.example.eyes.map.UserLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MapScreenSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun permissionRequiredState_hasPermissionAction() {
        // GIVEN / WHEN
        composeTestRule.setContent {
            MaterialTheme {
                MapScreenContent(
                    uiState = MapUiState.LocationPermissionRequired,
                    hasLocationPermission = false,
                    onRequestLocationPermission = {},
                    onRetryLocation = {},
                    mapContent = ::FakeMapContent
                )
            }
        }

        // THEN
        composeTestRule.onNodeWithContentDescription(
            "Trạng thái bản đồ: cần quyền vị trí để mở bản đồ."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "Nút cấp quyền vị trí chính xác và vị trí gần đúng"
        ).assertHasClickAction()
    }

    @Test
    fun locatingState_showsLoadingStatusAndMapArea() {
        // GIVEN / WHEN
        composeTestRule.setContent {
            MaterialTheme {
                MapScreenContent(
                    uiState = MapUiState.Locating,
                    hasLocationPermission = true,
                    onRequestLocationPermission = {},
                    onRetryLocation = {},
                    mapContent = ::FakeMapContent
                )
            }
        }

        // THEN
        composeTestRule.onNodeWithContentDescription(
            "Trạng thái bản đồ: đang lấy vị trí hiện tại."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Bản đồ kiểm thử")
            .assertIsDisplayed()
    }

    @Test
    fun readyState_passesLocationToMapContent() {
        // GIVEN
        val location = UserLocation(latitude = 10.7769, longitude = 106.7009)
        var renderedLocation: UserLocation? = null

        // WHEN
        composeTestRule.setContent {
            MaterialTheme {
                MapScreenContent(
                    uiState = MapUiState.Ready(location),
                    hasLocationPermission = true,
                    onRequestLocationPermission = {},
                    onRetryLocation = {},
                    mapContent = { currentLocation, _, modifier ->
                        renderedLocation = currentLocation
                        FakeMapContent(currentLocation, true, modifier)
                    }
                )
            }
        }

        // THEN
        composeTestRule.onNodeWithText("Bản đồ kiểm thử có vị trí")
            .assertIsDisplayed()
        assertEquals(location, renderedLocation)
    }
}

@Composable
private fun FakeMapContent(
    location: UserLocation?,
    locationEnabled: Boolean,
    modifier: Modifier
) {
    val text = if (location != null && locationEnabled) {
        "Bản đồ kiểm thử có vị trí"
    } else {
        "Bản đồ kiểm thử đang chờ vị trí"
    }

    Text(
        text = text,
        modifier = modifier.semantics {
            contentDescription = "Bản đồ kiểm thử"
        }
    )
}
