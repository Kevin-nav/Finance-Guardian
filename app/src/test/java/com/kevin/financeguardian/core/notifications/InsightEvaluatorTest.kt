package com.kevin.financeguardian.core.notifications

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightEvaluatorTest {
    private val evaluator = InsightEvaluator()
    private val now = Instant.parse("2026-04-23T18:00:00Z")

    @Test
    fun highOutgoingBurst_triggersSingleInsightEvent() {
        val result = evaluator.evaluate(
            transactions = sampleBurstTransactions(),
            now = now,
        )

        assertEquals(NotificationEvent.Insight.OutgoingBurstToday, result?.kind)
    }

    @Test
    fun lowOutgoingVolume_doesNotTriggerInsight() {
        val result = evaluator.evaluate(
            transactions = listOf(
                transaction("today-1", now.minusSeconds(60 * 60)),
                transaction("today-2", now.minusSeconds(2 * 60 * 60)),
                transaction("today-3", now.minusSeconds(3 * 60 * 60)),
            ),
            now = now,
        )

        assertNull(result)
    }

    private fun sampleBurstTransactions(): List<Transaction> =
        listOf(
            transaction("history-1", Instant.parse("2026-04-21T10:00:00Z")),
            transaction("history-2", Instant.parse("2026-04-21T12:00:00Z")),
            transaction("history-3", Instant.parse("2026-04-22T09:00:00Z")),
            transaction("today-1", Instant.parse("2026-04-23T09:00:00Z")),
            transaction("today-2", Instant.parse("2026-04-23T10:00:00Z")),
            transaction("today-3", Instant.parse("2026-04-23T12:00:00Z")),
            transaction("today-4", Instant.parse("2026-04-23T14:00:00Z")),
        )

    private fun transaction(
        id: String,
        occurredAt: Instant,
    ): Transaction =
        Transaction(
            id = id,
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MobileMoney",
            rawBodyHash = "hash-$id",
            occurredAt = occurredAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 2_400,
            currency = "GHS",
            counterpartyName = "Merchant $id",
            counterpartyPhone = null,
            reference = "R-$id",
            balanceAfterMinor = null,
            categoryId = "food",
            confidence = 0.9f,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )
}
