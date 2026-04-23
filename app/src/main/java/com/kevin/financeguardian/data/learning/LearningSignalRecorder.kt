package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import java.time.Instant

open class LearningSignalRecorder(
    private val learningSignalDao: LearningSignalDao,
    private val idGenerator: IdGenerator,
) {
    open suspend fun recordCorrection(
        transaction: TransactionEntity,
        categoryId: String?,
        moneyMovementType: com.kevin.financeguardian.domain.model.MoneyMovementType,
        now: Instant,
    ) {
        val features = LearningFeatureExtractor.fromTransaction(transaction.toDomain(categoryId, moneyMovementType))
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
            val weight = (existing?.weight ?: 0f) + USER_CORRECTION_WEIGHT
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
                    moneyMovementType = moneyMovementType,
                    categoryId = categoryId,
                    signalType = SIGNAL_TYPE_USER_CORRECTION,
                    weight = weight,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun TransactionEntity.toDomain(
        categoryId: String?,
        moneyMovementType: com.kevin.financeguardian.domain.model.MoneyMovementType,
    ) = com.kevin.financeguardian.domain.model.Transaction(
        id = id,
        sourceMessageId = sourceMessageId,
        provider = provider,
        rawSender = rawSender,
        rawBodyHash = rawBodyHash,
        providerTransactionId = providerTransactionId,
        dedupeKey = dedupeKey,
        occurredAt = occurredAt,
        direction = direction,
        moneyMovementType = moneyMovementType,
        amountMinor = amountMinor,
        currency = currency,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        reference = reference,
        balanceAfterMinor = balanceAfterMinor,
        categoryId = categoryId,
        confidence = confidence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val SIGNAL_TYPE_USER_CORRECTION = "USER_CORRECTION"
        const val USER_CORRECTION_WEIGHT = 1f
    }
}
