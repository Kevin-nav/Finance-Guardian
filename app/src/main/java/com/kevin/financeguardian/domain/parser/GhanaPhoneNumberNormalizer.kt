package com.kevin.financeguardian.domain.parser

data class NormalizedGhanaPhone(
    val canonical: String,
    val raw: String,
    val masked: Boolean = false,
)

object GhanaPhoneNumberNormalizer {
    private val maskedPattern = Regex("""\*{2,}\d{2,6}""")

    fun normalize(raw: String): NormalizedGhanaPhone? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (isMasked(trimmed)) {
            return NormalizedGhanaPhone(canonical = trimmed.filter { it == '*' || it.isDigit() }, raw = raw, masked = true)
        }

        val digits = trimmed.filter(Char::isDigit)
        val canonical = when {
            digits.length == 10 && digits.startsWith("0") -> "233" + digits.drop(1)
            digits.length == 12 && digits.startsWith("233") -> digits
            else -> null
        } ?: return null

        val nationalPart = canonical.drop(3)
        if (nationalPart.length != 9 || nationalPart.firstOrNull() !in '2'..'5') return null
        return NormalizedGhanaPhone(canonical = canonical, raw = raw)
    }

    fun sameNumber(left: String?, right: String?): Boolean {
        val normalizedLeft = left?.let(::normalize)?.takeUnless { it.masked }?.canonical
        val normalizedRight = right?.let(::normalize)?.takeUnless { it.masked }?.canonical
        return normalizedLeft != null && normalizedLeft == normalizedRight
    }

    fun isMasked(raw: String): Boolean =
        maskedPattern.matches(raw.trim().replace(" ", ""))
}
