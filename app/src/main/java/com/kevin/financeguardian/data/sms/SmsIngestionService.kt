package com.kevin.financeguardian.data.sms

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import javax.inject.Inject

class SmsIngestionService @Inject constructor(
    private val smsMessageRecordDao: SmsMessageRecordDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsTransactionParser,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
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

        return when (
            val result = parser.parse(
                SmsParseInput(
                    sender = envelope.sender,
                    body = envelope.body,
                    receivedAt = envelope.receivedAt,
                ),
            )
        ) {
            is SmsParseResult.Parsed -> persistParsed(envelope, bodyHash, result)
            is SmsParseResult.Ignored -> persistNonParsed(
                envelope = envelope,
                bodyHash = bodyHash,
                status = ParseStatus.IGNORED,
                reason = result.reason,
            )

            is SmsParseResult.Failed -> persistNonParsed(
                envelope = envelope,
                bodyHash = bodyHash,
                status = ParseStatus.FAILED,
                reason = result.reason,
            )
        }
    }

    private suspend fun persistParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        result: SmsParseResult.Parsed,
    ): SmsIngestionResult.Parsed {
        val now = clock.now()
        val smsRecordId = idGenerator.newId()
        val transactionId = idGenerator.newId()

        smsMessageRecordDao.insert(
            SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = now,
                parseStatus = ParseStatus.PARSED,
                parseReason = null,
            ),
        )

        val parsed = result.transaction
        transactionDao.insert(
            TransactionEntity(
                id = transactionId,
                sourceMessageId = smsRecordId,
                provider = parsed.provider,
                rawSender = parsed.rawSender,
                rawBodyHash = bodyHash,
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

        return SmsIngestionResult.Parsed(smsRecordId, transactionId)
    }

    private suspend fun persistNonParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        status: ParseStatus,
        reason: String,
    ): SmsIngestionResult {
        val smsRecordId = idGenerator.newId()
        smsMessageRecordDao.insert(
            SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = clock.now(),
                parseStatus = status,
                parseReason = reason,
            ),
        )

        return when (status) {
            ParseStatus.IGNORED -> SmsIngestionResult.Ignored(smsRecordId, reason)
            ParseStatus.FAILED -> SmsIngestionResult.Failed(smsRecordId, reason)
            else -> error("Unsupported non-parsed status: $status")
        }
    }
}
