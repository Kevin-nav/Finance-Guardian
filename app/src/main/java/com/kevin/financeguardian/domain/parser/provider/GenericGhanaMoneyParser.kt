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
        if (!hasTransactionEvidence(normalizedBody)) return null
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

    private fun hasTransactionEvidence(body: String): Boolean =
        balancePattern.containsMatchIn(body) ||
            providerTransactionIdPattern.containsMatchIn(body) ||
            accountOrWalletPattern.containsMatchIn(body) ||
            counterpartyPattern.containsMatchIn(body) ||
            explicitDebitCreditPattern.containsMatchIn(body)

    private companion object {
        val transactionVerbPattern = Regex("""(?i)\b(paid|payment|debited|credited|received|sent|bought)\b""")
        val creditPattern = Regex("""(?i)\b(?:credited|received\s+(?:GHS\s*[0-9,.]+\s+)?from)\b""")
        val balancePattern = Regex("""(?i)\b(?:current balance|available balance|new balance|balance|bal)\s*[:is]*\s*GHS\s*[0-9,.]+""")
        val providerTransactionIdPattern = Regex("""(?i)\b(?:financial transaction id|transaction id|external transaction id|reference|ref)\s*:""")
        val accountOrWalletPattern = Regex("""(?i)\b(?:wallet|account|a/c|mobile money|momo)\b""")
        val counterpartyPattern = Regex("""(?i)\b(?:from|to|sent to|paid to)\s+[A-Z0-9*][A-Z0-9* .'-]{2,}""")
        val explicitDebitCreditPattern = Regex("""(?i)\b(?:debited|credited)\b""")
    }
}
