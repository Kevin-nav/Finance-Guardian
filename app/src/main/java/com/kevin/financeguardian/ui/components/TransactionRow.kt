package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * A single transaction list item showing category icon, merchant info,
 * amount, and a subtle warning tint for unknown-category items.
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
    balancesVisible: Boolean = true,
    onClick: () -> Unit = {},
) {
    val ext = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isUnknownCategory) 2.dp else 1.dp,
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isUnknownCategory) {
                ext.warningContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Category icon with optional warning dot
            Box {
                CategoryIcon(categoryName = categoryName)
                if (isUnknownCategory) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ext.warning)
                            .align(Alignment.TopEnd),
                    )
                }
            }

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
                    color = if (isUnknownCategory) ext.warning
                    else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    visible = balancesVisible,
                )
                if (balanceAfterMinor != null) {
                    MoneyText(
                        amountMinor = balanceAfterMinor,
                        currency = currency,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        overrideColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        visible = balancesVisible,
                    )
                }
            }
        }
    }
}
