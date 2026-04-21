package com.kevin.financeguardian.feature.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kevin.financeguardian.ui.components.EmptyState
import com.kevin.financeguardian.ui.theme.spacing
import androidx.compose.material3.MaterialTheme

@Composable
fun InsightsRoute(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = Icons.Filled.PieChart,
            title = "Insights Coming Soon",
            subtitle = "Spending breakdowns, trends, and budget tracking will appear here as your transaction data grows.",
        )
    }
}
