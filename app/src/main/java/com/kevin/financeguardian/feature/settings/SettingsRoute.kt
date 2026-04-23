package com.kevin.financeguardian.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kevin.financeguardian.ui.theme.extendedColors
import com.kevin.financeguardian.ui.theme.spacing
import kotlinx.coroutines.delay

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onRequestSmsPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    // Easter egg: tap version 7 times to reveal developer tools
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showDevTools by remember { mutableStateOf(false) }

    // Staggered animation states
    var showSection0 by remember { mutableStateOf(false) }
    var showSection1 by remember { mutableStateOf(false) }
    var showSection2 by remember { mutableStateOf(false) }
    var showSection3 by remember { mutableStateOf(false) }
    var showSection4 by remember { mutableStateOf(false) }
    var showSection5 by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showSection0 = true
        delay(60)
        showSection1 = true
        delay(60)
        showSection2 = true
        delay(60)
        showSection3 = true
        delay(60)
        showSection4 = true
        delay(60)
        showSection5 = true
    }

    val fixturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (json != null) {
                viewModel.importFixtureJson(json)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = "Reset All Data?",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all transactions, merchants, and categories. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    },
                    enabled = !uiState.isResetInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.md))

        // ── Profile Header ──────────────────────────────────────────────
        AnimatedSettingsSection(visible = showSection0, index = 0) {
            SettingsProfileHeader()
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        // ── Security & Privacy ──────────────────────────────────────────
        AnimatedSettingsSection(visible = showSection1, index = 1) {
            SettingsSectionLabel(text = "Security & Privacy")

            SettingsGroupCard {
                SettingsToggleRow(
                    icon = Icons.Filled.Fingerprint,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "App Lock",
                    subtitle = "Require biometric or PIN to open app",
                    checked = uiState.appLockEnabled,
                    onCheckedChange = viewModel::setAppLockEnabled,
                )

                SettingsRowDivider()

                SettingsToggleRow(
                    icon = Icons.Filled.VisibilityOff,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Screen Privacy",
                    subtitle = "Hide content in screenshots and Recents",
                    checked = uiState.screenPrivacyEnabled,
                    onCheckedChange = viewModel::setScreenPrivacyEnabled,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // ── Permissions ─────────────────────────────────────────────────
        AnimatedSettingsSection(visible = showSection2, index = 2) {
            SettingsSectionLabel(text = "Permissions")

            SettingsGroupCard {
                SettingsPermissionRow(
                    icon = Icons.Filled.Sms,
                    iconTint = MaterialTheme.extendedColors.income,
                    title = "SMS Access",
                    description = "Required to automatically detect incoming transactions from your bank",
                    isGranted = uiState.permissions.receiveSmsGranted,
                    onEnableClick = onRequestSmsPermission,
                )

                SettingsRowDivider()

                SettingsPermissionRow(
                    icon = Icons.Filled.Notifications,
                    iconTint = MaterialTheme.extendedColors.income,
                    title = "Notifications",
                    description = "Get notified when new transactions are detected",
                    isGranted = uiState.permissions.postNotificationsGranted,
                    onEnableClick = onRequestNotificationPermission,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // ── Appearance (placeholder) ────────────────────────────────────
        AnimatedSettingsSection(visible = showSection3, index = 3) {
            SettingsSectionLabel(text = "Notifications & Alerts")

            SettingsGroupCard {
                SettingsToggleRow(
                    icon = Icons.Filled.Notifications,
                    iconTint = MaterialTheme.extendedColors.income,
                    title = "Notifications",
                    subtitle = "Allow Finance Guardian to send alerts for important activity",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )

                SettingsRowDivider()

                SettingsToggleRow(
                    icon = Icons.Filled.Info,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Proactive insights",
                    subtitle = "Send occasional high-signal nudges when spending spikes",
                    checked = uiState.proactiveInsightsEnabled,
                    onCheckedChange = viewModel::setProactiveInsightsEnabled,
                )

                SettingsRowDivider()

                SettingsToggleRow(
                    icon = Icons.Filled.Lock,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "Show amounts on lock screen",
                    subtitle = "Keep transaction amounts visible while hiding merchant details",
                    checked = uiState.showAmountsOnLockScreen,
                    onCheckedChange = viewModel::setShowAmountsOnLockScreen,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // ── About ───────────────────────────────────────────────────────
        AnimatedSettingsSection(visible = showSection4, index = 4) {
            SettingsSectionLabel(text = "Appearance")

            SettingsGroupCard {
                SettingsInfoRow(
                    icon = Icons.Filled.DarkMode,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "Theme",
                    value = "System Default",
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        AnimatedSettingsSection(visible = showSection5, index = 5) {
            SettingsSectionLabel(text = "About")

            SettingsGroupCard {
                SettingsInfoRow(
                    icon = Icons.Filled.Info,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Version",
                    value = "0.1.0 (MVP)",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        versionTapCount++
                        if (versionTapCount >= 7) {
                            showDevTools = true
                        }
                    },
                )

                SettingsRowDivider()

                // Privacy badge row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    SettingsIconCircle(
                        icon = Icons.Filled.Shield,
                        tint = MaterialTheme.extendedColors.income,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Private by Design",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "All your data stays on this device. No accounts, no cloud, no tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsRowDivider()

                SettingsInfoRow(
                    icon = Icons.Filled.Code,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Licenses",
                    value = "Open Source",
                )
            }
        }

        // ── Developer Tools (hidden behind easter egg) ──────────────────
        AnimatedVisibility(
            visible = showDevTools,
            enter = expandVertically(tween(400)) + fadeIn(tween(400)),
        ) {
            Column {
                Spacer(modifier = Modifier.height(spacing.md))

                SettingsSectionLabel(text = "Developer Tools")

                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Build,
                        iconTint = MaterialTheme.extendedColors.warning,
                        title = "Parser Debug Mode",
                        subtitle = "Enable fixture SMS import for testing",
                        checked = uiState.debugParserModeEnabled,
                        onCheckedChange = viewModel::setDebugParserModeEnabled,
                    )

                    if (uiState.debugParserModeEnabled) {
                        SettingsRowDivider()

                        SettingsActionRow(
                            icon = Icons.Filled.FileDownload,
                            iconTint = MaterialTheme.extendedColors.warning,
                            title = "Import SMS Fixtures",
                            subtitle = "Load anonymized JSON through the SMS ingestion backend",
                            actionLabel = "Import",
                            onActionClick = {
                                fixturePicker.launch(arrayOf("application/json", "text/*"))
                            },
                        )
                    }

                    SettingsRowDivider()

                    // Danger zone: Reset All Data
                    SettingsActionRow(
                        icon = Icons.Filled.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Reset All Data",
                        subtitle = "Delete all transactions, merchants, and categories",
                        actionLabel = "Reset",
                        isDestructive = true,
                        enabled = !uiState.isResetInProgress,
                        onActionClick = { showResetDialog = true },
                    )

                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.xxl))
    }
}

// ── Reusable Settings Components ────────────────────────────────────────────

/**
 * App branding header with avatar circle and tagline.
 */
@Composable
private fun SettingsProfileHeader(
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar circle with app icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(spacing.sm))

        Text(
            text = "Finance Guardian",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xxs))

        Text(
            text = "Your private financial companion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Section label placed above a group card.
 */
@Composable
private fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(
            start = MaterialTheme.spacing.xxs,
            bottom = MaterialTheme.spacing.xs,
        ),
    )
}

/**
 * Filled card container for a group of settings rows.
 * Uses surfaceContainerLow with rounded corners — no outline border.
 */
@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm,
            ),
    ) {
        content()
    }
}

/**
 * Small colored circle containing an icon — the signature visual element.
 */
@Composable
private fun SettingsIconCircle(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

/**
 * Subtle divider between rows within a group card.
 */
@Composable
private fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * Toggle row with colored icon circle, title, subtitle, and switch.
 */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SettingsIconCircle(icon = icon, tint = iconTint)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

/**
 * Permission row with contextual description and enable button.
 */
@Composable
private fun SettingsPermissionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isGranted: Boolean,
    onEnableClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val ext = MaterialTheme.extendedColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SettingsIconCircle(icon = icon, tint = iconTint)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Status indicator
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = if (isGranted) "Granted" else "Not granted",
                    tint = if (isGranted) ext.income else ext.expense,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isGranted && onEnableClick != null) {
                Spacer(modifier = Modifier.height(spacing.xs))
                TextButton(
                    onClick = onEnableClick,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * Simple info row: icon + label + value.
 */
@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SettingsIconCircle(icon = icon, tint = iconTint)

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Action row with a button (used for import, reset, etc.).
 */
@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SettingsIconCircle(icon = icon, tint = iconTint)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onActionClick,
            enabled = enabled,
            colors = if (isDestructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Text(actionLabel)
        }
    }
}

/**
 * Wrapper for staggered entrance animation per section.
 */
@Composable
private fun AnimatedSettingsSection(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = index * 60,
            ),
        ) + slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = index * 60,
            ),
        ),
    ) {
        Column {
            content()
        }
    }
}
