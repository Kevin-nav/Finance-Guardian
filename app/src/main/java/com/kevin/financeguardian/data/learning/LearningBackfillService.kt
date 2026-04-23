package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import javax.inject.Inject

class LearningBackfillService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val learningSignalDao: LearningSignalDao,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend fun backfill() {
        val now = clock.now()
        val transactions = transactionDao.getAllOnce()
        for (transaction in transactions) {
            val categoryId = transaction.categoryId
            if (categoryId.isNullOrBlank() || categoryId == "unknown") continue
            val features = LearningFeatureExtractor.fromTransaction(
                com.kevin.financeguardian.domain.model.Transaction(
                    id = transaction.id,
                    sourceMessageId = transaction.sourceMessageId,
                    provider = transaction.provider,
                    rawSender = transaction.rawSender,
                    rawBodyHash = transaction.rawBodyHash,
                    providerTransactionId = transaction.providerTransactionId,
                    dedupeKey = transaction.dedupeKey,
                    occurredAt = transaction.occurredAt,
                    direction = transaction.direction,
                    moneyMovementType = transaction.moneyMovementType,
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    counterpartyName = transaction.counterpartyName,
                    counterpartyPhone = transaction.counterpartyPhone,
                    reference = transaction.reference,
                    balanceAfterMinor = transaction.balanceAfterMinor,
                    categoryId = categoryId,
                    confidence = transaction.confidence,
                    createdAt = transaction.createdAt,
                    updatedAt = transaction.updatedAt,
                ),
            )
            val signalKeys = buildList {
                features.normalizedMerchantName?.let {
                    add(LearningFeatureExtractor.merchantSignalKey(transaction.provider, it))
                }
                features.normalizedPhone?.let {
                    add(LearningFeatureExtractor.phoneSignalKey(transaction.provider, it))
                }
                features.normalizedReference?.let {
                    add(LearningFeatureExtractor.referenceSignalKey(transaction.provider, it))
                }
                val merchantName = features.normalizedMerchantName
                val reference = features.normalizedReference
                if (merchantName != null && reference != null) {
                    add(LearningFeatureExtractor.merchantReferenceSignalKey(transaction.provider, merchantName, reference))
                }
            }
            for (signalKey in signalKeys) {
                val existing = learningSignalDao.getBySignalKey(signalKey)
                learningSignalDao.upsert(
                    LearningSignalEntity(
                        id = existing?.id ?: idGenerator.newId(),
                        signalKey = signalKey,
                        transactionId = transaction.id,
                        provider = transaction.provider,
                        normalizedMerchantName = features.normalizedMerchantName,
                        normalizedPhone = features.normalizedPhone,
                        normalizedReference = features.normalizedReference,
                        amountBucket = features.amountBucket,
                        direction = transaction.direction,
                        moneyMovementType = transaction.moneyMovementType,
                        categoryId = categoryId,
                        signalType = SIGNAL_TYPE_BACKFILLED_PATTERN,
                        weight = (existing?.weight ?: 0f) + BACKFILL_WEIGHT,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    companion object {
        const val SIGNAL_TYPE_BACKFILLED_PATTERN = "BACKFILLED_PATTERN"
        const val BACKFILL_WEIGHT = 0.35f
    }
}
