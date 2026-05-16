package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.CounterpartyDetailsNormalizer
import com.kevin.financeguardian.domain.parser.GhanaPhoneNumberNormalizer
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.ParsedInstrument
import com.kevin.financeguardian.domain.parser.ParsedTransactionEvent
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
import java.time.Instant

class MtnMomoParser : ProviderParser {
    override val provider: Provider = Provider.MTN_MOMO

    override fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult? =
        parseCompletedPayment(input, normalizedBody)
            ?: parsePaymentMade(input, normalizedBody)
            ?: parsePaymentFor(input, normalizedBody)
            ?: parseYelloMerchantPayment(input, normalizedBody)
            ?: parseCashIn(input, normalizedBody)
            ?: parsePaymentReceived(input, normalizedBody)

    private fun parseCompletedPayment(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Your payment of (GHS\s*[0-9,.]+) to (.+?) has been completed at ([0-9-]{10} [0-9:]{8})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseDateTimeInstant(match.groupValues[3]) ?: input.receivedAt
        val reference = referenceAfter(body)
        val channel = if (match.groupValues[2].contains("airtime", ignoreCase = true)) {
            MoneyMovementChannel.AIRTIME_DATA
        } else {
            MoneyMovementChannel.MERCHANT_PAYMENT
        }
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
            balanceAfterMinor = balanceAfter(mtnBalancePattern, body),
            event = baseEvent(input, body, occurredAt, amountMinor, TransactionDirection.DEBIT, channel, match.groupValues[2], reference = reference),
            confidence = 0.95f,
        )
    }

    private fun parsePaymentMade(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment made for (GHS\s*[0-9,.]+) to (.+?)(?:\.| Current Balance:)""")
            .find(body) ?: return null
        val counterpartyDetails = CounterpartyDetailsNormalizer.normalize(
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
        )
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = counterpartyDetails.counterpartyName,
            reference = counterpartyDetails.reference,
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            event = baseEvent(
                input,
                body,
                input.receivedAt,
                amountMinor,
                TransactionDirection.DEBIT,
                MoneyMovementChannel.MERCHANT_PAYMENT,
                counterpartyDetails.counterpartyName,
                reference = counterpartyDetails.reference,
            ),
            confidence = 0.9f,
        )
    }

    private fun parsePaymentFor(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment for (GHS\s*[0-9,.]+) to (.+?)\s*\.(?:Current Balance:)""")
            .find(body) ?: return null
        val counterpartyDetails = CounterpartyDetailsNormalizer.normalize(
            counterpartyName = match.groupValues[2],
            reference = referenceAfter(body),
        )
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = counterpartyDetails.counterpartyName,
            reference = counterpartyDetails.reference,
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            event = baseEvent(
                input,
                body,
                input.receivedAt,
                amountMinor,
                TransactionDirection.DEBIT,
                MoneyMovementChannel.MERCHANT_PAYMENT,
                counterpartyDetails.counterpartyName,
                reference = counterpartyDetails.reference,
            ),
            confidence = 0.9f,
        )
    }

    private fun parseYelloMerchantPayment(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)You have Paid (GHS\s*[0-9,.]+) to (Merchant\s+[0-9]+).*? at ([0-9]{12})\.""",
        ).find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val occurredAt = parseMtnCompactInstant(match.groupValues[3], input.receivedAt) ?: input.receivedAt
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
            balanceAfterMinor = balanceAfter(mtnBalancePattern, body),
            event = baseEvent(input, body, occurredAt, amountMinor, TransactionDirection.DEBIT, MoneyMovementChannel.MERCHANT_PAYMENT, match.groupValues[2], reference = reference),
            confidence = 0.88f,
        )
    }

    private fun parseCashIn(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Cash In received for (GHS\s*[0-9,.]+) from (.+?)(?:\.| Current Balance:)""")
            .find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val reference = referenceAfter(body) ?: "Cash In"
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = amountMinor,
            counterpartyName = match.groupValues[2],
            reference = reference,
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            event = baseEvent(input, body, input.receivedAt, amountMinor, TransactionDirection.CREDIT, MoneyMovementChannel.CASH_IN, match.groupValues[2], reference = reference),
            confidence = 0.9f,
        )
    }

    private fun parsePaymentReceived(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex("""(?i)Payment received for (GHS\s*[0-9,.]+) from (.+?)\s+Current Balance:""")
            .find(body) ?: return null
        val amountMinor = parseAmountMinor(match.groupValues[1]) ?: return null
        val reference = referenceAfter(body)
        val sourcePhone = Regex("""(?i)(233[0-9]{9}|0[235][0-9]{8})""")
            .find(reference.orEmpty())
            ?.value
            ?.let { GhanaPhoneNumberNormalizer.normalize(it)?.canonical }
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = TransactionDirection.CREDIT,
            moneyMovementType = MoneyMovementType.INCOME,
            amountMinor = amountMinor,
            counterpartyName = match.groupValues[2],
            counterpartyPhone = sourcePhone,
            reference = reference,
            balanceAfterMinor = balanceAfter(currentBalancePattern, body),
            event = baseEvent(
                input = input,
                body = body,
                occurredAt = input.receivedAt,
                amountMinor = amountMinor,
                direction = TransactionDirection.CREDIT,
                channel = if (sourcePhone != null) MoneyMovementChannel.WALLET_TO_WALLET else MoneyMovementChannel.UNKNOWN,
                counterpartyName = match.groupValues[2],
                counterpartyPhone = sourcePhone,
                reference = reference,
                sourceInstrument = sourcePhone?.let {
                    ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.TELECEL, it, it, inferred = true)
                },
                destinationInstrument = ParsedInstrument(
                    type = InstrumentType.WALLET,
                    provider = InstrumentProvider.MTN,
                    identifier = null,
                    displayIdentifier = "MTN MoMo",
                    inferred = true,
                ),
            ),
            confidence = 0.9f,
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
            feeMinor = Regex("""(?i)Fee (?:was|charged):?\s*GHS\s*([0-9,.]+)""").find(body)?.groupValues?.getOrNull(1)?.let(::parseAmountMinor),
            taxMinor = Regex("""(?i)Tax (?:was|charged):?\s*GHS?\s*([0-9,.]+|-)""").find(body)?.groupValues?.getOrNull(1)?.takeIf { it != "-" }?.let(::parseAmountMinor),
            balanceAfterMinor = balanceAfter(mtnBalancePattern, body) ?: balanceAfter(currentBalancePattern, body),
            balanceReliability = BalanceReliability.RELIABLE,
            inferredIdentifiers = listOfNotNull(counterpartyPhone),
            confidence = 0.9f,
        )

    private companion object {
        val mtnBalancePattern = Regex("""(?i)(?:Your new balance:|new balance:)\s*GHS\s*([0-9,.]+)""")
        val currentBalancePattern = Regex("""(?i)Current Balance:\s*GHS\s*([0-9,.]+)""")
    }
}
