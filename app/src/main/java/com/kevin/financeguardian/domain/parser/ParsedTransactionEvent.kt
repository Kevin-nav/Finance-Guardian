package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

enum class MoneyMovementChannel {
    MERCHANT_PAYMENT,
    WALLET_TO_WALLET,
    WALLET_TO_BANK,
    BANK_TO_WALLET,
    CARD_TOP_UP,
    CARD_SPEND,
    CASH_IN,
    CASH_DEPOSIT,
    AIRTIME_DATA,
    UNKNOWN,
}

enum class OwnershipHint {
    OWN_TO_OWN,
    OWN_TO_EXTERNAL,
    EXTERNAL_TO_OWN,
    UNKNOWN,
}

enum class BalanceReliability {
    RELIABLE,
    SUSPICIOUS,
    UNKNOWN,
}

data class ParsedInstrument(
    val type: InstrumentType,
    val provider: InstrumentProvider,
    val identifier: String?,
    val displayIdentifier: String? = identifier,
    val inferred: Boolean = false,
)

data class ParsedTransactionEvent(
    val provider: Provider,
    val sourceMessageId: String? = null,
    val occurredAt: Instant,
    val amountMinor: Long,
    val currency: String = "GHS",
    val directionFromProviderPerspective: TransactionDirection,
    val channel: MoneyMovementChannel = MoneyMovementChannel.UNKNOWN,
    val sourceInstrument: ParsedInstrument? = null,
    val destinationInstrument: ParsedInstrument? = null,
    val counterpartyName: String? = null,
    val counterpartyPhone: String? = null,
    val providerTransactionId: String? = null,
    val providerReference: String? = null,
    val description: String? = null,
    val plannedUse: String? = null,
    val feeMinor: Long? = null,
    val taxMinor: Long? = null,
    val balanceAfterMinor: Long? = null,
    val balanceReliability: BalanceReliability = BalanceReliability.UNKNOWN,
    val inferredIdentifiers: List<String> = emptyList(),
    val ownershipHint: OwnershipHint = OwnershipHint.UNKNOWN,
    val confidence: Float,
)
