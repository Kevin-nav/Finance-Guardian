package com.kevin.financeguardian.core.startup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.learning.LearningBackfillService
import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.data.transaction.HistoricalTransactionRepairService
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.DefaultCategories
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStartupRunnerTest {
    private lateinit var database: FinanceGuardianDatabase
    private lateinit var runner: AppStartupRunner

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinanceGuardianDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        runner = AppStartupRunner(
            DefaultCategorySeeder(database.categoryDao(), FixedClock()),
            HistoricalTransactionRepairService(
                database = database,
                transactionDao = database.transactionDao(),
                merchantCategoryResolver = MerchantCategoryResolver(
                    merchantDao = database.merchantDao(),
                    idGenerator = FakeIdGenerator(listOf("merchant-1", "merchant-2", "merchant-3")),
                ),
                clock = FixedClock(),
            ),
            LearningBackfillService(
                transactionDao = database.transactionDao(),
                learningSignalDao = database.learningSignalDao(),
                idGenerator = FakeIdGenerator(listOf("signal-1", "signal-2", "signal-3", "signal-4")),
                clock = FixedClock(),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun runStartupTasksSeedsDefaultCategories() = runTest {
        runner.runStartupTasks()

        val categoryIds = database.categoryDao().getAllOnce().map { it.id }.toSet()

        assertEquals(DefaultCategories.values.map { it.id }.toSet(), categoryIds)
    }

    @Test
    fun runningStartupTasksTwiceDoesNotDuplicateCategories() = runTest {
        runner.runStartupTasks()
        runner.runStartupTasks()

        val categories = database.categoryDao().getAllOnce()

        assertEquals(DefaultCategories.values.size, categories.size)
    }

    @Test
    fun runStartupTasksRepairsHistoricalDuplicatesAndBackfillsCategories() = runTest {
        val occurredAt = Instant.parse("2026-04-21T15:55:04Z")
        val createdAt = Instant.parse("2026-04-21T18:05:00Z")
        database.transactionDao().insert(
            TransactionEntity(
                id = "transaction-1",
                sourceMessageId = "sms-1",
                provider = Provider.MTN_MOMO,
                rawSender = "MTN MoMo",
                rawBodyHash = "hash-1",
                occurredAt = occurredAt,
                direction = TransactionDirection.DEBIT,
                moneyMovementType = MoneyMovementType.EXPENSE,
                amountMinor = 1850,
                currency = "GHS",
                counterpartyName = "Merchant 004501",
                counterpartyPhone = null,
                reference = "snacks",
                balanceAfterMinor = 44961,
                categoryId = null,
                confidence = 0.88f,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
        database.transactionDao().insert(
            TransactionEntity(
                id = "transaction-2",
                sourceMessageId = "sms-2",
                provider = Provider.MTN_MOMO,
                rawSender = "MTN MoMo",
                rawBodyHash = "hash-2",
                occurredAt = occurredAt,
                direction = TransactionDirection.DEBIT,
                moneyMovementType = MoneyMovementType.EXPENSE,
                amountMinor = 1850,
                currency = "GHS",
                counterpartyName = "BATAMADWOM ENTERPRISE",
                counterpartyPhone = null,
                reference = "snacks",
                balanceAfterMinor = 44961,
                categoryId = null,
                confidence = 0.95f,
                createdAt = createdAt.plusSeconds(1),
                updatedAt = createdAt.plusSeconds(1),
            ),
        )

        runner.runStartupTasks()

        val transactions = database.transactionDao().getAllOnce()
        assertEquals(1, transactions.size)
        val repaired = transactions.single()
        assertEquals("BATAMADWOM ENTERPRISE", repaired.counterpartyName)
        assertEquals("food", repaired.categoryId)
        assertEquals("snacks", repaired.reference)
        assertEquals(true, repaired.dedupeKey?.isNotBlank())
        assertEquals(3, database.learningSignalDao().getAllOnce().size)
    }

    private class FixedClock : AppClock {
        override fun now(): Instant = Instant.parse("2026-04-21T12:00:00Z")
    }

    private class FakeIdGenerator(ids: List<String>) : com.kevin.financeguardian.core.id.IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }
}
