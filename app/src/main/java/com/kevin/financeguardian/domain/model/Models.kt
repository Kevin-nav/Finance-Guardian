package com.kevin.financeguardian.domain.model

import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
import java.time.Instant

data class Transaction(
    val id: String,
    val sourceMessageId: String?,
    val provider: Provider,
    val rawSender: String,
    val rawBodyHash: String,
    val providerTransactionId: String? = null,
    val dedupeKey: String? = null,
    val occurredAt: Instant,
    val direction: TransactionDirection,
    val moneyMovementType: MoneyMovementType,
    val amountMinor: Long,
    val currency: String,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val reference: String?,
    val balanceAfterMinor: Long?,
    val balanceReliability: BalanceReliability = BalanceReliability.UNKNOWN,
    val categoryId: String?,
    val flowId: String? = null,
    val flowType: TransactionFlowType? = null,
    val flowStatus: TransactionFlowStatus? = null,
    val plannedUse: String? = null,
    val eventChannel: MoneyMovementChannel? = null,
    val eventSourceInstrumentType: InstrumentType? = null,
    val eventSourceInstrumentProvider: InstrumentProvider? = null,
    val eventSourceInstrumentIdentifier: String? = null,
    val eventDestinationInstrumentType: InstrumentType? = null,
    val eventDestinationInstrumentProvider: InstrumentProvider? = null,
    val eventDestinationInstrumentIdentifier: String? = null,
    val eventProviderReference: String? = null,
    val eventInferredIdentifiers: String? = null,
    val includedInSpendingTotals: Boolean = moneyMovementType == MoneyMovementType.EXPENSE ||
        moneyMovementType == MoneyMovementType.SUBSCRIPTION_CANDIDATE ||
        (moneyMovementType == MoneyMovementType.UNKNOWN && direction == TransactionDirection.DEBIT),
    val includedInIncomeTotals: Boolean = moneyMovementType == MoneyMovementType.INCOME ||
        (moneyMovementType == MoneyMovementType.UNKNOWN && direction == TransactionDirection.CREDIT),
    val confidence: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Merchant(
    val id: String,
    val displayName: String,
    val normalizedName: String,
    val phone: String?,
    val defaultCategoryId: String?,
    val createdFromTransactionId: String?,
)

data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
)

data class SmsMessageRecord(
    val id: String,
    val sender: String,
    val bodyHash: String,
    val receivedAt: Instant,
    val processedAt: Instant?,
    val parseStatus: ParseStatus,
)

data class ParserRule(
    val id: String,
    val provider: Provider,
    val name: String,
    val enabled: Boolean,
)

enum class Provider {
    MTN_MOMO,
    TELECEL_CASH,
    GCB,
    UNKNOWN_BANK,
    UNKNOWN,
}

enum class TransactionDirection {
    DEBIT,
    CREDIT,
}

enum class MoneyMovementType {
    EXPENSE,
    INCOME,
    INTERNAL_TRANSFER,
    SAVINGS_CONTRIBUTION,
    SUBSCRIPTION_CANDIDATE,
    UNKNOWN,
}

enum class CategoryType {
    EXPENSE,
    INCOME,
    TRANSFER,
    SAVINGS,
}

enum class ParseStatus {
    PARSED,
    IGNORED,
    FAILED,
    DUPLICATE,
}
