package com.example.eyes.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.system.SttErrorReason
import com.example.eyes.system.SttState
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoiceCommandScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateBackHome: () -> Unit,
    viewModel: VoiceCommandViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }

    fun requestMicrophoneOrStart() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-start STT when the screen first appears.
    LaunchedEffect(Unit) {
        requestMicrophoneOrStart()
    }

    // Consume one-shot navigation events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { target ->
            when (target) {
                VoiceNavigationTarget.Camera -> onNavigateToCamera()
                VoiceNavigationTarget.Home -> onNavigateBackHome()
            }
        }
    }

    VoiceCommandContent(
        state = state,
        onMicTap = ::requestMicrophoneOrStart,
        onStopTap = viewModel::stopListening
    )
}

@Composable
private fun VoiceCommandContent(
    state: VoiceCommandUiState,
    onMicTap: () -> Unit,
    onStopTap: () -> Unit
) {
    val scrollState = rememberScrollState()
    val screenDescription = stringResource(R.string.voice_screen_description)
    val title = stringResource(R.string.voice_title)
    val titleDescription = stringResource(R.string.voice_title_description)
    val instruction = stringResource(R.string.voice_instruction)
    val instructionDescription = stringResource(R.string.voice_instruction_description)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    heading()
                    contentDescription = titleDescription
                }
                .blindFocusable(
                    id = "voice_title",
                    label = titleDescription,
                    onActivate = {}
                )
        )

        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                contentDescription = instructionDescription
            }
        )

        StatusBlock(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        MicButton(
            sttState = state.sttState,
            onMicTap = onMicTap,
            onStopTap = onStopTap
        )

        Spacer(modifier = Modifier.height(8.dp))

        AvailableCommandsCard(expanded = state.helpExpanded)
    }
}

@Composable
private fun StatusBlock(state: VoiceCommandUiState) {
    val statusText = statusLabelFor(state.sttState)
    val statusDescription = stringResource(R.string.voice_status_description, statusText)
    val currentStatusDescription = stringResource(R.string.voice_current_status_description, statusText)
    val partialDescription = stringResource(R.string.voice_partial_description, state.partialText)
    val finalText = stringResource(R.string.voice_final_text, state.finalText)
    val finalDescription = stringResource(R.string.voice_final_description, state.finalText)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = statusDescription
                liveRegion = LiveRegionMode.Polite
            }
            .blindFocusable(
                id = "voice_status",
                label = statusDescription,
                onActivate = {}
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = currentStatusDescription
                }
            )

            if (state.partialText.isNotEmpty()) {
                Text(
                    text = "\"${state.partialText}...\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = partialDescription
                    }
                )
            }

            if (state.finalText.isNotEmpty()) {
                Text(
                    text = finalText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = finalDescription
                    }
                )
            }

            state.lastCommand?.let { command ->
                val commandLabel = commandLabelFor(command)
                val commandText = stringResource(R.string.voice_command_text, commandLabel)
                val commandDescription = stringResource(R.string.voice_command_description, commandLabel)
                Text(
                    text = commandText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        contentDescription = commandDescription
                    }
                )
            }
        }
    }
}

