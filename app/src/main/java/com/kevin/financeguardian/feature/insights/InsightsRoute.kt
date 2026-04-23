package com.kevin.financeguardian.feature.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.components.MoneyText
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
            bottom = spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.animateItem()) {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(spacing.xxs))
                Text(
                    text = "This month's overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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

        item(key = "net_card") {
            SummaryStatCard(
                icon = Icons.Filled.AccountBalanceWallet,
                label = "Net Cash Flow",
                amountMinor = uiState.netCashFlowMinor,
                tintColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }

        item(key = "breakdown_header") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "SPENDING BY CATEGORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item(key = "breakdown_card") {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }

        item(key = "large_txn_header") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "LARGEST TRANSACTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item(key = "large_txn_card") {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                ) {
                    uiState.largeTransactions.forEachIndexed { index, txn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.merchantName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
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
