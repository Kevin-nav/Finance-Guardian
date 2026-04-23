package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningFeatureExtractorTest {
    @Test
    fun fromTransactionNormalizesMerchantPhoneAndReference() {
        val features = LearningFeatureExtractor.fromTransaction(
            transaction(
                counterpartyName = "  Shoprite - OSU, Mall!! ",
                counterpartyPhone = "+233 24-***-1234",
                reference = " Fried Rice ",
            ),
        )

        assertEquals("shoprite osu mall", features.normalizedMerchantName)
        assertEquals("23324***1234", features.normalizedPhone)
        assertEquals("fried rice", features.normalizedReference)
    }

    @Test
    fun amountBucketMapsExpectedRanges() {
        assertEquals("micro", LearningFeatureExtractor.amountBucket(499))
        assertEquals("small", LearningFeatureExtractor.amountBucket(500))
        assertEquals("small", LearningFeatureExtractor.amountBucket(2_999))
        assertEquals("medium", LearningFeatureExtractor.amountBucket(3_000))
        assertEquals("medium", LearningFeatureExtractor.amountBucket(14_999))
        assertEquals("large", LearningFeatureExtractor.amountBucket(15_000))
    }

    @Test
    fun signalKeysAreStableAndProviderScoped() {
        assertEquals(
            "merchant|mtn_momo|batamadwom enterprise",
            LearningFeatureExtractor.merchantSignalKey(
                provider = Provider.MTN_MOMO,
                normalizedMerchantName = "batamadwom enterprise",
            ),
        )
        assertEquals(
            "phone|telecel_cash|233244000111",
            LearningFeatureExtractor.phoneSignalKey(
                provider = Provider.TELECEL_CASH,
                normalizedPhone = "233244000111",
            ),
        )
        assertEquals(
            "reference|gcb|snacks",
            LearningFeatureExtractor.referenceSignalKey(
                provider = Provider.GCB,
                normalizedReference = "snacks",
            ),
        )
        assertEquals(
            "merchant_reference|mtn_momo|batamadwom enterprise|snacks",
            LearningFeatureExtractor.merchantReferenceSignalKey(
                provider = Provider.MTN_MOMO,
                normalizedMerchantName = "batamadwom enterprise",
                normalizedReference = "snacks",
            ),
        )
    }

    private fun transaction(
        counterpartyName: String? = "Sample Merchant",
        counterpartyPhone: String? = "0240000000",
        reference: String? = "snacks",
        amountMinor: Long = 1_850,
    ): Transaction =
        Transaction(
            id = "transaction-1",
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MTN MoMo",
            rawBodyHash = "hash-1",
            occurredAt = Instant.parse("2026-04-23T12:00:00Z"),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            currency = "GHS",
            counterpartyName = counterpartyName,
            counterpartyPhone = counterpartyPhone,
            reference = reference,
            balanceAfterMinor = null,
            categoryId = null,
            confidence = 0.9f,
            createdAt = Instant.parse("2026-04-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-04-23T12:00:00Z"),
        )
}