@Composable
private fun MicButton(
    sttState: SttState,
    onMicTap: () -> Unit,
    onStopTap: () -> Unit
) {
    val isListening = sttState is SttState.Listening
    val isProcessing = sttState is SttState.Processing

    val containerColor = when {
        isListening -> MaterialTheme.colorScheme.errorContainer
        isProcessing -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isListening -> MaterialTheme.colorScheme.onErrorContainer
        isProcessing -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val description = when (sttState) {
        SttState.Idle -> stringResource(R.string.voice_mic_idle_description)
        SttState.Listening -> stringResource(R.string.voice_mic_listening_description)
        SttState.Processing -> stringResource(R.string.voice_mic_processing_description)
        is SttState.Error -> stringResource(R.string.voice_mic_error_description)
    }

    Button(
        onClick = {
            when (sttState) {
                SttState.Listening -> onStopTap()
                SttState.Processing -> Unit
                else -> onMicTap()
            }
        },
        enabled = !isProcessing,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = CircleShape,
        modifier = Modifier
            .size(160.dp)
            .semantics { contentDescription = description }
            .blindFocusable(
                id = "voice_mic_button",
                label = description,
                onActivate = {
                    when (sttState) {
                        SttState.Listening -> onStopTap()
                        SttState.Processing -> Unit
                        else -> onMicTap()
                    }
                }
            )
    ) {
        Icon(
            imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun AvailableCommandsCard(expanded: Boolean) {
    val description = stringResource(R.string.voice_commands_description)
    val title = stringResource(R.string.voice_commands_title)
    val titleDescription = stringResource(R.string.voice_commands_title_description)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = description
            }
            .blindFocusable(
                id = "voice_commands",
                label = description,
                onActivate = {}
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = titleDescription
                }
            )
            CommandLine(stringResource(R.string.voice_command_read_example), stringResource(R.string.voice_command_read_action))
            CommandLine(stringResource(R.string.voice_command_scene_example), stringResource(R.string.voice_command_scene_action))
            CommandLine(stringResource(R.string.voice_command_money_example), stringResource(R.string.voice_command_money_action))
            CommandLine(stringResource(R.string.voice_command_repeat_example), stringResource(R.string.voice_command_repeat_action))
            CommandLine(stringResource(R.string.voice_command_stop_example), stringResource(R.string.voice_command_stop_action))
            CommandLine(stringResource(R.string.voice_command_help_example), stringResource(R.string.voice_command_help_action))
        }
    }
}

@Composable
private fun CommandLine(command: String, action: String) {
    val text = stringResource(R.string.voice_command_line_text, command, action)
    val description = stringResource(R.string.voice_command_line_description, command, action)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.semantics {
            contentDescription = description
        }
    )
}

@Composable
private fun statusLabelFor(state: SttState): String = when (state) {
    SttState.Idle -> stringResource(R.string.voice_status_idle)
    SttState.Listening -> stringResource(R.string.voice_status_listening)
    SttState.Processing -> stringResource(R.string.voice_status_processing)
    is SttState.Error -> when (state.reason) {
        SttErrorReason.Network -> stringResource(R.string.voice_status_error_network)
        SttErrorReason.NoMatch -> stringResource(R.string.voice_status_error_no_match)
        SttErrorReason.Audio -> stringResource(R.string.voice_status_error_audio)
        SttErrorReason.PermissionDenied -> stringResource(R.string.voice_status_error_permission)
        SttErrorReason.NotAvailable -> stringResource(R.string.voice_status_error_not_available)
        is SttErrorReason.Unknown -> stringResource(R.string.voice_status_error_unknown)
    }
}

@Composable
private fun commandLabelFor(command: VoiceCommand): String = when (command) {
    VoiceCommand.ReadText -> stringResource(R.string.voice_label_read_text)
    VoiceCommand.DescribeScene -> stringResource(R.string.voice_label_describe_scene)
    VoiceCommand.RecognizeCurrency -> stringResource(R.string.voice_label_recognize_currency)
    VoiceCommand.Repeat -> stringResource(R.string.voice_label_repeat)
    VoiceCommand.Stop -> stringResource(R.string.voice_label_stop)
    VoiceCommand.Help -> stringResource(R.string.voice_label_help)
    is VoiceCommand.Unknown -> stringResource(R.string.voice_label_unknown, command.rawText)
}

@Preview(showBackground = true)
@Composable
private fun VoiceCommandScreenPreview() {
    EyesTheme(dynamicColor = false) {
        VoiceCommandContent(
            state = VoiceCommandUiState(
                sttState = SttState.Idle,
                finalText = stringResource(R.string.voice_preview_final_text),
                lastCommand = VoiceCommand.ReadText
            ),
            onMicTap = {},
            onStopTap = {}
        )
    }
}
