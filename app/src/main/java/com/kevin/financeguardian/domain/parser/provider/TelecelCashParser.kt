package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.GhanaPhoneNumberNormalizer
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.ParsedInstrument
import com.kevin.financeguardian.domain.parser.ParsedTransactionEvent
import com.kevin.financeguardian.domain.parser.ProviderParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.balanceAfter
import com.kevin.financeguardian.domain.parser.parseAmountMinor
import com.kevin.financeguardian.domain.parser.parseDateAndTimeInstant
import com.kevin.financeguardian.domain.parser.parsedResult
import com.kevin.financeguardian.domain.parser.providerTransactionIdAfter
import com.kevin.financeguardian.domain.parser.referenceAfter
import java.time.Instant

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
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateAndTimeInstant(match.groupValues[4], match.groupValues[5]) ?: input.receivedAt
        val normalizedPhone = GhanaPhoneNumberNormalizer.normalize(match.groupValues[2])?.canonical
        val reference = referenceAfter(body)
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = match.groupValues[3],
            counterpartyPhone = normalizedPhone ?: match.groupValues[2],
            reference = reference,
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            event = baseEvent(
                input = input,
                body = body,
                occurredAt = occurredAt,
                amountMinor = amountMinor,
                direction = TransactionDirection.DEBIT,
                channel = MoneyMovementChannel.WALLET_TO_WALLET,
                counterpartyName = match.groupValues[3],
                counterpartyPhone = normalizedPhone,
                reference = reference,
                sourceInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.TELECEL, null, "Telecel Cash", inferred = true),
                destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, normalizedPhone, match.groupValues[2], inferred = true),
            ),
            confidence = 0.93f,
        )
    }

    private fun parsePaidMerchant(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Confirmed\. (GHS[0-9,.]+) paid to (.+?) on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateAndTimeInstant(match.groupValues[3], match.groupValues[4]) ?: input.receivedAt
        val reference = referenceAfter(body)
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = match.groupValues[2],
            reference = reference,
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            event = baseEvent(input, body, occurredAt, amountMinor, TransactionDirection.DEBIT, MoneyMovementChannel.MERCHANT_PAYMENT, match.groupValues[2], reference = reference),
            confidence = 0.93f,
        )
    }

    private fun parseReceivedTransfer(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You have received (GHS[0-9,.]+) from (?:(?:MTN MOBILE MONEY with transaction reference: Transfer From: )?([0-9*]+)\s*-\s*)?(.+?)\s+on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateAndTimeInstant(match.groupValues[4], match.groupValues[5]) ?: input.receivedAt
        val normalizedPhone = match.groupValues[2].ifBlank { null }?.let { GhanaPhoneNumberNormalizer.normalize(it)?.canonical ?: it }
        val reference = referenceAfter(body)
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = amountMinor,
            counterpartyName = match.groupValues[3],
            counterpartyPhone = normalizedPhone,
            reference = reference,
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            event = baseEvent(
                input = input,
                body = body,
                occurredAt = occurredAt,
                amountMinor = amountMinor,
                direction = TransactionDirection.CREDIT,
                channel = if (normalizedPhone != null) MoneyMovementChannel.WALLET_TO_WALLET else MoneyMovementChannel.UNKNOWN,
                counterpartyName = match.groupValues[3],
                counterpartyPhone = normalizedPhone,
                reference = reference,
                sourceInstrument = normalizedPhone?.let {
                    ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, it, it, inferred = true)
                },
                destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.TELECEL, null, "Telecel Cash", inferred = true),
            ),
            confidence = 0.93f,
        )
    }

    private fun parseBundlePurchase(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)bundle purchase request of (GHS[0-9,.]+) on ([0-9-]{10}) has been received at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateAndTimeInstant(match.groupValues[2], match.groupValues[3]) ?: input.receivedAt
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = "Data Bundle",
            reference = "Bundle purchase",
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            event = baseEvent(input, body, occurredAt, amountMinor, TransactionDirection.DEBIT, MoneyMovementChannel.AIRTIME_DATA, "Data Bundle", reference = "Bundle purchase"),
            confidence = 0.9f,
        )
    }

    private fun parseAirtimePurchase(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You bought (GHS[0-9,.]+) of airtime for ([0-9]+) on ([0-9-]{10}) at ([0-9:]{8})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateAndTimeInstant(match.groupValues[3], match.groupValues[4]) ?: input.receivedAt
        val normalizedPhone = GhanaPhoneNumberNormalizer.normalize(match.groupValues[2])?.canonical ?: match.groupValues[2]
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = "Airtime",
            counterpartyPhone = normalizedPhone,
            reference = "Airtime purchase",
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body),
            event = baseEvent(input, body, occurredAt, amountMinor, TransactionDirection.DEBIT, MoneyMovementChannel.AIRTIME_DATA, "Airtime", counterpartyPhone = normalizedPhone, reference = "Airtime purchase"),
            confidence = 0.9f,
        )
    }

    private fun parseInterestCredit(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)you have received (GHS[0-9,.]+) from Telecel Cash as interest earned""")
            .find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = amountMinor,
            counterpartyName = "Telecel Cash Interest",
            reference = "Interest earned",
            balanceAfterMinor = balanceAfter(interestBalancePattern, body),
            event = baseEvent(input, body, input.receivedAt, amountMinor, TransactionDirection.CREDIT, MoneyMovementChannel.UNKNOWN, "Telecel Cash Interest", reference = "Interest earned"),
            confidence = 0.85f,
        )
    }

    private fun baseEvent(
        input: SmsParseInput,
        body: String,
        occurredAt: Instant,
        amountMinor: Long,
        direction: TransactionDirection,
        channel: MoneyMovementChannel,
        counterpartyName: String?,
        counterpartyPhone: String? = null,
        reference: String?,
        sourceInstrument: ParsedInstrument? = null,
        destinationInstrument: ParsedInstrument? = null,
    ): ParsedTransactionEvent =
        ParsedTransactionEvent(
            provider = provider,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            directionFromProviderPerspective = direction,
            channel = channel,
            sourceInstrument = sourceInstrument,
            destinationInstrument = destinationInstrument,
            counterpartyName = counterpartyName,
            counterpartyPhone = counterpartyPhone,
            providerTransactionId = providerTransactionIdAfter(body),
            providerReference = reference,
            description = counterpartyName,
            plannedUse = reference,
            feeMinor = Regex("""(?i)You were charged GHS\s*([0-9,.]+)""").find(body)?.groupValues?.getOrNull(1)?.let(::parseAmountMinor),
            taxMinor = Regex("""(?i)E-levy charge is GHS\s*([0-9,.]+)""").find(body)?.groupValues?.getOrNull(1)?.let(::parseAmountMinor),
            balanceAfterMinor = balanceAfter(telecelBalancePattern, body) ?: balanceAfter(interestBalancePattern, body),
            balanceReliability = BalanceReliability.RELIABLE,
            inferredIdentifiers = listOfNotNull(counterpartyPhone),
            confidence = 0.9f,
        )

    private companion object {
        val telecelBalancePattern = Regex("""(?i)(?:new\s+)?Telecel Cash balance is\s*GHS\s*([0-9,.]+)""")
        val interestBalancePattern = Regex("""(?i)new balance is\s*GHS\s*([0-9,.]+)""")
    }
}
