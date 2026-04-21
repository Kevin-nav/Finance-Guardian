package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.financeguardian.domain.model.ParseStatus
import java.time.Instant

@Entity(
    tableName = "sms_message_records",
    indices = [
        Index(value = ["sender", "bodyHash", "receivedAt"], unique = true),
        Index(value = ["parseStatus"]),
    ],
)
data class SmsMessageRecordEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val bodyHash: String,
    val receivedAt: Instant,
    val processedAt: Instant?,
    val parseStatus: ParseStatus,
    val parseReason: String?,
)
