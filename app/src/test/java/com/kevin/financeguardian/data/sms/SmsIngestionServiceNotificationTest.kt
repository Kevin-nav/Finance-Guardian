package com.kevin.financeguardian.data.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.notifications.NoOpNotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.learning.CategorySuggestionService
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.domain.model.MoneyMovementType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsIngestionServiceNotificationTest {
    private lateinit var database: FinanceGuardianDatabase

    private val receivedAt = Instant.parse("2026-04-23T10:15:30Z")
    private val processedAt = Instant.parse("2026-04-23T10:15:35Z")
    private val envelope = SmsMessageEnvelope(
        sender = "MobileMoney",
        body = "Payment received for GHS 24.00 from Melcom Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
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
    fun parsedHighConfidenceCategorizedTransaction_dispatchesTransactionDetectedAfterPersistence() = runTest {
        insertMerchant(defaultCategoryId = "shopping")
        var transactionPersistedAtDispatch = false
        val dispatcher = RecordingNotificationDispatcher { event ->
            val transactionId = (event as NotificationEvent.TransactionDetected).transactionId
            transactionPersistedAtDispatch = database.transactionDao().getById(transactionId) != null
        }
        val service = service(
            parserResult = parsedResult(confidence = 0.9f),
            ids = listOf("sms-1", "transaction-1"),
            notificationDispatcher = dispatcher,
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), result)
        assertEquals(
            listOf(
                NotificationEvent.TransactionDetected(
                    transactionId = "transaction-1",
                    amountMinor = 2_400,
                    currency = "GHS",
                    merchantName = "Melcom",
                    occurredAt = receivedAt,
                ),
            ),
            dispatcher.events,
        )
        assertTrue(transactionPersistedAtDispatch)
    }

    @Test
    fun parsedLowConfidenceTransaction_dispatchesTransactionNeedsReview() = runTest {
        insertMerchant(defaultCategoryId = "shopping")
        val dispatcher = RecordingNotificationDispatcher()
        val service = service(
            parserResult = parsedResult(confidence = 0.45f),
            ids = listOf("sms-1", "transaction-1"),
            notificationDispatcher = dispatcher,
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), result)
        assertEquals(
            listOf(
                NotificationEvent.TransactionNeedsReview(
                    transactionId = "transaction-1",
                    amountMinor = 2_400,
                    currency = "GHS",
                    merchantName = "Melcom",
                    occurredAt = receivedAt,
                ),
            ),
            dispatcher.events,
        )
    }

    @Test
    fun parsedUnknownCategoryTransaction_dispatchesTransactionNeedsReview() = runTest {
        val dispatcher = RecordingNotificationDispatcher()
        val service = service(
            parserResult = parsedResult(confidence = 0.9f),
            ids = listOf("sms-1", "transaction-1", "merchant-1"),
            notificationDispatcher = dispatcher,
        )

        val result = service.ingest(envelope)

        assertEquals(SmsIngestionResult.Parsed("sms-1", "transaction-1"), result)
        assertEquals(
            listOf(
                NotificationEvent.TransactionNeedsReview(
                    transactionId = "transaction-1",
                    amountMinor = 2_400,
                    currency = "GHS",
                    merchantName = "Melcom",
                    occurredAt = receivedAt,
                ),
            ),
            dispatcher.events,
        )
        assertEquals(null, database.transactionDao().getById("transaction-1")?.categoryId)
    }

    @Test
    fun ignoredFlow_doesNotDispatchNotificationEvents() = runTest {
        val ignoredDispatcher = RecordingNotificationDispatcher()
        val ignoredService = service(
            parserResult = SmsParseResult.Ignored("OTP message"),
            ids = listOf("sms-ignored"),
            notificationDispatcher = ignoredDispatcher,
        )

        val ignoredResult = ignoredService.ingest(envelope)

        assertEquals(SmsIngestionResult.Ignored("sms-ignored", "OTP message"), ignoredResult)
        assertTrue(ignoredDispatcher.events.isEmpty())
    }

    @Test
    fun duplicateFlow_doesNotDispatchNotificationEvents() = runTest {
        service(
            parserResult = parsedResult(confidence = 0.9f),
            ids = listOf("sms-1", "transaction-1", "merchant-1"),
            notificationDispatcher = NoOpNotificationDispatcher(),
        ).ingest(envelope)
        val duplicateDispatcher = RecordingNotificationDispatcher()
        val duplicateService = service(
            parserResult = parsedResult(confidence = 0.9f),
            ids = emptyList(),
            notificationDispatcher = duplicateDispatcher,
        )

        val duplicateResult = duplicateService.ingest(envelope)

        assertEquals(SmsIngestionResult.Duplicate("sms-1"), duplicateResult)
        assertTrue(duplicateDispatcher.events.isEmpty())
    }

    private fun service(
        parserResult: SmsParseResult,
        ids: List<String>,
        notificationDispatcher: NotificationDispatcher = NoOpNotificationDispatcher(),
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
            categorySuggestionService = object : CategorySuggestionService(database.learningSignalDao()) {},
            notificationDispatcher = notificationDispatcher,
        )
    }

    private suspend fun insertMerchant(defaultCategoryId: String?) {
        database.merchantDao().upsert(
            MerchantEntity(
                id = "merchant-existing",
                displayName = "Melcom",
                normalizedName = "melcom",
                phone = null,
                defaultCategoryId = defaultCategoryId,
                createdFromTransactionId = null,
                createdAt = processedAt,
                updatedAt = processedAt,
            ),
        )
    }

    private fun parsedResult(confidence: Float): SmsParseResult.Parsed =
        SmsParseResult.Parsed(
            transaction = ParsedTransaction(
                provider = Provider.MTN_MOMO,
                rawSender = envelope.sender,
                providerTransactionId = "123",
                occurredAt = receivedAt,
                direction = TransactionDirection.DEBIT,
                moneyMovementType = MoneyMovementType.EXPENSE,
                amountMinor = 2_400,
                currency = "GHS",
                counterpartyName = "Melcom",
                counterpartyPhone = null,
                reference = "R",
                balanceAfterMinor = 53_801,
            ),
            confidence = confidence,
        )

    private class RecordingParser(
        private val result: SmsParseResult,
    ) : SmsTransactionParser {
        override fun parse(input: SmsParseInput): SmsParseResult = result
    }

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val queue = ArrayDeque(ids)

        override fun newId(): String = queue.removeFirst()
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }

    private class RecordingNotificationDispatcher(
        private val onDispatch: suspend (NotificationEvent) -> Unit = {},
    ) : NotificationDispatcher {
        val events = mutableListOf<NotificationEvent>()

        override suspend fun dispatch(event: NotificationEvent) {
            events += event
            onDispatch(event)
        }
    }
}
