package com.kevin.financeguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.MoneyTypography

/**
 * Small pill-shaped stat indicator used in the balance card.
 * Shows an icon, label, and monetary amount in a tinted capsule.
 */
@Composable
fun StatPill(
    icon: ImageVector,
    label: String,
    amountMinor: Long,
    tintColor: Color,
    modifier: Modifier = Modifier,
    currency: String = "GHS",
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        MoneyText(
            amountMinor = amountMinor,
            currency = currency,
            style = MoneyTypography.small.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            overrideColor = Color.White,
        )
    }
}
