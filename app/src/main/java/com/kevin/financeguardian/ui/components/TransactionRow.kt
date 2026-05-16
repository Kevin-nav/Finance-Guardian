package com.kevin.financeguardian.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * A single transaction list item showing category icon, merchant/flow info,
 * amount, and optional status chip.
 *
 * For collapsed flows, [merchantName] is the source→destination title,
 * [isInternalTransfer] controls neutral amount styling, and [flowStatus]
 * drives the status chip.
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
    isInternalTransfer: Boolean = false,
    plannedUse: String? = null,
    flowStatus: FlowStatusUi? = null,
    flowType: FlowTypeUi? = null,
    onClick: () -> Unit = {},
) {
    val ext = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing

    // Build subtitle text: "{plannedUse} - {flowLabel} - {timestamp}"
    val subtitleParts = buildList {
        if (!plannedUse.isNullOrBlank()) add(plannedUse)
        if (flowType != null && flowType != FlowTypeUi.UNKNOWN && flowType != FlowTypeUi.EXPENSE) {
            add(flowType.label)
        } else {
            add(categoryName)
        }
        add(timestamp)
    }
    val subtitleText = subtitleParts.joinToString(" - ")

    // Determine if we should show a status chip
    val showChip = flowStatus != null && flowStatus != FlowStatusUi.COMPLETE

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp,
        ),
        colors = CardDefaults.elevatedCardColors(
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
            // Left accent bar for unknown categories (replaces old warning tint)
            if (isUnknownCategory) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ext.warning),
                )
            }

            // Category icon with optional warning dot
            Box {
                CategoryIcon(categoryName = if (isInternalTransfer) "transfers" else categoryName)
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
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUnknownCategory) ext.warning
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (showChip) {
                        FlowStatusChip(flowStatus = flowStatus!!)
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MoneyText(
                    amountMinor = amountMinor,
                    currency = currency,
                    // Neutral styling for internal transfers
                    isCredit = if (isInternalTransfer) null else isCredit,
                    overrideColor = if (isInternalTransfer) ext.neutralAmount else null,
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

/**
 * Small status chip indicating the flow's matching/review state.
 */
@Composable
private fun FlowStatusChip(
    flowStatus: FlowStatusUi,
    modifier: Modifier = Modifier,
) {
    val ext = MaterialTheme.extendedColors

    val (textColor, backgroundColor) = when (flowStatus) {
        FlowStatusUi.MATCHED -> ext.matchedBadge to ext.matchedBadgeContainer
        FlowStatusUi.PENDING_MATCH -> ext.pendingBadge to ext.pendingBadgeContainer
        FlowStatusUi.INFERRED -> ext.pendingBadge to ext.pendingBadgeContainer
        FlowStatusUi.UNMATCHED -> ext.reviewBadge to ext.reviewBadgeContainer
        FlowStatusUi.NEEDS_REVIEW -> ext.reviewBadge to ext.reviewBadgeContainer
        FlowStatusUi.COMPLETE -> ext.matchedBadge to ext.matchedBadgeContainer
    }

    Text(
        text = flowStatus.label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}
