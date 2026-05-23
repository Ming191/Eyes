package com.example.eyes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.R
import com.example.eyes.ui.navigation.CameraMode
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenCamera: (CameraMode) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onActionSelected = { action ->
            when (action) {
                HomeActionType.ScanAround -> onOpenCamera(CameraMode.Navigation)
                HomeActionType.ReadText -> onOpenCamera(CameraMode.OCR)
                HomeActionType.IdentifyCurrency -> onOpenCamera(CameraMode.Currency)

                HomeActionType.Navigate -> onOpenMap()
                HomeActionType.Settings -> onOpenSettings()
            }
        }
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onActionSelected: (HomeActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenContentDescription = stringResource(R.string.home_screen_content_description)
    val shortcutsTitle = stringResource(R.string.home_shortcuts_title)
    val safetyTipContentDescription = stringResource(R.string.home_safety_tip_content_description)
    val safetyTipTitle = stringResource(R.string.home_safety_tip_title)
    val safetyTipBody = stringResource(R.string.home_safety_tip_body)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = screenContentDescription },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHeroCard(
                title = uiState.welcomeTitle,
                summary = uiState.welcomeSummary
            )
        }
        item {
            Text(
                text = shortcutsTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        }
        item {
            AdaptiveActionGrid(
                actions = uiState.actions,
                onActionSelected = onActionSelected
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = safetyTipContentDescription
                    },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = safetyTipTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = safetyTipBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    val heroLabel = stringResource(R.string.home_hero_label)
    val heroContentDescription = stringResource(R.string.home_hero_content_description, title, summary)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = heroContentDescription
            },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = heroLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AdaptiveActionGrid(
    actions: List<HomeAction>,
    onActionSelected: (HomeActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth < 560.dp) 1 else 2
        val rows = actions.chunked(columnCount)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        HomeActionCard(
                            action = action,
                            onClick = { onActionSelected(action.type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columnCount - rowActions.size) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    EyesTheme(dynamicColor = false) {
        HomeContent(
            uiState = HomeUiState(
                welcomeTitle = "Hỗ trợ di chuyển rõ ràng, ngắn gọn và an toàn",
                welcomeSummary = "Chọn một chế độ để quét lối đi, đọc văn bản, nhận diện tiền hoặc chuẩn bị lộ trình trước khi ra ngoài.",
                actions = emptyList(),
            ),
            onActionSelected = {}
        )
    }
}
