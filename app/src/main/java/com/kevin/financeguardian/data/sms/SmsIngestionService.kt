package com.kevin.financeguardian.data.sms

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.learning.CategorySuggestionService
import com.kevin.financeguardian.core.notifications.InsightEvaluator
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.notifications.NoOpNotificationDispatcher
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.data.transaction.TransactionFingerprintFactory
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SmsIngestionService @Inject constructor(
    private val database: FinanceGuardianDatabase,
    private val smsMessageRecordDao: SmsMessageRecordDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsTransactionParser,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
    private val merchantCategoryResolver: MerchantCategoryResolver,
    private val categorySuggestionService: CategorySuggestionService,
    private val notificationDispatcher: NotificationDispatcher = NoOpNotificationDispatcher(),
    private val insightEvaluator: InsightEvaluator = InsightEvaluator(),
) {
    suspend fun ingest(envelope: SmsMessageEnvelope): SmsIngestionResult {
        val bodyHash = BodyHasher.sha256Hex(envelope.body)
        val duplicate = smsMessageRecordDao.findDuplicate(
            sender = envelope.sender,
            bodyHash = bodyHash,
            receivedAt = envelope.receivedAt,
        )
        if (duplicate != null) {
            return SmsIngestionResult.Duplicate(duplicate.id)
        }

        val result = parser.parse(
            SmsParseInput(
                sender = envelope.sender,
                body = envelope.body,
                receivedAt = envelope.receivedAt,
            ),
        )

        val persisted = database.withTransaction {
            val transactionDuplicate = smsMessageRecordDao.findDuplicate(
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
            )
            if (transactionDuplicate != null) {
                ParsedPersistence(
                    ingestionResult = SmsIngestionResult.Duplicate(transactionDuplicate.id),
                    notificationEvent = null,
                )
            } else {
                when (result) {
                    is SmsParseResult.Parsed -> persistParsed(envelope, bodyHash, result)
                    is SmsParseResult.Ignored -> ParsedPersistence(
                        ingestionResult = persistNonParsed(
                            envelope = envelope,
                            bodyHash = bodyHash,
                            status = ParseStatus.IGNORED,
                            reason = result.reason,
                        ),
                        notificationEvent = null,
                    )

                    is SmsParseResult.Failed -> ParsedPersistence(
                        ingestionResult = persistNonParsed(
                            envelope = envelope,
                            bodyHash = bodyHash,
                            status = ParseStatus.FAILED,
                            reason = result.reason,
                        ),
                        notificationEvent = null,
                    )
                }
            }
        }
        persisted.notificationEvent?.let { notificationDispatcher.dispatch(it) }
        if (persisted.ingestionResult is SmsIngestionResult.Parsed) {
            maybeDispatchInsight()
        }
        return persisted.ingestionResult
    }

    private suspend fun maybeDispatchInsight() {
        val insight = insightEvaluator.evaluate(
            transactions = transactionDao.observeAll().first().map { it.toDomain() },
            now = clock.now(),
        ) ?: return
        notificationDispatcher.dispatch(
            NotificationEvent.InsightTriggered(
                insight = insight.kind,
                summary = insight.summary,
                occurredAt = clock.now(),
            ),
        )
    }

    private suspend fun persistParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        result: SmsParseResult.Parsed,
    ): ParsedPersistence {
        val now = clock.now()
        val smsRecordId = idGenerator.newId()
        val transactionId = idGenerator.newId()
        val parsed = result.transaction
        val fingerprint = TransactionFingerprintFactory.fromParsed(parsed)
        val existingTransaction = transactionDao.findByDedupeKey(fingerprint.dedupeKey)
        if (existingTransaction != null) {
            val duplicateSmsRecordId = persistDuplicateSmsRecord(
                smsRecordId = smsRecordId,
                envelope = envelope,
                bodyHash = bodyHash,
                now = now,
                reason = "Duplicate transaction ${existingTransaction.id}",
            )
            return ParsedPersistence(
                ingestionResult = SmsIngestionResult.Duplicate(
                    existingTransaction.sourceMessageId ?: duplicateSmsRecordId,
                ),
                notificationEvent = null,
            )
        }

        var categoryId = merchantCategoryResolver.resolveForParsedTransaction(
            provider = parsed.provider,
            moneyMovementType = parsed.moneyMovementType,
            counterpartyName = parsed.counterpartyName,
            counterpartyPhone = parsed.counterpartyPhone,
            reference = parsed.reference,
            transactionId = transactionId,
            now = now,
        )
        var moneyMovementType = parsed.moneyMovementType
        if (categoryId == null) {
            val suggestion = categorySuggestionService.suggestFor(
                com.kevin.financeguardian.domain.model.Transaction(
                    id = transactionId,
                    sourceMessageId = null,
                    provider = parsed.provider,
                    rawSender = parsed.rawSender,
                    rawBodyHash = bodyHash,
                    providerTransactionId = fingerprint.providerTransactionId,
                    dedupeKey = fingerprint.dedupeKey,
                    occurredAt = parsed.occurredAt,
                    direction = parsed.direction,
                    moneyMovementType = parsed.moneyMovementType,
                    amountMinor = parsed.amountMinor,
                    currency = parsed.currency,
                    counterpartyName = parsed.counterpartyName,
                    counterpartyPhone = parsed.counterpartyPhone,
                    reference = parsed.reference,
                    balanceAfterMinor = parsed.balanceAfterMinor,
                    categoryId = null,
                    confidence = result.confidence,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (suggestion.shouldAutoApply) {
                categoryId = suggestion.suggestedCategoryId
                moneyMovementType = suggestion.suggestedMoneyMovementType ?: moneyMovementType
            }
        }
        val duplicateId = insertSmsRecordOrDuplicate(
            entity = SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = now,
                parseStatus = ParseStatus.PARSED,
                parseReason = null,
            ),
            sender = envelope.sender,
            bodyHash = bodyHash,
            receivedAt = envelope.receivedAt,
        )
        if (duplicateId != null) {
            return ParsedPersistence(
                ingestionResult = SmsIngestionResult.Duplicate(duplicateId),
                notificationEvent = null,
            )
        }

        transactionDao.insert(
            TransactionEntity(
                id = transactionId,
                sourceMessageId = smsRecordId,
                provider = parsed.provider,
                rawSender = parsed.rawSender,
                rawBodyHash = bodyHash,
                providerTransactionId = fingerprint.providerTransactionId,
                dedupeKey = fingerprint.dedupeKey,
                occurredAt = parsed.occurredAt,
                direction = parsed.direction,
                moneyMovementType = moneyMovementType,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency,
                counterpartyName = parsed.counterpartyName,
                counterpartyPhone = parsed.counterpartyPhone,
                reference = parsed.reference,
                balanceAfterMinor = parsed.balanceAfterMinor,
                categoryId = categoryId,
                confidence = result.confidence,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return ParsedPersistence(
            ingestionResult = SmsIngestionResult.Parsed(smsRecordId, transactionId),
            notificationEvent = parsed.toNotificationEvent(
                transactionId = transactionId,
                categoryId = categoryId,
                confidence = result.confidence,
            ),
        )
    }

    private suspend fun persistNonParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        status: ParseStatus,
        reason: String,
    ): SmsIngestionResult {
        val smsRecordId = idGenerator.newId()
        val duplicateId = insertSmsRecordOrDuplicate(
            entity = SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = clock.now(),
                parseStatus = status,
                parseReason = reason,
            ),
            sender = envelope.sender,
            bodyHash = bodyHash,
            receivedAt = envelope.receivedAt,
        )
        if (duplicateId != null) return SmsIngestionResult.Duplicate(duplicateId)

        return when (status) {
            ParseStatus.IGNORED -> SmsIngestionResult.Ignored(smsRecordId, reason)
            ParseStatus.FAILED -> SmsIngestionResult.Failed(smsRecordId, reason)
            else -> error("Unsupported non-parsed status: $status")
        }
    }

    private suspend fun persistDuplicateSmsRecord(
        smsRecordId: String,
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        now: Instant,
        reason: String,
    ): String {
        val duplicateId = insertSmsRecordOrDuplicate(
            entity = SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = now,
                parseStatus = ParseStatus.DUPLICATE,
                parseReason = reason,
            ),
            sender = envelope.sender,
            bodyHash = bodyHash,
            receivedAt = envelope.receivedAt,
        )
        return duplicateId ?: smsRecordId
    }

    private suspend fun insertSmsRecordOrDuplicate(
        entity: SmsMessageRecordEntity,
        sender: String,
        bodyHash: String,
        receivedAt: Instant,
    ): String? {
        try {
            smsMessageRecordDao.insert(entity)
            return null
        } catch (error: SQLiteConstraintException) {
            val duplicate = smsMessageRecordDao.findDuplicate(
                sender = sender,
                bodyHash = bodyHash,
                receivedAt = receivedAt,
            )
            if (duplicate != null) return duplicate.id
            throw error
        }
    }

    private fun com.kevin.financeguardian.domain.parser.ParsedTransaction.toNotificationEvent(
        transactionId: String,
        categoryId: String?,
        confidence: Float,
    ): NotificationEvent {
        val needsReview = confidence < REVIEW_CONFIDENCE_THRESHOLD || categoryId == null
        return if (needsReview) {
            NotificationEvent.TransactionNeedsReview(
                transactionId = transactionId,
                amountMinor = amountMinor,
                currency = currency,
                merchantName = counterpartyName,
                occurredAt = occurredAt,
            )
        } else {
            NotificationEvent.TransactionDetected(
                transactionId = transactionId,
                amountMinor = amountMinor,
                currency = currency,
                merchantName = counterpartyName,
                occurredAt = occurredAt,
            )
        }
    }

    private data class ParsedPersistence(
        val ingestionResult: SmsIngestionResult,
        val notificationEvent: NotificationEvent?,
    )

    private companion object {
        const val REVIEW_CONFIDENCE_THRESHOLD = 0.85f
    }
}
