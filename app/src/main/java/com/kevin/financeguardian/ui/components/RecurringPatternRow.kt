package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.data.learning.RecurringPattern
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Row component for displaying a detected recurring pattern.
 * Shows kind icon, merchant name, frequency, average amount, and kind badge.
 */
@Composable
fun RecurringPatternRow(
    displayName: String,
    kind: RecurringPattern.Kind,
    kindLabel: String,
    frequency: String,
    averageAmountMinor: Long,
    isCredit: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors
    val (icon, tintColor) = kindVisual(kind, ext)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kind icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = kindLabel,
                tint = tintColor,
                modifier = Modifier.size(18.dp),
            )
        }

        // Name + frequency + kind
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = frequency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tintColor,
                )
            }
        }

        // Average amount
        MoneyText(
            amountMinor = averageAmountMinor,
            isCredit = isCredit,
            style = MoneyTypography.small,
        )
    }
}

@Composable
private fun kindVisual(
    kind: RecurringPattern.Kind,
    ext: com.kevin.financeguardian.ui.theme.ExtendedColorScheme,
): Pair<ImageVector, Color> {
    return when (kind) {
        RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE ->
            Icons.Filled.Autorenew to ext.warning

        RecurringPattern.Kind.RECURRING_EXPENSE ->
            Icons.Filled.Receipt to ext.expense

        RecurringPattern.Kind.INCOME_SOURCE ->
            Icons.AutoMirrored.Filled.TrendingUp to ext.income
    }
}
