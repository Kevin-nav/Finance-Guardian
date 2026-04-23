package com.kevin.financeguardian.core.notifications

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.ceil

class InsightEvaluator @Inject constructor() {
    fun evaluate(
        transactions: List<Transaction>,
        now: Instant,
    ): InsightResult? {
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val outgoingByDay = transactions
            .filter { it.isOutgoing() }
            .groupBy { it.occurredAt.atZone(zone).toLocalDate() }

        val outgoingToday = outgoingByDay[today].orEmpty()
        if (outgoingToday.size < MIN_OUTGOING_BURST_COUNT) return null

        val recentBaseline = outgoingByDay
            .filterKeys { day -> day.isBefore(today) && !day.isBefore(today.minusDays(RECENT_BASELINE_DAYS)) }
            .values
            .map { it.size }
        val requiredCount = if (recentBaseline.isEmpty()) {
            MIN_OUTGOING_BURST_COUNT
        } else {
            maxOf(MIN_OUTGOING_BURST_COUNT, ceil(recentBaseline.average()).toInt() + BASELINE_BUFFER)
        }
        if (outgoingToday.size < requiredCount) return null

        return InsightResult(
            kind = NotificationEvent.Insight.OutgoingBurstToday,
            title = "Spending is higher than usual today",
            summary = "You have ${outgoingToday.size} outgoing transactions today, higher than your recent daily pattern.",
        )
    }

    private fun Transaction.isOutgoing(): Boolean =
        direction == TransactionDirection.DEBIT &&
            moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER

    data class InsightResult(
        val kind: NotificationEvent.Insight,
        val title: String,
        val summary: String,
    )

    private companion object {
        const val MIN_OUTGOING_BURST_COUNT = 4
        const val BASELINE_BUFFER = 2
        const val RECENT_BASELINE_DAYS = 7L
    }
}
