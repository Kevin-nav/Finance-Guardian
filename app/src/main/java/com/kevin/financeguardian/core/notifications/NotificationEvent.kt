package com.kevin.financeguardian.core.notifications

import java.time.Instant

sealed interface NotificationEvent {
    val family: NotificationFamily
    val priority: NotificationPriority
    val surface: NotificationSurface
    val privacy: NotificationPrivacy
    val occurredAt: Instant

    data class TransactionDetected(
        val transactionId: String,
        val amountMinor: Long,
        val currency: String,
        val merchantName: String?,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.TransactionDetected
        override val priority: NotificationPriority = NotificationPriority.Default
        override val surface: NotificationSurface = NotificationSurface.System
        override val privacy: NotificationPrivacy = NotificationPrivacy.AmountOnly
    }

    data class TransactionNeedsReview(
        val transactionId: String,
        val amountMinor: Long,
        val currency: String,
        val merchantName: String?,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.ReviewNeeded
        override val priority: NotificationPriority = NotificationPriority.High
        override val surface: NotificationSurface = NotificationSurface.System
        override val privacy: NotificationPrivacy = NotificationPrivacy.AmountOnly
    }

    data class PermissionRevoked(
        val permission: Permission,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.Permissions
        override val priority: NotificationPriority = NotificationPriority.High
        override val surface: NotificationSurface = NotificationSurface.System
        override val privacy: NotificationPrivacy = NotificationPrivacy.Private
    }

    data class SecurityStateChanged(
        val state: SecurityState,
        val enabled: Boolean,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.Security
        override val priority: NotificationPriority = NotificationPriority.High
        override val surface: NotificationSurface = NotificationSurface.System
        override val privacy: NotificationPrivacy = NotificationPrivacy.Private
    }

    data class InsightTriggered(
        val insight: Insight,
        val summary: String,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.Insight
        override val priority: NotificationPriority = NotificationPriority.Default
        override val surface: NotificationSurface = NotificationSurface.System
        override val privacy: NotificationPrivacy = NotificationPrivacy.Private
    }

    data class CorrectionSaved(
        val transactionId: String,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.Confirmation
        override val priority: NotificationPriority = NotificationPriority.Default
        override val surface: NotificationSurface = NotificationSurface.InApp
        override val privacy: NotificationPrivacy = NotificationPrivacy.Private
    }

    data class PermissionGranted(
        val permission: Permission,
        override val occurredAt: Instant,
    ) : NotificationEvent {
        override val family: NotificationFamily = NotificationFamily.Permissions
        override val priority: NotificationPriority = NotificationPriority.Default
        override val surface: NotificationSurface = NotificationSurface.InApp
        override val privacy: NotificationPrivacy = NotificationPrivacy.Private
    }

    enum class Permission {
        Sms,
        Notifications,
    }

    enum class SecurityState {
        AppLock,
        ScreenPrivacy,
    }

    enum class Insight {
        OutgoingBurstToday,
    }
}
