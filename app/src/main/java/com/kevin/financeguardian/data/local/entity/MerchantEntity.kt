package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "merchants",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["phone"]),
    ],
)
data class MerchantEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val normalizedName: String,
    val phone: String?,
    val defaultCategoryId: String?,
    val createdFromTransactionId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
