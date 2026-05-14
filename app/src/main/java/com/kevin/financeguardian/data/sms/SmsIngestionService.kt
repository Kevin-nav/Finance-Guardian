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
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.data.transaction.TransactionFingerprintFactory
import com.kevin.financeguardian.data.transaction.TransactionFlowMatcher
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.ParsedTransactionEvent
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import com.kevin.financeguardian.domain.parser.TransactionFlowClassifier
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
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
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val flowClassifier: TransactionFlowClassifier = TransactionFlowClassifier(),
    private val flowMatcher: TransactionFlowMatcher = TransactionFlowMatcher(),
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
        val event = parsed.event ?: parsed.toFallbackEvent(result.confidence)
        val ownedWallets = userPreferencesRepository?.preferences?.first()?.ownedWallets.orEmpty()
        val flowDraft = flowClassifier.classify(
            event = event,
            userConfirmedInstruments = ownedWallets,
        )
        moneyMovementType = flowDraft.moneyMovementType
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
                    moneyMovementType = moneyMovementType,
                    amountMinor = parsed.amountMinor,
                    currency = parsed.currency,
                    counterpartyName = parsed.counterpartyName,
                    counterpartyPhone = parsed.counterpartyPhone,
                    reference = parsed.reference,
                    balanceAfterMinor = parsed.balanceAfterMinor,
                    balanceReliability = event.balanceReliability,
                    categoryId = null,
                    flowId = transactionId,
                    flowType = flowDraft.flowType,
                    flowStatus = flowDraft.status,
                    plannedUse = flowDraft.plannedUse,
                    includedInSpendingTotals = flowDraft.includedInSpendingTotals,
                    includedInIncomeTotals = flowDraft.includedInIncomeTotals,
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
                balanceReliability = event.balanceReliability,
                categoryId = categoryId,
                flowId = transactionId,
                flowType = flowDraft.flowType,
                flowStatus = flowDraft.status,
                plannedUse = flowDraft.plannedUse,
                eventChannel = event.channel,
                eventSourceInstrumentType = event.sourceInstrument?.type,
                eventSourceInstrumentProvider = event.sourceInstrument?.provider,
                eventSourceInstrumentIdentifier = event.sourceInstrument?.identifier,
                eventDestinationInstrumentType = event.destinationInstrument?.type,
                eventDestinationInstrumentProvider = event.destinationInstrument?.provider,
                eventDestinationInstrumentIdentifier = event.destinationInstrument?.identifier,
                eventProviderReference = event.providerReference,
                eventInferredIdentifiers = event.inferredIdentifiers.encodeIdentifiers(),
                includedInSpendingTotals = flowDraft.includedInSpendingTotals,
                includedInIncomeTotals = flowDraft.includedInIncomeTotals,
                confidence = result.confidence,
                createdAt = now,
                updatedAt = now,
            ),
        )
        linkMatchingFlowIfPresent(
            transactionId = transactionId,
            newEvent = event,
            now = now,
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

    private suspend fun linkMatchingFlowIfPresent(
        transactionId: String,
        newEvent: ParsedTransactionEvent,
        now: Instant,
    ) {
        val candidates = transactionDao.getAllOnce().filter { it.id != transactionId }
        val match = candidates.firstOrNull { candidate ->
            val candidateEvent = candidate.toFallbackEvent()
            flowMatcher.match(candidateEvent, newEvent).matched
        } ?: return
        val flowId = match.flowId ?: match.id
        transactionDao.updateFlowMetadata(
            transactionId = match.id,
            flowId = flowId,
            flowType = TransactionFlowType.INTERNAL_TRANSFER,
            flowStatus = TransactionFlowStatus.COMPLETE,
            plannedUse = match.plannedUse,
            includedInSpendingTotals = false,
            includedInIncomeTotals = false,
            updatedAt = now,
        )
        transactionDao.updateFlowMetadata(
            transactionId = transactionId,
            flowId = flowId,
            flowType = TransactionFlowType.INTERNAL_TRANSFER,
            flowStatus = TransactionFlowStatus.COMPLETE,
            plannedUse = newEvent.plannedUse,
            includedInSpendingTotals = false,
            includedInIncomeTotals = false,
            updatedAt = now,
        )
    }

    private fun com.kevin.financeguardian.domain.parser.ParsedTransaction.toFallbackEvent(
        confidence: Float,
    ): ParsedTransactionEvent =
        ParsedTransactionEvent(
            provider = provider,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            directionFromProviderPerspective = direction,
            channel = when (moneyMovementType) {
                com.kevin.financeguardian.domain.model.MoneyMovementType.INTERNAL_TRANSFER -> MoneyMovementChannel.UNKNOWN
                else -> MoneyMovementChannel.UNKNOWN
            },
            counterpartyName = counterpartyName,
            counterpartyPhone = counterpartyPhone,
            providerTransactionId = providerTransactionId,
            providerReference = reference,
            description = reference,
            plannedUse = plannedUse ?: reference,
            balanceAfterMinor = balanceAfterMinor,
            balanceReliability = BalanceReliability.UNKNOWN,
            confidence = confidence,
        )

    private fun TransactionEntity.toFallbackEvent(): ParsedTransactionEvent =
        ParsedTransactionEvent(
            provider = provider,
            sourceMessageId = sourceMessageId,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            directionFromProviderPerspective = direction,
            channel = eventChannel ?: when (flowType) {
                TransactionFlowType.CASH_DEPOSIT -> MoneyMovementChannel.CASH_DEPOSIT
                TransactionFlowType.CARD_SPEND -> MoneyMovementChannel.CARD_SPEND
                else -> MoneyMovementChannel.UNKNOWN
            },
            sourceInstrument = eventSourceInstrumentIdentifier?.let { identifier ->
                com.kevin.financeguardian.domain.parser.ParsedInstrument(
                    type = eventSourceInstrumentType ?: com.kevin.financeguardian.domain.model.InstrumentType.UNKNOWN,
                    provider = eventSourceInstrumentProvider ?: com.kevin.financeguardian.domain.model.InstrumentProvider.UNKNOWN,
                    identifier = identifier,
                    inferred = true,
                )
            },
            destinationInstrument = eventDestinationInstrumentIdentifier?.let { identifier ->
                com.kevin.financeguardian.domain.parser.ParsedInstrument(
                    type = eventDestinationInstrumentType ?: com.kevin.financeguardian.domain.model.InstrumentType.UNKNOWN,
                    provider = eventDestinationInstrumentProvider ?: com.kevin.financeguardian.domain.model.InstrumentProvider.UNKNOWN,
                    identifier = identifier,
                    inferred = true,
                )
            },
            counterpartyName = counterpartyName,
            counterpartyPhone = counterpartyPhone,
            providerTransactionId = providerTransactionId,
            providerReference = eventProviderReference ?: reference,
            description = reference,
            plannedUse = plannedUse,
            balanceAfterMinor = balanceAfterMinor,
            balanceReliability = balanceReliability,
            inferredIdentifiers = eventInferredIdentifiers.decodeIdentifiers(),
            confidence = confidence,
        )

    private fun List<String>.encodeIdentifiers(): String? =
        takeIf { it.isNotEmpty() }?.joinToString("|") {
            it.replace("%", "%25").replace("|", "%7C")
        }

    private fun String?.decodeIdentifiers(): List<String> =
        this
            ?.split("|")
            ?.map { it.replace("%7C", "|").replace("%25", "%") }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private data class ParsedPersistence(
        val ingestionResult: SmsIngestionResult,
        val notificationEvent: NotificationEvent?,
    )

    private companion object {
        const val REVIEW_CONFIDENCE_THRESHOLD = 0.85f
    }
}
