package com.kevin.financeguardian.core.notifications

import java.time.Instant

data class NotificationPolicyContext(
    val notificationsEnabled: Boolean = true,
    val proactiveInsightsEnabled: Boolean = true,
    val now: Instant = Instant.EPOCH,
)

fun interface NotificationRateLimiter {
    fun allow(key: String, now: Instant): Boolean
}

class NotificationPolicyEngine(
    private val rateLimiter: NotificationRateLimiter = NotificationRateLimiter { _, _ -> true },
) {
    fun decide(
        event: NotificationEvent,
        context: NotificationPolicyContext,
    ): NotificationDecision =
        when (event) {
            is NotificationEvent.TransactionDetected -> {
                if (!context.notificationsEnabled || !rateLimiter.allow(TRANSACTION_GROUP_KEY, context.now)) {
                    silentDecision(event)
                } else {
                    decision(
                        event = event,
                        groupKey = TRANSACTION_GROUP_KEY,
                    )
                }
            }

            is NotificationEvent.TransactionNeedsReview -> {
                if (!context.notificationsEnabled || !rateLimiter.allow(REVIEW_GROUP_KEY, context.now)) {
                    silentDecision(event)
                } else {
                    decision(
                        event = event,
                        groupKey = REVIEW_GROUP_KEY,
                    )
                }
            }

            is NotificationEvent.PermissionRevoked -> {
                if (!context.notificationsEnabled) {
                    silentDecision(event)
                } else {
                    decision(
                        event = event,
                        groupKey = PERMISSION_GROUP_KEY,
                    )
                }
            }

            is NotificationEvent.SecurityStateChanged -> {
                if (!context.notificationsEnabled) {
                    silentDecision(event)
                } else {
                    decision(
                        event = event,
                        groupKey = SECURITY_GROUP_KEY,
                    )
                }
            }

            is NotificationEvent.InsightTriggered -> {
                if (!context.notificationsEnabled ||
                    !context.proactiveInsightsEnabled ||
                    !rateLimiter.allow(INSIGHT_GROUP_KEY, context.now)
                ) {
                    silentDecision(event)
                } else {
                    decision(
                        event = event.copy(
                            summary = event.summary,
                        ),
                        priority = NotificationPriority.Low,
                        groupKey = INSIGHT_GROUP_KEY,
                    )
                }
            }

            is NotificationEvent.CorrectionSaved -> decision(
                event = event,
                surface = NotificationSurface.InApp,
                groupKey = CONFIRMATION_GROUP_KEY,
            )

            is NotificationEvent.PermissionGranted -> decision(
                event = event,
                surface = NotificationSurface.InApp,
                groupKey = CONFIRMATION_GROUP_KEY,
            )
        }

    private fun decision(
        event: NotificationEvent,
        priority: NotificationPriority = event.priority,
        surface: NotificationSurface = event.surface,
        groupKey: String,
    ): NotificationDecision =
        NotificationDecision(
            family = event.family,
            priority = priority,
            surface = surface,
            privacy = event.privacy,
            groupKey = groupKey,
        )

    private fun silentDecision(event: NotificationEvent): NotificationDecision =
        NotificationDecision(
            family = event.family,
            priority = event.priority,
            surface = NotificationSurface.Silent,
            privacy = event.privacy,
            groupKey = SILENT_GROUP_KEY,
        )

    private companion object {
        const val TRANSACTION_GROUP_KEY = "transactions"
        const val REVIEW_GROUP_KEY = "review_needed"
        const val PERMISSION_GROUP_KEY = "permissions"
        const val SECURITY_GROUP_KEY = "security"
        const val INSIGHT_GROUP_KEY = "insights"
        const val CONFIRMATION_GROUP_KEY = "confirmations"
        const val SILENT_GROUP_KEY = "silent"
    }
}
