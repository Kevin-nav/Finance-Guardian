package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Date group header for the transaction list (e.g., "Today", "Yesterday", "Mon, 14 Apr").
 * Optionally displays a transaction count badge.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier = modifier.padding(
            top = MaterialTheme.spacing.lg,
            bottom = MaterialTheme.spacing.xs,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
