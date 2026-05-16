package com.kevin.financeguardian.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.ui.components.BalanceHeroCard
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.components.FilterChipRow
import com.kevin.financeguardian.ui.components.QuickActionRow
import com.kevin.financeguardian.ui.components.SectionHeader
import com.kevin.financeguardian.ui.components.TransactionDetailSheet
import com.kevin.financeguardian.ui.components.TransactionRow
import com.kevin.financeguardian.ui.theme.spacing
import java.util.Calendar

private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun getFilteredEmptyMessage(filter: TransactionFilter): String =
    when (filter) {
        TransactionFilter.Income -> "No income transactions yet.\nIncome will appear here as it's detected."
        TransactionFilter.Expenses -> "No expenses recorded yet.\nSpending will show up as SMS messages arrive."
        TransactionFilter.Transfers -> "No transfers found.\nMobile money transfers will appear here."
        TransactionFilter.Unknown -> "No uncategorized transactions.\nAll your transactions have been categorized."
        TransactionFilter.All -> "No transactions match this filter."
    }

private fun getEmptyMessage(receiveSmsGranted: Boolean): String =
    if (receiveSmsGranted) {
        "Parsed financial SMS transactions will appear here as they arrive."
    } else {
        "Grant SMS access in Settings to start auto-detecting your transactions."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
    onViewInsightsClick: () -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    uiState.selectedTransaction?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            categoryOptions = uiState.categoryOptions.map { it.name },
            onDismiss = viewModel::dismissTransaction,
            onSave = { selectedCategory, selectedType ->
                viewModel.saveCorrection(selectedCategory, selectedType)
            },
        )
    }

    if (uiState.isEmpty) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "No Transactions Yet",
                subtitle = getEmptyMessage(uiState.receiveSmsGranted),
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
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        item(key = "greeting") {
            Text(
                text = getGreeting(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(spacing.xxs))
            Text(
                text = "Your transactions at a glance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
        }

        item(key = "balance_card") {
            BalanceHeroCard(
                totalBalanceMinor = uiState.totalBalanceMinor,
                providerBalances = uiState.providerBalances.map {
                    com.kevin.financeguardian.ui.components.ProviderBalanceSnapshot(
                        provider = it.provider,
                        balanceMinor = it.balanceMinor,
                        currency = it.currency,
                    )
                },
                incomeMinor = uiState.incomeMinor,
                expensesMinor = uiState.expensesMinor,
                savingsMinor = uiState.savingsMinor,
                balancesVisible = uiState.balancesVisible,
                onBalancesVisibleChange = viewModel::setBalancesVisible,
            )
        }

        item(key = "quick_actions") {
            QuickActionRow(
                onViewInsightsClick = onViewInsightsClick,
                modifier = Modifier.padding(vertical = spacing.xs),
            )
        }

        item(key = "filters") {
            FilterChipRow(
                filters = uiState.filters.map { it.label },
                selectedFilter = uiState.selectedFilter.label,
                onFilterSelected = { selectedLabel ->
                    val filter = TransactionFilter.entries.firstOrNull { it.label == selectedLabel }
                    if (filter != null) viewModel.selectFilter(filter)
                },
                modifier = Modifier.padding(vertical = spacing.xs),
            )
        }

        if (uiState.groups.isEmpty()) {
            item(key = "filtered_empty") {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight(0.4f)
                        .fillParentMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = uiState.selectedFilter.emptyIcon(),
                        title = "No ${uiState.selectedFilter.label.lowercase()} transactions",
                        subtitle = getFilteredEmptyMessage(uiState.selectedFilter),
                    )
                }
            }
        }

        uiState.groups.forEach { group ->
            item(key = "header_${group.dateGroup}") {
                SectionHeader(
                    title = group.dateGroup,
                    count = group.transactions.size,
                )
            }

            items(
                items = group.transactions,
                key = { it.id },
            ) { transaction ->
                TransactionRow(
                    merchantName = transaction.merchantName,
                    categoryName = transaction.categoryName,
                    amountMinor = transaction.amountMinor,
                    isCredit = transaction.isCredit,
                    timestamp = transaction.timestamp,
                    balanceAfterMinor = transaction.balanceAfterMinor,
                    currency = transaction.currency,
                    isUnknownCategory = transaction.isUnknownCategory,
                    balancesVisible = uiState.balancesVisible,
                    onClick = { viewModel.selectTransaction(transaction.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun TransactionFilter.emptyIcon() =
    when (this) {
        TransactionFilter.Income -> Icons.AutoMirrored.Filled.TrendingUp
        TransactionFilter.Expenses -> Icons.AutoMirrored.Filled.TrendingDown
        TransactionFilter.Transfers -> Icons.Filled.SwapHoriz
        TransactionFilter.Unknown -> Icons.AutoMirrored.Filled.HelpOutline
        TransactionFilter.All -> Icons.Filled.FilterList
    }
