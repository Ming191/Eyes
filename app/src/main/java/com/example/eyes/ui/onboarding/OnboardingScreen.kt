package com.example.eyes.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.eyes.R
import com.example.eyes.ui.permission.PermissionScreen
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val summary: String,
    val accessibilityLabel: String
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_permissions_title),
            summary = stringResource(R.string.onboarding_page_permissions_summary),
            accessibilityLabel = stringResource(R.string.onboarding_page_permissions_description)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_gestures_title),
            summary = stringResource(R.string.onboarding_page_gestures_summary),
            accessibilityLabel = stringResource(R.string.onboarding_page_gestures_description)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_ready_title),
            summary = stringResource(R.string.onboarding_page_ready_summary),
            accessibilityLabel = stringResource(R.string.onboarding_page_ready_description)
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val screenDescription = stringResource(R.string.onboarding_screen_description)
    val heroDescription = stringResource(R.string.onboarding_hero_description)
    val heroLabel = stringResource(R.string.onboarding_hero_label)
    val heroTitle = stringResource(R.string.onboarding_hero_title)
    val heroSummary = stringResource(R.string.onboarding_hero_summary)
    val pagerDescription = stringResource(R.string.onboarding_pager_description)
    val pagerStateDescription = stringResource(R.string.onboarding_pager_state_description, pagerState.currentPage + 1, pages.size)
    val backDescription = stringResource(R.string.onboarding_back_description)
    val backEnabledDescription = stringResource(R.string.onboarding_back_enabled_description)
    val backDisabledDescription = stringResource(R.string.onboarding_back_disabled_description)
    val backText = stringResource(R.string.onboarding_back_text)
    val startDescription = stringResource(R.string.onboarding_start_description)
    val nextDescription = stringResource(R.string.onboarding_next_description)
    val startText = stringResource(R.string.onboarding_start_text)
    val nextText = stringResource(R.string.onboarding_next_text)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = screenDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = heroDescription
                },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = heroLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = heroTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = heroSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics {
                    contentDescription = pagerDescription
                    stateDescription = pagerStateDescription
                    liveRegion = LiveRegionMode.Polite
                }
        ) { page ->
            val item = pages[page]
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = item.accessibilityLabel },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_step_label, page + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (page == 0) {
                        PermissionScreen(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription = backDescription
                        stateDescription = if (pagerState.currentPage > 0) {
                            backEnabledDescription
                        } else {
                            backDisabledDescription
                        }
                    }
            ) {
                Text(backText)
            }

            Button(
                onClick = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = if (pagerState.currentPage == pages.lastIndex) {
                            startDescription
                        } else {
                            nextDescription
                        }
                    },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(if (pagerState.currentPage == pages.lastIndex) startText else nextText)
            }
        }
    }
}
