package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.parser.ParsedTransaction

data class TransactionFingerprint(
    val providerTransactionId: String?,
    val dedupeKey: String,
)

object TransactionFingerprintFactory {
    fun fromParsed(transaction: ParsedTransaction): TransactionFingerprint =
        create(
            provider = transaction.provider.name,
            providerTransactionId = transaction.providerTransactionId,
            occurredAtEpochSecond = transaction.occurredAt.epochSecond,
            direction = transaction.direction.name,
            moneyMovementType = transaction.moneyMovementType.name,
            amountMinor = transaction.amountMinor,
            currency = transaction.currency,
            counterpartyName = transaction.counterpartyName,
            counterpartyPhone = transaction.counterpartyPhone,
            reference = transaction.reference,
            balanceAfterMinor = transaction.balanceAfterMinor,
        )

    fun fromEntity(transaction: TransactionEntity): TransactionFingerprint =
        create(
            provider = transaction.provider.name,
            providerTransactionId = transaction.providerTransactionId,
            occurredAtEpochSecond = transaction.occurredAt.epochSecond,
            direction = transaction.direction.name,
            moneyMovementType = transaction.moneyMovementType.name,
            amountMinor = transaction.amountMinor,
            currency = transaction.currency,
            counterpartyName = transaction.counterpartyName,
            counterpartyPhone = transaction.counterpartyPhone,
            reference = transaction.reference,
            balanceAfterMinor = transaction.balanceAfterMinor,
        )

    private fun create(
        provider: String,
        providerTransactionId: String?,
        occurredAtEpochSecond: Long,
        direction: String,
        moneyMovementType: String,
        amountMinor: Long,
        currency: String,
        counterpartyName: String?,
        counterpartyPhone: String?,
        reference: String?,
        balanceAfterMinor: Long?,
    ): TransactionFingerprint {
        val normalizedProviderTransactionId = providerTransactionId?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedProviderTransactionId != null) {
            return TransactionFingerprint(
                providerTransactionId = normalizedProviderTransactionId,
                dedupeKey = "${provider.lowercase()}|tx|${normalizedProviderTransactionId.lowercase()}",
            )
        }

        val normalizedReference = MerchantNormalizer.normalizeName(reference).orEmpty()
        val normalizedPhone = MerchantNormalizer.normalizePhone(counterpartyPhone).orEmpty()
        val normalizedName = MerchantNormalizer.normalizeName(counterpartyName).orEmpty()
        val fallbackIdentity = when {
            normalizedPhone.isNotBlank() -> normalizedPhone
            normalizedReference.isBlank() && balanceAfterMinor == null -> normalizedName
            else -> ""
        }
        return TransactionFingerprint(
            providerTransactionId = null,
            dedupeKey = listOf(
                provider.lowercase(),
                direction.lowercase(),
                moneyMovementType.lowercase(),
                amountMinor.toString(),
                currency.lowercase(),
                occurredAtEpochSecond.toString(),
                balanceAfterMinor?.toString().orEmpty(),
                normalizedReference,
                fallbackIdentity,
            ).joinToString("|"),
        )
    }
}
