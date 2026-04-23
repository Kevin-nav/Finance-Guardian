package com.kevin.financeguardian.core.notifications

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyEngineTest {
    private val now = Instant.parse("2026-04-23T12:00:00Z")

    @Test
    fun reviewNeededEvents_useHighPrioritySystemSurface() {
        val engine = NotificationPolicyEngine()

        val decision = engine.decide(
            event = reviewEvent(),
            context = NotificationPolicyContext(now = now),
        )

        assertEquals(NotificationSurface.System, decision.surface)
        assertEquals(NotificationPriority.High, decision.priority)
        assertEquals("review_needed", decision.groupKey)
    }

    @Test
    fun transactionEvents_areSilentWhenRateLimited() {
        val engine = NotificationPolicyEngine(
            rateLimiter = NotificationRateLimiter { _, _ -> false },
        )

        val decision = engine.decide(
            event = transactionEvent(),
            context = NotificationPolicyContext(now = now),
        )

        assertEquals(NotificationSurface.Silent, decision.surface)
    }

    @Test
    fun insights_requireProactiveInsightsToBeEnabled() {
        val engine = NotificationPolicyEngine()

        val decision = engine.decide(
            event = NotificationEvent.InsightTriggered(
                insight = NotificationEvent.Insight.OutgoingBurstToday,
                summary = "You have more outgoing transactions than usual today.",
                occurredAt = now,
            ),
            context = NotificationPolicyContext(
                proactiveInsightsEnabled = false,
                now = now,
            ),
        )

        assertEquals(NotificationSurface.Silent, decision.surface)
    }

    @Test
    fun correctionSaved_isAnInAppNotice() {
        val engine = NotificationPolicyEngine()

        val decision = engine.decide(
            event = NotificationEvent.CorrectionSaved(
                transactionId = "txn-1",
                occurredAt = now,
            ),
            context = NotificationPolicyContext(now = now),
        )

        assertEquals(NotificationSurface.InApp, decision.surface)
        assertEquals("confirmations", decision.groupKey)
    }

    @Test
    fun permissionRevoked_staysSystemVisibleWhenNotificationsAreEnabled() {
        val engine = NotificationPolicyEngine()

        val decision = engine.decide(
            event = NotificationEvent.PermissionRevoked(
                permission = NotificationEvent.Permission.Sms,
                occurredAt = now,
            ),
            context = NotificationPolicyContext(
                notificationsEnabled = true,
                now = now,
            ),
        )

        assertEquals(NotificationSurface.System, decision.surface)
        assertTrue(decision.priority == NotificationPriority.High)
    }

    private fun reviewEvent(): NotificationEvent.TransactionNeedsReview =
        NotificationEvent.TransactionNeedsReview(
            transactionId = "txn-review",
            amountMinor = 2_400,
            currency = "GHS",
            merchantName = "Melcom",
            occurredAt = now,
        )

    private fun transactionEvent(): NotificationEvent.TransactionDetected =
        NotificationEvent.TransactionDetected(
            transactionId = "txn-detected",
            amountMinor = 2_400,
            currency = "GHS",
            merchantName = "Melcom",
            occurredAt = now,
        )
}
