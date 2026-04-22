package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Horizontally scrollable row of quick action chips for the dashboard.
 */
@Composable
fun QuickActionRow(
    onAddManualClick: () -> Unit,
    onScanHistoryClick: () -> Unit,
    onViewInsightsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        QuickActionChip(
            icon = Icons.Filled.Add,
            label = "Add Manual",
            onClick = onAddManualClick,
        )
        QuickActionChip(
            icon = Icons.Filled.History,
            label = "Scan History",
            onClick = onScanHistoryClick,
        )
        QuickActionChip(
            icon = Icons.Filled.PieChart,
            label = "View Insights",
            onClick = onViewInsightsClick,
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = null,
    )
}
