package com.kevin.financeguardian.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.kevin.financeguardian.ui.theme.MoneyTypography
import com.kevin.financeguardian.ui.theme.extendedColors
import java.text.NumberFormat
import java.util.Locale

/**
 * Displays a monetary amount with tabular figures, correct sign prefix,
 * and semantic coloring based on credit/debit direction.
 *
 * @param amountMinor Amount in minor units (e.g., pesewas). 12500 = GHS 125.00
 * @param currency ISO currency code, defaults to "GHS"
 * @param isCredit Whether this amount is a credit (income). Null = neutral color.
 * @param style Money typography variant to use
 * @param modifier Modifier for the Text composable
 */
@Composable
fun MoneyText(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    currency: String = "GHS",
    isCredit: Boolean? = null,
    style: TextStyle = MoneyTypography.small,
    overrideColor: Color? = null,
    visible: Boolean = true,
) {
    val extColors = MaterialTheme.extendedColors
    val color = overrideColor ?: when (isCredit) {
        true -> extColors.income
        false -> extColors.expense
        null -> MaterialTheme.colorScheme.onSurface
    }

    val amountMajor = amountMinor / 100.0
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val formatted = if (visible) formatter.format(amountMajor) else "•••••"
    val prefix = when (isCredit) {
        true -> "+"
        false -> "−"
        null -> ""
    }

    Text(
        text = if (visible) "$prefix$currency $formatted" else "$currency $formatted",
        style = style,
        color = color,
        modifier = modifier,
    )
}
