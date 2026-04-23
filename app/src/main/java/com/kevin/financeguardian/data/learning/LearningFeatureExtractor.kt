package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction

data class LearningFeatures(
    val normalizedMerchantName: String?,
    val normalizedPhone: String?,
    val normalizedReference: String?,
    val amountBucket: String,
)

object LearningFeatureExtractor {
    fun fromTransaction(transaction: Transaction): LearningFeatures =
        LearningFeatures(
            normalizedMerchantName = MerchantNormalizer.normalizeName(transaction.counterpartyName),
            normalizedPhone = MerchantNormalizer.normalizePhone(transaction.counterpartyPhone),
            normalizedReference = MerchantNormalizer.normalizeName(transaction.reference),
            amountBucket = amountBucket(transaction.amountMinor),
        )

    fun amountBucket(amountMinor: Long): String {
        val amountMajor = amountMinor / 100.0
        return when {
            amountMajor < 5.0 -> "micro"
            amountMajor < 30.0 -> "small"
            amountMajor < 150.0 -> "medium"
            else -> "large"
        }
    }

    fun merchantSignalKey(provider: Provider, normalizedMerchantName: String): String =
        "merchant|${provider.name.lowercase()}|$normalizedMerchantName"

    fun phoneSignalKey(provider: Provider, normalizedPhone: String): String =
        "phone|${provider.name.lowercase()}|$normalizedPhone"

    fun referenceSignalKey(provider: Provider, normalizedReference: String): String =
        "reference|${provider.name.lowercase()}|$normalizedReference"

    fun merchantReferenceSignalKey(
        provider: Provider,
        normalizedMerchantName: String,
        normalizedReference: String,
    ): String = "merchant_reference|${provider.name.lowercase()}|$normalizedMerchantName|$normalizedReference"
}
