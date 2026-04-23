package com.kevin.financeguardian.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.notifications.InsightEvaluator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.learning.LearningBackfillService
import com.kevin.financeguardian.data.learning.LearningSignalRecorder
import com.kevin.financeguardian.data.learning.RecurringPattern
import com.kevin.financeguardian.data.learning.RecurringPatternDetector
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.domain.model.Category
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val learningSignalDao: LearningSignalDao,
    private val recurringPatternDetector: RecurringPatternDetector,
    private val insightEvaluator: InsightEvaluator,
    private val clock: AppClock,
) : ViewModel() {
    val uiState: StateFlow<InsightsUiState> = combine(
        transactionRepository.observeTransactions(),
        categoryDao.observeAll(),
        learningSignalDao.observeAll(),
    ) { transactions, categoryEntities, learningSignals ->
        buildUiState(
            transactions = transactions,
            categories = categoryEntities.map { it.toDomain() },
            learningSignals = learningSignals,
            now = clock.now(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InsightsUiState(),
    )

    private fun buildUiState(
        transactions: List<Transaction>,
        categories: List<Category>,
        learningSignals: List<LearningSignalEntity>,
        now: Instant,
    ): InsightsUiState {
        val categoryById = categories.associateBy { it.id }
        val incomeMinor = transactions
            .filter { it.isIncome() }
            .sumOf { it.amountMinor }
        val spendingTransactions = transactions.filter { it.isCashOutflowForInsights() }
        val spendingMinor = spendingTransactions.sumOf { it.amountMinor }
        val categorySpending = spendingTransactions
            .groupBy { transaction ->
                transaction.categoryId
                    ?.let { categoryById[it]?.name }
                    ?: "Unknown"
            }
            .map { (name, groupedTransactions) ->
                CategorySpendingItem(
                    name = name,
                    amountMinor = groupedTransactions.sumOf { it.amountMinor },
                )
            }
            .sortedWith(
                compareByDescending<CategorySpendingItem> { it.amountMinor }
                    .thenBy { it.name },
            )

        val largeTransactions = transactions
            .sortedWith(
                compareByDescending<Transaction> { it.amountMinor }
                    .thenByDescending { it.occurredAt },
            )
            .take(5)
            .map { transaction ->
                LargeTransactionItem(
                    merchantName = transaction.displayName(),
                    amountMinor = transaction.amountMinor,
                    isCredit = transaction.direction == TransactionDirection.CREDIT,
                    date = transaction.occurredAt.formatDate(now),
                )
            }
        val highlightInsight = insightEvaluator.evaluate(transactions, now)?.let { insight ->
            InsightHighlightItem(
                kind = insight.kind,
                title = insight.title,
                summary = insight.summary,
            )
        }

        // ── Learning Engine Data ────────────────────────────────────────────
        val recurringPatterns = buildRecurringPatterns(transactions)
        val recurringSummary = buildRecurringSummary(recurringPatterns)
        val learningStats = buildLearningStats(learningSignals)
        val topMerchantRules = buildTopMerchantRules(learningSignals, categoryById)

        // ── Month label ─────────────────────────────────────────────────────
        val zone = ZoneId.systemDefault()
        val currentMonth = now.atZone(zone).toLocalDate()
        val monthLabel = currentMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()),
        )

        return InsightsUiState(
            hasData = transactions.isNotEmpty(),
            currentMonthLabel = monthLabel,
            incomeMinor = incomeMinor,
            spendingMinor = spendingMinor,
            netCashFlowMinor = incomeMinor - spendingMinor,
            categorySpending = categorySpending,
            largeTransactions = largeTransactions,
            highlightInsight = highlightInsight,
            recurringPatterns = recurringPatterns,
            recurringSummary = recurringSummary,
            learningStats = learningStats,
            topMerchantRules = topMerchantRules,
        )
    }

    private fun buildRecurringPatterns(transactions: List<Transaction>): List<RecurringPatternItem> {
        return recurringPatternDetector.detect(transactions).map { pattern ->
            val displayName = pattern.identityKey
                .substringAfter("|")
                .replaceFirstChar { it.uppercase() }
            val intervalDays = pattern.averageIntervalDays.toLong()
            val frequency = when {
                intervalDays in 27..33 -> "Every ~month"
                intervalDays in 6..8 -> "Every ~week"
                intervalDays in 13..16 -> "Every ~2 weeks"
                else -> "Every ~$intervalDays days"
            }
            RecurringPatternItem(
                displayName = displayName,
                kind = pattern.kind,
                kindLabel = when (pattern.kind) {
                    RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE -> "Subscription"
                    RecurringPattern.Kind.RECURRING_EXPENSE -> "Recurring"
                    RecurringPattern.Kind.INCOME_SOURCE -> "Income Source"
                },
                frequency = frequency,
                averageAmountMinor = pattern.averageAmountMinor,
                isCredit = pattern.kind == RecurringPattern.Kind.INCOME_SOURCE,
                transactionCount = pattern.transactionCount,
            )
        }
    }

    private fun buildRecurringSummary(patterns: List<RecurringPatternItem>): RecurringSummary? {
        if (patterns.isEmpty()) return null
        return RecurringSummary(
            totalCount = patterns.size,
            subscriptionCount = patterns.count { it.kind == RecurringPattern.Kind.SUBSCRIPTION_CANDIDATE },
            recurringExpenseCount = patterns.count { it.kind == RecurringPattern.Kind.RECURRING_EXPENSE },
            incomeSourceCount = patterns.count { it.kind == RecurringPattern.Kind.INCOME_SOURCE },
        )
    }

    private fun buildLearningStats(signals: List<LearningSignalEntity>): LearningStats {
        val total = signals.size
        val corrections = signals.count {
            it.signalType == LearningSignalRecorder.SIGNAL_TYPE_USER_CORRECTION
        }
        val backfilled = signals.count {
            it.signalType == LearningBackfillService.SIGNAL_TYPE_BACKFILLED_PATTERN
        }
        val autoApplyRate = if (total > 0) {
            ((total - corrections).toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
        return LearningStats(
            totalSignals = total,
            correctionCount = corrections,
            autoApplyRate = autoApplyRate,
        )
    }

    private fun buildTopMerchantRules(
        signals: List<LearningSignalEntity>,
        categoryById: Map<String, Category>,
    ): List<MerchantRuleItem> {
        return signals
            .filter { it.normalizedMerchantName != null && it.categoryId != null }
            .groupBy { it.normalizedMerchantName!! }
            .map { (merchantName, merchantSignals) ->
                val best = merchantSignals.maxByOrNull { it.weight } ?: return@map null
                val categoryName = best.categoryId?.let { categoryById[it]?.name } ?: "Unknown"
                val confidence = (best.weight / (best.weight + 0.5f)).coerceIn(0f, 1f)
                MerchantRuleItem(
                    merchantName = merchantName.replaceFirstChar { it.uppercase() },
                    categoryName = categoryName,
                    categoryId = best.categoryId,
                    confidence = confidence,
                    signalCount = merchantSignals.size,
                )
            }
            .filterNotNull()
            .sortedByDescending { it.confidence }
            .take(5)
    }

    private fun Transaction.isIncome(): Boolean =
        direction == TransactionDirection.CREDIT || moneyMovementType == MoneyMovementType.INCOME

    private fun Transaction.isCashOutflowForInsights(): Boolean =
        direction == TransactionDirection.DEBIT &&
            moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER

    private fun Transaction.displayName(): String =
        counterpartyName?.takeIf { it.isNotBlank() }
            ?: counterpartyPhone?.takeIf { it.isNotBlank() }
            ?: provider.toDisplayName()

    private fun Provider.toDisplayName(): String =
        when (this) {
            Provider.MTN_MOMO -> "MTN MoMo"
            Provider.TELECEL_CASH -> "Telecel Cash"
            Provider.GCB -> "GCB Bank"
            Provider.UNKNOWN_BANK -> "Unknown Bank"
            Provider.UNKNOWN -> "Unknown"
        }

    private fun Instant.formatDate(now: Instant): String {
        val zone = ZoneId.systemDefault()
        val date = atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
        }
    }
}

// ── UI State ────────────────────────────────────────────────────────────────

data class InsightsUiState(
    val hasData: Boolean = false,
    val currentMonthLabel: String = "",
    val incomeMinor: Long = 0L,
    val spendingMinor: Long = 0L,
    val netCashFlowMinor: Long = 0L,
    val categorySpending: List<CategorySpendingItem> = emptyList(),
    val largeTransactions: List<LargeTransactionItem> = emptyList(),
    val highlightInsight: InsightHighlightItem? = null,
    // Learning Engine
    val recurringPatterns: List<RecurringPatternItem> = emptyList(),
    val recurringSummary: RecurringSummary? = null,
    val learningStats: LearningStats = LearningStats(),
    val topMerchantRules: List<MerchantRuleItem> = emptyList(),
)

data class CategorySpendingItem(
    val name: String,
    val amountMinor: Long,
)

data class LargeTransactionItem(
    val merchantName: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val date: String,
)

data class InsightHighlightItem(
    val kind: com.kevin.financeguardian.core.notifications.NotificationEvent.Insight,
    val title: String,
    val summary: String,
)

// ── Learning Engine Models ──────────────────────────────────────────────────

data class RecurringPatternItem(
    val displayName: String,
    val kind: com.kevin.financeguardian.data.learning.RecurringPattern.Kind,
    val kindLabel: String,
    val frequency: String,
    val averageAmountMinor: Long,
    val isCredit: Boolean,
    val transactionCount: Int,
)

data class RecurringSummary(
    val totalCount: Int,
    val subscriptionCount: Int,
    val recurringExpenseCount: Int,
    val incomeSourceCount: Int,
) {
    fun toDisplayText(): String {
        val parts = buildList {
            if (subscriptionCount > 0) add("$subscriptionCount subscription${if (subscriptionCount > 1) "s" else ""}")
            if (recurringExpenseCount > 0) add("$recurringExpenseCount recurring")
            if (incomeSourceCount > 0) add("$incomeSourceCount income")
        }
        return parts.joinToString(" · ")
    }
}

data class LearningStats(
    val totalSignals: Int = 0,
    val correctionCount: Int = 0,
    val autoApplyRate: Float = 0f,
) {
    val hasData: Boolean get() = totalSignals > 0
}

data class MerchantRuleItem(
    val merchantName: String,
    val categoryName: String,
    val categoryId: String?,
    val confidence: Float,
    val signalCount: Int,
)
