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
            listOf(
                AppStartupRunner.StartupTask("seed default categories") {
                    DefaultCategorySeeder(database.categoryDao(), FixedClock()).seedIfEmpty()
                },
                AppStartupRunner.StartupTask("repair historical transactions") {
                    HistoricalTransactionRepairService(
                        database = database,
                        transactionDao = database.transactionDao(),
                        merchantCategoryResolver = MerchantCategoryResolver(
                            merchantDao = database.merchantDao(),
                            idGenerator = FakeIdGenerator(listOf("merchant-1", "merchant-2", "merchant-3")),
                        ),
                        clock = FixedClock(),
                    ).repair()
                },
                AppStartupRunner.StartupTask("backfill learning signals") {
                    LearningBackfillService(
                        transactionDao = database.transactionDao(),
                        learningSignalDao = database.learningSignalDao(),
                        idGenerator = FakeIdGenerator(listOf("signal-1", "signal-2", "signal-3", "signal-4")),
                        clock = FixedClock(),
                    ).backfill()
                },
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
        assertEquals(true, transactions.all { it.dedupeKey?.isNotBlank() == true })
        assertEquals(listOf("BATAMADWOM ENTERPRISE"), transactions.map { it.counterpartyName })
        assertEquals(setOf("food"), transactions.mapNotNull { it.categoryId }.toSet())
        assertEquals(3, database.learningSignalDao().getAllOnce().size)
    }

    @Test
    fun runStartupTasksRemovesGenericMerchantReceiptFalseIncome() = runTest {
        val occurredAt = Instant.parse("2026-04-21T15:56:00Z")
        database.transactionDao().insert(
            TransactionEntity(
                id = "seritex-receipt",
                sourceMessageId = "sms-seritex",
                provider = Provider.UNKNOWN,
                rawSender = "Seritex",
                rawBodyHash = "hash-seritex",
                occurredAt = occurredAt,
                direction = TransactionDirection.CREDIT,
                moneyMovementType = MoneyMovementType.UNKNOWN,
                amountMinor = 5050,
                currency = "GHS",
                counterpartyName = null,
                counterpartyPhone = null,
                reference = null,
                balanceAfterMinor = null,
                categoryId = null,
                confidence = 0.45f,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
        database.transactionDao().insert(
            TransactionEntity(
                id = "momo-bill",
                sourceMessageId = "sms-momo",
                provider = Provider.MTN_MOMO,
                rawSender = "MobileMoney",
                rawBodyHash = "hash-momo",
                providerTransactionId = "79891168722",
                occurredAt = occurredAt.minusSeconds(30),
                direction = TransactionDirection.DEBIT,
                moneyMovementType = MoneyMovementType.EXPENSE,
                amountMinor = 5050,
                currency = "GHS",
                counterpartyName = "Bills.INV",
                counterpartyPhone = null,
                reference = null,
                balanceAfterMinor = 11341,
                categoryId = null,
                confidence = 0.9f,
                createdAt = occurredAt.minusSeconds(30),
                updatedAt = occurredAt.minusSeconds(30),
            ),
        )

        runner.runStartupTasks()

        val transactions = database.transactionDao().getAllOnce()
        assertEquals(listOf("momo-bill"), transactions.map { it.id })
        assertEquals("Bills", transactions.single().counterpartyName)
        assertEquals("Merchant ID: INV", transactions.single().reference)
        assertEquals(TransactionDirection.DEBIT, transactions.single().direction)
        assertEquals(MoneyMovementType.EXPENSE, transactions.single().moneyMovementType)
    }

    @Test
    fun runStartupTasksContinuesWhenOneTaskFails() = runTest {
        val completed = mutableListOf<String>()
        val safeRunner = AppStartupRunner(
            listOf(
                AppStartupRunner.StartupTask("first") {
                    completed += "first"
                },
                AppStartupRunner.StartupTask("failing") {
                    throw IllegalStateException("boom")
                },
                AppStartupRunner.StartupTask("third") {
                    completed += "third"
                },
            ),
        )

        safeRunner.runStartupTasks()

        assertEquals(listOf("first", "third"), completed)
    }

    private class FixedClock : AppClock {
        override fun now(): Instant = Instant.parse("2026-04-21T12:00:00Z")
    }

    private class FakeIdGenerator(ids: List<String>) : com.kevin.financeguardian.core.id.IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }
}
