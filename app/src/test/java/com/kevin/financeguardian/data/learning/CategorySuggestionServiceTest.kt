package com.kevin.financeguardian.data.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategorySuggestionServiceTest {
    private lateinit var database: FinanceGuardianDatabase
    private lateinit var service: CategorySuggestionService
    private val now = Instant.parse("2026-04-23T18:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinanceGuardianDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = CategorySuggestionService(database.learningSignalDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exactPhoneMatchHasHighestPriority() = runTest {
        insertSignal("merchant-food", "merchant|mtn_momo|sample sender", "food", 1f)
        insertSignal("phone-family", "phone|mtn_momo|233244000111", "family", 2f)

        val suggestion = service.suggestFor(sampleTransaction())

        assertEquals("family", suggestion.suggestedCategoryId)
        assertTrue("matched phone history" in suggestion.reasons)
    }

    @Test
    fun merchantReferenceCombinationBeatsMerchantOnlyHistory() = runTest {
        insertSignal("merchant-food", "merchant|mtn_momo|sample sender", "food", 1f)
        insertSignal(
            "combo-transport",
            "merchant_reference|mtn_momo|sample sender|fried rice",
            "transport",
            1f,
        )

        val suggestion = service.suggestFor(sampleTransaction())

        assertEquals("transport", suggestion.suggestedCategoryId)
        assertTrue("matched merchant and reference history" in suggestion.reasons)
    }

    @Test
    fun referenceMatchCanSuggestCategoryForGenericMerchant() = runTest {
        insertSignal("reference-food", "reference|mtn_momo|fried rice", "food", 1.5f)

        val suggestion = service.suggestFor(
            sampleTransaction(counterpartyName = "Merchant 004501", counterpartyPhone = null),
        )

        assertEquals("food", suggestion.suggestedCategoryId)
        assertTrue("matched reference history" in suggestion.reasons)
    }

    @Test
    fun amountBucketMismatchReducesConfidenceButStillReturnsSuggestion() = runTest {
        insertSignal(
            id = "merchant-food",
            signalKey = "merchant|mtn_momo|sample sender",
            categoryId = "food",
            weight = 2f,
            amountBucket = "small",
        )

        val suggestion = service.suggestFor(sampleTransaction(amountMinor = 20_000))

        assertEquals("food", suggestion.suggestedCategoryId)
        assertTrue(suggestion.confidence > 0f)
        assertTrue(suggestion.confidence < 0.85f)
    }

    @Test
    fun noSignalsReturnsEmptySuggestion() = runTest {
        val suggestion = service.suggestFor(sampleTransaction())

        assertEquals(null, suggestion.suggestedCategoryId)
        assertEquals(0f, suggestion.confidence)
        assertTrue(suggestion.reasons.isEmpty())
    }

    private suspend fun insertSignal(
        id: String,
        signalKey: String,
        categoryId: String,
        weight: Float,
        amountBucket: String = "medium",
    ) {
        database.learningSignalDao().upsert(
            LearningSignalEntity(
                id = id,
                signalKey = signalKey,
                transactionId = "transaction-1",
                provider = Provider.MTN_MOMO,
                normalizedMerchantName = "sample sender",
                normalizedPhone = "233244000111",
                normalizedReference = "fried rice",
                amountBucket = amountBucket,
                direction = TransactionDirection.DEBIT,
                moneyMovementType = MoneyMovementType.EXPENSE,
                categoryId = categoryId,
                signalType = "USER_CORRECTION",
                weight = weight,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun sampleTransaction(
        counterpartyName: String? = "Sample Sender",
        counterpartyPhone: String? = "+233 24 400 0111",
        amountMinor: Long = 4_000,
    ): Transaction =
        Transaction(
            id = "transaction-1",
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MobileMoney",
            rawBodyHash = "hash-1",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            currency = "GHS",
            counterpartyName = counterpartyName,
            counterpartyPhone = counterpartyPhone,
            reference = "Fried Rice",
            balanceAfterMinor = 10_000,
            categoryId = null,
            confidence = 0.9f,
            createdAt = now,
            updatedAt = now,
        )
}
