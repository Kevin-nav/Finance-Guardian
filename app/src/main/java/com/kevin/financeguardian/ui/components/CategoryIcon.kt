package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.extendedColors

/**
 * Category icon displayed inside a tinted circle. Used in transaction rows
 * and the categories grid.
 */
@Composable
fun CategoryIcon(
    categoryName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val (icon, tint) = categoryVisual(categoryName)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = categoryName,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Maps category names to their (icon, color) pairs.
 */
@Composable
private fun categoryVisual(categoryName: String): Pair<ImageVector, Color> {
    val ext = MaterialTheme.extendedColors
    val primary = MaterialTheme.colorScheme.primary

    return when (categoryName.lowercase()) {
        "food" -> Icons.Filled.Restaurant to Color(0xFFE67E22)
        "transport" -> Icons.Filled.DirectionsCar to Color(0xFF3498DB)
        "airtime/data", "airtime", "data" -> Icons.Filled.PhoneAndroid to Color(0xFF9B59B6)
        "bills" -> Icons.Filled.Receipt to Color(0xFFE74C3C)
        "subscriptions" -> Icons.Filled.Autorenew to ext.warning
        "laundry" -> Icons.Filled.LocalLaundryService to Color(0xFF1ABC9C)
        "family" -> Icons.Filled.FamilyRestroom to Color(0xFFE91E63)
        "transfers" -> Icons.Filled.SwapHoriz to ext.transfer
        "income" -> Icons.AutoMirrored.Filled.TrendingUp to ext.income
        "savings" -> Icons.Filled.Savings to primary
        "unknown" -> Icons.AutoMirrored.Filled.HelpOutline to ext.unknown
        else -> Icons.Filled.Category to MaterialTheme.colorScheme.secondary
    }
}
