package com.kevin.financeguardian.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended color scheme for financial semantic colors that go beyond
 * the standard Material 3 palette. These represent domain-specific
 * color roles used throughout the Finance Guardian UI.
 */
@Immutable
data class ExtendedColorScheme(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val savings: Color,
    val transfer: Color,
    val warning: Color,
    val warningContainer: Color,
    val unknown: Color,
    val balanceCardBackground: Color,
    val onBalanceCard: Color,
    val matchedBadge: Color,
    val matchedBadgeContainer: Color,
    val pendingBadge: Color,
    val pendingBadgeContainer: Color,
    val reviewBadge: Color,
    val reviewBadgeContainer: Color,
    val neutralAmount: Color,
)

val LightExtendedColors = ExtendedColorScheme(
    income = IncomeLight,
    onIncome = OnIncomeLight,
    incomeContainer = IncomeContainerLight,
    expense = ExpenseLight,
    onExpense = OnExpenseLight,
    expenseContainer = ExpenseContainerLight,
    savings = SavingsLight,
    transfer = TransferLight,
    warning = WarningLight,
    warningContainer = WarningContainerLight,
    unknown = UnknownLight,
    balanceCardBackground = BalanceCardBackgroundLight,
    onBalanceCard = OnBalanceCardLight,
    matchedBadge = MatchedBadgeLight,
    matchedBadgeContainer = MatchedBadgeContainerLight,
    pendingBadge = PendingBadgeLight,
    pendingBadgeContainer = PendingBadgeContainerLight,
    reviewBadge = ReviewBadgeLight,
    reviewBadgeContainer = ReviewBadgeContainerLight,
    neutralAmount = NeutralAmountLight,
)

val DarkExtendedColors = ExtendedColorScheme(
    income = IncomeDark,
    onIncome = OnIncomeDark,
    incomeContainer = IncomeContainerDark,
    expense = ExpenseDark,
    onExpense = OnExpenseDark,
    expenseContainer = ExpenseContainerDark,
    savings = SavingsDark,
    transfer = TransferDark,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    unknown = UnknownDark,
    balanceCardBackground = BalanceCardBackgroundDark,
    onBalanceCard = OnBalanceCardDark,
    matchedBadge = MatchedBadgeDark,
    matchedBadgeContainer = MatchedBadgeContainerDark,
    pendingBadge = PendingBadgeDark,
    pendingBadgeContainer = PendingBadgeContainerDark,
    reviewBadge = ReviewBadgeDark,
    reviewBadgeContainer = ReviewBadgeContainerDark,
    neutralAmount = NeutralAmountDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
