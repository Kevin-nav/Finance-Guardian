package com.kevin.financeguardian.data.merchant

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant
import javax.inject.Inject

class MerchantCategoryResolver @Inject constructor(
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
) {
    suspend fun resolveForParsedTransaction(
        provider: Provider = Provider.UNKNOWN,
        moneyMovementType: MoneyMovementType = MoneyMovementType.UNKNOWN,
        counterpartyName: String?,
        counterpartyPhone: String?,
        reference: String? = null,
        transactionId: String,
        now: Instant,
    ): String? {
        val normalizedPhone = MerchantNormalizer.normalizePhone(counterpartyPhone)
        val normalizedName = MerchantNormalizer.normalizeName(counterpartyName)
        val existing = normalizedPhone
            ?.let { merchantDao.findByPhone(it) }
            ?: normalizedName?.let { merchantDao.findByNormalizedName(it) }

        val existingDefault = existing?.defaultCategoryId
        if (existingDefault != null) return existingDefault
        if (normalizedName == null && normalizedPhone == null) return null

        val displayName = counterpartyName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalizedPhone
            ?: return null
        val normalizedMerchantName = normalizedName ?: normalizedPhone ?: return null

        val inferredCategoryId = inferCategory(
            provider = provider,
            moneyMovementType = moneyMovementType,
            counterpartyName = counterpartyName,
            reference = reference,
        )
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
        return inferredCategoryId
    }

    fun inferCategory(
        provider: Provider,
        moneyMovementType: MoneyMovementType,
        counterpartyName: String?,
        reference: String?,
    ): String? {
        return when (moneyMovementType) {
            MoneyMovementType.INCOME -> "income"
            MoneyMovementType.INTERNAL_TRANSFER -> "transfers"
            MoneyMovementType.SAVINGS_CONTRIBUTION -> "savings"
            MoneyMovementType.SUBSCRIPTION_CANDIDATE -> "subscriptions"
            else -> inferExpenseCategory(provider, counterpartyName, reference)
        }
    }

    private fun inferExpenseCategory(
        provider: Provider,
        counterpartyName: String?,
        reference: String?,
    ): String? {
        val normalizedName = MerchantNormalizer.normalizeName(counterpartyName).orEmpty()
        val normalizedReference = MerchantNormalizer.normalizeName(reference).orEmpty()
        val text = listOf(normalizedName, normalizedReference)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return when {
            text.contains("airtime") || text.contains("bundle") || text.contains("data") -> "airtime_data"
            text.contains("laundry") || text.contains("dry clean") -> "laundry"
            text.contains("restaurant") ||
                text.contains("snacks") ||
                text.contains("fried rice") ||
                text.contains("kenkey") ||
                text.contains("food") -> "food"
            provider == Provider.GCB && normalizedReference.contains("bank to wallet") -> "transfers"
            else -> null
        }
    }
}
