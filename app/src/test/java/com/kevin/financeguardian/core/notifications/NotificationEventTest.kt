package com.kevin.financeguardian.core.notifications

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventTest {
    @Test
    fun reviewNeededEvent_carriesTransactionContextWithoutLockScreenDetails() {
        val event = NotificationEvent.TransactionNeedsReview(
            transactionId = "txn-1",
            amountMinor = 2400,
            currency = "GHS",
            merchantName = "Melcom",
            occurredAt = Instant.parse("2026-04-23T10:15:30Z"),
        )

        assertEquals("txn-1", event.transactionId)
        assertEquals(NotificationFamily.ReviewNeeded, event.family)
        assertEquals(NotificationPriority.High, event.priority)
        assertEquals(NotificationSurface.System, event.surface)
        assertEquals(NotificationPrivacy.AmountOnly, event.privacy)
    }

    @Test
    fun permissionGrantedEvent_defaultsToInAppConfirmation() {
        val event = NotificationEvent.PermissionGranted(
            permission = NotificationEvent.Permission.Sms,
            occurredAt = Instant.parse("2026-04-23T11:00:00Z"),
        )

        assertEquals(NotificationFamily.Permissions, event.family)
        assertEquals(NotificationPriority.Default, event.priority)
        assertEquals(NotificationSurface.InApp, event.surface)
        assertEquals(NotificationPrivacy.Private, event.privacy)
    }

    @Test
    fun sealedHierarchy_coversApprovedV1Signals() {
        val now = Instant.parse("2026-04-23T12:00:00Z")
        val events = listOf(
            NotificationEvent.TransactionDetected(
                transactionId = "txn-1",
                amountMinor = 1200,
                currency = "GHS",
                merchantName = "Cafe",
                occurredAt = now,
            ),
            NotificationEvent.TransactionNeedsReview(
                transactionId = "txn-2",
                amountMinor = 4500,
                currency = "GHS",
                merchantName = null,
                occurredAt = now,
            ),
            NotificationEvent.PermissionRevoked(
                permission = NotificationEvent.Permission.Sms,
                occurredAt = now,
            ),
            NotificationEvent.SecurityStateChanged(
                state = NotificationEvent.SecurityState.AppLock,
                enabled = false,
                occurredAt = now,
            ),
            NotificationEvent.InsightTriggered(
                insight = NotificationEvent.Insight.OutgoingBurstToday,
                summary = "Higher outgoing transaction volume than usual.",
                occurredAt = now,
            ),
            NotificationEvent.CorrectionSaved(
                transactionId = "txn-3",
                occurredAt = now,
            ),
            NotificationEvent.PermissionGranted(
                permission = NotificationEvent.Permission.Notifications,
                occurredAt = now,
            ),
        )

        assertEquals(7, events.size)
        assertTrue(events.any { it is NotificationEvent.TransactionDetected })
        assertTrue(events.any { it is NotificationEvent.TransactionNeedsReview })
        assertTrue(events.any { it is NotificationEvent.PermissionRevoked })
        assertTrue(events.any { it is NotificationEvent.SecurityStateChanged })
        assertTrue(events.any { it is NotificationEvent.InsightTriggered })
        assertTrue(events.any { it is NotificationEvent.CorrectionSaved })
        assertTrue(events.any { it is NotificationEvent.PermissionGranted })
    }
}
