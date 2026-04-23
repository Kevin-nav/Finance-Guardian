package com.kevin.financeguardian.core.notifications

import java.util.Locale
import javax.inject.Inject

class NotificationComposer @Inject constructor() {
    fun compose(
        event: NotificationEvent,
        decision: NotificationDecision,
        showAmountsOnLockScreen: Boolean,
    ): ComposedNotification =
        when (event) {
            is NotificationEvent.TransactionDetected -> composeTransactionDetected(
                event = event,
                decision = decision,
                showAmountsOnLockScreen = showAmountsOnLockScreen,
            )

            is NotificationEvent.TransactionNeedsReview -> composeReviewNeeded(
                event = event,
                decision = decision,
                showAmountsOnLockScreen = showAmountsOnLockScreen,
            )

            is NotificationEvent.PermissionRevoked -> composePermissionRevoked(event, decision)
            is NotificationEvent.SecurityStateChanged -> composeSecurityStateChanged(event, decision)
            is NotificationEvent.InsightTriggered -> composeInsightTriggered(event, decision)
            is NotificationEvent.CorrectionSaved -> composeCorrectionSaved(event, decision)
            is NotificationEvent.PermissionGranted -> composePermissionGranted(event, decision)
        }

    private fun composeTransactionDetected(
        event: NotificationEvent.TransactionDetected,
        decision: NotificationDecision,
        showAmountsOnLockScreen: Boolean,
    ): ComposedNotification {
        val amount = formatMoney(event.amountMinor, event.currency)
        val lockBody = if (showAmountsOnLockScreen) {
            "$amount recorded in Finance Guardian"
        } else {
            "A new transaction was recorded in Finance Guardian"
        }
        val merchant = event.merchantName?.takeIf { it.isNotBlank() } ?: "a recent activity"
        return ComposedNotification(
            systemTitle = "New transaction detected",
            lockScreenTitle = "New transaction detected",
            lockScreenBody = lockBody,
            unlockedBody = "$amount at $merchant",
            inAppMessage = "New transaction recorded",
            actionLabel = "Open",
            groupKey = decision.groupKey,
            channelId = "transactions",
            privacy = decision.privacy,
        )
    }

    private fun composeReviewNeeded(
        event: NotificationEvent.TransactionNeedsReview,
        decision: NotificationDecision,
        showAmountsOnLockScreen: Boolean,
    ): ComposedNotification {
        val amount = formatMoney(event.amountMinor, event.currency)
        val lockBody = if (showAmountsOnLockScreen) {
            "$amount needs your review"
        } else {
            "A transaction needs your review"
        }
        return ComposedNotification(
            systemTitle = "Transaction needs review",
            lockScreenTitle = "Transaction needs review",
            lockScreenBody = lockBody,
            unlockedBody = "$amount could not be fully categorized",
            inAppMessage = "A transaction needs review",
            actionLabel = "Review",
            groupKey = decision.groupKey,
            channelId = "review_needed",
            privacy = decision.privacy,
        )
    }

    private fun composePermissionRevoked(
        event: NotificationEvent.PermissionRevoked,
        decision: NotificationDecision,
    ): ComposedNotification {
        val (title, body, action) = when (event.permission) {
            NotificationEvent.Permission.Sms -> Triple(
                "SMS access turned off",
                "New transactions may stop appearing until access is restored.",
                "Fix",
            )

            NotificationEvent.Permission.Notifications -> Triple(
                "Notifications turned off",
                "Finance Guardian may not be able to alert you about new activity.",
                "Open Settings",
            )
        }
        return composedMessage(
            title = title,
            lockScreenBody = body,
            unlockedBody = body,
            inAppMessage = body,
            actionLabel = action,
            channelId = "security",
            decision = decision,
        )
    }

    private fun composeSecurityStateChanged(
        event: NotificationEvent.SecurityStateChanged,
        decision: NotificationDecision,
    ): ComposedNotification {
        val body = when (event.state) {
            NotificationEvent.SecurityState.AppLock -> {
                if (event.enabled) {
                    "App lock is now protecting your financial data."
                } else {
                    "Your financial data will open without authentication."
                }
            }

            NotificationEvent.SecurityState.ScreenPrivacy -> {
                if (event.enabled) {
                    "Screen privacy is now hiding content from screenshots and Recents."
                } else {
                    "Finance Guardian may appear in screenshots and Recents."
                }
            }
        }
        return composedMessage(
            title = "Security update",
            lockScreenBody = body,
            unlockedBody = body,
            inAppMessage = body,
            actionLabel = "Review",
            channelId = "security",
            decision = decision,
        )
    }

    private fun composeInsightTriggered(
        event: NotificationEvent.InsightTriggered,
        decision: NotificationDecision,
    ): ComposedNotification =
        composedMessage(
            title = when (event.insight) {
                NotificationEvent.Insight.OutgoingBurstToday -> "Spending is higher than usual today"
            },
            lockScreenBody = event.summary,
            unlockedBody = event.summary,
            inAppMessage = event.summary,
            actionLabel = "View",
            channelId = "insights",
            decision = decision,
        )

    private fun composeCorrectionSaved(
        event: NotificationEvent.CorrectionSaved,
        decision: NotificationDecision,
    ): ComposedNotification =
        composedMessage(
            title = "Correction saved",
            lockScreenBody = "Your transaction update was saved.",
            unlockedBody = "Your transaction update was saved.",
            inAppMessage = "Correction saved",
            actionLabel = "Open",
            channelId = "transactions",
            decision = decision,
        )

    private fun composePermissionGranted(
        event: NotificationEvent.PermissionGranted,
        decision: NotificationDecision,
    ): ComposedNotification {
        val message = when (event.permission) {
            NotificationEvent.Permission.Sms -> "SMS access enabled"
            NotificationEvent.Permission.Notifications -> "Notifications enabled"
        }
        return composedMessage(
            title = message,
            lockScreenBody = message,
            unlockedBody = message,
            inAppMessage = message,
            actionLabel = "Open",
            channelId = "transactions",
            decision = decision,
        )
    }

    private fun composedMessage(
        title: String,
        lockScreenBody: String,
        unlockedBody: String,
        inAppMessage: String,
        actionLabel: String,
        channelId: String,
        decision: NotificationDecision,
    ): ComposedNotification =
        ComposedNotification(
            systemTitle = title,
            lockScreenTitle = title,
            lockScreenBody = lockScreenBody,
            unlockedBody = unlockedBody,
            inAppMessage = inAppMessage,
            actionLabel = actionLabel,
            groupKey = decision.groupKey,
            channelId = channelId,
            privacy = decision.privacy,
        )

    private fun formatMoney(
        amountMinor: Long,
        currency: String,
    ): String = "$currency ${String.format(Locale.US, "%.2f", amountMinor / 100.0)}"
}
