package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Hero card displaying the total balance with a gradient background
 * and income/expense/savings stat pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceHeroCard(
    totalBalanceMinor: Long,
    incomeMinor: Long,
    expensesMinor: Long,
    savingsMinor: Long,
    modifier: Modifier = Modifier,
    currency: String = "GHS",
) {
    val ext = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing
    val gradient = Brush.linearGradient(
        colors = listOf(ext.balanceGradientStart, ext.balanceGradientEnd),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = "Total Balance",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )

        MoneyText(
            amountMinor = totalBalanceMinor,
            currency = currency,
            style = MoneyTypography.large,
            overrideColor = Color.White,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            StatPill(
                icon = Icons.Filled.TrendingUp,
                label = "Income",
                amountMinor = incomeMinor,
                tintColor = Color(0xFF6CD99B),
                currency = currency,
            )
            StatPill(
                icon = Icons.Filled.TrendingDown,
                label = "Spent",
                amountMinor = expensesMinor,
                tintColor = Color(0xFFFFB4AB),
                currency = currency,
            )
            StatPill(
                icon = Icons.Filled.Savings,
                label = "Saved",
                amountMinor = savingsMinor,
                tintColor = Color(0xFF8BCEFF),
                currency = currency,
            )
        }
    }
}
