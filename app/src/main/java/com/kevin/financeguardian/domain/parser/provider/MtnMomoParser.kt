package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.ProviderParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.balanceAfter
import com.kevin.financeguardian.domain.parser.parseAmountMinor
import com.kevin.financeguardian.domain.parser.parseDateTimeInstant
import com.kevin.financeguardian.domain.parser.parseMtnCompactInstant
import com.kevin.financeguardian.domain.parser.parsedResult
import com.kevin.financeguardian.domain.parser.providerTransactionIdAfter
import com.kevin.financeguardian.domain.parser.referenceAfter

class MtnMomoParser : ProviderParser {
    override val provider: Provider = Provider.MTN_MOMO

    override fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult? =
        parseCompletedPayment(input, normalizedBody)
            ?: parsePaymentMade(input, normalizedBody)
            ?: parsePaymentFor(input, normalizedBody)
            ?: parseYelloMerchantPayment(input, normalizedBody)
            ?: parsePaymentReceived(input, normalizedBody)

    private fun parseCompletedPayment(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Your payment of (GHS\s*[0-9,.]+) to (.+?) has been completed at ([0-9-]{10} [0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = parseDateTimeInstant(match.groupValues[3]) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(mtnBalancePattern, body),
            confidence = 0.95f,
        )
    }

    private fun parsePaymentMade(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment made for (GHS\s*[0-9,.]+) to (.+?)(?:\.| Current Balance:)""")
            .find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            confidence = 0.9f,
        )
    }

    private fun parsePaymentFor(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment for (GHS\s*[0-9,.]+) to (.+?)\s*\.(?:Current Balance:)""")
            .find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            confidence = 0.9f,
        )
    }

    private fun parseYelloMerchantPayment(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You have Paid (GHS\s*[0-9,.]+) to (Merchant\s+[0-9]+).*? at ([0-9]{12})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = parseMtnCompactInstant(match.groupValues[3], input.receivedAt) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(mtnBalancePattern, body),
            confidence = 0.88f,
        )
    }

    private fun parsePaymentReceived(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment received for (GHS\s*[0-9,.]+) from (.+?)\s+Current Balance:""")
            .find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            confidence = 0.9f,
        )
    }

    private companion object {
        val mtnBalancePattern = Regex("""(?i)(?:Your new balance:|new balance:)\s*GHS\s*([0-9,.]+)""")
        val currentBalancePattern = Regex("""(?i)Current Balance:\s*GHS\s*([0-9,.]+)""")
    }
}
