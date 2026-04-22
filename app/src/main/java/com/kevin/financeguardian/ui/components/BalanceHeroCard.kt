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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Hero card displaying the total balance with a solid primary background
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(ext.balanceCardBackground)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = "Total Balance",
            style = MaterialTheme.typography.labelMedium,
            color = ext.onBalanceCard.copy(alpha = 0.8f),
        )

        MoneyText(
            amountMinor = totalBalanceMinor,
            currency = currency,
            style = MoneyTypography.large,
            overrideColor = ext.onBalanceCard,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            StatPill(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "Income",
                amountMinor = incomeMinor,
                tintColor = ext.income,
                currency = currency,
            )
            StatPill(
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                label = "Spent",
                amountMinor = expensesMinor,
                tintColor = ext.expense,
                currency = currency,
            )
            StatPill(
                icon = Icons.Filled.Savings,
                label = "Saved",
                amountMinor = savingsMinor,
                tintColor = ext.savings,
                currency = currency,
            )
        }
    }
}
