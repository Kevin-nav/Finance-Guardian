package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.spacing

/**
 * A row showing a category's spending with a proportional bar.
 * Used in the Insights screen for the spending breakdown list.
 *
 * @param categoryName Name of the category
 * @param amountMinor Amount spent in minor units
 * @param fraction Fraction of total spend (0.0–1.0) for the progress bar
 * @param barColor Tint color for the progress bar
 * @param currency Currency code
 */
@Composable
fun SpendingBreakdownRow(
    categoryName: String,
    amountMinor: Long,
    fraction: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
    currency: String = "GHS",
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(
                    categoryName = categoryName,
                    size = 32.dp,
                    iconSize = 16.dp,
                )
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            MoneyText(
                amountMinor = amountMinor,
                currency = currency,
                style = MoneyTypography.small,
            )
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(barColor),
            )
        }
    }
}
