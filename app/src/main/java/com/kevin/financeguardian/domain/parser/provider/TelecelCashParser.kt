package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.ProviderParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.balanceAfter
import com.kevin.financeguardian.domain.parser.parseAmountMinor
import com.kevin.financeguardian.domain.parser.parseDateAndTimeInstant
import com.kevin.financeguardian.domain.parser.parsedResult
import com.kevin.financeguardian.domain.parser.referenceAfter

class TelecelCashParser : ProviderParser {
    override val provider: Provider = Provider.TELECEL_CASH

    override fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult? =
        parseSentToMtn(input, normalizedBody)
            ?: parsePaidMerchant(input, normalizedBody)
            ?: parseReceivedTransfer(input, normalizedBody)
            ?: parseBundlePurchase(input, normalizedBody)
            ?: parseAirtimePurchase(input, normalizedBody)
            ?: parseInterestCredit(input, normalizedBody)

    private fun parseSentToMtn(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Confirmed\. (GHS[0-9,.]+) sent to\s+([0-9*]+)\s+(.+?) on MTN MOBILE MONEY on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            occurredAt = parseDateAndTimeInstant(match.groupValues[4], match.groupValues[5]) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[3],
            counterpartyPhone = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            confidence = 0.93f,
        )
    }

    private fun parsePaidMerchant(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Confirmed\. (GHS[0-9,.]+) paid to (.+?) on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            occurredAt = parseDateAndTimeInstant(match.groupValues[3], match.groupValues[4]) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            confidence = 0.93f,
        )
    }

    private fun parseReceivedTransfer(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You have received (GHS[0-9,.]+) from (?:(?:MTN MOBILE MONEY with transaction reference: Transfer From: )?([0-9*]+)\s*-\s*)?(.+?)\s+on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            occurredAt = parseDateAndTimeInstant(match.groupValues[4], match.groupValues[5]) ?: input.receivedAt,
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = match.groupValues[3],
            counterpartyPhone = match.groupValues[2].ifBlank { null },
            reference = referenceAfter(body),
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            confidence = 0.93f,
        )
    }

    private fun parseBundlePurchase(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)bundle purchase request of (GHS[0-9,.]+) on ([0-9-]{10}) has been received at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            occurredAt = parseDateAndTimeInstant(match.groupValues[2], match.groupValues[3]) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = "Data Bundle",
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            confidence = 0.9f,
        )
    }

    private fun parseAirtimePurchase(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You bought (GHS[0-9,.]+) of airtime for ([0-9]+) on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            occurredAt = parseDateAndTimeInstant(match.groupValues[3], match.groupValues[4]) ?: input.receivedAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = "Airtime",
            counterpartyPhone = match.groupValues[2],
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            confidence = 0.9f,
        )
    }

    private fun parseInterestCredit(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)you have received (GHS[0-9,.]+) from Telecel Cash as interest earned""")
            .find(body) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null,
            counterpartyName = "Telecel Cash Interest",
            balanceAfterMinor = balanceAfter(interestBalancePattern, body),
            confidence = 0.85f,
        )
    }

    private companion object {
        val telecelBalancePattern = Regex("""(?i)Telecel Cash balance is\s*GHS\s*([0-9,.]+)""")
        val interestBalancePattern = Regex("""(?i)new balance is\s*GHS\s*([0-9,.]+)""")
    }
}
