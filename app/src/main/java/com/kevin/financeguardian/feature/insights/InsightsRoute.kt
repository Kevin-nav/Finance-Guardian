package com.kevin.financeguardian.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.components.MoneyText
import com.kevin.financeguardian.ui.components.SpendingBreakdownRow
import com.kevin.financeguardian.ui.components.SummaryStatCard
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

// ── Preview data ────────────────────────────────────────────────────────────

private data class CategorySpending(
    val name: String,
    val amountMinor: Long,
    val color: Color,
)

private data class LargeTransaction(
    val merchantName: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val date: String,
)

private val previewCategorySpending = listOf(
    CategorySpending("Savings", 60000, Color(0xFF006590)),
    CategorySpending("Food", 8500, Color(0xFFE67E22)),
    CategorySpending("Subscriptions", 6500, Color(0xFF7D5800)),
    CategorySpending("Airtime/Data", 5000, Color(0xFF9B59B6)),
    CategorySpending("Laundry", 3500, Color(0xFF1ABC9C)),
    CategorySpending("Transport", 1800, Color(0xFF3498DB)),
)

private val previewLargeTransactions = listOf(
    LargeTransaction("Savings Account", 60000, false, "Mon, 14 Apr"),
    LargeTransaction("MTN MoMo - Transfer", 20000, false, "Yesterday"),
    LargeTransaction("Shoprite", 8500, false, "Today"),
)

private const val totalIncomeMinor = 570000L
private const val totalExpensesMinor = 85300L
private const val netCashFlowMinor = 484700L

// ── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun InsightsRoute(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors
    val totalSpend = remember { previewCategorySpending.sumOf { it.amountMinor } }

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
        // ── Header ──────────────────────────────────────────────────
        item(key = "header") {
            Column {
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

        // ── Summary Cards ───────────────────────────────────────────
        item(key = "summary_cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SummaryStatCard(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "Income",
                    amountMinor = totalIncomeMinor,
                    tintColor = ext.income,
                    isCredit = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatCard(
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    label = "Spent",
                    amountMinor = totalExpensesMinor,
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
                amountMinor = netCashFlowMinor,
                tintColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Spending Breakdown ──────────────────────────────────────
        item(key = "breakdown_header") {
            Column {
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
                modifier = Modifier.fillMaxWidth(),
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
                    previewCategorySpending.forEachIndexed { index, category ->
                        SpendingBreakdownRow(
                            categoryName = category.name,
                            amountMinor = category.amountMinor,
                            fraction = if (totalSpend > 0) category.amountMinor.toFloat() / totalSpend else 0f,
                            barColor = category.color,
                        )
                        if (index < previewCategorySpending.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }

        // ── Large Transactions ──────────────────────────────────────
        item(key = "large_txn_header") {
            Column {
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
                modifier = Modifier.fillMaxWidth(),
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
                    previewLargeTransactions.forEachIndexed { index, txn ->
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
                        if (index < previewLargeTransactions.lastIndex) {
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
