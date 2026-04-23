package com.kevin.financeguardian.data.transaction

import androidx.room.withTransaction
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant
import javax.inject.Inject

class HistoricalTransactionRepairService @Inject constructor(
    private val database: FinanceGuardianDatabase,
    private val transactionDao: TransactionDao,
    private val merchantCategoryResolver: MerchantCategoryResolver,
    private val clock: AppClock,
) {
    suspend fun repair() {
        database.withTransaction {
            val transactions = transactionDao.getAllOnce()
            if (transactions.isEmpty()) return@withTransaction

            val survivorsByKey = linkedMapOf<String, TransactionEntity>()
            val duplicateIds = mutableListOf<String>()

            for (transaction in transactions) {
                val repaired = repairTransaction(transaction, clock.now())
                val key = repaired.dedupeKey ?: continue
                val existing = survivorsByKey[key]
                if (existing == null) {
                    survivorsByKey[key] = repaired
                } else {
                    val merged = mergeTransactions(existing, repaired)
                    val winner = betterTransaction(existing, repaired)
                    val loser = if (winner.id == existing.id) repaired else existing
                    survivorsByKey[key] = if (winner.id == existing.id) merged.copy(id = existing.id) else merged.copy(id = repaired.id)
                    duplicateIds += loser.id
                }
            }

            survivorsByKey.values.forEach { transactionDao.update(it) }
            if (duplicateIds.isNotEmpty()) {
                transactionDao.deleteByIds(duplicateIds.distinct())
            }
        }
    }

    private suspend fun repairTransaction(
        transaction: TransactionEntity,
        now: Instant,
    ): TransactionEntity {
        val normalizedReference = transaction.reference
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: transaction.counterpartyName
                ?.takeIf { transaction.provider == Provider.GCB && transaction.reference.isNullOrBlank() }
        val fingerprint = TransactionFingerprintFactory.fromEntity(
            transaction.copy(reference = normalizedReference),
        )
        val categoryId = when {
            transaction.categoryId != null && transaction.categoryId != "unknown" -> transaction.categoryId
            else -> merchantCategoryResolver.resolveForParsedTransaction(
                provider = transaction.provider,
                moneyMovementType = transaction.moneyMovementType,
                counterpartyName = transaction.counterpartyName,
                counterpartyPhone = transaction.counterpartyPhone,
                reference = normalizedReference,
                transactionId = transaction.id,
                now = now,
            ) ?: transaction.categoryId
        }
        return transaction.copy(
            providerTransactionId = fingerprint.providerTransactionId,
            dedupeKey = fingerprint.dedupeKey,
            reference = normalizedReference,
            categoryId = categoryId,
            updatedAt = now,
        )
    }

    private fun mergeTransactions(
        first: TransactionEntity,
        second: TransactionEntity,
    ): TransactionEntity {
        val preferred = betterTransaction(first, second)
        val alternate = if (preferred.id == first.id) second else first
        return preferred.copy(
            sourceMessageId = preferred.sourceMessageId ?: alternate.sourceMessageId,
            providerTransactionId = preferred.providerTransactionId ?: alternate.providerTransactionId,
            reference = preferred.reference ?: alternate.reference,
            counterpartyName = betterCounterpartyName(preferred.counterpartyName, alternate.counterpartyName),
            counterpartyPhone = preferred.counterpartyPhone ?: alternate.counterpartyPhone,
            categoryId = preferred.categoryId ?: alternate.categoryId,
            dedupeKey = preferred.dedupeKey ?: alternate.dedupeKey,
            confidence = maxOf(preferred.confidence, alternate.confidence),
            createdAt = minOf(preferred.createdAt, alternate.createdAt),
            updatedAt = maxOf(preferred.updatedAt, alternate.updatedAt),
        )
    }

    private fun betterTransaction(
        first: TransactionEntity,
        second: TransactionEntity,
    ): TransactionEntity {
        val firstScore = transactionScore(first)
        val secondScore = transactionScore(second)
        return when {
            firstScore > secondScore -> first
            secondScore > firstScore -> second
            first.createdAt <= second.createdAt -> first
            else -> second
        }
    }

    private fun transactionScore(transaction: TransactionEntity): Int {
        var score = 0
        if (!transaction.providerTransactionId.isNullOrBlank()) score += 8
        if (isSpecificCounterparty(transaction.counterpartyName)) score += 4
        if (!transaction.counterpartyPhone.isNullOrBlank()) score += 2
        if (!transaction.reference.isNullOrBlank()) score += 2
        if (!transaction.categoryId.isNullOrBlank() && transaction.categoryId != "unknown") score += 2
        score += (transaction.confidence * 10).toInt()
        return score
    }

    private fun isSpecificCounterparty(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        return trimmed.isNotBlank() && !trimmed.matches(Regex("""(?i)merchant\s+\d+"""))
    }

    private fun betterCounterpartyName(
        preferred: String?,
        alternate: String?,
    ): String? {
        return when {
            isSpecificCounterparty(preferred) -> preferred
            isSpecificCounterparty(alternate) -> alternate
            !preferred.isNullOrBlank() -> preferred
            else -> alternate
        }
    }
}
