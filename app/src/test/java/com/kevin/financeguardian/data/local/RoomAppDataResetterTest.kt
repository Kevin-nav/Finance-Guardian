package com.kevin.financeguardian.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAppDataResetterTest {
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
    fun resetAllDataClearsMutableDataAndRestoresDefaultCategories() = runTest {
        val now = Instant.parse("2026-04-22T12:00:00Z")
        database.categoryDao().upsert(
            CategoryEntity(
                id = "custom",
                name = "Custom",
                type = CategoryType.EXPENSE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.smsMessageRecordDao().insert(sampleSmsRecord(now))
        database.transactionDao().insert(sampleTransaction(now))
        database.merchantDao().upsert(sampleMerchant(now))

        RoomAppDataResetter(
            database = database,
            transactionDao = database.transactionDao(),
            merchantDao = database.merchantDao(),
            smsMessageRecordDao = database.smsMessageRecordDao(),
            parserRuleDao = database.parserRuleDao(),
            categoryDao = database.categoryDao(),
            clock = FixedClock(now.plusSeconds(60)),
        ).resetAllData()

        assertNull(database.transactionDao().getById("transaction-1"))
        assertNull(database.merchantDao().getById("merchant-1"))
        assertNull(
            database.smsMessageRecordDao().findDuplicate(
                sender = "MTN MoMo",
                bodyHash = "hash-1",
                receivedAt = now,
            ),
        )
        assertEquals(
            DefaultCategories.values.map { it.id }.toSet(),
            database.categoryDao().getAllOnce().map { it.id }.toSet(),
        )
    }

    private fun sampleTransaction(now: Instant): TransactionEntity =
        TransactionEntity(
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
            categoryId = "custom",
            confidence = 0.95f,
            createdAt = now,
            updatedAt = now,
        )

    private fun sampleSmsRecord(now: Instant): SmsMessageRecordEntity =
        SmsMessageRecordEntity(
            id = "sms-1",
            sender = "MTN MoMo",
            bodyHash = "hash-1",
            receivedAt = now,
            processedAt = now,
            parseStatus = ParseStatus.PARSED,
            parseReason = null,
        )

    private fun sampleMerchant(now: Instant): MerchantEntity =
        MerchantEntity(
            id = "merchant-1",
            displayName = "Sample Merchant",
            normalizedName = "sample merchant",
            phone = null,
            defaultCategoryId = "custom",
            createdFromTransactionId = "transaction-1",
            createdAt = now,
            updatedAt = now,
        )

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
