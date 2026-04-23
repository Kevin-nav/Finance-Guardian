package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.ProviderParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.knownSubscriptionMovement
import com.kevin.financeguardian.domain.parser.parseAmountMinor
import com.kevin.financeguardian.domain.parser.parseDateTimeInstant
import com.kevin.financeguardian.domain.parser.parsedResult
import com.kevin.financeguardian.domain.parser.providerTransactionIdAfter

class GcbBankParser : ProviderParser {
    override val provider: Provider = Provider.GCB

    override fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult? =
        parseAccountMessage(input, normalizedBody) ?: parsePrepaidCardMessage(input, normalizedBody)

    private fun parseAccountMessage(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)A/C No:(\S+)\s+has been (debited|credited) (GHS[0-9,.]+)(?: Fees:\s*GHS\s*[0-9,.]+)? Desc:\s*(.+?) Date:\s*([0-9-]{10} [0-9:]{5}) Bal:\s*GHS\s*([0-9,.]+)""",
        ).find(body) ?: return null
        val isCredit = match.groupValues[2].equals("credited", ignoreCase = true)
        val description = match.groupValues[4]
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = parseDateTimeInstant(match.groupValues[5]) ?: input.receivedAt,
            direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
            moneyMovementType = accountMovementType(description, isCredit),
            amountMinor = parseAmountMinor(match.groupValues[3]) ?: return null,
            counterpartyName = description,
            reference = description,
            balanceAfterMinor = parseAmountMinor(match.groupValues[6]),
            confidence = 0.94f,
        )
    }

    private fun parsePrepaidCardMessage(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Prepaid Card.*?has been (debited|credited) with\s+(?:an\s+)?amount of\s*:\s*([0-9,.]+)\s*(?:GHS|GHANA CEDIS).*?balance is\s*([0-9,.]+)\s*(?:GHS|GHANA CEDIS)""",
        ).find(body) ?: return null
        val isCredit = match.groupValues[1].equals("credited", ignoreCase = true)
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
            moneyMovementType = if (isCredit) MoneyMovementType.INCOME else MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[2]) ?: return null,
            counterpartyName = "Prepaid Card",
            reference = "Prepaid Card",
            balanceAfterMinor = parseAmountMinor(match.groupValues[3]),
            confidence = 0.86f,
        )
    }

    private fun accountMovementType(description: String, isCredit: Boolean): MoneyMovementType {
        if (isCredit) return MoneyMovementType.INCOME
        val lower = description.lowercase()
        return when {
            lower.contains("bank to wallet") ||
                lower.contains("b2w") ||
                lower.contains("visa card top up") -> MoneyMovementType.INTERNAL_TRANSFER
            else -> knownSubscriptionMovement(description)
        }
    }
}
