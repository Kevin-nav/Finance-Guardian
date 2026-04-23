package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

interface SmsTransactionParser {
    fun parse(input: SmsParseInput): SmsParseResult
}

data class SmsParseInput(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)

sealed interface SmsParseResult {
    data class Parsed(
        val transaction: ParsedTransaction,
        val confidence: Float,
    ) : SmsParseResult

    data class Ignored(val reason: String) : SmsParseResult
    data class Failed(val reason: String) : SmsParseResult
}

data class ParsedTransaction(
    val provider: Provider,
    val rawSender: String,
    val providerTransactionId: String? = null,
    val occurredAt: Instant,
    val direction: TransactionDirection,
    val moneyMovementType: MoneyMovementType,
    val amountMinor: Long,
    val currency: String,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val reference: String?,
    val balanceAfterMinor: Long?,
)
