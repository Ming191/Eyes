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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.permission.PermissionScreen
import com.example.eyes.ui.theme.EyesTheme
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val summary: String
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnboardingScreen(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_gestures_title),
            summary = stringResource(R.string.onboarding_page_gestures_summary)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_permissions_title),
            summary = stringResource(R.string.onboarding_page_permissions_summary)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_page_ready_title),
            summary = stringResource(R.string.onboarding_page_ready_summary)
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    var permissionsGranted by remember { mutableStateOf(false) }
    val screenDescription = stringResource(R.string.onboarding_screen_description)
    val topBarDescription = stringResource(R.string.onboarding_top_bar_description)
    val topBarTitle = stringResource(R.string.onboarding_top_bar_title)
    val heroDescription = stringResource(R.string.onboarding_hero_description)
    val heroLabel = stringResource(R.string.onboarding_hero_label)
    val heroTitle = stringResource(R.string.onboarding_hero_title)
    val heroSummary = stringResource(R.string.onboarding_hero_summary)
    val languageSectionDescription = stringResource(R.string.onboarding_language_section_description)
    val languageTitle = stringResource(R.string.onboarding_language_title)
    val languageSummary = stringResource(R.string.onboarding_language_summary)
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
    val stepSpeech = listOf(
        stringResource(R.string.onboarding_step_language_speech),
        stringResource(R.string.onboarding_step_permissions_speech),
        stringResource(R.string.onboarding_step_gestures_speech)
    )

    Scaffold(
        modifier = Modifier.semantics { contentDescription = screenDescription },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                modifier = Modifier.semantics { contentDescription = topBarDescription }
            )
        },
        bottomBar = {
            OnboardingActions(
                currentPage = pagerState.currentPage,
                lastPage = pages.lastIndex,
                nextEnabled = pagerState.currentPage != 1 || permissionsGranted,
                backText = backText,
                nextText = nextText,
                startText = startText,
                backDescription = backDescription,
                backEnabledDescription = backEnabledDescription,
                backDisabledDescription = backDisabledDescription,
                nextDescription = nextDescription,
                startDescription = startDescription,
                onBack = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                onNextOrStart = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = heroDescription
                    }
                    .blindFocusable(
                        id = "onboarding_hero",
                        label = heroDescription,
                        onActivate = {}
                    ),
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = heroLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                .blindFocusable(
                    id = "onboarding_page_${pagerState.currentPage}",
                    label = stepSpeech[pagerState.currentPage],
                    onActivate = {},
                    actions = listOf(
                        BlindAction(label = nextDescription, onActivate = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pages.lastIndex))
                            }
                        }),
                        BlindAction(label = backDescription, onActivate = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        })
                    )
                )
        ) { page ->
            val item = pages[page]
            val pageSpeech = stepSpeech[page]
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = pageSpeech },
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
                        LanguageSelector(
                            selectedLanguage = selectedLanguage,
                            title = languageTitle,
                            summary = languageSummary,
                            sectionDescription = languageSectionDescription,
                            onLanguageSelected = onLanguageSelected
                        )
                    } else if (page == 1) {
                        PermissionScreen(
                            modifier = Modifier.fillMaxWidth(),
                            showContainer = false,
                            onAllPermissionsGrantedChange = { permissionsGranted = it }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: AppLanguage,
    title: String,
    summary: String,
    sectionDescription: String,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val selectedDescription = stringResource(R.string.nav_selected_description)
    val unselectedDescription = stringResource(R.string.nav_unselected_description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { contentDescription = sectionDescription },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppLanguage.entries.forEach { language ->
                val optionDescription = stringResource(R.string.onboarding_language_option_description, language.nativeLabel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selectedLanguage == language,
                            onClick = { onLanguageSelected(language) },
                            role = Role.RadioButton
                        )
                        .semantics {
                            contentDescription = optionDescription
                            stateDescription = if (selectedLanguage == language) {
                                selectedDescription
                            } else {
                                unselectedDescription
                            }
                        }
                        .blindFocusable(
                            id = "onboarding_language_${language.storageValue}",
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
                        onClick = null,
                        modifier = Modifier.semantics { contentDescription = optionDescription }
                    )
                }
            }
    }
}

@Composable
private fun OnboardingActions(
    currentPage: Int,
    lastPage: Int,
    nextEnabled: Boolean,
    backText: String,
    nextText: String,
    startText: String,
    backDescription: String,
    backEnabledDescription: String,
    backDisabledDescription: String,
    nextDescription: String,
    startDescription: String,
    onBack: () -> Unit,
    onNextOrStart: () -> Unit
) {
    val actionsDescription = stringResource(R.string.onboarding_actions_description)

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.semantics { contentDescription = actionsDescription }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = currentPage > 0,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = backDescription
                        stateDescription = if (currentPage > 0) backEnabledDescription else backDisabledDescription
                    }
                    .then(
                        if (currentPage > 0) {
                            Modifier.blindFocusable(
                                id = "onboarding_back",
                                label = backDescription,
                                onActivate = onBack
                            )
                        } else {
                            Modifier.clearAndSetSemantics { }
                        }
                    )
            ) {
                Text(backText)
            }

            Button(
                onClick = onNextOrStart,
                enabled = nextEnabled,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (currentPage == lastPage) startDescription else nextDescription
                    }
                    .then(
                        if (nextEnabled) {
                            Modifier.blindFocusable(
                                id = "onboarding_next_start",
                                label = if (currentPage == lastPage) startDescription else nextDescription,
                                onActivate = onNextOrStart
                            )
                        } else {
                            Modifier
                        }
                    ),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(if (currentPage == lastPage) startText else nextText)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingScreenPreview() {
    EyesTheme {
        OnboardingScreen(
            selectedLanguage = AppLanguage.VI,
            onLanguageSelected = {},
            onFinish = {}
        )
    }
}
