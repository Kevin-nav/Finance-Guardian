package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val occurredAt: Instant,
    val direction: TransactionDirection,
    val moneyMovementType: MoneyMovementType,
    val amountMinor: Long,
    val currency: String,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val reference: String?,
    val balanceAfterMinor: Long?,
    val categoryId: String?,
    val confidence: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
)
