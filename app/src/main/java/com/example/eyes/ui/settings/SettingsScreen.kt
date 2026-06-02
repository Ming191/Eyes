package com.example.eyes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val ttsSpeed = state.ttsSpeed.snapToTtsSpeedStep()
    val ttsSpeedLabel = "%.2f".format(ttsSpeed)
    val screenDescription = stringResource(R.string.settings_screen_description)
    val autoTranslateDescription = stringResource(R.string.settings_auto_translate_description)
    val autoTranslateSwitchDescription = stringResource(R.string.settings_auto_translate_switch_description)
    val increaseLabel = stringResource(R.string.blind_action_increase)
    val decreaseLabel = stringResource(R.string.blind_action_decrease)
    val ttsSpeedDecreaseDescription = stringResource(R.string.settings_tts_speed_decrease_description)
    val ttsSpeedIncreaseDescription = stringResource(R.string.settings_tts_speed_increase_description)
    val decreaseTtsSpeed = viewModel::selectPreviousTtsSpeedAndAnnounce
    val increaseTtsSpeed = viewModel::selectNextTtsSpeedAndAnnounce
    val ttsSpeedPresets = TTS_SPEED_PRESET_VALUES.map { preset ->
        SettingSpeedPreset(
            focusId = "settings_tts_speed_preset_${preset.idSuffix}",
            label = "${preset.label}x",
            contentDescription = stringResource(R.string.settings_tts_speed_preset_description, preset.label),
            selected = ttsSpeed == preset.speed,
            onSelect = { viewModel.setTtsSpeedAndAnnounce(preset.speed) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingSpeedCard(
            title = stringResource(R.string.settings_tts_speed_title),
            valueLabel = "${ttsSpeedLabel}x",
            contentDescription = stringResource(R.string.settings_tts_speed_selector_description, ttsSpeedLabel),
            sliderContentDescription = stringResource(R.string.settings_tts_speed_slider_description, ttsSpeedLabel),
            sliderStateDescription = stringResource(R.string.settings_slider_state_description, ttsSpeedLabel),
            sliderFocusId = "settings_tts_speed_slider",
            sliderAdjustStartDescription = stringResource(R.string.settings_tts_speed_slider_adjust_start),
            value = ttsSpeed,
            valueRange = TTS_SPEED_RANGE,
            steps = TTS_SPEED_SLIDER_STEPS,
            onValueChange = { viewModel.setTtsSpeed(it.snapToTtsSpeedStep()) },
            onValueChangeFinished = viewModel::announceCurrentTtsSpeed,
            onDecrease = decreaseTtsSpeed,
            onIncrease = increaseTtsSpeed,
            decreaseFocusId = "settings_tts_speed_decrease",
            increaseFocusId = "settings_tts_speed_increase",
            decreaseContentDescription = ttsSpeedDecreaseDescription,
            increaseContentDescription = ttsSpeedIncreaseDescription,
            canDecrease = ttsSpeed > TTS_SPEED_MIN,
            canIncrease = ttsSpeed < TTS_SPEED_MAX,
            presets = ttsSpeedPresets,
            modifier = Modifier.blindFocusable(
                id = "settings_tts_speed",
                label = stringResource(R.string.settings_tts_speed_selector_description, ttsSpeedLabel),
                onActivate = {},
                actions = listOf(
                    BlindAction(label = increaseLabel, onActivate = increaseTtsSpeed),
                    BlindAction(label = decreaseLabel, onActivate = decreaseTtsSpeed)
                )
            )
        )

        val vietnameseDescription = stringResource(R.string.settings_select_vietnamese)
        val englishDescription = stringResource(R.string.settings_select_english)
        LanguageCard(
            selectedLanguage = state.appLanguage,
            autoTranslateEnabled = state.autoTranslateEnglishOcrToVietnamese,
            vietnameseDescription = vietnameseDescription,
            englishDescription = englishDescription,
            autoTranslateDescription = autoTranslateDescription,
            autoTranslateSwitchDescription = autoTranslateSwitchDescription,
            onAutoTranslateChanged = viewModel::setAutoTranslateEnglishOcrToVietnamese,
            onLanguageSelected = viewModel::setAppLanguage
        )
    }
}

@Composable
private fun LanguageCard(
    selectedLanguage: AppLanguage,
    autoTranslateEnabled: Boolean,
    vietnameseDescription: String,
    englishDescription: String,
    autoTranslateDescription: String,
    autoTranslateSwitchDescription: String,
    onAutoTranslateChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val languageSectionDescription = stringResource(R.string.settings_language_section_description)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = languageSectionDescription },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = languageSectionDescription
                }
            )
            AppLanguage.entries.forEach { language ->
                val optionDescription = if (language == AppLanguage.VI) vietnameseDescription else englishDescription

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onLanguageSelected(language) }
                        .semantics { contentDescription = optionDescription }
                        .blindFocusable(
                            id = "settings_language_${language.storageValue}",
                            label = optionDescription,
                            onActivate = { onLanguageSelected(language) }
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language.nativeLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = selectedLanguage == language,
                        onClick = { onLanguageSelected(language) },
                        modifier = Modifier.semantics {
                            contentDescription = optionDescription
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = autoTranslateDescription }
                    .blindFocusable(
                        id = "settings_auto_translate",
                        label = autoTranslateDescription,
                        onActivate = { onAutoTranslateChanged(!autoTranslateEnabled) }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_auto_translate_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = autoTranslateEnabled,
                    onCheckedChange = onAutoTranslateChanged,
                    modifier = Modifier.semantics {
                        contentDescription = autoTranslateSwitchDescription
                    }
                )
            }
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
            SettingSpeedCard(
                title = stringResource(R.string.settings_tts_speed_title),
                valueLabel = "1.00x",
                contentDescription = stringResource(R.string.settings_tts_speed_selector_description, "1.00"),
                sliderContentDescription = stringResource(R.string.settings_tts_speed_slider_description, "1.00"),
                sliderStateDescription = stringResource(R.string.settings_slider_state_description, "1.00"),
                sliderFocusId = "preview_tts_speed_slider",
                sliderAdjustStartDescription = stringResource(R.string.settings_tts_speed_slider_adjust_start),
                value = 1.0f,
                valueRange = TTS_SPEED_RANGE,
                steps = TTS_SPEED_SLIDER_STEPS,
                onValueChange = {},
                onValueChangeFinished = {},
                onDecrease = {},
                onIncrease = {},
                decreaseFocusId = "preview_tts_speed_decrease",
                increaseFocusId = "preview_tts_speed_increase",
                decreaseContentDescription = stringResource(R.string.settings_tts_speed_decrease_description),
                increaseContentDescription = stringResource(R.string.settings_tts_speed_increase_description),
                canDecrease = true,
                canIncrease = true,
                presets = TTS_SPEED_PRESET_VALUES.map { preset ->
                    SettingSpeedPreset(
                        focusId = "preview_tts_speed_preset_${preset.idSuffix}",
                        label = "${preset.label}x",
                        contentDescription = stringResource(R.string.settings_tts_speed_preset_description, preset.label),
                        selected = preset.speed == 1.0f,
                        onSelect = {}
                    )
                }
            )
            LanguageCard(
                selectedLanguage = AppLanguage.VI,
                autoTranslateEnabled = true,
                vietnameseDescription = stringResource(R.string.settings_select_vietnamese),
                englishDescription = stringResource(R.string.settings_select_english),
                autoTranslateDescription = stringResource(R.string.settings_auto_translate_description),
                autoTranslateSwitchDescription = stringResource(R.string.settings_auto_translate_switch_description),
                onAutoTranslateChanged = {},
                onLanguageSelected = {}
            )
        }
    }
}
