package com.example.eyes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.eyes.R
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.theme.EyesTheme

@Composable
fun HomeActionCard(
    action: HomeAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openLabel = stringResource(R.string.home_action_open_label, action.title.lowercase())
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
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
                )
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = action.supportingLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
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
