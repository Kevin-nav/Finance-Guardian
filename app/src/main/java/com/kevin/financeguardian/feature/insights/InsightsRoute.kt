package com.kevin.financeguardian.feature.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.components.InsightBanner
import com.kevin.financeguardian.ui.components.MerchantRuleRow
import com.kevin.financeguardian.ui.components.MoneyText
import com.kevin.financeguardian.ui.components.RecurringPatternRow
import com.kevin.financeguardian.ui.components.SpendingBreakdownRow
import com.kevin.financeguardian.ui.components.SummaryStatCard
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

@Composable
fun InsightsRoute(
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors
    val totalSpend = uiState.categorySpending.sumOf { it.amountMinor }

    var animateBars by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateBars = true }

    if (!uiState.hasData) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Filled.BarChart,
                title = "No Insights Yet",
                subtitle = "Start tracking transactions to see spending breakdowns, trends, and more.",
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.md,
            end = spacing.md,
            top = spacing.md,
            bottom = spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item(key = "header") {
            Column(modifier = Modifier.animateItem()) {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (uiState.currentMonthLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = uiState.currentMonthLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Summary Cards (Income / Spent) ──────────────────────────────────
        item(key = "summary_cards") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SummaryStatCard(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "Income",
                    amountMinor = uiState.incomeMinor,
                    tintColor = ext.income,
                    isCredit = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatCard(
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    label = "Spent",
                    amountMinor = uiState.spendingMinor,
                    tintColor = ext.expense,
                    isCredit = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Net Cash Flow (compact inline row) ──────────────────────────────
        item(key = "net_flow") {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 0.5.dp,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Net Cash Flow",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    MoneyText(
                        amountMinor = uiState.netCashFlowMinor,
                        style = MoneyTypography.small.copy(fontWeight = FontWeight.SemiBold),
                        isCredit = if (uiState.netCashFlowMinor >= 0) true else false,
                    )
                }
            }
        }

        // ── Smart Insights Section ──────────────────────────────────────────
        val hasSmartInsights = uiState.highlightInsight != null || uiState.recurringSummary != null
        if (hasSmartInsights) {
            item(key = "smart_insights_header") {
                InsightsSectionHeader(
                    title = "SMART INSIGHTS",
                    icon = Icons.Filled.AutoAwesome,
                    modifier = Modifier.animateItem(),
                )
            }

            // Proactive insight banner
            uiState.highlightInsight?.let { insight ->
                item(key = "proactive_insight") {
                    InsightBanner(
                        icon = Icons.Filled.Lightbulb,
                        title = insight.title,
                        summary = insight.summary,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Recurring patterns summary teaser
            uiState.recurringSummary?.let { summary ->
                item(key = "recurring_summary_teaser") {
                    InsightBanner(
                        icon = Icons.Filled.Repeat,
                        title = "${summary.totalCount} recurring pattern${if (summary.totalCount > 1) "s" else ""} detected",
                        summary = summary.toDisplayText(),
                        tintColor = ext.transfer,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        // ── Spending by Category ────────────────────────────────────────────
        if (uiState.categorySpending.isNotEmpty()) {
            item(key = "breakdown_header") {
                InsightsSectionHeader(
                    title = "SPENDING BY CATEGORY",
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "breakdown_card") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.5.dp,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        uiState.categorySpending.forEachIndexed { index, category ->
                            val rawFraction = if (totalSpend > 0) {
                                category.amountMinor.toFloat() / totalSpend
                            } else {
                                0f
                            }
                            val animatedFraction by animateFloatAsState(
                                targetValue = if (animateBars) rawFraction else 0f,
                                animationSpec = tween(
                                    durationMillis = 600,
                                    delayMillis = index * 80,
                                ),
                                label = "bar_${category.name}",
                            )

                            SpendingBreakdownRow(
                                categoryName = category.name,
                                amountMinor = category.amountMinor,
                                fraction = animatedFraction,
                                barColor = category.colorForIndex(index),
                            )
                            if (index < uiState.categorySpending.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Recurring Patterns (Expanded List) ──────────────────────────────
        if (uiState.recurringPatterns.isNotEmpty()) {
            item(key = "recurring_header") {
                InsightsSectionHeader(
                    title = "RECURRING PATTERNS",
                    icon = Icons.Filled.Repeat,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "recurring_card") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.5.dp,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                    ) {
                        uiState.recurringPatterns.forEachIndexed { index, pattern ->
                            RecurringPatternRow(
                                displayName = pattern.displayName,
                                kind = pattern.kind,
                                kindLabel = pattern.kindLabel,
                                frequency = pattern.frequency,
                                averageAmountMinor = pattern.averageAmountMinor,
                                isCredit = pattern.isCredit,
                            )
                            if (index < uiState.recurringPatterns.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Largest Transactions ────────────────────────────────────────────
        if (uiState.largeTransactions.isNotEmpty()) {
            item(key = "large_txn_header") {
                InsightsSectionHeader(
                    title = "LARGEST TRANSACTIONS",
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "large_txn_card") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.5.dp,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                    ) {
                        uiState.largeTransactions.forEachIndexed { index, txn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Rank badge
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer
                                                .copy(alpha = 0.6f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = txn.merchantName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = txn.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MoneyText(
                                    amountMinor = txn.amountMinor,
                                    isCredit = txn.isCredit,
                                    style = MoneyTypography.small,
                                )
                            }
                            if (index < uiState.largeTransactions.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Smart Categorization (Learning Engine Footer) ───────────────────
        if (uiState.learningStats.hasData) {
            item(key = "learning_header") {
                InsightsSectionHeader(
                    title = "SMART CATEGORIZATION",
                    icon = Icons.Filled.Psychology,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "learning_card") {
                val stats = uiState.learningStats
                var animateRate by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { animateRate = true }

                val animatedRate by animateFloatAsState(
                    targetValue = if (animateRate) stats.autoApplyRate else 0f,
                    animationSpec = tween(durationMillis = 800, delayMillis = 200),
                    label = "auto_rate",
                )

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.5.dp,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        // Stats summary row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            LearningStatItem(
                                value = "${stats.totalSignals}",
                                label = "Signals",
                                modifier = Modifier.weight(1f),
                            )
                            LearningStatItem(
                                value = "${stats.correctionCount}",
                                label = "Corrections",
                                modifier = Modifier.weight(1f),
                            )
                            LearningStatItem(
                                value = "${(stats.autoApplyRate * 100).toInt()}%",
                                label = "Auto-rate",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Auto-categorization progress bar
                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            Text(
                                text = "Auto-categorization confidence",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = animatedRate.coerceIn(0f, 1f))
                                        .height(6.dp)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(ext.income),
                                )
                            }
                        }

                        // Top merchant rules
                        if (uiState.topMerchantRules.isNotEmpty()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                            Text(
                                text = "Top learned rules",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.topMerchantRules.forEachIndexed { index, rule ->
                                MerchantRuleRow(
                                    merchantName = rule.merchantName,
                                    categoryName = rule.categoryName,
                                    confidence = rule.confidence,
                                    signalCount = rule.signalCount,
                                    animationDelay = index * 100,
                                )
                                if (index < uiState.topMerchantRules.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Private Components ──────────────────────────────────────────────────────

/**
 * Section header with optional leading icon for visual hierarchy.
 */
@Composable
private fun InsightsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier.padding(top = MaterialTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Compact stat display for the Learning Engine section.
 */
@Composable
private fun LearningStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CategorySpendingItem.colorForIndex(index: Int): Color {
    val palette = listOf(
        Color(0xFF006590),
        Color(0xFFE67E22),
        Color(0xFF7D5800),
        Color(0xFF1ABC9C),
        Color(0xFF3498DB),
        Color(0xFF8E44AD),
    )
    return palette[index % palette.size]
}
