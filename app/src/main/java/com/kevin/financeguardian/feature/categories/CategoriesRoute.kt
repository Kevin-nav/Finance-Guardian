package com.kevin.financeguardian.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.components.CategoryIcon
import com.kevin.financeguardian.ui.theme.spacing

// ── Preview data ────────────────────────────────────────────────────────────

private data class PreviewCategory(
    val name: String,
    val type: String,
    val transactionCount: Int,
)

private val previewCategories = listOf(
    PreviewCategory("Food", "Expense", 24),
    PreviewCategory("Transport", "Expense", 18),
    PreviewCategory("Airtime/Data", "Expense", 12),
    PreviewCategory("Bills", "Expense", 5),
    PreviewCategory("Subscriptions", "Expense", 3),
    PreviewCategory("Laundry", "Expense", 8),
    PreviewCategory("Family", "Transfer", 6),
    PreviewCategory("Transfers", "Transfer", 14),
    PreviewCategory("Income", "Income", 9),
    PreviewCategory("Savings", "Savings", 4),
    PreviewCategory("Unknown", "Expense", 7),
)

// ── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun CategoriesRoute(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val categories = remember { previewCategories }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Add category */ },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Category")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.md),
        ) {
            Spacer(modifier = Modifier.height(spacing.md))

            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            Text(
                text = "${categories.size} categories",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(spacing.md))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = PaddingValues(bottom = spacing.xxl),
            ) {
                items(
                    items = categories,
                    key = { it.name },
                ) { category ->
                    CategoryCard(category = category)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: PreviewCategory,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                CategoryIcon(
                    categoryName = category.name,
                    size = 36.dp,
                    iconSize = 18.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${category.transactionCount} txns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = category.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
