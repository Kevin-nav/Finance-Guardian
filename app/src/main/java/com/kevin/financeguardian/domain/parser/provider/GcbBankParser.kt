package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
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
            """(?i)A/C No:(\S+)\s+has been (debited|credited) (GHS[0-9,.]+)(?: Fees:\s*GHS\s*([0-9,.]+))? Desc:\s*(.+?) Date:\s*([0-9-]{10} [0-9:]{5}) Bal:\s*GHS\s*(-?[0-9,.]+)""",
        ).find(body) ?: return null
        val isCredit = match.groupValues[2].equals("credited", ignoreCase = true)
        val account = match.groupValues[1]
        val description = match.groupValues[5]
        val occurredAt = parseDateTimeInstant(match.groupValues[6]) ?: input.receivedAt
        val amountMinor = parseAmountMinor(match.groupValues[3]) ?: return null
        val balanceText = match.groupValues[7]
        val balanceAfterMinor = parseSignedAmountMinor(balanceText)
        val descriptionFacts = GcbDescriptionParser.parse(description)
        val direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            occurredAt = occurredAt,
            direction = direction,
            moneyMovementType = accountMovementType(description, isCredit),
            amountMinor = amountMinor,
            counterpartyName = description,
            counterpartyPhone = descriptionFacts.phone,
            reference = description,
            balanceAfterMinor = balanceAfterMinor,
            event = ParsedTransactionEvent(
                provider = provider,
                occurredAt = occurredAt,
                amountMinor = amountMinor,
                directionFromProviderPerspective = direction,
                channel = descriptionFacts.channel,
                sourceInstrument = descriptionFacts.sourceInstrument ?: ParsedInstrument(
                    type = InstrumentType.BANK_ACCOUNT,
                    provider = InstrumentProvider.GCB,
                    identifier = account,
                    displayIdentifier = account,
                    inferred = true,
                ),
                destinationInstrument = descriptionFacts.destinationInstrument,
                counterpartyName = description,
                counterpartyPhone = descriptionFacts.phone,
                providerTransactionId = providerTransactionIdAfter(body),
                providerReference = description,
                description = description,
                plannedUse = descriptionFacts.plannedUse,
                feeMinor = match.groupValues[4].takeIf { it.isNotBlank() }?.let(::parseAmountMinor),
                balanceAfterMinor = balanceAfterMinor,
                balanceReliability = if (balanceAfterMinor != null && balanceAfterMinor < 0) {
                    BalanceReliability.SUSPICIOUS
                } else {
                    BalanceReliability.RELIABLE
                },
                inferredIdentifiers = descriptionFacts.inferredIdentifiers,
                confidence = 0.94f,
            ),
            confidence = 0.94f,
        )
    }

    private fun parsePrepaidCardMessage(input: SmsParseInput, body: String): SmsParseResult? {
        val match = Regex(
            """(?i)Prepaid Card.*?has been (debited|credited) with\s+(?:an\s+)?amount of\s*:\s*([0-9,.]+)\s*(?:GHS|GHANA CEDIS).*?balance is\s*([0-9,.]+)\s*(?:GHS|GHANA CEDIS)""",
        ).find(body) ?: return null
        val isCredit = match.groupValues[1].equals("credited", ignoreCase = true)
        val direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT
        val amountMinor = parseAmountMinor(match.groupValues[2]) ?: return null
        return parsedResult(
            provider = provider,
            input = input,
            providerTransactionId = providerTransactionIdAfter(body),
            direction = direction,
            moneyMovementType = if (isCredit) MoneyMovementType.INCOME else MoneyMovementType.EXPENSE,
            amountMinor = amountMinor,
            counterpartyName = "Prepaid Card",
            reference = "Prepaid Card",
            balanceAfterMinor = parseAmountMinor(match.groupValues[3]),
            event = ParsedTransactionEvent(
                provider = provider,
                occurredAt = input.receivedAt,
                amountMinor = amountMinor,
                directionFromProviderPerspective = direction,
                channel = if (isCredit) MoneyMovementChannel.CARD_TOP_UP else MoneyMovementChannel.CARD_SPEND,
                sourceInstrument = if (!isCredit) ParsedInstrument(
                    type = InstrumentType.CARD,
                    provider = InstrumentProvider.GCB,
                    identifier = "GCB_PREPAID_CARD",
                    displayIdentifier = "Prepaid Card",
                    inferred = true,
                ) else null,
                destinationInstrument = if (isCredit) ParsedInstrument(
                    type = InstrumentType.CARD,
                    provider = InstrumentProvider.GCB,
                    identifier = "GCB_PREPAID_CARD",
                    displayIdentifier = "Prepaid Card",
                    inferred = true,
                ) else null,
                counterpartyName = "Prepaid Card",
                providerReference = "Prepaid Card",
                description = "Prepaid Card",
                balanceAfterMinor = parseAmountMinor(match.groupValues[3]),
                balanceReliability = BalanceReliability.RELIABLE,
                inferredIdentifiers = listOf("GCB_PREPAID_CARD"),
                confidence = 0.86f,
            ),
            confidence = 0.86f,
        )
    }

    private fun accountMovementType(description: String, isCredit: Boolean): MoneyMovementType {
        if (isCredit) return MoneyMovementType.INCOME
        return when {
            GcbDescriptionParser.parse(description).channel == MoneyMovementChannel.CARD_TOP_UP -> MoneyMovementType.EXPENSE
            else -> knownSubscriptionMovement(description)
        }
    }

    private fun parseSignedAmountMinor(raw: String): Long? {
        val negative = raw.trim().startsWith("-")
        val parsed = parseAmountMinor(raw.removePrefix("-")) ?: return null
        return if (negative) -parsed else parsed
    }
}

