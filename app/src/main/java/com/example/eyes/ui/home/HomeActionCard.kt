package com.example.eyes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.eyes.R
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.theme.EyesTheme

@Composable
fun HomeActionCard(
    action: HomeAction,
    onClick: () -> Unit,
    secondaryActions: List<BlindAction> = emptyList(),
    modifier: Modifier = Modifier
) {
    val openLabel = stringResource(R.string.home_action_open_label, action.title.lowercase())
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(
                onClickLabel = openLabel,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = action.accessibilityLabel
                role = Role.Button
            }
            .blindFocusable(
                id = "home_action_${action.type.name}",
                label = action.accessibilityLabel,
                activateLabel = openLabel,
                onActivate = onClick,
                actions = listOf(
                    BlindAction(
                        label = openLabel,
                        activateLabel = openLabel,
                        onActivate = onClick
                    )
                ) + secondaryActions
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.34f)
            )
            Text(
                text = action.compactLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun HomeAction.icon(): ImageVector = when (type) {
    HomeActionType.ReadTextQuick,
    HomeActionType.DescribeScene -> Icons.Rounded.ImageSearch
    HomeActionType.DetectObjects -> Icons.Rounded.Visibility
    HomeActionType.RecognizeCurrency -> Icons.Rounded.AttachMoney
    HomeActionType.EmergencyCall -> Icons.Rounded.Call
    HomeActionType.Voice -> Icons.Rounded.Mic
}

@Composable
private fun HomeAction.compactLabel(): String = when (type) {
    HomeActionType.ReadTextQuick -> stringResource(R.string.home_compact_action_ocr_quick)
    HomeActionType.DescribeScene -> stringResource(R.string.home_compact_action_describe_scene)
    HomeActionType.DetectObjects -> stringResource(R.string.home_compact_action_detect_objects)
    HomeActionType.RecognizeCurrency -> stringResource(R.string.home_compact_action_recognize_currency)
    HomeActionType.EmergencyCall -> stringResource(R.string.home_compact_action_emergency)
    HomeActionType.Voice -> title
}

@Preview(showBackground = true)
@Composable
private fun HomeActionCardPreview() {
    EyesTheme(dynamicColor = false) {
        val title = stringResource(R.string.home_action_read_quick_title)
        HomeActionCard(
            action = HomeAction(
                type = HomeActionType.ReadTextQuick,
                title = title,
                description = stringResource(R.string.home_action_read_quick_description),
                supportingLabel = stringResource(R.string.home_action_read_quick_supporting),
                accessibilityLabel = stringResource(R.string.home_action_read_quick_accessibility)
            ),
            onClick = {}
        )
    }
}
