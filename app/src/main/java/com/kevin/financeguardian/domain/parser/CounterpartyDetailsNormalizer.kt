package com.kevin.financeguardian.domain.parser

data class CounterpartyDetails(
    val counterpartyName: String?,
    val reference: String?,
)

object CounterpartyDetailsNormalizer {
    fun normalize(
        counterpartyName: String?,
        reference: String?,
    ): CounterpartyDetails {
        val cleanName = counterpartyName.cleanTransactionText()
        val cleanReference = reference.cleanTransactionText()
        val merchantIdMatch = cleanName?.let(::extractMerchantIdSuffix)
        if (merchantIdMatch == null) {
            return CounterpartyDetails(
                counterpartyName = cleanName,
                reference = cleanReference,
            )
        }

        return CounterpartyDetails(
            counterpartyName = merchantIdMatch.displayName,
            reference = mergeReference(
                reference = cleanReference,
                extraDetail = "Merchant ID: ${merchantIdMatch.merchantId}",
            ),
        )
    }

    fun qualityScore(counterpartyName: String?): Int {
        val cleanName = counterpartyName.cleanTransactionText() ?: return 0
        return when {
            genericMerchantIdPattern.matches(cleanName) -> 1
            else -> 3
        }
    }

    fun isDetailsReference(reference: String?): Boolean =
        reference.cleanTransactionText()?.startsWith("Merchant ID:", ignoreCase = true) == true

    private fun mergeReference(
        reference: String?,
        extraDetail: String,
    ): String =
        when {
            reference.isNullOrBlank() -> extraDetail
            reference.contains(extraDetail, ignoreCase = true) -> reference
            else -> "$reference | $extraDetail"
        }

    private fun extractMerchantIdSuffix(value: String): MerchantIdMatch? {
        val match = merchantIdSuffixPattern.matchEntire(value) ?: return null
        return MerchantIdMatch(
            displayName = match.groupValues[1].cleanTransactionText() ?: return null,
            merchantId = match.groupValues[2].cleanTransactionText() ?: return null,
        )
    }

    private fun String?.cleanTransactionText(): String? {
        val cleaned = this
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trim('.', ',', ' ')
            ?.takeIf { it.isNotBlank() }
        return cleaned
    }

    private data class MerchantIdMatch(
        val displayName: String,
        val merchantId: String,
    )

    private val merchantIdSuffixPattern = Regex("""^(.+?)\.([A-Z]{2,12}[A-Z0-9-]{0,12}|[0-9]{3,16})$""")
    private val genericMerchantIdPattern = Regex("""(?i)^merchant\s+[0-9]{3,16}$""")
}
