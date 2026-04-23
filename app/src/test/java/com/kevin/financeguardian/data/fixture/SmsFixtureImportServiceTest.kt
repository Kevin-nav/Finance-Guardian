package com.kevin.financeguardian.data.fixture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.data.sms.BodyHasher
import com.kevin.financeguardian.data.sms.SmsIngestionResult
import com.kevin.financeguardian.data.sms.SmsIngestionService
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.parser.FinanceGuardianSmsParser
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
class SmsFixtureImportServiceTest {
    private lateinit var database: FinanceGuardianDatabase

    private val receivedAt = Instant.parse("2026-04-21T18:00:00Z")
    private val processedAt = Instant.parse("2026-04-21T18:00:05Z")

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
    fun validMtnFixtureImportsParsedTransaction() = runTest {
        val result = service(ids = listOf("sms-1", "transaction-1", "merchant-1"))
            .importJson(mtnIncomeJson())
            .single()

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), result.ingestionResult)
        val transaction = database.transactionDao().getById("transaction-1")
        assertEquals(7700L, transaction?.amountMinor)
        assertEquals("MobileMoney", transaction?.rawSender)
        assertEquals(BodyHasher.sha256Hex(result.fixture.body), transaction?.rawBodyHash)
        assertTrue(database.merchantDao().findByNormalizedName("sample sender") != null)
    }

    @Test
    fun otpFixtureImportsIgnoredSmsRecordOnly() = runTest {
        val result = service(ids = listOf("sms-ignored"))
            .importJson(otpJson())
            .single()

        assertEquals(SmsIngestionResult.Ignored("sms-ignored", "No financial transaction pattern matched"), result.ingestionResult)
        val record = database.smsMessageRecordDao()
            .findDuplicate("Unknown", BodyHasher.sha256Hex("Your OTP is 123456. Do not share it."), receivedAt)
        assertEquals(ParseStatus.IGNORED, record?.parseStatus)
        assertEquals(null, database.transactionDao().getById("transaction-1"))
    }

    @Test
    fun duplicateFixtureReturnsDuplicateOnSecondImport() = runTest {
        val importService = service(ids = listOf("sms-1", "transaction-1", "merchant-1"))

        importService.importJson(mtnIncomeJson())
        val duplicate = importService.importJson(mtnIncomeJson()).single()

        assertEquals(SmsIngestionResult.Duplicate("sms-1"), duplicate.ingestionResult)
    }

    @Test
    fun existingMerchantDefaultAppliesThroughFixtureImport() = runTest {
        insertMerchant(defaultCategoryId = "income")

        service(ids = listOf("sms-1", "transaction-1"))
            .importJson(mtnIncomeJson())

        assertEquals("income", database.transactionDao().getById("transaction-1")?.categoryId)
    }

    @Test
    fun importsStaticFixtureResources() = runTest {
        val parsed = service(ids = listOf("sms-1", "transaction-1", "merchant-1"))
            .importJson(resourceText("sms-fixtures/mtn-income.json"))
            .single()

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), parsed.ingestionResult)

        val ignored = service(ids = listOf("sms-ignored"))
            .importJson(resourceText("sms-fixtures/ignored-otp.json"))
            .single()

        assertEquals(
            SmsIngestionResult.Ignored("sms-ignored", "No financial transaction pattern matched"),
            ignored.ingestionResult,
        )
    }

    private fun service(ids: List<String>): SmsFixtureImportService {
        val idGenerator = FakeIdGenerator(ids)
        val ingestionService = SmsIngestionService(
            database = database,
            smsMessageRecordDao = database.smsMessageRecordDao(),
            transactionDao = database.transactionDao(),
            parser = FinanceGuardianSmsParser(),
            idGenerator = idGenerator,
            clock = FixedClock(processedAt),
            merchantCategoryResolver = MerchantCategoryResolver(
                merchantDao = database.merchantDao(),
                idGenerator = idGenerator,
            ),
            notificationDispatcher = NoOpNotificationDispatcher,
        )
        return SmsFixtureImportService(ingestionService)
    }

    private suspend fun insertMerchant(defaultCategoryId: String?) {
        database.merchantDao().upsert(
            MerchantEntity(
                id = "merchant-existing",
                displayName = "Sample Sender",
                normalizedName = "sample sender",
                phone = null,
                defaultCategoryId = defaultCategoryId,
                createdFromTransactionId = null,
                createdAt = processedAt,
                updatedAt = processedAt,
            ),
        )
    }

    private fun mtnIncomeJson(): String =
        """
        {
          "provider": "MTN_MOMO",
          "sender": "MobileMoney",
          "body": "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
          "receivedAt": "2026-04-21T18:00:00Z"
        }
        """.trimIndent()

    private fun otpJson(): String =
        """
        {
          "provider": "UNKNOWN",
          "sender": "Unknown",
          "body": "Your OTP is 123456. Do not share it.",
          "receivedAt": "2026-04-21T18:00:00Z"
        }
        """.trimIndent()

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }

    private object NoOpNotificationDispatcher : NotificationDispatcher {
        override suspend fun dispatch(event: NotificationEvent) = Unit
    }
}
