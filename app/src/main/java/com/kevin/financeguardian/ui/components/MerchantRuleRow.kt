package com.kevin.financeguardian.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing

/**
 * Compact row for a learned merchant → category mapping.
 * Shows the merchant name, an arrow, category icon + name,
 * and a thin confidence progress bar underneath.
 */
@Composable
fun MerchantRuleRow(
    merchantName: String,
    categoryName: String,
    confidence: Float,
    signalCount: Int,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors

    var animateBar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateBar = true }

    val animatedConfidence by animateFloatAsState(
        targetValue = if (animateBar) confidence else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = animationDelay,
        ),
        label = "confidence_$merchantName",
    )

    val confidenceColor = when {
        confidence >= 0.8f -> ext.income
        confidence >= 0.6f -> ext.warning
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Merchant name
            Text(
                text = merchantName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp),
            )

            // Category icon + name
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(
                    categoryName = categoryName,
                    size = 24.dp,
                    iconSize = 12.dp,
                )
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            // Signal count
            Text(
                text = "${signalCount}×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // Confidence bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedConfidence.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(confidenceColor),
            )
        }
    }
}
