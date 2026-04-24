package com.example.eyes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val ttsSpeed: Float = 1.0f,
    val alertSensitivity: Float = 0.5f
)

class SettingsViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStoreManager.ttsSpeedFlow,
        dataStoreManager.alertSensitivityFlow
    ) { ttsSpeed, alertSensitivity ->
        SettingsUiState(
            ttsSpeed = ttsSpeed,
            alertSensitivity = alertSensitivity
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setTtsSpeed(value: Float) {
        viewModelScope.launch {
            dataStoreManager.setTtsSpeed(value)
        }
    }

    fun setAlertSensitivity(value: Float) {
        viewModelScope.launch {
            dataStoreManager.setAlertSensitivity(value)
        }
    }
}
