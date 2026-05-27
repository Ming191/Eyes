package com.example.eyes.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.application.home.HomeActionKind
import com.example.eyes.application.home.HomeActionState
import com.example.eyes.application.home.HomeState
import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeActionType {
    ReadTextQuick,
    DescribeScene,
    DetectObjects,
    RecognizeCurrency,
    EmergencyCall,
    Voice,
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
    val welcomeTitle: String = "",
    val welcomeSummary: String = "",
    val actions: List<HomeAction> = emptyList()
)

class HomeViewModel(
    private val buildHomeState: BuildHomeStateUseCase,
    private val announceHomeGreeting: AnnounceHomeGreetingUseCase,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(buildHomeState(AppLanguage.VI).toUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasSpokenGreeting = false
    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingGreeting = false

    init {
        settingsRepository?.let { repository ->
            viewModelScope.launch {
                repository.appLanguageFlow.collect { language ->
                    appLanguage = language
                    isAppLanguageLoaded = true
                    _uiState.update { buildHomeState(language).toUiState() }
                    if (pendingGreeting) {
                        pendingGreeting = false
                        speakGreetingIfNeeded()
                    }
                }
            }
        }
    }

    fun onScreenShown() {
        if (settingsRepository != null && !isAppLanguageLoaded) {
            pendingGreeting = true
            return
        }
        speakGreetingIfNeeded()
    }

    private fun speakGreetingIfNeeded() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        announceHomeGreeting.invoke(appLanguage)
    }

    private fun HomeState.toUiState(): HomeUiState = HomeUiState(
        welcomeTitle = welcomeTitle,
        welcomeSummary = welcomeSummary,
        actions = actions.map { it.toUiAction() }
    )

    private fun HomeActionState.toUiAction(): HomeAction = HomeAction(
        type = kind.toUiType(),
        title = title,
        description = description,
        supportingLabel = supportingLabel,
        accessibilityLabel = accessibilityLabel
    )

    private fun HomeActionKind.toUiType(): HomeActionType = when (this) {
        HomeActionKind.ReadTextQuick -> HomeActionType.ReadTextQuick
        HomeActionKind.DescribeScene -> HomeActionType.DescribeScene
        HomeActionKind.DetectObjects -> HomeActionType.DetectObjects
        HomeActionKind.RecognizeCurrency -> HomeActionType.RecognizeCurrency
        HomeActionKind.EmergencyCall -> HomeActionType.EmergencyCall
        HomeActionKind.Voice -> HomeActionType.Voice
    }
}
