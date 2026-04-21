package com.kevin.financeguardian.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant

@Entity(
    tableName = "parser_rules",
    indices = [
        Index(value = ["provider"]),
        Index(value = ["enabled"]),
    ],
)
data class ParserRuleEntity(
    @PrimaryKey val id: String,
    val provider: Provider,
    val name: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
