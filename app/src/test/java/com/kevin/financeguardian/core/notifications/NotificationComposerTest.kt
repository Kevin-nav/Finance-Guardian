package com.kevin.financeguardian.core.notifications

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationComposerTest {
    private val composer = NotificationComposer()
    private val now = Instant.parse("2026-04-23T12:00:00Z")

    @Test
    fun transactionDetected_hidesMerchantOnLockScreen() {
        val composed = composer.compose(
            event = NotificationEvent.TransactionDetected(
                transactionId = "txn-1",
                amountMinor = 2_400,
                currency = "GHS",
                merchantName = "Melcom",
                occurredAt = now,
            ),
            decision = NotificationDecision(
                family = NotificationFamily.TransactionDetected,
                priority = NotificationPriority.Default,
                surface = NotificationSurface.System,
                privacy = NotificationPrivacy.AmountOnly,
                groupKey = "transactions",
            ),
            showAmountsOnLockScreen = true,
        )

        assertEquals("New transaction detected", composed.lockScreenTitle)
        assertEquals("GHS 24.00 recorded in Finance Guardian", composed.lockScreenBody)
        assertEquals("GHS 24.00 at Melcom", composed.unlockedBody)
        assertEquals("Open", composed.actionLabel)
    }

    @Test
    fun transactionDetected_canHideAmountsOnLockScreen() {
        val composed = composer.compose(
            event = NotificationEvent.TransactionDetected(
                transactionId = "txn-1",
                amountMinor = 2_400,
                currency = "GHS",
                merchantName = "Melcom",
                occurredAt = now,
            ),
            decision = NotificationDecision(
                family = NotificationFamily.TransactionDetected,
                priority = NotificationPriority.Default,
                surface = NotificationSurface.System,
                privacy = NotificationPrivacy.AmountOnly,
                groupKey = "transactions",
            ),
            showAmountsOnLockScreen = false,
        )

        assertEquals("A new transaction was recorded in Finance Guardian", composed.lockScreenBody)
    }

    @Test
    fun reviewNeeded_usesActionOrientedCopy() {
        val composed = composer.compose(
            event = NotificationEvent.TransactionNeedsReview(
                transactionId = "txn-review",
                amountMinor = 2_400,
                currency = "GHS",
                merchantName = null,
                occurredAt = now,
            ),
            decision = NotificationDecision(
                family = NotificationFamily.ReviewNeeded,
                priority = NotificationPriority.High,
                surface = NotificationSurface.System,
                privacy = NotificationPrivacy.AmountOnly,
                groupKey = "review_needed",
            ),
            showAmountsOnLockScreen = true,
        )

        assertEquals("Transaction needs review", composed.systemTitle)
        assertEquals("GHS 24.00 needs your review", composed.lockScreenBody)
        assertEquals("GHS 24.00 could not be fully categorized", composed.unlockedBody)
        assertEquals("Review", composed.actionLabel)
    }
}
