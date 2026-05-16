package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.learning.LearningSignalRecorder
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
import javax.inject.Inject

class TransactionCorrectionService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
    private val learningSignalRecorder: LearningSignalRecorder,
) : TransactionCorrectionApplier {
    override suspend fun applyCorrection(
        transactionId: String,
        categoryId: String?,
        moneyMovementType: MoneyMovementType?,
        saveMerchantDefault: Boolean,
        plannedUse: String?,
        updatePlannedUse: Boolean,
    ): TransactionCorrectionResult {
        val transaction = transactionDao.getById(transactionId) ?: return TransactionCorrectionResult.NotFound
        val now = clock.now()

        if (moneyMovementType != null) {
            val includedInSpendingTotals = moneyMovementType == MoneyMovementType.EXPENSE ||
                moneyMovementType == MoneyMovementType.SUBSCRIPTION_CANDIDATE
            val includedInIncomeTotals = moneyMovementType == MoneyMovementType.INCOME
            transactionDao.updateFlowCorrection(
                flowId = transaction.flowId ?: transaction.id,
                categoryId = categoryId,
                type = moneyMovementType,
                flowType = moneyMovementType.toFlowType(),
                flowStatus = TransactionFlowStatus.COMPLETE,
                plannedUse = if (updatePlannedUse) plannedUse else transaction.plannedUse,
                includedInSpendingTotals = includedInSpendingTotals,
                includedInIncomeTotals = includedInIncomeTotals,
                updatedAt = now,
            )
        } else {
            transactionDao.updateCategory(transactionId, categoryId, now)
        }

        if (saveMerchantDefault) {
            val normalizedPhone = MerchantNormalizer.normalizePhone(transaction.counterpartyPhone)
            val normalizedName = MerchantNormalizer.normalizeName(transaction.counterpartyName)
            val merchant = normalizedPhone
                ?.let { merchantDao.findByPhone(it) }
                ?: normalizedName?.let { merchantDao.findByNormalizedName(it) }

            if (merchant != null) {
                merchantDao.updateDefaultCategory(merchant.id, categoryId, now)
            } else if (normalizedName != null || normalizedPhone != null) {
                val displayName = transaction.counterpartyName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: normalizedPhone
                    ?: return TransactionCorrectionResult.Applied
                val normalizedMerchantName = normalizedName ?: normalizedPhone
                    ?: return TransactionCorrectionResult.Applied

                merchantDao.upsert(
                    MerchantEntity(
                        id = idGenerator.newId(),
                        displayName = displayName,
                        normalizedName = normalizedMerchantName,
                        phone = normalizedPhone,
                        defaultCategoryId = categoryId,
                        createdFromTransactionId = transactionId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }

        learningSignalRecorder.recordCorrection(
            transaction = transaction,
            categoryId = categoryId,
            moneyMovementType = moneyMovementType ?: transaction.moneyMovementType,
            now = now,
        )

        return TransactionCorrectionResult.Applied
    }

    override suspend fun unlinkFlow(flowId: String): TransactionCorrectionResult {
        val updatedRows = transactionDao.unlinkFlow(
            flowId = flowId,
            flowStatus = TransactionFlowStatus.NEEDS_REVIEW,
            updatedAt = clock.now(),
        )
        return if (updatedRows == 0) {
            TransactionCorrectionResult.NotFound
        } else {
            TransactionCorrectionResult.Applied
        }
    }

    private fun MoneyMovementType.toFlowType(): TransactionFlowType =
        when (this) {
            MoneyMovementType.EXPENSE,
            MoneyMovementType.SUBSCRIPTION_CANDIDATE,
            -> TransactionFlowType.EXPENSE
            MoneyMovementType.INCOME -> TransactionFlowType.INCOME
            MoneyMovementType.INTERNAL_TRANSFER,
            MoneyMovementType.SAVINGS_CONTRIBUTION,
            -> TransactionFlowType.INTERNAL_TRANSFER
            MoneyMovementType.UNKNOWN -> TransactionFlowType.UNKNOWN
        }
}
