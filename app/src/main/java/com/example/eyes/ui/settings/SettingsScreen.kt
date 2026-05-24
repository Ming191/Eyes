package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val ttsSpeedLabel = "%.2f".format(state.ttsSpeed)
    val alertSensitivityPercent = (state.alertSensitivity * 100).toInt()
    val screenDescription = stringResource(R.string.settings_screen_description)
    val autoTranslateDescription = stringResource(R.string.settings_auto_translate_description)
    val autoTranslateSwitchDescription = stringResource(R.string.settings_auto_translate_switch_description)
    val previewButtonDescription = stringResource(R.string.settings_preview_button_description)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.settings_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingSliderCard(
            title = stringResource(R.string.settings_tts_speed_title),
            summary = stringResource(R.string.settings_tts_speed_summary),
            valueLabel = "${ttsSpeedLabel}x",
            contentDescription = stringResource(R.string.settings_tts_speed_slider_description, ttsSpeedLabel),
            sliderStateDescription = stringResource(R.string.settings_slider_state_description, ttsSpeedLabel),
            value = state.ttsSpeed,
            valueRange = 0.5f..2.0f,
            onValueChange = viewModel::setTtsSpeed
        )

        SettingSliderCard(
            title = stringResource(R.string.settings_alert_sensitivity_title),
            summary = stringResource(R.string.settings_alert_sensitivity_summary),
            valueLabel = "${alertSensitivityPercent}%",
            contentDescription = stringResource(R.string.settings_alert_sensitivity_slider_description, alertSensitivityPercent),
            sliderStateDescription = stringResource(R.string.settings_percent_state_description, alertSensitivityPercent),
            value = state.alertSensitivity,
            valueRange = 0f..1f,
            onValueChange = viewModel::setAlertSensitivity
        )

        val languageSectionDescription = stringResource(R.string.settings_language_section_description)
        Text(
            text = stringResource(R.string.settings_language_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                heading()
                contentDescription = languageSectionDescription
            }
        )
        val vietnameseDescription = stringResource(R.string.settings_select_vietnamese)
        val englishDescription = stringResource(R.string.settings_select_english)
        AppLanguage.entries.forEach { language ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription = if (language == AppLanguage.VI) vietnameseDescription else englishDescription
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.nativeLabel,
                    style = MaterialTheme.typography.bodyLarge
                )
                RadioButton(
                    selected = state.appLanguage == language,
                    onClick = { viewModel.setAppLanguage(language) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = autoTranslateDescription },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_auto_translate_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = state.autoTranslateEnglishOcrToVietnamese,
                onCheckedChange = viewModel::setAutoTranslateEnglishOcrToVietnamese,
                modifier = Modifier.semantics {
                    contentDescription = autoTranslateSwitchDescription
                }
            )
        }

        Button(
            onClick = { viewModel.previewFeedback(state) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .semantics {
                    contentDescription = previewButtonDescription
                }
        ) {
            Text(stringResource(R.string.settings_preview_button_label))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EyesTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.settings_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingSliderCard(
                title = stringResource(R.string.settings_tts_speed_title),
                summary = stringResource(R.string.settings_tts_speed_summary),
                valueLabel = "1.10x",
                contentDescription = stringResource(R.string.settings_tts_speed_slider_description, "1.10"),
                sliderStateDescription = stringResource(R.string.settings_slider_state_description, "1.10"),
                value = 1.1f,
                valueRange = 0.5f..2.0f,
                onValueChange = {}
            )
            SettingSliderCard(
                title = stringResource(R.string.settings_alert_sensitivity_title),
                summary = stringResource(R.string.settings_alert_sensitivity_summary),
                valueLabel = "60%",
                contentDescription = stringResource(R.string.settings_alert_sensitivity_slider_description, 60),
                sliderStateDescription = stringResource(R.string.settings_percent_state_description, 60),
                value = 0.6f,
                valueRange = 0f..1f,
                onValueChange = {}
            )
        }
    }
}
