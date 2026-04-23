package com.kevin.financeguardian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.financeguardian.core.notifications.InAppNotice
import com.kevin.financeguardian.core.notifications.InAppNoticeManager
import com.kevin.financeguardian.core.notifications.InAppNoticeSeverity
import com.kevin.financeguardian.ui.theme.spacing

@Composable
fun InAppNoticeHost(
    noticeManager: InAppNoticeManager,
    modifier: Modifier = Modifier,
    onActionClick: ((InAppNotice) -> Unit)? = null,
) {
    val notice by noticeManager.notice.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
    ) {
        val activeNotice = notice ?: return@AnimatedVisibility
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = MaterialTheme.spacing.xxs,
            color = activeNotice.severity.containerColor(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(activeNotice.severity.containerColor())
                    .padding(
                        horizontal = MaterialTheme.spacing.md,
                        vertical = MaterialTheme.spacing.sm,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = activeNotice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = activeNotice.severity.contentColor(),
                    modifier = Modifier.weight(1f),
                )

                if (activeNotice.actionLabel != null) {
                    TextButton(
                        onClick = {
                            onActionClick?.invoke(activeNotice)
                            noticeManager.dismiss(activeNotice.id)
                        },
                    ) {
                        Text(
                            text = activeNotice.actionLabel,
                            color = activeNotice.severity.contentColor(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppNoticeSeverity.containerColor() =
    when (this) {
        InAppNoticeSeverity.Info -> MaterialTheme.colorScheme.secondaryContainer
        InAppNoticeSeverity.Success -> MaterialTheme.colorScheme.primaryContainer
        InAppNoticeSeverity.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        InAppNoticeSeverity.Error -> MaterialTheme.colorScheme.errorContainer
    }

@Composable
private fun InAppNoticeSeverity.contentColor() =
    when (this) {
        InAppNoticeSeverity.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        InAppNoticeSeverity.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        InAppNoticeSeverity.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        InAppNoticeSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
