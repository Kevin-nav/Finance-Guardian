package com.kevin.financeguardian.domain.parser

import java.math.BigDecimal
import java.math.RoundingMode

private val amountPattern = Regex(
    pattern = """(?i)(?:GHS\s*)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:GHS|GHANA\s+CEDIS)?""",
)
private val currencyBoundAmountPattern = Regex(
    pattern = """(?i)(?:GHS\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:GHS|GHANA\s+CEDIS))""",
)

fun parseAmountMinor(raw: String): Long? {
    val amountText = amountPattern.find(raw)?.groupValues?.getOrNull(1) ?: return null
    return amountText
        .replace(",", "")
        .toBigDecimalOrNull()
        ?.multiply(BigDecimal(100))
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.longValueExact()
}

fun findAmountMinor(body: String): Long? {
    val match = currencyBoundAmountPattern.find(body) ?: return null
    val amountText = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: match.groupValues.getOrNull(2)
        ?: return null
    return parseAmountMinor(amountText)
}
