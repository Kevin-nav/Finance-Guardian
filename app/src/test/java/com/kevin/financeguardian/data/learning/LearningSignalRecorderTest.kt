package com.kevin.financeguardian.data.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
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
class LearningSignalRecorderTest {
    private lateinit var database: FinanceGuardianDatabase
    private val now = Instant.parse("2026-04-23T18:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinanceGuardianDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordCorrectionCreatesSignalsForMerchantPhoneReferenceAndCombination() = runTest {
        val recorder = recorder(ids = listOf("s1", "s2", "s3", "s4"))

        recorder.recordCorrection(
            transaction = sampleTransaction(),
            categoryId = "food",
            moneyMovementType = MoneyMovementType.EXPENSE,
            now = now,
        )

        val signals = database.learningSignalDao().getAllOnce()
        assertEquals(4, signals.size)
        assertTrue(signals.any { it.signalKey == "merchant|mtn_momo|sample sender" })
        assertTrue(signals.any { it.signalKey == "phone|mtn_momo|233244000111" })
        assertTrue(signals.any { it.signalKey == "reference|mtn_momo|fried rice" })
        assertTrue(signals.any { it.signalKey == "merchant_reference|mtn_momo|sample sender|fried rice" })
    }

    @Test
    fun recordCorrectionStrengthensExistingSignalInsteadOfDuplicating() = runTest {
        val recorder = recorder(ids = listOf("s1", "s2", "s3", "s4"))
        val transaction = sampleTransaction()

        recorder.recordCorrection(transaction, "food", MoneyMovementType.EXPENSE, now)
        recorder.recordCorrection(transaction, "food", MoneyMovementType.EXPENSE, now.plusSeconds(60))

        val merchantSignal = database.learningSignalDao().getBySignalKey("merchant|mtn_momo|sample sender")
        assertEquals(4, database.learningSignalDao().getAllOnce().size)
        assertEquals(2.0f, merchantSignal?.weight)
        assertEquals(now.plusSeconds(60), merchantSignal?.updatedAt)
    }

    @Test
    fun recordCorrectionStoresMovementTypeAlongsideCategory() = runTest {
        val recorder = recorder(ids = listOf("s1", "s2", "s3", "s4"))

        recorder.recordCorrection(
            transaction = sampleTransaction(),
            categoryId = "transfers",
            moneyMovementType = MoneyMovementType.INTERNAL_TRANSFER,
            now = now,
        )

        val merchantSignal = database.learningSignalDao().getBySignalKey("merchant|mtn_momo|sample sender")
        assertEquals(MoneyMovementType.INTERNAL_TRANSFER, merchantSignal?.moneyMovementType)
        assertEquals("transfers", merchantSignal?.categoryId)
    }

    private fun recorder(ids: List<String>): LearningSignalRecorder =
        LearningSignalRecorder(
            learningSignalDao = database.learningSignalDao(),
            idGenerator = FakeIdGenerator(ids),
        )

    private fun sampleTransaction(): TransactionEntity =
        TransactionEntity(
            id = "transaction-1",
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MobileMoney",
            rawBodyHash = "hash-1",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 4_000,
            currency = "GHS",
            counterpartyName = "Sample Sender",
            counterpartyPhone = "+233 24 400 0111",
            reference = "Fried Rice",
            balanceAfterMinor = 10_000,
            categoryId = null,
            confidence = 0.9f,
            createdAt = now,
            updatedAt = now,
        )

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }
}
