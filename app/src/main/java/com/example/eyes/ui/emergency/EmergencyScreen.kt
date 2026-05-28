package com.example.eyes.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.ui.blind.LocalBlindFocusManager
import com.example.eyes.ui.blind.blindFocusable
import org.koin.compose.koinInject

@Composable
fun EmergencyScreen(
    appLanguage: AppLanguage,
    onBack: () -> Unit,
    speechOutput: SpeechOutput = koinInject(),
    localizedTextProvider: LocalizedTextProvider = koinInject()
) {
    val context = LocalContext.current
    val focusManager = LocalBlindFocusManager.current
    val screenDescription = stringResource(R.string.emergency_screen_description)
    val title = stringResource(R.string.emergency_screen_title)
    val cancelDescription = stringResource(R.string.emergency_cancel_description)
    val ttsText = localizedTextProvider.getString(R.string.emergency_screen_tts_short, appLanguage)

    fun openDialer(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", number, null)
        }
        runCatching { context.startActivity(intent) }
    }

    LaunchedEffect(appLanguage) {
        speechOutput.stop()
        speechOutput.speak(ttsText, appLanguage.ttsLocale)
        focusManager?.focusItem(EMERGENCY_FIRST_BUTTON_ID)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        EmergencyNumberButton(
            id = EMERGENCY_FIRST_BUTTON_ID,
            label = stringResource(R.string.emergency_police),
            description = stringResource(R.string.emergency_police_description),
            activateLabel = stringResource(R.string.emergency_open_dialer_113),
            onClick = { openDialer("113") }
        )
        EmergencyNumberButton(
            id = "emergency_114",
            label = stringResource(R.string.emergency_fire),
            description = stringResource(R.string.emergency_fire_description),
            activateLabel = stringResource(R.string.emergency_open_dialer_114),
            onClick = { openDialer("114") }
        )
        EmergencyNumberButton(
            id = "emergency_115",
            label = stringResource(R.string.emergency_medical),
            description = stringResource(R.string.emergency_medical_description),
            activateLabel = stringResource(R.string.emergency_open_dialer_115),
            onClick = { openDialer("115") }
        )
        EmergencyNumberButton(
            id = "emergency_112",
            label = stringResource(R.string.emergency_general),
            description = stringResource(R.string.emergency_general_description),
            activateLabel = stringResource(R.string.emergency_open_dialer_112),
            onClick = { openDialer("112") }
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = cancelDescription }
                .blindFocusable(
                    id = "emergency_back",
                    label = cancelDescription,
                    activateLabel = cancelDescription,
                    onActivate = onBack
                )
        ) {
            Text(stringResource(R.string.emergency_cancel))
        }
    }
}

@Composable
private fun EmergencyNumberButton(
    id: String,
    label: String,
    description: String,
    activateLabel: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { contentDescription = description }
            .blindFocusable(
                id = id,
                label = description,
                activateLabel = activateLabel,
                onActivate = onClick
            )
    ) {
        Text(label)
    }
}

private const val EMERGENCY_FIRST_BUTTON_ID = "emergency_113"
