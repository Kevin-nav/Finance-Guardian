package com.kevin.financeguardian.data.merchant

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.MerchantEntity
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
class MerchantCategoryResolverTest {
    private lateinit var database: FinanceGuardianDatabase
    private val now = Instant.parse("2026-04-21T18:00:00Z")

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
    fun createsMerchantForNewParsedCounterparty() = runTest {
        val resolver = resolver(ids = listOf("merchant-1"))

        val categoryId = resolver.resolveForParsedTransaction(
            counterpartyName = "Shoprite - Osu",
            counterpartyPhone = "024 123 4567",
            transactionId = "transaction-1",
            now = now,
        )

        assertNull(categoryId)
        val merchant = database.merchantDao().findByNormalizedName("shoprite osu")
        assertEquals("merchant-1", merchant?.id)
        assertEquals("Shoprite - Osu", merchant?.displayName)
        assertEquals("0241234567", merchant?.phone)
        assertEquals("transaction-1", merchant?.createdFromTransactionId)
    }

    @Test
    fun existingPhoneDefaultWinsBeforeNameDefault() = runTest {
        insertMerchant(
            id = "merchant-phone",
            displayName = "Phone Merchant",
            normalizedName = "phone merchant",
            phone = "0241234567",
            defaultCategoryId = "transport",
        )
        insertMerchant(
            id = "merchant-name",
            displayName = "Shoprite",
            normalizedName = "shoprite",
            phone = null,
            defaultCategoryId = "food",
        )

        val categoryId = resolver().resolveForParsedTransaction(
            counterpartyName = "Shoprite",
            counterpartyPhone = "024 123 4567",
            transactionId = "transaction-1",
            now = now,
        )

        assertEquals("transport", categoryId)
    }

    @Test
    fun existingNameDefaultAppliesWhenPhoneMissing() = runTest {
        insertMerchant(
            id = "merchant-name",
            displayName = "Shoprite",
            normalizedName = "shoprite osu",
            phone = null,
            defaultCategoryId = "food",
        )

        val categoryId = resolver().resolveForParsedTransaction(
            counterpartyName = "Shoprite, Osu",
            counterpartyPhone = null,
            transactionId = "transaction-1",
            now = now,
        )

        assertEquals("food", categoryId)
    }

    @Test
    fun unusableIdentityReturnsNullAndCreatesNoMerchant() = runTest {
        val categoryId = resolver().resolveForParsedTransaction(
            counterpartyName = "   ",
            counterpartyPhone = " - + ",
            transactionId = "transaction-1",
            now = now,
        )

        assertNull(categoryId)
        assertNull(database.merchantDao().findByNormalizedName("transaction 1"))
    }

    private fun resolver(ids: List<String> = emptyList()): MerchantCategoryResolver =
        MerchantCategoryResolver(
            merchantDao = database.merchantDao(),
            idGenerator = FakeIdGenerator(ids),
        )

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
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }
}
