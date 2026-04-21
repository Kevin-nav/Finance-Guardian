package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.ProviderParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.findAmountMinor
import com.kevin.financeguardian.domain.parser.parsedResult

class GenericGhanaMoneyParser : ProviderParser {
    override val provider: Provider = Provider.UNKNOWN

    override fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult? {
        if (!hasMoney(normalizedBody) || !hasTransactionVerb(normalizedBody)) return null
        val amount = findAmountMinor(normalizedBody) ?: return null
        val direction = when {
            creditPattern.containsMatchIn(normalizedBody) -> TransactionDirection.CREDIT
            else -> TransactionDirection.DEBIT
        }
        return parsedResult(
            provider = Provider.UNKNOWN,
            input = input,
            direction = direction,
            moneyMovementType = MoneyMovementType.UNKNOWN,
            amountMinor = amount,
            counterpartyName = null,
            confidence = 0.45f,
        )
    }

    private fun hasMoney(body: String): Boolean =
        body.contains("GHS", ignoreCase = true) || body.contains("GHANA CEDIS", ignoreCase = true)

    private fun hasTransactionVerb(body: String): Boolean =
        transactionVerbPattern.containsMatchIn(body)

    private companion object {
        val transactionVerbPattern = Regex("""(?i)\b(paid|payment|debited|credited|received|sent|bought)\b""")
        val creditPattern = Regex("""(?i)\b(credited|received)\b""")
    }
}
