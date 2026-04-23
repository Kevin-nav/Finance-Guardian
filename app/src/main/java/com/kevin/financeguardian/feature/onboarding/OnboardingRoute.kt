package com.kevin.financeguardian.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevin.financeguardian.ui.theme.spacing

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onRequestSmsPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onSetUpLater: () -> Unit = {},
) {
    var currentPage by remember { mutableIntStateOf(0) }

    val actions = when (currentPage) {
        0 -> OnboardingPageActions(
            primaryButtonText = "Get Started",
            onPrimaryClick = { currentPage = 1 },
            secondaryButtonText = "Skip setup",
            onSecondaryClick = onSetUpLater,
        )
        1 -> OnboardingPageActions(
            primaryButtonText = "Enable SMS Access",
            onPrimaryClick = {
                onRequestSmsPermission()
                currentPage = 2
            },
            secondaryButtonText = "Set up later",
            onSecondaryClick = { currentPage = 2 },
            showBackButton = true,
            onBackClick = { currentPage = 0 },
        )
        else -> OnboardingPageActions(
            primaryButtonText = "Enable Notifications",
            onPrimaryClick = {
                onRequestNotificationPermission()
                onSetUpLater()
            },
            secondaryButtonText = "Skip for now",
            onSecondaryClick = onSetUpLater,
            showBackButton = true,
            onBackClick = { currentPage = 1 },
        )
    }

    OnboardingPageScaffold(
        modifier = modifier,
        currentPage = currentPage,
        primaryButtonText = actions.primaryButtonText,
        onPrimaryClick = actions.onPrimaryClick,
        secondaryButtonText = actions.secondaryButtonText,
        onSecondaryClick = actions.onSecondaryClick,
        showBackButton = actions.showBackButton,
        onBackClick = actions.onBackClick,
    ) {
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "onboarding_page_body",
        ) { page ->
            when (page) {
                0 -> WelcomePageContent(modifier = Modifier.fillMaxSize())
                1 -> SmsPermissionPageContent(modifier = Modifier.fillMaxSize())
                else -> NotificationPageContent(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private data class OnboardingPageActions(
    val primaryButtonText: String,
    val onPrimaryClick: () -> Unit,
    val secondaryButtonText: String,
    val onSecondaryClick: () -> Unit,
    val showBackButton: Boolean = false,
    val onBackClick: () -> Unit = {},
)

// ── Page 1: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun WelcomePageContent(
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome_scale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome_glow",
    )

    OnboardingContentColumn(modifier = modifier) {
        Spacer(modifier = Modifier.height(spacing.xxl))
        Spacer(modifier = Modifier.height(spacing.xl))

        // Hero icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Finance Guardian",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        Text(
            text = "Finance Guardian",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = "Your finances, your device.\nPrivate, automatic transaction tracking that never leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xl))

        // Feature highlights
        BenefitCard(
            icon = Icons.Filled.Lock,
            title = "100% Private",
            description = "All data stored locally. No cloud, no third-party access.",
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        BenefitCard(
            icon = Icons.Filled.Speed,
            title = "Fully Automatic",
            description = "Transactions detected in real-time from SMS messages.",
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        BenefitCard(
            icon = Icons.Filled.TrackChanges,
            title = "Learns Over Time",
            description = "Gets smarter with every correction you make.",
        )
    }
}

// ── Page 2: SMS Permission ──────────────────────────────────────────────────

@Composable
private fun SmsPermissionPageContent(
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    val infiniteTransition = rememberInfiniteTransition(label = "sms_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sms_scale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sms_glow",
    )

    OnboardingContentColumn(modifier = modifier) {
        Spacer(modifier = Modifier.height(spacing.xxl))
        Spacer(modifier = Modifier.height(spacing.xl))

        // Hero icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Sms,
                    contentDescription = "SMS",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        Text(
            text = "Smart SMS Detection",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = "Finance Guardian reads your financial SMS messages to automatically track transactions. Your data never leaves your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xl))

        // What SMS access enables
        BenefitCard(
            icon = Icons.Filled.Speed,
            title = "Real-Time Detection",
            description = "Transactions appear instantly as SMS messages arrive.",
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        BenefitCard(
            icon = Icons.Filled.Lock,
            title = "Privacy First",
            description = "Only financial SMS is read. Nothing is uploaded or shared.",
        )
    }
}

// ── Page 3: Notifications ───────────────────────────────────────────────────

@Composable
private fun NotificationPageContent(
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    val infiniteTransition = rememberInfiniteTransition(label = "notif_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "notif_scale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "notif_glow",
    )

    OnboardingContentColumn(modifier = modifier) {
        Spacer(modifier = Modifier.height(spacing.xxl))
        Spacer(modifier = Modifier.height(spacing.xl))

        // Hero icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        Text(
            text = "Stay Updated",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = "Get notified when new transactions are detected so you can review and categorize them right away.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.xl))

        BenefitCard(
            icon = Icons.Filled.Notifications,
            title = "Instant Alerts",
            description = "See each new transaction the moment it's detected.",
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        BenefitCard(
            icon = Icons.Filled.CheckCircle,
            title = "Quick Corrections",
            description = "Tap a notification to review and fix categorization.",
        )
    }
}

// ── Shared Scaffolding ──────────────────────────────────────────────────────

@Composable
private fun OnboardingContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.lg))
    }
}

@Composable
private fun OnboardingPageScaffold(
    modifier: Modifier = Modifier,
    currentPage: Int,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            content()
        }

        // Page indicator dots
        PageIndicator(currentPage = currentPage, totalPages = 3)

        Spacer(modifier = Modifier.height(spacing.lg))

        // Primary CTA
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = primaryButtonText,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.height(spacing.xxs))

        // Secondary / Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showBackButton) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            if (showBackButton) {
                TextButton(onClick = onBackClick) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onSecondaryClick) {
                Text(
                    text = secondaryButtonText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))
    }
}

// ── Page Indicator ──────────────────────────────────────────────────────────

@Composable
private fun PageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalPages) { index ->
            val isActive = index == currentPage
            val targetWidth by animateFloatAsState(
                targetValue = if (isActive) 24f else 8f,
                animationSpec = tween(300),
                label = "dot_width_$index",
            )
            val targetAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.35f,
                animationSpec = tween(300),
                label = "dot_alpha_$index",
            )

            Box(
                modifier = Modifier
                    .width(targetWidth.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .alpha(targetAlpha)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ── Benefit Card ────────────────────────────────────────────────────────────

@Composable
private fun BenefitCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
