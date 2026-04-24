package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics { contentDescription = "Màn hình cài đặt" },
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Cài đặt",
            style = MaterialTheme.typography.headlineSmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Tốc độ đọc: ${"%.2f".format(state.ttsSpeed)}")
            Slider(
                value = state.ttsSpeed,
                onValueChange = viewModel::setTtsSpeed,
                valueRange = 0.5f..2.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Thanh trượt tốc độ đọc" }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Độ nhạy cảnh báo: ${"%.2f".format(state.alertSensitivity)}")
            Slider(
                value = state.alertSensitivity,
                onValueChange = viewModel::setAlertSensitivity,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Thanh trượt độ nhạy cảnh báo" }
            )
        }
    }
}
