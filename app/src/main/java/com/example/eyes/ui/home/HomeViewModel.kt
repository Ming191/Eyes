package com.example.eyes.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.eyes.R
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeActionType {
    ScanAround,
    ReadText,
    IdentifyCurrency,
    Navigate,
    Settings
}

@Immutable
data class HomeAction(
    val type: HomeActionType,
    val title: String,
    val description: String,
    val supportingLabel: String,
    val accessibilityLabel: String
)

@Immutable
data class HomeUiState(
    val welcomeTitle: String,
    val welcomeSummary: String,
    val actions: List<HomeAction>,
)

private fun defaultHomeActions(context: Context): List<HomeAction> = listOf(
    HomeAction(
        type = HomeActionType.ScanAround,
        title = context.getString(R.string.home_action_scan_title),
        description = context.getString(R.string.home_action_scan_description),
        supportingLabel = context.getString(R.string.home_action_scan_supporting),
        accessibilityLabel = context.getString(R.string.home_action_scan_accessibility),
    ),
    HomeAction(
        type = HomeActionType.ReadText,
        title = context.getString(R.string.home_action_read_title),
        description = context.getString(R.string.home_action_read_description),
        supportingLabel = context.getString(R.string.home_action_read_supporting),
        accessibilityLabel = context.getString(R.string.home_action_read_accessibility),
    ),
    HomeAction(
        type = HomeActionType.IdentifyCurrency,
        title = context.getString(R.string.home_action_currency_title),
        description = context.getString(R.string.home_action_currency_description),
        supportingLabel = context.getString(R.string.home_action_currency_supporting),
        accessibilityLabel = context.getString(R.string.home_action_currency_accessibility),
    ),
    HomeAction(
        type = HomeActionType.Navigate,
        title = context.getString(R.string.home_action_navigate_title),
        description = context.getString(R.string.home_action_navigate_description),
        supportingLabel = context.getString(R.string.home_action_navigate_supporting),
        accessibilityLabel = context.getString(R.string.home_action_navigate_accessibility),
    ),
    HomeAction(
        type = HomeActionType.Settings,
        title = context.getString(R.string.home_action_settings_title),
        description = context.getString(R.string.home_action_settings_description),
        supportingLabel = context.getString(R.string.home_action_settings_supporting),
        accessibilityLabel = context.getString(R.string.home_action_settings_accessibility),
    ),
)

class HomeViewModel(
    private val tts: SpeechOutput,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            welcomeTitle = context.getString(R.string.home_welcome_title),
            welcomeSummary = context.getString(R.string.home_welcome_summary),
            actions = defaultHomeActions(context),
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasSpokenGreeting = false

    fun onScreenShown() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        tts.speak(context.getString(R.string.home_welcome_tts))
    }
}
