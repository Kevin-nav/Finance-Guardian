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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kevin.financeguardian.ui.components.BalanceHeroCard
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.components.FilterChipRow
import com.kevin.financeguardian.ui.components.SectionHeader
import com.kevin.financeguardian.ui.components.TransactionDetail
import com.kevin.financeguardian.ui.components.TransactionDetailSheet
import com.kevin.financeguardian.ui.components.TransactionRow
import com.kevin.financeguardian.ui.theme.spacing

// ── Preview / placeholder data ──────────────────────────────────────────────

private data class PreviewTransaction(
    val id: String,
    val merchantName: String,
    val categoryName: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val timestamp: String,
    val balanceAfterMinor: Long? = null,
    val dateGroup: String,
    val provider: String = "MTN MoMo",
    val reference: String? = null,
)

private val previewTransactions = listOf(
    PreviewTransaction("1", "MTN MoMo - Airtime", "Airtime/Data", 5000, false, "14:32", 245000, "Today", reference = "REF202604001"),
    PreviewTransaction("2", "Bolt Ride", "Transport", 1800, false, "12:15", 250000, "Today"),
    PreviewTransaction("3", "Shoprite", "Food", 8500, false, "10:40", 251800, "Today"),
    PreviewTransaction("4", "Salary Credit", "Income", 520000, true, "09:00", 260300, "Today", provider = "GCB Bank"),
    PreviewTransaction("5", "MTN MoMo - Transfer", "Transfers", 20000, false, "18:20", null, "Yesterday"),
    PreviewTransaction("6", "Laundry Express", "Laundry", 3500, false, "15:00", null, "Yesterday"),
    PreviewTransaction("7", "Unknown Merchant", "Unknown", 7200, false, "11:30", null, "Yesterday"),
    PreviewTransaction("8", "Netflix", "Subscriptions", 6500, false, "08:00", null, "Mon, 14 Apr", provider = "GCB Bank"),
    PreviewTransaction("9", "Mom - MoMo", "Family", 50000, true, "16:45", null, "Mon, 14 Apr"),
    PreviewTransaction("10", "Savings Account", "Savings", 60000, false, "09:30", null, "Mon, 14 Apr"),
)

private val filters = listOf("All", "Income", "Expenses", "Transfers", "Unknown")

// ── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedTransaction by remember { mutableStateOf<PreviewTransaction?>(null) }

    val filteredTransactions = remember(selectedFilter) {
        when (selectedFilter) {
            "Income" -> previewTransactions.filter { it.isCredit }
            "Expenses" -> previewTransactions.filter { !it.isCredit && it.categoryName != "Transfers" && it.categoryName != "Unknown" }
            "Transfers" -> previewTransactions.filter { it.categoryName == "Transfers" }
            "Unknown" -> previewTransactions.filter { it.categoryName == "Unknown" }
            else -> previewTransactions
        }
    }

    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.dateGroup }
    }

    // Transaction detail sheet
    selectedTransaction?.let { txn ->
        TransactionDetailSheet(
            transaction = TransactionDetail(
                id = txn.id,
                merchantName = txn.merchantName,
                categoryName = txn.categoryName,
                amountMinor = txn.amountMinor,
                isCredit = txn.isCredit,
                timestamp = txn.timestamp,
                dateGroup = txn.dateGroup,
                provider = txn.provider,
                reference = txn.reference,
                balanceAfterMinor = txn.balanceAfterMinor,
            ),
            onDismiss = { selectedTransaction = null },
            onSave = { _, _ ->
                // TODO: persist correction
                selectedTransaction = null
            },
        )
    }

    if (previewTransactions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "No Transactions Yet",
                subtitle = "Grant SMS access in Settings to start auto-detecting your transactions.",
            )
        }
    } else {
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
            // Greeting Header
            item(key = "greeting") {
                Text(
                    text = "Finance Guardian",
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

            // Balance Hero Card
            item(key = "balance_card") {
                BalanceHeroCard(
                    totalBalanceMinor = 245000,
                    incomeMinor = 570000,
                    expensesMinor = 265000,
                    savingsMinor = 60000,
                )
            }

            // Filter Chips
            item(key = "filters") {
                FilterChipRow(
                    filters = filters,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    modifier = Modifier.padding(vertical = spacing.xs),
                )
            }

            // Grouped Transaction List
            groupedTransactions.forEach { (dateGroup, transactions) ->
                item(key = "header_$dateGroup") {
                    SectionHeader(title = dateGroup)
                }

                items(
                    items = transactions,
                    key = { it.id },
                ) { txn ->
                    TransactionRow(
                        merchantName = txn.merchantName,
                        categoryName = txn.categoryName,
                        amountMinor = txn.amountMinor,
                        isCredit = txn.isCredit,
                        timestamp = txn.timestamp,
                        balanceAfterMinor = txn.balanceAfterMinor,
                        isUnknownCategory = txn.categoryName == "Unknown",
                        onClick = { selectedTransaction = txn },
                    )
                }
            }
        }
    }
}
