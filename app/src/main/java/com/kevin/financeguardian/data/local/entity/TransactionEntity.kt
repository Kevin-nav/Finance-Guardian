package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sourceMessageId"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["categoryId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
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
    val includedInSpendingTotals: Boolean = moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER &&
        moneyMovementType != MoneyMovementType.SAVINGS_CONTRIBUTION &&
        direction == TransactionDirection.DEBIT,
    val includedInIncomeTotals: Boolean = moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER &&
        direction == TransactionDirection.CREDIT,
    val confidence: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
)
