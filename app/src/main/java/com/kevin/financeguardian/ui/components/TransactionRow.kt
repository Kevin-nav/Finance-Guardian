package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * A single transaction list item showing category icon, merchant info,
 * amount, and an optional left accent border for unknown-category items.
 */
@Composable
fun TransactionRow(
    merchantName: String,
    categoryName: String,
    amountMinor: Long,
    isCredit: Boolean,
    timestamp: String,
    modifier: Modifier = Modifier,
    balanceAfterMinor: Long? = null,
    currency: String = "GHS",
    isUnknownCategory: Boolean = false,
    onClick: () -> Unit = {},
) {
    val ext = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        border = if (isUnknownCategory) {
            BorderStroke(2.dp, ext.warning)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CategoryIcon(categoryName = categoryName)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "$categoryName · $timestamp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MoneyText(
                    amountMinor = amountMinor,
                    currency = currency,
                    isCredit = isCredit,
                )
                if (balanceAfterMinor != null) {
                    MoneyText(
                        amountMinor = balanceAfterMinor,
                        currency = currency,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        overrideColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
