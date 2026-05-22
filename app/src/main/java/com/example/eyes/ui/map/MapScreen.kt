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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.VoiceCommand
import org.koin.compose.koinInject

@Composable
fun MapScreen() {
    val dataStoreManager: DataStoreManager = koinInject()
    val command by dataStoreManager.lastVoiceCommandFlow.collectAsStateWithLifecycle(initialValue = null)
    var retainedDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = retainedDestination ?: (command as? VoiceCommand.Navigate)?.destination
    val title = destination?.let { "Dẫn đường đến $it" } ?: "Chuẩn bị lộ trình"
    val summary = destination
        ?.let { "Màn hình đang giữ chỗ cho tìm điểm đến $it, xem mốc định hướng và hướng dẫn từng chặng." }
        ?: "Trong giai đoạn này, màn hình đang giữ chỗ cho tìm điểm đến, xem mốc định hướng và hướng dẫn từng chặng."

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
            .semantics { contentDescription = "Màn hình bản đồ và dẫn đường" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Tiêu đề: $title"
            }
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Khu vực giữ chỗ cho bản đồ. Tính năng dẫn đường đang được hoàn thiện."
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Bản đồ sẽ hiển thị ở đây",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = "Bản đồ sẽ hiển thị ở đây"
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
