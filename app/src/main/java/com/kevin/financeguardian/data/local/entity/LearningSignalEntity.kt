package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

@Entity(
    tableName = "learning_signals",
    indices = [
        Index(value = ["signalKey"], unique = true),
        Index(value = ["normalizedMerchantName"]),
        Index(value = ["normalizedPhone"]),
        Index(value = ["normalizedReference"]),
    ],
)
data class LearningSignalEntity(
    @PrimaryKey val id: String,
    val signalKey: String,
    val transactionId: String?,
    val provider: Provider,
    val normalizedMerchantName: String?,
    val normalizedPhone: String?,
    val normalizedReference: String?,
    val amountBucket: String?,
    val direction: TransactionDirection,
    val moneyMovementType: MoneyMovementType,
    val categoryId: String?,
    val signalType: String,
    val weight: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
)
