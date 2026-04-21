package com.kevin.financeguardian.data.merchant

object MerchantNormalizer {
    private val nonNameCharacters = Regex("""[^a-z0-9]+""")
    private val whitespace = Regex("""\s+""")

    fun normalizeName(name: String?): String? {
        val normalized = name
            ?.trim()
            ?.lowercase()
            ?.replace(nonNameCharacters, " ")
            ?.replace(whitespace, " ")
            ?.trim()
            .orEmpty()
        return normalized.ifBlank { null }
    }

    fun normalizePhone(phone: String?): String? {
        val normalized = phone
            ?.filter { it.isDigit() || it == '*' }
            .orEmpty()
        return normalized.ifBlank { null }
    }
}
