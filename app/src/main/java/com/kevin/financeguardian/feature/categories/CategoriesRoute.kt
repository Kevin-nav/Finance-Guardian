package com.kevin.financeguardian.feature.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.ui.components.CategoryIcon
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(
    onCategoryClick: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val spacing = MaterialTheme.spacing
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.editor?.let { editor ->
        CategoryEditorSheet(
            editor = editor,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveCategory,
            onArchive = viewModel::archiveEditingCategory,
        )
    }

    var fabVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fabVisible = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = scaleIn(
                    initialScale = 0.6f,
                    animationSpec = tween(durationMillis = 400, delayMillis = 300),
                ) + fadeIn(animationSpec = tween(durationMillis = 400, delayMillis = 300)),
            ) {
                FloatingActionButton(
                    onClick = viewModel::startAddCategory,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Category")
                }
            }
        },
    ) { innerPadding ->
        if (uiState.categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Category,
                    title = "No Categories Yet",
                    subtitle = "Categories will appear automatically as transactions are detected and categorized.",
                    action = {
                        Button(onClick = viewModel::startAddCategory) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text("Create Category")
                        }
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = spacing.xxl + 72.dp),
            ) {
                // ── Header ──────────────────────────────────────────────
                item(key = "header") {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = spacing.md,
                            vertical = spacing.md,
                        ),
                    ) {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        Text(
                            text = "Organize and track your spending",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Summary Card ────────────────────────────────────────
                item(key = "summary") {
                    CategorySummaryCard(
                        totalCount = uiState.totalCount,
                        expenseCount = uiState.expenseCount,
                        incomeCount = uiState.incomeCount,
                        transferCount = uiState.transferCount,
                        savingsCount = uiState.savingsCount,
                        modifier = Modifier.padding(horizontal = spacing.md),
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                }

                // ── Filter Chips ────────────────────────────────────────
                item(key = "filters") {
                    CategoryFilterChipRow(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = viewModel::selectFilter,
                        expenseCount = uiState.expenseCount,
                        incomeCount = uiState.incomeCount,
                        transferCount = uiState.transferCount,
                        savingsCount = uiState.savingsCount,
                        modifier = Modifier.padding(
                            horizontal = spacing.md,
                            vertical = spacing.xs,
                        ),
                    )
                }

                // ── Category Rows ───────────────────────────────────────
                if (uiState.filteredCategories.isEmpty()) {
                    item(key = "filtered_empty") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.4f)
                                .fillParentMaxWidth()
                                .padding(horizontal = spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                icon = Icons.Filled.Category,
                                title = "No ${uiState.selectedFilter.label.lowercase()} categories",
                                subtitle = "You don't have any categories of this type yet.",
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = uiState.filteredCategories,
                    key = { _, item -> item.id },
                ) { index, category ->
                    CategoryListRow(
                        category = category,
                        onClick = { onCategoryClick(category.id) },
                        onEditClick = {
                            if (category.canEdit) viewModel.startEditCategory(category.id)
                        },
                        showDivider = index < uiState.filteredCategories.lastIndex,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = spacing.md),
                    )
                }
            }
        }
    }
}

// ── Summary Card ────────────────────────────────────────────────────────────

@Composable
private fun CategorySummaryCard(
    totalCount: Int,
    expenseCount: Int,
    incomeCount: Int,
    transferCount: Int,
    savingsCount: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors

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
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "$totalCount",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (totalCount == 1) "category" else "categories",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                SummaryDot(
                    color = ext.expense,
                    label = "Expenses",
                    count = expenseCount,
                    modifier = Modifier.weight(1f),
                )
                SummaryDot(
                    color = ext.income,
                    label = "Income",
                    count = incomeCount,
                    modifier = Modifier.weight(1f),
                )
                SummaryDot(
                    color = ext.transfer,
                    label = "Transfers",
                    count = transferCount,
                    modifier = Modifier.weight(1f),
                )
                SummaryDot(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Savings",
                    count = savingsCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryDot(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Filter Chip Row ─────────────────────────────────────────────────────────

@Composable
private fun CategoryFilterChipRow(
    selectedFilter: CategoryTypeFilter,
    onFilterSelected: (CategoryTypeFilter) -> Unit,
    expenseCount: Int,
    incomeCount: Int,
    transferCount: Int,
    savingsCount: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        CategoryTypeFilter.entries.forEach { filter ->
            val count = when (filter) {
                CategoryTypeFilter.All -> null
                CategoryTypeFilter.Expense -> expenseCount
                CategoryTypeFilter.Income -> incomeCount
                CategoryTypeFilter.Transfer -> transferCount
                CategoryTypeFilter.Savings -> savingsCount
            }
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = if (count != null) "${filter.label} ($count)" else filter.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

// ── Category List Row ───────────────────────────────────────────────────────

@Composable
private fun CategoryListRow(
    category: CategoryListItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors

    val typePillColor = when (category.type) {
        CategoryType.EXPENSE -> ext.expense
        CategoryType.INCOME -> ext.income
        CategoryType.TRANSFER -> ext.transfer
        CategoryType.SAVINGS -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CategoryIcon(
                categoryName = category.name,
                size = 44.dp,
                iconSize = 22.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Type pill badge
                    Text(
                        text = category.typeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = typePillColor,
                        modifier = Modifier
                            .background(
                                color = typePillColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "${category.transactionCount} " +
                            if (category.transactionCount == 1) "transaction" else "transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 44.dp + spacing.sm),
            )
        }
    }
}

// ── Category Editor Bottom Sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    editor: CategoryEditorUiState,
    onDismiss: () -> Unit,
    onSave: (String, CategoryType) -> Unit,
    onArchive: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(editor.id, editor.name) { mutableStateOf(editor.name) }
    var selectedType by remember(editor.id, editor.selectedType) {
        mutableStateOf(editor.selectedType)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // ── Header with Cancel / Title / Save ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Text(
                    text = editor.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { onSave(name, selectedType) },
                    enabled = name.isNotBlank(),
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Name Field ──────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            // ── Type Selector ───────────────────────────────────────
            Text(
                text = "TYPE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                editor.typeOptions.forEach { option ->
                    FilterChip(
                        selected = selectedType == option.type,
                        onClick = { selectedType = option.type },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }

            // ── Error Message ───────────────────────────────────────
            if (editor.errorMessage != null) {
                Text(
                    text = editor.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(spacing.sm),
                )
            }

            // ── Archive Button ──────────────────────────────────────
            if (editor.canArchive) {
                Spacer(modifier = Modifier.height(spacing.xs))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(spacing.xs))
                OutlinedButton(
                    onClick = onArchive,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text("Archive Category")
                }
            }
        }
    }
}
