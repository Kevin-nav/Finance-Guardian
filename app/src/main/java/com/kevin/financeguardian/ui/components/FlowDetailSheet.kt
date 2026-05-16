package com.kevin.financeguardian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

private val flowTypeOptions = listOf(
    FlowTypeUi.EXPENSE,
    FlowTypeUi.INCOME,
    FlowTypeUi.INTERNAL_TRANSFER,
    FlowTypeUi.CASH_DEPOSIT,
    FlowTypeUi.CARD_SPEND,
    FlowTypeUi.UNKNOWN,
)

private val defaultCategoryOptions = listOf(
    "Food", "Transport", "Airtime/Data", "Bills", "Subscriptions",
    "Laundry", "Family", "Transfers", "Income", "Savings", "Unknown",
)

/**
 * Flow-aware detail bottom sheet replacing the old TransactionDetailSheet.
 * Shows header, accounting impact, instruments, evidence timeline,
 * matching state, and correction controls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlowDetailSheet(
    flow: TransactionFlowDetail,
    balancesVisible: Boolean,
    onDismiss: () -> Unit,
    onSave: (selectedCategory: String, selectedType: String, plannedUse: String?) -> Unit,
    modifier: Modifier = Modifier,
    onUnlink: (() -> Unit)? = null,
    categoryOptions: List<String> = defaultCategoryOptions,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember(flow.flowId) { mutableStateOf(flow.categoryName) }
    var selectedType by remember(flow.flowId) { mutableStateOf(flow.flowType) }
    var editedPlannedUse by remember(flow.flowId) { mutableStateOf(flow.plannedUse ?: "") }
    var showEvidence by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // ── Header ──────────────────────────────────────────────────
            FlowHeader(
                flow = flow,
                balancesVisible = balancesVisible,
                onDismiss = onDismiss,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Accounting Impact ────────────────────────────────────────
            AccountingImpactCard(
                impact = accountingImpactFor(selectedType),
            )

            // ── Instruments ─────────────────────────────────────────────
            if (flow.sourceInstrument != null || flow.destinationInstrument != null) {
                InstrumentsSection(
                    source = flow.sourceInstrument,
                    destination = flow.destinationInstrument,
                )
            }

            // ── Matching State Banner ───────────────────────────────────
            flow.matchingState?.let { state ->
                MatchingStateBanner(state = state)
            }

            // ── Evidence Timeline ───────────────────────────────────────
            if (flow.events.isNotEmpty()) {
                EvidenceTimelineSection(
                    events = flow.events,
                    expanded = showEvidence,
                    onToggle = { showEvidence = !showEvidence },
                    balancesVisible = balancesVisible,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Correction Controls ─────────────────────────────────────
            Text(
                text = "FLOW TYPE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                flowTypeOptions.forEach { type ->
                    val isSelected = type == selectedType
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }

            // Live preview of accounting change
            if (selectedType != flow.flowType) {
                val newImpact = accountingImpactFor(selectedType)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = newImpact.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                categoryOptions.forEach { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                CategoryIcon(
                                    categoryName = category,
                                    size = 18.dp,
                                    iconSize = 12.dp,
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            // Planned use field
            OutlinedTextField(
                value = editedPlannedUse,
                onValueChange = { editedPlannedUse = it },
                label = { Text("Planned use / note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            // ── Actions ─────────────────────────────────────────────────
            Button(
                onClick = {
                    onSave(
                        selectedCategory,
                        selectedType.label,
                        editedPlannedUse.takeIf { it.isNotBlank() },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "Save Correction",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (onUnlink != null && flow.flowEventCount > 1) {
                OutlinedButton(
                    onClick = onUnlink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "Unlink Messages",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun FlowHeader(
    flow: TransactionFlowDetail,
    balancesVisible: Boolean,
    onDismiss: () -> Unit,
) {
    val ext = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Transaction Details",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Direction icon + amount
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (icon, iconTint, containerColor) = flowDirectionVisual(flow.flowType, ext)

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(spacing.sm))

        MoneyText(
            amountMinor = flow.amountMinor,
            currency = flow.currency,
            isCredit = when (flow.flowType) {
                FlowTypeUi.INCOME, FlowTypeUi.CASH_DEPOSIT -> true
                FlowTypeUi.INTERNAL_TRANSFER -> null
                else -> false
            },
            overrideColor = if (flow.flowType == FlowTypeUi.INTERNAL_TRANSFER) ext.neutralAmount else null,
            style = MoneyTypography.large,
            visible = balancesVisible,
        )

        Text(
            text = flow.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Flow type + status line
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = flow.flowType.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!flow.plannedUse.isNullOrBlank()) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = flow.plannedUse,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (flow.flowStatus != FlowStatusUi.COMPLETE) {
            Spacer(modifier = Modifier.height(spacing.xxs))
            FlowStatusBadge(status = flow.flowStatus)
        }
    }
}

@Composable
private fun FlowStatusBadge(status: FlowStatusUi) {
    val ext = MaterialTheme.extendedColors
    val (textColor, bgColor) = when (status) {
        FlowStatusUi.MATCHED -> ext.matchedBadge to ext.matchedBadgeContainer
        FlowStatusUi.PENDING_MATCH -> ext.pendingBadge to ext.pendingBadgeContainer
        FlowStatusUi.INFERRED -> ext.pendingBadge to ext.pendingBadgeContainer
        FlowStatusUi.UNMATCHED -> ext.reviewBadge to ext.reviewBadgeContainer
        FlowStatusUi.NEEDS_REVIEW -> ext.reviewBadge to ext.reviewBadgeContainer
        FlowStatusUi.COMPLETE -> ext.matchedBadge to ext.matchedBadgeContainer
    }

    Text(
        text = status.label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private data class FlowVisual(val icon: ImageVector, val tint: Color, val container: Color)

@Composable
private fun flowDirectionVisual(
    flowType: FlowTypeUi,
    ext: com.kevin.financeguardian.ui.theme.ExtendedColorScheme,
): FlowVisual = when (flowType) {
    FlowTypeUi.INCOME, FlowTypeUi.CASH_DEPOSIT -> FlowVisual(
        Icons.Filled.ArrowDownward, ext.income, ext.incomeContainer,
    )
    FlowTypeUi.INTERNAL_TRANSFER -> FlowVisual(
        Icons.Filled.SwapHoriz, ext.transfer, ext.transfer.copy(alpha = 0.15f),
    )
    FlowTypeUi.NEEDS_REVIEW -> FlowVisual(
        Icons.Filled.Warning, ext.warning, ext.warningContainer,
    )
    else -> FlowVisual(
        Icons.Filled.ArrowUpward, ext.expense, ext.expenseContainer,
    )
}

// ── Accounting Impact ───────────────────────────────────────────────────────

@Composable
private fun AccountingImpactCard(impact: AccountingImpactUi) {
    val ext = MaterialTheme.extendedColors
    val bgColor = if (impact.isExcluded) {
        ext.transfer.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = if (impact.isExcluded) ext.transfer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = impact.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = impact.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun accountingImpactFor(type: FlowTypeUi): AccountingImpactUi = when (type) {
    FlowTypeUi.INTERNAL_TRANSFER -> AccountingImpactUi(
        label = "Excluded from spending and income",
        description = "This will remove the flow from spending and income totals",
        isExcluded = true,
    )
    FlowTypeUi.EXPENSE, FlowTypeUi.CARD_SPEND -> AccountingImpactUi(
        label = "Counts as spending",
        description = "This flow is included in your expense totals",
        isExcluded = false,
    )
    FlowTypeUi.INCOME, FlowTypeUi.CASH_DEPOSIT -> AccountingImpactUi(
        label = "Counts as income",
        description = "This flow is included in your income totals",
        isExcluded = false,
    )
    else -> AccountingImpactUi(
        label = "Uncategorized",
        description = "Classify this flow to include it in your reports",
        isExcluded = false,
    )
}

// ── Instruments ─────────────────────────────────────────────────────────────

@Composable
private fun InstrumentsSection(
    source: InstrumentUi?,
    destination: InstrumentUi?,
) {
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "INSTRUMENTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (source != null) {
                InstrumentCard(
                    instrument = source,
                    role = "Source",
                    modifier = Modifier.weight(1f),
                )
            }
            if (source != null && destination != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterVertically),
                )
            }
            if (destination != null) {
                InstrumentCard(
                    instrument = destination,
                    role = "Destination",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InstrumentCard(
    instrument: InstrumentUi,
    role: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = instrument.userLabel ?: instrument.provider,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!instrument.maskedIdentifier.isNullOrBlank()) {
            Text(
                text = instrument.maskedIdentifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OwnershipBadge(ownership = instrument.ownership)
    }
}

@Composable
private fun OwnershipBadge(ownership: OwnershipUi) {
    val ext = MaterialTheme.extendedColors
    val (text, color) = when (ownership) {
        OwnershipUi.USER_CONFIRMED -> "Confirmed" to ext.matchedBadge
        OwnershipUi.STRONGLY_INFERRED -> "Inferred" to ext.pendingBadge
        OwnershipUi.EXTERNAL -> "External" to ext.unknown
        OwnershipUi.UNKNOWN -> "Unknown" to ext.unknown
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Evidence Timeline ───────────────────────────────────────────────────────

@Composable
private fun EvidenceTimelineSection(
    events: List<EvidenceEventUi>,
    expanded: Boolean,
    onToggle: () -> Unit,
    balancesVisible: Boolean,
) {
    val spacing = MaterialTheme.spacing

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EVIDENCE - ${events.size} message${if (events.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                events.forEach { event ->
                    EvidenceEventCard(event = event, balancesVisible = balancesVisible)
                }
                Text(
                    text = "Parsed from SMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EvidenceEventCard(
    event: EvidenceEventUi,
    balancesVisible: Boolean,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${event.provider} - ${event.direction}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = event.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MoneyText(
            amountMinor = event.amountMinor,
            currency = event.currency,
            style = MaterialTheme.typography.bodyMedium,
            visible = balancesVisible,
        )

        if (event.channel.isNotBlank()) {
            Text(
                text = event.channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!event.sourceIdentifier.isNullOrBlank()) {
            Text(
                text = "Source: ${event.sourceIdentifier}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!event.destinationIdentifier.isNullOrBlank()) {
            Text(
                text = "Destination: ${event.destinationIdentifier}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!event.reference.isNullOrBlank()) {
            Text(
                text = "Ref: ${event.reference}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Matching State Banner ───────────────────────────────────────────────────

@Composable
private fun MatchingStateBanner(state: MatchingStateUi) {
    val ext = MaterialTheme.extendedColors
    val (iconTint, bgColor) = if (state.isWaiting) {
        ext.pendingBadge to ext.pendingBadgeContainer
    } else {
        ext.matchedBadge to ext.matchedBadgeContainer
    }
    val icon = if (state.isWaiting) Icons.Filled.Schedule else Icons.Filled.CheckCircle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = state.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = iconTint,
            )
            if (!state.detail.isNullOrBlank()) {
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = iconTint.copy(alpha = 0.8f),
                )
            }
        }
    }
}
