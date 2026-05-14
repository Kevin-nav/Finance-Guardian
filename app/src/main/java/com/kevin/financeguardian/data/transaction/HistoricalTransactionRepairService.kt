package com.kevin.financeguardian.data.transaction

import androidx.room.withTransaction
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.CounterpartyDetailsNormalizer
import java.time.Instant
import javax.inject.Inject

class HistoricalTransactionRepairService @Inject constructor(
    private val database: FinanceGuardianDatabase,
    private val transactionDao: TransactionDao,
    private val merchantCategoryResolver: MerchantCategoryResolver,
    private val clock: AppClock,
) {
    suspend fun repair() {
        val now = clock.now()
        database.withTransaction {
            val transactions = transactionDao.getAllOnce()
            if (transactions.isEmpty()) return@withTransaction

            val receiptFalsePositiveIds = transactions
                .filter(::isLikelyGenericReceiptFalsePositive)
                .map(TransactionEntity::id)
            val repairCandidates = transactions.filterNot { it.id in receiptFalsePositiveIds }

            val repairedTransactions = repairCandidates.map { transaction ->
                repairTransaction(transaction, now)
            }
            val duplicateIds = receiptFalsePositiveIds.toMutableList()

            repairedTransactions
                .filter { it.dedupeKey.isNullOrBlank() }
                .forEach { transactionDao.update(it) }

            repairedTransactions
                .filter { !it.dedupeKey.isNullOrBlank() }
                .groupBy { requireNotNull(it.dedupeKey) }
                .values
                .forEach { group ->
                    val canonical = mergeDuplicateGroup(group, now)
                    transactionDao.update(canonical)
                    duplicateIds += group.map { it.id }.filter { it != canonical.id }
                }

            if (duplicateIds.isNotEmpty()) {
                transactionDao.deleteByIds(duplicateIds)
            }
        }
    }

    private suspend fun repairTransaction(
        transaction: TransactionEntity,
        now: Instant,
    ): TransactionEntity {
        val counterpartyDetails = CounterpartyDetailsNormalizer.normalize(
            counterpartyName = transaction.counterpartyName,
            reference = transaction.reference,
        )
        val normalizedReference = counterpartyDetails.reference
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: counterpartyDetails.counterpartyName
                ?.takeIf { transaction.provider == Provider.GCB && transaction.reference.isNullOrBlank() }
        val normalizedDirection = normalizeDirection(
            direction = transaction.direction,
            moneyMovementType = transaction.moneyMovementType,
        )
        val normalizedTransaction = transaction.copy(
            counterpartyName = counterpartyDetails.counterpartyName,
            reference = normalizedReference,
            direction = normalizedDirection,
        )
        val fingerprint = TransactionFingerprintFactory.fromEntity(
            normalizedTransaction,
        )
        val categoryId = when {
            transaction.categoryId != null && transaction.categoryId != "unknown" -> transaction.categoryId
            else -> merchantCategoryResolver.resolveForParsedTransaction(
                provider = transaction.provider,
                moneyMovementType = transaction.moneyMovementType,
                counterpartyName = counterpartyDetails.counterpartyName,
                counterpartyPhone = normalizedTransaction.counterpartyPhone,
                reference = normalizedReference,
                transactionId = transaction.id,
                now = now,
            ) ?: transaction.categoryId
        }
        return normalizedTransaction.copy(
            providerTransactionId = fingerprint.providerTransactionId,
            dedupeKey = fingerprint.dedupeKey,
            categoryId = categoryId,
            updatedAt = now,
        )
    }

    private fun mergeDuplicateGroup(
        group: List<TransactionEntity>,
        now: Instant,
    ): TransactionEntity {
        val canonical = group.maxWithOrNull(canonicalComparator) ?: error("Duplicate group was empty")
        val mergedMoneyMovementType = group
            .firstNotNullOfOrNull { transaction ->
                transaction.moneyMovementType.takeUnless { it == MoneyMovementType.UNKNOWN }
            }
            ?: canonical.moneyMovementType

        return canonical.copy(
            providerTransactionId = group.firstNotNullOfOrNull { it.providerTransactionId } ?: canonical.providerTransactionId,
            direction = normalizeDirection(
                direction = canonical.direction,
                moneyMovementType = mergedMoneyMovementType,
            ),
            moneyMovementType = mergedMoneyMovementType,
            counterpartyName = group.maxByOrNull { candidate ->
                CounterpartyDetailsNormalizer.qualityScore(candidate.counterpartyName)
            }?.counterpartyName ?: canonical.counterpartyName,
            counterpartyPhone = group.firstNotNullOfOrNull { it.counterpartyPhone } ?: canonical.counterpartyPhone,
            reference = group
                .mapNotNull { it.reference?.takeIf(String::isNotBlank) }
                .maxWithOrNull(
                    compareBy<String> { reference ->
                        if (CounterpartyDetailsNormalizer.isDetailsReference(reference)) 0 else 1
                    }.thenBy(String::length),
                ) ?: canonical.reference,
            balanceAfterMinor = group.firstNotNullOfOrNull { it.balanceAfterMinor } ?: canonical.balanceAfterMinor,
            categoryId = group.firstNotNullOfOrNull { transaction ->
                transaction.categoryId?.takeUnless { it == "unknown" }
            } ?: canonical.categoryId,
            confidence = group.maxOf { it.confidence },
            createdAt = group.minOf { it.createdAt },
            updatedAt = now,
        )
    }

    private fun normalizeDirection(
        direction: TransactionDirection,
        moneyMovementType: MoneyMovementType,
    ): TransactionDirection =
        when (moneyMovementType) {
            MoneyMovementType.INCOME -> TransactionDirection.CREDIT
            MoneyMovementType.EXPENSE,
            MoneyMovementType.SUBSCRIPTION_CANDIDATE,
            -> TransactionDirection.DEBIT
            else -> direction
        }

    private fun isLikelyGenericReceiptFalsePositive(transaction: TransactionEntity): Boolean =
        transaction.provider == Provider.UNKNOWN &&
            transaction.direction == TransactionDirection.CREDIT &&
            transaction.moneyMovementType == MoneyMovementType.UNKNOWN &&
            transaction.providerTransactionId.isNullOrBlank() &&
            transaction.counterpartyName.isNullOrBlank() &&
            transaction.counterpartyPhone.isNullOrBlank() &&
            transaction.reference.isNullOrBlank() &&
            transaction.confidence <= GENERIC_FALLBACK_CONFIDENCE

    private companion object {
        const val GENERIC_FALLBACK_CONFIDENCE = 0.45f

        val canonicalComparator =
            compareBy<TransactionEntity> { CounterpartyDetailsNormalizer.qualityScore(it.counterpartyName) }
                .thenBy { if (it.reference.isNullOrBlank()) 0 else 1 }
                .thenBy { if (it.providerTransactionId.isNullOrBlank()) 0 else 1 }
                .thenBy(TransactionEntity::confidence)
                .thenByDescending(TransactionEntity::createdAt)
    }
}
