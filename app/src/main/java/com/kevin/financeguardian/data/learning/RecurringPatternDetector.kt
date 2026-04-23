package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Duration
import javax.inject.Inject
import kotlin.math.abs

class RecurringPatternDetector @Inject constructor() {
    fun detect(transactions: List<Transaction>): List<RecurringPattern> {
        return transactions
            .groupBy(::identityKey)
            .mapNotNull { (identityKey, grouped) ->
                if (identityKey == null || grouped.size < MIN_TRANSACTIONS) return@mapNotNull null
                val ordered = grouped.sortedBy { it.occurredAt }
                val intervals = ordered.zipWithNext { first, second ->
                    Duration.between(first.occurredAt, second.occurredAt).toDays().toDouble()
                }
                if (intervals.isEmpty()) return@mapNotNull null
                val averageInterval = intervals.average()
                val maxIntervalDeviation = intervals.maxOf { abs(it - averageInterval) }
                if (maxIntervalDeviation > MAX_INTERVAL_DEVIATION_DAYS) return@mapNotNull null

                val amounts = ordered.map { it.amountMinor }
                val averageAmount = amounts.average().toLong()
                val maxAmountDeviationRatio = amounts.maxOf {
                    if (averageAmount == 0L) 0.0 else abs(it - averageAmount).toDouble() / averageAmount.toDouble()
                }
                val sample = ordered.last()
                val kind = when {
                    sample.direction == TransactionDirection.CREDIT ->
                        RecurringPattern.Kind.INCOME_SOURCE
                    maxAmountDeviationRatio <= SAME_AMOUNT_TOLERANCE_RATIO ->
                        RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE
                    else -> RecurringPattern.Kind.RECURRING_EXPENSE
                }
                RecurringPattern(
                    identityKey = identityKey,
                    kind = kind,
                    transactionCount = ordered.size,
                    averageIntervalDays = averageInterval,
                    averageAmountMinor = averageAmount,
                    suggestedMoneyMovementType = when (kind) {
                        RecurringPattern.Kind.INCOME_SOURCE -> MoneyMovementType.INCOME
                        RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE -> MoneyMovementType.SUBSCRIPTION_CANDIDATE
                        RecurringPattern.Kind.RECURRING_EXPENSE -> MoneyMovementType.EXPENSE
                    },
                )
            }
    }

    private fun identityKey(transaction: Transaction): String? {
        val merchant = MerchantNormalizer.normalizeName(transaction.counterpartyName)
        val phone = MerchantNormalizer.normalizePhone(transaction.counterpartyPhone)
        val reference = MerchantNormalizer.normalizeName(transaction.reference)
        val base = merchant ?: phone ?: reference ?: return null
        return listOf(transaction.direction.name.lowercase(), base).joinToString("|")
    }

    private companion object {
        const val MIN_TRANSACTIONS = 3
        const val MAX_INTERVAL_DEVIATION_DAYS = 3.0
        const val SAME_AMOUNT_TOLERANCE_RATIO = 0.08
    }
}