private data class GcbDescriptionFacts(
    val channel: MoneyMovementChannel,
    val phone: String? = null,
    val sourceInstrument: ParsedInstrument? = null,
    val destinationInstrument: ParsedInstrument? = null,
    val plannedUse: String? = null,
    val inferredIdentifiers: List<String> = emptyList(),
)

private object GcbDescriptionParser {
    fun parse(description: String): GcbDescriptionFacts {
        parseBankToWallet(description)?.let { return it }
        parseB2w(description)?.let { return it }
        parseWalletToBank(description)?.let { return it }
        parseCardTopUp(description)?.let { return it }
        if (description.contains("CASH DEPOSIT BY", ignoreCase = true)) {
            return GcbDescriptionFacts(channel = MoneyMovementChannel.CASH_DEPOSIT)
        }
        return GcbDescriptionFacts(channel = MoneyMovementChannel.UNKNOWN)
    }

    private fun parseBankToWallet(description: String): GcbDescriptionFacts? {
        val match = Regex("""(?i)^Bank to Wallet\s+(\+?[0-9 ]{9,16})\s*(.*)$""").find(description) ?: return null
        val phone = GhanaPhoneNumberNormalizer.normalize(match.groupValues[1])?.canonical
        val tail = match.groupValues[2].trim()
        val tokens = tail.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val internalId = tokens.lastOrNull()?.takeIf { it.matches(Regex("""(?i)T[0-9]*|[0-9A-Z]{6,}""")) }
        val plannedUse = tokens.dropLast(if (internalId != null) 1 else 0).joinToString(" ").ifBlank { null }
        val destination = ParsedInstrument(
            type = InstrumentType.WALLET,
            provider = InstrumentProvider.UNKNOWN,
            identifier = phone,
            displayIdentifier = match.groupValues[1].trim(),
            inferred = true,
        )
        return GcbDescriptionFacts(
            channel = MoneyMovementChannel.BANK_TO_WALLET,
            phone = phone,
            destinationInstrument = destination,
            plannedUse = plannedUse,
            inferredIdentifiers = listOfNotNull(phone, internalId),
        )
    }

    private fun parseB2w(description: String): GcbDescriptionFacts? {
        val match = Regex("""(?i)^B2W\s+(?:MTN|TELECEL)?\s*(\+?[0-9 ]{9,16})\s*(.*)$""").find(description) ?: return null
        val phone = GhanaPhoneNumberNormalizer.normalize(match.groupValues[1])?.canonical
        val tokens = match.groupValues[2].trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        val internalId = tokens.lastOrNull()?.takeIf { it.matches(Regex("""[0-9A-Z]{6,}""")) }
        val plannedUse = tokens.dropLast(if (internalId != null) 1 else 0).joinToString(" ").ifBlank { null }
        return GcbDescriptionFacts(
            channel = MoneyMovementChannel.BANK_TO_WALLET,
            phone = phone,
            destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, phone, match.groupValues[1], true),
            plannedUse = plannedUse,
            inferredIdentifiers = listOfNotNull(phone, internalId),
        )
    }

    private fun parseWalletToBank(description: String): GcbDescriptionFacts? {
        val match = Regex("""(?i)^Wallet to Bank\s+(\+?[0-9 ]{9,16})\s*(.*)$""").find(description) ?: return null
        val phone = GhanaPhoneNumberNormalizer.normalize(match.groupValues[1])?.canonical
        val internalId = match.groupValues[2].trim().takeIf { it.isNotBlank() }
        return GcbDescriptionFacts(
            channel = MoneyMovementChannel.WALLET_TO_BANK,
            phone = phone,
            sourceInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.UNKNOWN, phone, match.groupValues[1], true),
            inferredIdentifiers = listOfNotNull(phone, internalId),
        )
    }

    private fun parseCardTopUp(description: String): GcbDescriptionFacts? {
        val match = Regex("""(?i)^VISA Card Top Up\s+(.+)$""").find(description) ?: return null
        val token = match.groupValues[1].trim()
        return GcbDescriptionFacts(
            channel = MoneyMovementChannel.CARD_TOP_UP,
            destinationInstrument = ParsedInstrument(InstrumentType.CARD, InstrumentProvider.GCB, token, token, true),
            plannedUse = null,
            inferredIdentifiers = listOf(token),
        )
    }
}
