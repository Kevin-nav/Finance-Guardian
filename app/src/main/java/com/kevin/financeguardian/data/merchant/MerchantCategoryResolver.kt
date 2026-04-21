package com.kevin.financeguardian.data.merchant

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import java.time.Instant
import javax.inject.Inject

class MerchantCategoryResolver @Inject constructor(
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
) {
    suspend fun resolveForParsedTransaction(
        counterpartyName: String?,
        counterpartyPhone: String?,
        transactionId: String,
        now: Instant,
    ): String? {
        val normalizedPhone = MerchantNormalizer.normalizePhone(counterpartyPhone)
        val normalizedName = MerchantNormalizer.normalizeName(counterpartyName)
        val existing = normalizedPhone
            ?.let { merchantDao.findByPhone(it) }
            ?: normalizedName?.let { merchantDao.findByNormalizedName(it) }

        if (existing != null) return existing.defaultCategoryId
        if (normalizedName == null && normalizedPhone == null) return null

        val displayName = counterpartyName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalizedPhone
            ?: return null
        val normalizedMerchantName = normalizedName ?: normalizedPhone ?: return null

        merchantDao.upsert(
            MerchantEntity(
                id = idGenerator.newId(),
                displayName = displayName,
                normalizedName = normalizedMerchantName,
                phone = normalizedPhone,
                defaultCategoryId = null,
                createdFromTransactionId = transactionId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return null
    }
}
