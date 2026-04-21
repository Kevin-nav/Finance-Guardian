package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

internal fun parsedResult(
    provider: Provider,
    input: SmsParseInput,
    occurredAt: Instant = input.receivedAt,
    direction: TransactionDirection,
    moneyMovementType: MoneyMovementType,
    amountMinor: Long,
    counterpartyName: String?,
    counterpartyPhone: String? = null,
    reference: String? = null,
    balanceAfterMinor: Long? = null,
    confidence: Float,
): SmsParseResult.Parsed =
    SmsParseResult.Parsed(
        transaction = ParsedTransaction(
            provider = provider,
            rawSender = input.sender,
            occurredAt = occurredAt,
            direction = direction,
            moneyMovementType = moneyMovementType,
            amountMinor = amountMinor,
            currency = "GHS",
            counterpartyName = counterpartyName?.cleanParsedText(),
            counterpartyPhone = counterpartyPhone?.cleanParsedText(),
            reference = reference?.cleanReference(),
            balanceAfterMinor = balanceAfterMinor,
        ),
        confidence = confidence,
    )

internal fun String.cleanParsedText(): String =
    normalizeWhitespace(this)
        .trim(' ', '.', ',')
        .ifBlank { "" }

internal fun String.cleanReference(): String? {
    val cleaned = cleanParsedText()
    return cleaned.takeUnless { it == "-" || it.equals("null", ignoreCase = true) }
}

internal fun balanceAfter(pattern: Regex, body: String): Long? =
    pattern.find(body)?.groupValues?.getOrNull(1)?.let(::parseAmountMinor)

internal fun referenceAfter(body: String): String? =
    Regex(
        """(?i)(?:Reference|Ref|Message from sender):\s*(.+?)(?:\.\s*(?:Transaction|Financial|Your|Sendi|Download)|$)""",
    )
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanReference()

internal fun knownSubscriptionMovement(description: String): MoneyMovementType {
    val lower = description.lowercase()
    return when {
        lower.contains("openai") ||
            lower.contains("chatgpt") ||
            lower.contains("spotify") ||
            lower.contains("t3 chat") -> MoneyMovementType.SUBSCRIPTION_CANDIDATE
        else -> MoneyMovementType.EXPENSE
    }
}
