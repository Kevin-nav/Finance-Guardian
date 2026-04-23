package com.kevin.financeguardian.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
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
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val clock: AppClock,
) : ViewModel() {
    val uiState: StateFlow<InsightsUiState> = combine(
        transactionRepository.observeTransactions(),
        categoryDao.observeAll(),
    ) { transactions, categoryEntities ->
        buildUiState(
            transactions = transactions,
            categories = categoryEntities.map { it.toDomain() },
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

        return InsightsUiState(
            hasData = transactions.isNotEmpty(),
            incomeMinor = incomeMinor,
            spendingMinor = spendingMinor,
            netCashFlowMinor = incomeMinor - spendingMinor,
            categorySpending = categorySpending,
            largeTransactions = largeTransactions,
        )
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

data class InsightsUiState(
    val hasData: Boolean = false,
    val incomeMinor: Long = 0L,
    val spendingMinor: Long = 0L,
    val netCashFlowMinor: Long = 0L,
    val categorySpending: List<CategorySpendingItem> = emptyList(),
    val largeTransactions: List<LargeTransactionItem> = emptyList(),
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
