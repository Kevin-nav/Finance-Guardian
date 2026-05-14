package com.kevin.financeguardian.data.local.mapper

import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.ParserRuleEntity
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.Category
import com.kevin.financeguardian.domain.model.Merchant
import com.kevin.financeguardian.domain.model.ParserRule
import com.kevin.financeguardian.domain.model.SmsMessageRecord
import com.kevin.financeguardian.domain.model.Transaction
import java.time.Instant

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        sourceMessageId = sourceMessageId,
        provider = provider,
        rawSender = rawSender,
        rawBodyHash = rawBodyHash,
        providerTransactionId = providerTransactionId,
        dedupeKey = dedupeKey,
        occurredAt = occurredAt,
        direction = direction,
        moneyMovementType = moneyMovementType,
        amountMinor = amountMinor,
        currency = currency,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        reference = reference,
        balanceAfterMinor = balanceAfterMinor,
        balanceReliability = balanceReliability,
        categoryId = categoryId,
        flowId = flowId,
        flowType = flowType,
        flowStatus = flowStatus,
        plannedUse = plannedUse,
        includedInSpendingTotals = includedInSpendingTotals,
        includedInIncomeTotals = includedInIncomeTotals,
        confidence = confidence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        sourceMessageId = sourceMessageId,
        provider = provider,
        rawSender = rawSender,
        rawBodyHash = rawBodyHash,
        providerTransactionId = providerTransactionId,
        dedupeKey = dedupeKey,
        occurredAt = occurredAt,
        direction = direction,
        moneyMovementType = moneyMovementType,
        amountMinor = amountMinor,
        currency = currency,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        reference = reference,
        balanceAfterMinor = balanceAfterMinor,
        balanceReliability = balanceReliability,
        categoryId = categoryId,
        flowId = flowId,
        flowType = flowType,
        flowStatus = flowStatus,
        plannedUse = plannedUse,
        includedInSpendingTotals = includedInSpendingTotals,
        includedInIncomeTotals = includedInIncomeTotals,
        confidence = confidence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        type = type,
    )

fun Category.toEntity(now: Instant = Instant.now()): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        type = type,
        isArchived = false,
        createdAt = now,
        updatedAt = now,
    )

fun MerchantEntity.toDomain(): Merchant =
    Merchant(
        id = id,
        displayName = displayName,
        normalizedName = normalizedName,
        phone = phone,
        defaultCategoryId = defaultCategoryId,
        createdFromTransactionId = createdFromTransactionId,
    )

fun Merchant.toEntity(now: Instant = Instant.now()): MerchantEntity =
    MerchantEntity(
        id = id,
        displayName = displayName,
        normalizedName = normalizedName,
        phone = phone,
        defaultCategoryId = defaultCategoryId,
        createdFromTransactionId = createdFromTransactionId,
        createdAt = now,
        updatedAt = now,
    )

fun SmsMessageRecordEntity.toDomain(): SmsMessageRecord =
    SmsMessageRecord(
        id = id,
        sender = sender,
        bodyHash = bodyHash,
        receivedAt = receivedAt,
        processedAt = processedAt,
        parseStatus = parseStatus,
    )

fun SmsMessageRecord.toEntity(parseReason: String? = null): SmsMessageRecordEntity =
    SmsMessageRecordEntity(
        id = id,
        sender = sender,
        bodyHash = bodyHash,
        receivedAt = receivedAt,
        processedAt = processedAt,
        parseStatus = parseStatus,
        parseReason = parseReason,
    )

fun ParserRuleEntity.toDomain(): ParserRule =
    ParserRule(
        id = id,
        provider = provider,
        name = name,
        enabled = enabled,
    )

fun ParserRule.toEntity(now: Instant = Instant.now()): ParserRuleEntity =
    ParserRuleEntity(
        id = id,
        provider = provider,
        name = name,
        enabled = enabled,
        createdAt = now,
        updatedAt = now,
    )
