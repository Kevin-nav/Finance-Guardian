package com.kevin.financeguardian.ui.components

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Data class representing a transaction for the detail sheet.
 */
data class TransactionDetail(
    val id: String,
    val merchantName: String,
    val categoryName: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val timestamp: String,
    val dateGroup: String,
    val provider: String = "MTN MoMo",
    val reference: String? = null,
    val balanceAfterMinor: Long? = null,
    val currency: String = "GHS",
)

private val availableCategories = listOf(
    "Food", "Transport", "Airtime/Data", "Bills", "Subscriptions",
    "Laundry", "Family", "Transfers", "Income", "Savings", "Unknown",
)

private val transactionTypes = listOf(
    "Expense", "Income", "Internal Transfer", "Savings", "Ignore",
)

/**
 * Modal bottom sheet displaying full transaction details with
 * category correction and transaction type override capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionDetail,
    onDismiss: () -> Unit,
    onSave: (selectedCategory: String, selectedType: String) -> Unit,
    modifier: Modifier = Modifier,
    categoryOptions: List<String> = availableCategories,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember(transaction.id) { mutableStateOf(transaction.categoryName) }
    var selectedType by remember(transaction.id) {
        mutableStateOf(if (transaction.isCredit) "Income" else "Expense")
    }

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

            // ── Amount Hero ─────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (transaction.isCredit) ext.incomeContainer
                            else ext.expenseContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (transaction.isCredit)
                            Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = if (transaction.isCredit) ext.income else ext.expense,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.height(spacing.sm))

                MoneyText(
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    isCredit = transaction.isCredit,
                    style = MoneyTypography.large,
                )

                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Detail rows ─────────────────────────────────────────────
            DetailRow(label = "Date", value = "${transaction.dateGroup}, ${transaction.timestamp}")
            DetailRow(label = "Provider", value = transaction.provider)
            if (transaction.reference != null) {
                DetailRow(label = "Reference", value = transaction.reference)
            }
            if (transaction.balanceAfterMinor != null) {
                DetailRow(
                    label = "Balance After",
                    value = formatMoney(transaction.balanceAfterMinor, transaction.currency),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Category Selector ───────────────────────────────────────
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Type Override ───────────────────────────────────────────
            Text(
                text = "TRANSACTION TYPE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                transactionTypes.forEach { type ->
                    val isSelected = type == selectedType
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                text = type,
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

            Spacer(modifier = Modifier.height(spacing.sm))

            // ── Save Button ─────────────────────────────────────────────
            Button(
                onClick = { onSave(selectedCategory, selectedType) },
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
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatMoney(amountMinor: Long, currency: String): String {
    val amountMajor = amountMinor / 100.0
    return "$currency %.2f".format(amountMajor)
}
