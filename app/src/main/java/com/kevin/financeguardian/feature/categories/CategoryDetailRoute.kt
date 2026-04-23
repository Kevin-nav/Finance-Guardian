package com.kevin.financeguardian.feature.categories

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.ui.components.CategoryIcon
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.components.MoneyText
import com.kevin.financeguardian.ui.components.SectionHeader
import com.kevin.financeguardian.ui.components.TransactionDetailSheet
import com.kevin.financeguardian.ui.components.TransactionRow
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    val spacing = MaterialTheme.spacing
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Transaction detail sheet
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.categoryName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            uiState.isNotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.Warning,
                        title = "Category Not Found",
                        subtitle = "This category may have been archived or deleted.",
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = spacing.xxl),
                ) {
                    // ── Hero Section ─────────────────────────────────────
                    item(key = "hero") {
                        CategoryHeroSection(
                            categoryName = uiState.categoryName,
                            categoryType = uiState.categoryType,
                            typeLabel = uiState.typeLabel,
                            totalAmountMinor = uiState.totalAmountMinor,
                            transactionCount = uiState.transactionCount,
                            modifier = Modifier.padding(
                                horizontal = spacing.md,
                                vertical = spacing.sm,
                            ),
                        )
                    }

                    // ── Empty Transactions ───────────────────────────────
                    if (uiState.groups.isEmpty()) {
                        item(key = "empty_transactions") {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxHeight(0.4f)
                                    .fillParentMaxWidth()
                                    .padding(horizontal = spacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.Filled.Inbox,
                                    title = "No Transactions",
                                    subtitle = "Transactions in this category will appear here as they are detected.",
                                )
                            }
                        }
                    }

                    // ── Transaction Groups ───────────────────────────────
                    uiState.groups.forEach { group ->
                        item(key = "header_${group.dateGroup}") {
                            SectionHeader(
                                title = group.dateGroup,
                                count = group.transactions.size,
                                modifier = Modifier.padding(horizontal = spacing.md),
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
                                onClick = { viewModel.selectTransaction(transaction.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = spacing.md, vertical = spacing.xxs),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Hero Section ────────────────────────────────────────────────────────────

@Composable
private fun CategoryHeroSection(
    categoryName: String,
    categoryType: CategoryType,
    typeLabel: String,
    totalAmountMinor: Long,
    transactionCount: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors

    val typePillColor = when (categoryType) {
        CategoryType.EXPENSE -> ext.expense
        CategoryType.INCOME -> ext.income
        CategoryType.TRANSFER -> ext.transfer
        CategoryType.SAVINGS -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CategoryIcon(
                categoryName = categoryName,
                size = 64.dp,
                iconSize = 32.dp,
            )

            Text(
                text = categoryName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Type badge
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = typePillColor,
                modifier = Modifier
                    .background(
                        color = typePillColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = spacing.xs),
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MoneyText(
                        amountMinor = totalAmountMinor,
                        isCredit = when (categoryType) {
                            CategoryType.INCOME -> true
                            else -> null
                        },
                        style = MoneyTypography.medium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Total amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$transactionCount",
                        style = MoneyTypography.medium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (transactionCount == 1) "Transaction" else "Transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
