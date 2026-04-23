package com.kevin.financeguardian.data.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
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
class LearningBackfillServiceTest {
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
    fun backfillCreatesSignalsForCategorizedTransactionsOnly() = runTest {
        database.transactionDao().insert(sampleTransaction(id = "t1", categoryId = "food"))
        database.transactionDao().insert(sampleTransaction(id = "t2", categoryId = null))

        service(listOf("s1", "s2", "s3", "s4")).backfill()

        assertEquals(4, database.learningSignalDao().getAllOnce().size)
        assertTrue(database.learningSignalDao().getAllOnce().all { it.transactionId == "t1" })
    }

    @Test
    fun backfillDoesNotDuplicateExistingSignals() = runTest {
        database.transactionDao().insert(sampleTransaction(id = "t1", categoryId = "food"))

        val service = service(listOf("s1", "s2", "s3", "s4"))
        service.backfill()
        service.backfill()

        val merchantSignal = database.learningSignalDao().getBySignalKey("merchant|mtn_momo|sample merchant")
        assertEquals(4, database.learningSignalDao().getAllOnce().size)
        assertEquals(0.7f, merchantSignal?.weight)
    }

    private fun service(ids: List<String>): LearningBackfillService =
        LearningBackfillService(
            transactionDao = database.transactionDao(),
            learningSignalDao = database.learningSignalDao(),
            idGenerator = FakeIdGenerator(ids),
            clock = FixedClock(now),
        )

    private fun sampleTransaction(id: String, categoryId: String?): TransactionEntity =
        TransactionEntity(
            id = id,
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MTN MoMo",
            rawBodyHash = "hash-$id",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 1_850,
            currency = "GHS",
            counterpartyName = "Sample Merchant",
            counterpartyPhone = "0240000000",
            reference = "snacks",
            balanceAfterMinor = 4_000,
            categoryId = categoryId,
            confidence = 0.9f,
            createdAt = now,
            updatedAt = now,
        )

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)
        private var generatedCount = 0

        override fun newId(): String =
            if (queue.isNotEmpty()) queue.removeFirst() else "generated-${++generatedCount}"
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
