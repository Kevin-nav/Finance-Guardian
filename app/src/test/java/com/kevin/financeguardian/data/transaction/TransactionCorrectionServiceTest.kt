package com.kevin.financeguardian.data.transaction

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.domain.model.MoneyMovementType
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
class TransactionCorrectionServiceTest {
    private lateinit var database: FinanceGuardianDatabase

    private val occurredAt = Instant.parse("2026-04-21T18:00:00Z")
    private val correctedAt = Instant.parse("2026-04-21T18:05:00Z")

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
    fun missingTransactionReturnsNotFound() = runTest {
        val result = service().applyCorrection(
            transactionId = "missing",
            categoryId = "food",
            moneyMovementType = null,
            saveMerchantDefault = false,
        )

        assertEquals(TransactionCorrectionResult.NotFound, result)
    }

    @Test
    fun appliesCategoryToTransaction() = runTest {
        insertTransaction()

        val result = service().applyCorrection(
            transactionId = "transaction-1",
            categoryId = "food",
            moneyMovementType = null,
            saveMerchantDefault = false,
        )

        assertEquals(TransactionCorrectionResult.Applied, result)
        val transaction = database.transactionDao().getById("transaction-1")
        assertEquals("food", transaction?.categoryId)
        assertEquals(correctedAt, transaction?.updatedAt)
    }

    @Test
    fun appliesMoneyMovementTypeToTransaction() = runTest {
        insertTransaction()

        service().applyCorrection(
            transactionId = "transaction-1",
            categoryId = "transfers",
            moneyMovementType = MoneyMovementType.INTERNAL_TRANSFER,
            saveMerchantDefault = false,
        )

        val transaction = database.transactionDao().getById("transaction-1")
        assertEquals("transfers", transaction?.categoryId)
        assertEquals(MoneyMovementType.INTERNAL_TRANSFER, transaction?.moneyMovementType)
    }

    @Test
    fun savesMerchantDefaultForExistingMerchant() = runTest {
        insertTransaction()
        insertMerchant(
            id = "merchant-1",
            displayName = "Sample Sender",
            normalizedName = "sample sender",
            phone = null,
            defaultCategoryId = null,
        )

        service().applyCorrection(
            transactionId = "transaction-1",
            categoryId = "income",
            moneyMovementType = null,
            saveMerchantDefault = true,
        )

        assertEquals("income", database.merchantDao().getById("merchant-1")?.defaultCategoryId)
    }

    @Test
    fun createsMerchantDefaultWhenMerchantDoesNotExist() = runTest {
        insertTransaction()

        service(ids = listOf("merchant-1")).applyCorrection(
            transactionId = "transaction-1",
            categoryId = "income",
            moneyMovementType = null,
            saveMerchantDefault = true,
        )

        val merchant = database.merchantDao().findByNormalizedName("sample sender")
        assertEquals("merchant-1", merchant?.id)
        assertEquals("income", merchant?.defaultCategoryId)
        assertEquals("transaction-1", merchant?.createdFromTransactionId)
    }

    @Test
    fun savedMerchantDefaultAppliesToFutureTransactions() = runTest {
        insertTransaction()
        service(ids = listOf("merchant-1")).applyCorrection(
            transactionId = "transaction-1",
            categoryId = "income",
            moneyMovementType = null,
            saveMerchantDefault = true,
        )

        val categoryId = MerchantCategoryResolver(
            merchantDao = database.merchantDao(),
            idGenerator = FakeIdGenerator(emptyList()),
        ).resolveForParsedTransaction(
            counterpartyName = "Sample Sender",
            counterpartyPhone = null,
            transactionId = "transaction-2",
            now = correctedAt.plusSeconds(60),
        )

        assertEquals("income", categoryId)
    }

    private fun service(ids: List<String> = emptyList()): TransactionCorrectionService =
        TransactionCorrectionService(
            transactionDao = database.transactionDao(),
            merchantDao = database.merchantDao(),
            idGenerator = FakeIdGenerator(ids),
            clock = FixedClock(correctedAt),
        )

    private suspend fun insertTransaction(
        id: String = "transaction-1",
        counterpartyName: String? = "Sample Sender",
        counterpartyPhone: String? = null,
    ) {
        database.transactionDao().insert(
            TransactionEntity(
                id = id,
                sourceMessageId = null,
                provider = Provider.MTN_MOMO,
                rawSender = "MobileMoney",
                rawBodyHash = "hash-1",
                occurredAt = occurredAt,
                direction = TransactionDirection.CREDIT,
                moneyMovementType = MoneyMovementType.INCOME,
                amountMinor = 7700,
                currency = "GHS",
                counterpartyName = counterpartyName,
                counterpartyPhone = counterpartyPhone,
                reference = "R",
                balanceAfterMinor = 53801,
                categoryId = null,
                confidence = 0.9f,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
    }

    private suspend fun insertMerchant(
        id: String,
        displayName: String,
        normalizedName: String,
        phone: String?,
        defaultCategoryId: String?,
    ) {
        database.merchantDao().upsert(
            MerchantEntity(
                id = id,
                displayName = displayName,
                normalizedName = normalizedName,
                phone = phone,
                defaultCategoryId = defaultCategoryId,
                createdFromTransactionId = null,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
    }

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
