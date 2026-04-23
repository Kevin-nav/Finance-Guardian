package com.kevin.financeguardian.data.sms

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.ParsedTransaction
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsIngestionServiceTest {
    private lateinit var database: FinanceGuardianDatabase

    private val receivedAt = Instant.parse("2026-04-21T18:00:00Z")
    private val processedAt = Instant.parse("2026-04-21T18:00:05Z")
    private val envelope = SmsMessageEnvelope(
        sender = "MobileMoney",
        body = "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
        receivedAt = receivedAt,
    )

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
    fun parsedSmsInsertsSmsRecordAndTransaction() = runTest {
        val service = service(
            parserResult = parsedResult(),
            ids = listOf("sms-1", "transaction-1", "merchant-1"),
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), result)
        val bodyHash = BodyHasher.sha256Hex(envelope.body)
        val record = database.smsMessageRecordDao().findDuplicate(envelope.sender, bodyHash, receivedAt)
        assertEquals("sms-1", record?.id)
        assertEquals(ParseStatus.PARSED, record?.parseStatus)
        assertNull(record?.parseReason)

        val transaction = database.transactionDao().getById("transaction-1")
        assertEquals("sms-1", transaction?.sourceMessageId)
        assertEquals(Provider.MTN_MOMO, transaction?.provider)
        assertEquals(envelope.sender, transaction?.rawSender)
        assertEquals(bodyHash, transaction?.rawBodyHash)
        assertNotEquals(envelope.body, transaction?.rawBodyHash)
        assertEquals(7700L, transaction?.amountMinor)
        assertEquals(processedAt, transaction?.createdAt)
        assertEquals(processedAt, transaction?.updatedAt)
    }

    @Test
    fun parsedSmsCreatesMerchantForCounterparty() = runTest {
        val service = service(
            parserResult = parsedResult(),
            ids = listOf("sms-1", "transaction-1", "merchant-1"),
        )

        service.ingest(envelope)

        val merchant = database.merchantDao().findByNormalizedName("sample sender")
        assertEquals("merchant-1", merchant?.id)
        assertEquals("SAMPLE SENDER", merchant?.displayName)
        assertEquals("transaction-1", merchant?.createdFromTransactionId)
        assertNull(merchant?.defaultCategoryId)
    }

    @Test
    fun parsedSmsAppliesExistingMerchantDefaultCategoryByName() = runTest {
        insertMerchant(
            id = "merchant-1",
            displayName = "Sample Sender",
            normalizedName = "sample sender",
            phone = null,
            defaultCategoryId = "income",
        )
        val service = service(
            parserResult = parsedResult(),
            ids = listOf("sms-1", "transaction-1"),
        )

        service.ingest(envelope)

        assertEquals("income", database.transactionDao().getById("transaction-1")?.categoryId)
    }

    @Test
    fun ignoredSmsInsertsOnlyIgnoredSmsRecord() = runTest {
        val service = service(
            parserResult = SmsParseResult.Ignored("OTP message"),
            ids = listOf("sms-ignored"),
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Ignored("sms-ignored", "OTP message"), result)
        val record = database.smsMessageRecordDao()
            .findDuplicate(envelope.sender, BodyHasher.sha256Hex(envelope.body), receivedAt)
        assertEquals(ParseStatus.IGNORED, record?.parseStatus)
        assertEquals("OTP message", record?.parseReason)
        assertNull(database.transactionDao().getById("transaction-1"))
    }

    @Test
    fun failedSmsInsertsOnlyFailedSmsRecord() = runTest {
        val service = service(
            parserResult = SmsParseResult.Failed("Parser error"),
            ids = listOf("sms-failed"),
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Failed("sms-failed", "Parser error"), result)
        val record = database.smsMessageRecordDao()
            .findDuplicate(envelope.sender, BodyHasher.sha256Hex(envelope.body), receivedAt)
        assertEquals(ParseStatus.FAILED, record?.parseStatus)
        assertEquals("Parser error", record?.parseReason)
        assertNull(database.transactionDao().getById("transaction-1"))
    }

    @Test
    fun duplicateSmsReturnsDuplicateWithoutParsingOrInsertingAgain() = runTest {
        val service = service(
            parserResult = parsedResult(),
            ids = listOf("sms-1", "transaction-1", "merchant-1"),
        )
        service.ingest(envelope)

        val duplicateParser = RecordingParser(parsedResult())
        val duplicateService = SmsIngestionService(
            database = database,
            smsMessageRecordDao = database.smsMessageRecordDao(),
            transactionDao = database.transactionDao(),
            parser = duplicateParser,
            idGenerator = FakeIdGenerator(emptyList()),
            clock = FixedClock(processedAt),
            merchantCategoryResolver = MerchantCategoryResolver(
                merchantDao = database.merchantDao(),
                idGenerator = FakeIdGenerator(emptyList()),
            ),
            notificationDispatcher = NoOpNotificationDispatcher,
        )

        val result = duplicateService.ingest(envelope)

        assertEquals(SmsIngestionResult.Duplicate("sms-1"), result)
        assertEquals(0, duplicateParser.callCount)
        assertTrue(database.transactionDao().getById("transaction-1") != null)
    }

    @Test
    fun parsedSmsRollsBackSmsRecordAndMerchantWhenTransactionInsertFails() = runTest {
        database.transactionDao().insert(
            sampleTransaction(id = "existing-transaction", sourceMessageId = "sms-1"),
        )
        val service = service(
            parserResult = parsedResult(),
            ids = listOf("sms-1", "transaction-2", "merchant-1"),
        )

        try {
            service.ingest(envelope)
            fail("Expected transaction unique constraint failure")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }

        val bodyHash = BodyHasher.sha256Hex(envelope.body)
        assertNull(database.smsMessageRecordDao().findDuplicate(envelope.sender, bodyHash, receivedAt))
        assertNull(database.merchantDao().findByNormalizedName("sample sender"))
    }

    private fun service(
        parserResult: SmsParseResult,
        ids: List<String>,
    ): SmsIngestionService {
        val idGenerator = FakeIdGenerator(ids)
        return SmsIngestionService(
            database = database,
            smsMessageRecordDao = database.smsMessageRecordDao(),
            transactionDao = database.transactionDao(),
            parser = RecordingParser(parserResult),
            idGenerator = idGenerator,
            clock = FixedClock(processedAt),
            merchantCategoryResolver = MerchantCategoryResolver(
                merchantDao = database.merchantDao(),
                idGenerator = idGenerator,
            ),
            notificationDispatcher = NoOpNotificationDispatcher,
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
                createdAt = processedAt,
                updatedAt = processedAt,
            ),
        )
    }

    private fun parsedResult(): SmsParseResult.Parsed =
        SmsParseResult.Parsed(
            transaction = ParsedTransaction(
                provider = Provider.MTN_MOMO,
                rawSender = envelope.sender,
                occurredAt = receivedAt,
                direction = TransactionDirection.CREDIT,
                moneyMovementType = MoneyMovementType.INCOME,
                amountMinor = 7700,
                currency = "GHS",
                counterpartyName = "SAMPLE SENDER",
                counterpartyPhone = null,
                reference = "R",
                balanceAfterMinor = 53801,
            ),
            confidence = 0.9f,
        )

    private fun sampleTransaction(
        id: String,
        sourceMessageId: String?,
    ): TransactionEntity =
        TransactionEntity(
            id = id,
            sourceMessageId = sourceMessageId,
            provider = Provider.MTN_MOMO,
            rawSender = envelope.sender,
            rawBodyHash = "existing-hash",
            occurredAt = receivedAt,
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = 7700,
            currency = "GHS",
            counterpartyName = "Existing Sender",
            counterpartyPhone = null,
            reference = "R",
            balanceAfterMinor = 53801,
            categoryId = null,
            confidence = 0.9f,
            createdAt = processedAt,
            updatedAt = processedAt,
        )

    private class RecordingParser(
        private val result: SmsParseResult,
    ) : SmsTransactionParser {
        var callCount: Int = 0
            private set

        override fun parse(input: SmsParseInput): SmsParseResult {
            callCount += 1
            return result
        }
    }

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
