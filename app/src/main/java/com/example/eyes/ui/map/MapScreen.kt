package com.example.eyes.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.VoiceCommand
import org.koin.compose.koinInject

@Composable
fun MapScreen() {
    val dataStoreManager: DataStoreManager = koinInject()
    val command by dataStoreManager.lastVoiceCommandFlow.collectAsStateWithLifecycle(initialValue = null)
    var retainedDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = retainedDestination ?: (command as? VoiceCommand.Navigate)?.destination
    val title = destination?.let { stringResource(R.string.map_title_destination, it) } ?: stringResource(R.string.map_title_default)
    val summary = destination
        ?.let { stringResource(R.string.map_summary_destination, it) }
        ?: stringResource(R.string.map_summary_default)
    val screenDescription = stringResource(R.string.map_screen_description)
    val titleDescription = stringResource(R.string.map_title_description, title)
    val placeholderDescription = stringResource(R.string.map_placeholder_description)
    val placeholderTitle = stringResource(R.string.map_placeholder_title)

    LaunchedEffect(command) {
        val voiceDestination = (command as? VoiceCommand.Navigate)?.destination
        if (voiceDestination != null) {
            retainedDestination = voiceDestination
            dataStoreManager.clearLastVoiceCommand()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                contentDescription = titleDescription
            }
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = placeholderDescription
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = placeholderTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = placeholderTitle
                    }
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = summary
                    }
                )
            }
        }
    }
}
