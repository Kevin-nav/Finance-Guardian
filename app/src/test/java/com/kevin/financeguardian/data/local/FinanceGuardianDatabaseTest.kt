package com.kevin.financeguardian.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.DefaultCategories
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinanceGuardianDatabaseTest {
    private lateinit var database: FinanceGuardianDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinanceGuardianDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedIfEmptyInsertsDefaultCategories() = runTest {
        DefaultCategorySeeder(database.categoryDao(), FixedClock()).seedIfEmpty()

        val ids = database.categoryDao().getAllOnce().map { it.id }.toSet()

        assertEquals(DefaultCategories.values.map { it.id }.toSet(), ids)
    }

    @Test
    fun seedIfEmptyBackfillsMissingDefaultCategoriesWhenSomeCategoriesExist() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "custom",
                    name = "Custom",
                    type = CategoryType.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )

        DefaultCategorySeeder(database.categoryDao(), FixedClock(now.plusSeconds(60))).seedIfEmpty()

        val categories = database.categoryDao().getAllOnce()
        val ids = categories.map { it.id }.toSet()
        assertTrue(ids.contains("custom"))
        assertTrue(ids.containsAll(DefaultCategories.values.map { it.id }))
    }

    @Test
    fun insertTransactionCanBeObserved() = runTest {
        val transaction = sampleTransaction()

        database.transactionDao().insert(transaction)

        database.transactionDao().observeAll().test {
            val transactions = awaitItem()
            assertEquals(listOf(transaction), transactions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateCategoryChangesTransactionCategory() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "food",
                    name = "Food",
                    type = CategoryType.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.transactionDao().insert(sampleTransaction())

        database.transactionDao().updateCategory(
            transactionId = "transaction-1",
            categoryId = "food",
            updatedAt = now.plusSeconds(60),
        )

        assertEquals("food", database.transactionDao().getById("transaction-1")?.categoryId)
    }

    @Test
    fun duplicateSmsRecordIsRejected() = runTest {
        val record = sampleSmsRecord()

        database.smsMessageRecordDao().insert(record)

        try {
            database.smsMessageRecordDao().insert(record.copy(id = "sms-2"))
            fail("Expected duplicate SMS record to violate unique index")
        } catch (expected: SQLiteConstraintException) {
            assertNotNull(expected.message)
        }

        val duplicate = database.smsMessageRecordDao().findDuplicate(
            sender = record.sender,
            bodyHash = record.bodyHash,
            receivedAt = record.receivedAt,
        )
        assertEquals(record.id, duplicate?.id)
    }

    @Test
    fun seedingTwiceDoesNotDuplicateCategories() = runTest {
        val seeder = DefaultCategorySeeder(database.categoryDao(), FixedClock())

        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertTrue(database.categoryDao().getAllOnce().size == DefaultCategories.values.size)
    }

    @Test
    fun updateMerchantDefaultCategoryChangesMerchant() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.merchantDao().upsert(
            MerchantEntity(
                id = "merchant-1",
                displayName = "Shoprite",
                normalizedName = "shoprite",
                phone = null,
                defaultCategoryId = null,
                createdFromTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        database.merchantDao().updateDefaultCategory(
            id = "merchant-1",
            defaultCategoryId = "food",
            updatedAt = now.plusSeconds(60),
        )

        assertEquals("food", database.merchantDao().getById("merchant-1")?.defaultCategoryId)
    }

    private fun sampleTransaction(): TransactionEntity {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        return TransactionEntity(
            id = "transaction-1",
            sourceMessageId = "sms-1",
            provider = Provider.MTN_MOMO,
            rawSender = "MTN MoMo",
            rawBodyHash = "hash-1",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 1850,
            currency = "GHS",
            counterpartyName = "Sample Merchant",
            counterpartyPhone = null,
            reference = "snacks",
            balanceAfterMinor = 44961,
            categoryId = null,
            confidence = 0.95f,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun sampleSmsRecord(): SmsMessageRecordEntity {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        return SmsMessageRecordEntity(
            id = "sms-1",
            sender = "MTN MoMo",
            bodyHash = "hash-1",
            receivedAt = now,
            processedAt = now,
            parseStatus = ParseStatus.PARSED,
            parseReason = null,
        )
    }

    private class FixedClock(
        private val instant: Instant = Instant.parse("2026-04-21T12:00:00Z"),
    ) : AppClock {
        override fun now(): Instant = instant
    }
}
