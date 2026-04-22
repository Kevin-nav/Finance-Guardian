package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.model.MoneyMovementType
import javax.inject.Inject

class TransactionCorrectionService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) : TransactionCorrectionApplier {
    override suspend fun applyCorrection(
        transactionId: String,
        categoryId: String?,
        moneyMovementType: MoneyMovementType?,
        saveMerchantDefault: Boolean,
    ): TransactionCorrectionResult {
        val transaction = transactionDao.getById(transactionId) ?: return TransactionCorrectionResult.NotFound
        val now = clock.now()

        transactionDao.updateCategory(transactionId, categoryId, now)
        if (moneyMovementType != null) {
            transactionDao.updateMoneyMovementType(transactionId, moneyMovementType, now)
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

        return TransactionCorrectionResult.Applied
    }
}
