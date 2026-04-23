package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPatternDetectorTest {
    private val detector = RecurringPatternDetector()

    @Test
    fun detectsSubscriptionCandidateForSameMerchantSameAmountRegularInterval() {
        val patterns = detector.detect(
            listOf(
                transaction("spotify", "2026-01-01T10:00:00Z", 2_360),
                transaction("spotify", "2026-02-01T10:00:00Z", 2_360),
                transaction("spotify", "2026-03-01T10:00:00Z", 2_360),
            ),
        )

        assertEquals(1, patterns.size)
        assertEquals(RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE, patterns.single().kind)
        assertEquals(MoneyMovementType.SUBSCRIPTION_CANDIDATE, patterns.single().suggestedMoneyMovementType)
    }

    @Test
    fun detectsRecurringExpenseForVariableAmountsAtRegularIntervals() {
        val patterns = detector.detect(
            listOf(
                transaction("laundry", "2026-01-01T10:00:00Z", 12_000),
                transaction("laundry", "2026-01-08T10:00:00Z", 16_000),
                transaction("laundry", "2026-01-15T10:00:00Z", 13_000),
            ),
        )

        assertEquals(1, patterns.size)
        assertEquals(RecurringPattern.Kind.RECURRING_EXPENSE, patterns.single().kind)
    }

    @Test
    fun detectsIncomeSourceForRepeatedCredits() {
        val patterns = detector.detect(
            listOf(
                transaction("salary source", "2026-01-01T10:00:00Z", 120_000, TransactionDirection.CREDIT),
                transaction("salary source", "2026-02-01T10:00:00Z", 120_000, TransactionDirection.CREDIT),
                transaction("salary source", "2026-03-01T10:00:00Z", 120_000, TransactionDirection.CREDIT),
            ),
        )

        assertEquals(1, patterns.size)
        assertEquals(RecurringPattern.Kind.INCOME_SOURCE, patterns.single().kind)
        assertEquals(MoneyMovementType.INCOME, patterns.single().suggestedMoneyMovementType)
    }

    @Test
    fun ignoresSparseOrIrregularActivity() {
        val patterns = detector.detect(
            listOf(
                transaction("shop", "2026-01-01T10:00:00Z", 1_000),
                transaction("shop", "2026-01-20T10:00:00Z", 5_000),
                transaction("shop", "2026-03-20T10:00:00Z", 2_000),
            ),
        )

        assertTrue(patterns.isEmpty())
    }

    private fun transaction(
        counterpartyName: String,
        occurredAt: String,
        amountMinor: Long,
        direction: TransactionDirection = TransactionDirection.DEBIT,
    ): Transaction =
        Transaction(
            id = "$counterpartyName-$occurredAt",
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MTN MoMo",
            rawBodyHash = "hash-$counterpartyName-$occurredAt",
            occurredAt = Instant.parse(occurredAt),
            direction = direction,
            moneyMovementType = if (direction == TransactionDirection.CREDIT) {
                MoneyMovementType.INCOME
            } else {
                MoneyMovementType.EXPENSE
            },
            amountMinor = amountMinor,
            currency = "GHS",
            counterpartyName = counterpartyName,
            counterpartyPhone = null,
            reference = null,
            balanceAfterMinor = null,
            categoryId = null,
            confidence = 0.9f,
            createdAt = Instant.parse(occurredAt),
            updatedAt = Instant.parse(occurredAt),
        )
}
