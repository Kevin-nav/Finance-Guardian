package com.kevin.financeguardian.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.domain.model.Category
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.effectiveIsCredit
import com.kevin.financeguardian.ui.components.TransactionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val transactionCorrectionApplier: TransactionCorrectionApplier,
    private val notificationDispatcher: NotificationDispatcher,
    private val permissionStatusChecker: PermissionStatusChecker,
    private val clock: AppClock,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(TransactionFilter.All)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val permissionRefreshes = MutableStateFlow(0)

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(),
        categoryDao.observeAll(),
        selectedFilter,
        selectedTransactionId,
        permissionRefreshes,
    ) { transactions, categoryEntities, filter, selectedId, _ ->
        val categories = categoryEntities.map { it.toDomain() }
        buildUiState(
            transactions = transactions,
            categories = categories,
            selectedFilter = filter,
            selectedTransactionId = selectedId,
            receiveSmsGranted = permissionStatusChecker.currentStatuses().receiveSmsGranted,
            now = clock.now(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TransactionsUiState(),
    )

    fun selectFilter(filter: TransactionFilter) {
        selectedFilter.value = filter
    }

    fun selectTransaction(transactionId: String) {
        selectedTransactionId.value = transactionId
    }

    fun dismissTransaction() {
        selectedTransactionId.value = null
    }

    fun refreshPermissions() {
        permissionRefreshes.value += 1
    }

    fun saveCorrection(
        selectedCategory: String,
        selectedType: String,
    ) {
        val transactionId = selectedTransactionId.value ?: return
        val categoryId = uiState.value.categoryOptions
            .firstOrNull { it.name.equals(selectedCategory, ignoreCase = true) }
            ?.id
        val movementType = selectedType.toMoneyMovementType()
        viewModelScope.launch {
            transactionCorrectionApplier.applyCorrection(
                transactionId = transactionId,
                categoryId = categoryId,
                moneyMovementType = movementType,
                saveMerchantDefault = true,
            )
            notificationDispatcher.dispatch(
                NotificationEvent.CorrectionSaved(
                    transactionId = transactionId,
                    occurredAt = clock.now(),
                ),
            )
            selectedTransactionId.value = null
        }
    }

    private fun buildUiState(
        transactions: List<Transaction>,
        categories: List<Category>,
        selectedFilter: TransactionFilter,
        selectedTransactionId: String?,
        receiveSmsGranted: Boolean,
        now: Instant,
    ): TransactionsUiState {
        val categoryById = categories.associateBy { it.id }
        val categoryOptions = categories.map { TransactionCategoryOption(it.id, it.name) }
        val items = transactions.map { transaction ->
            transaction.toListItem(categoryById, now)
        }
        val filteredItems = items.filter { it.matches(selectedFilter) }
        val selectedItem = items.firstOrNull { it.id == selectedTransactionId }

        return TransactionsUiState(
            filters = TransactionFilter.entries.toList(),
            selectedFilter = selectedFilter,
            groups = filteredItems.toGroups(),
            categoryOptions = categoryOptions,
            selectedTransaction = selectedItem?.toDetail(),
            totalBalanceMinor = transactions
                .sortedByDescending { it.occurredAt }
                .firstNotNullOfOrNull { it.balanceAfterMinor }
                ?: 0L,
            incomeMinor = transactions
                .filter { it.isIncome() }
                .sumOf { it.amountMinor },
            expensesMinor = transactions
                .filter { it.isSpending() }
                .sumOf { it.amountMinor },
            savingsMinor = transactions
                .filter { it.moneyMovementType == MoneyMovementType.SAVINGS_CONTRIBUTION }
                .sumOf { it.amountMinor },
            receiveSmsGranted = receiveSmsGranted,
            isEmpty = transactions.isEmpty(),
        )
    }

    private fun Transaction.toListItem(
        categoryById: Map<String, Category>,
        now: Instant,
    ): TransactionListItem {
        val categoryName = categoryId
            ?.let { categoryById[it]?.name }
            ?: "Unknown"
        return TransactionListItem(
            id = id,
            merchantName = counterpartyName?.takeIf { it.isNotBlank() }
                ?: counterpartyPhone?.takeIf { it.isNotBlank() }
                ?: provider.toDisplayName(),
            categoryName = categoryName,
            categoryId = categoryId,
            amountMinor = amountMinor,
            isCredit = effectiveIsCredit(),
            timestamp = occurredAt.formatTime(),
            dateGroup = occurredAt.formatDateGroup(now),
            provider = provider.toDisplayName(),
            reference = reference,
            balanceAfterMinor = balanceAfterMinor,
            currency = currency,
            movementType = moneyMovementType,
        )
    }

    private fun List<TransactionListItem>.toGroups(): List<TransactionGroup> {
        val groups = linkedMapOf<String, MutableList<TransactionListItem>>()
        forEach { item ->
            groups.getOrPut(item.dateGroup) { mutableListOf() }.add(item)
        }
        return groups.map { (dateGroup, transactions) ->
            TransactionGroup(dateGroup, transactions)
        }
    }

    private fun TransactionListItem.matches(filter: TransactionFilter): Boolean =
        when (filter) {
            TransactionFilter.All -> true
            TransactionFilter.Income -> isCredit
            TransactionFilter.Expenses -> !isCredit &&
                movementType != MoneyMovementType.INTERNAL_TRANSFER &&
                movementType != MoneyMovementType.SAVINGS_CONTRIBUTION &&
                !isUnknownCategory
            TransactionFilter.Transfers -> movementType == MoneyMovementType.INTERNAL_TRANSFER
            TransactionFilter.Unknown -> isUnknownCategory || movementType == MoneyMovementType.UNKNOWN
        }

    private fun TransactionListItem.toDetail(): TransactionDetail =
        TransactionDetail(
            id = id,
            merchantName = merchantName,
            categoryName = categoryName,
            amountMinor = amountMinor,
            isCredit = isCredit,
            timestamp = timestamp,
            dateGroup = dateGroup,
            provider = provider,
            reference = reference,
            balanceAfterMinor = balanceAfterMinor,
            currency = currency,
        )

    private fun Instant.formatTime(): String =
        DateTimeFormatter.ofPattern("HH:mm").format(atZone(ZoneId.systemDefault()))

    private fun Instant.formatDateGroup(now: Instant): String {
        val zone = ZoneId.systemDefault()
        val date = atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
    }

    private fun Transaction.isIncome(): Boolean =
        effectiveIsCredit()

    private fun Transaction.isSpending(): Boolean =
        !effectiveIsCredit() &&
            moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER &&
            moneyMovementType != MoneyMovementType.SAVINGS_CONTRIBUTION

    private fun Provider.toDisplayName(): String =
        when (this) {
            Provider.MTN_MOMO -> "MTN MoMo"
            Provider.TELECEL_CASH -> "Telecel Cash"
            Provider.GCB -> "GCB Bank"
            Provider.UNKNOWN_BANK -> "Unknown Bank"
            Provider.UNKNOWN -> "Unknown"
        }

    private fun String.toMoneyMovementType(): MoneyMovementType? =
        when (this) {
            "Expense" -> MoneyMovementType.EXPENSE
            "Income" -> MoneyMovementType.INCOME
            "Internal Transfer" -> MoneyMovementType.INTERNAL_TRANSFER
            "Savings" -> MoneyMovementType.SAVINGS_CONTRIBUTION
            "Ignore" -> MoneyMovementType.UNKNOWN
            else -> null
        }
}

data class TransactionsUiState(
    val filters: List<TransactionFilter> = TransactionFilter.entries.toList(),
    val selectedFilter: TransactionFilter = TransactionFilter.All,
    val groups: List<TransactionGroup> = emptyList(),
    val categoryOptions: List<TransactionCategoryOption> = emptyList(),
    val selectedTransaction: TransactionDetail? = null,
    val totalBalanceMinor: Long = 0L,
    val incomeMinor: Long = 0L,
    val expensesMinor: Long = 0L,
    val savingsMinor: Long = 0L,
    val receiveSmsGranted: Boolean = false,
    val isEmpty: Boolean = true,
)

enum class TransactionFilter(val label: String) {
    All("All"),
    Income("Income"),
    Expenses("Expenses"),
    Transfers("Transfers"),
    Unknown("Unknown"),
}

data class TransactionGroup(
    val dateGroup: String,
    val transactions: List<TransactionListItem>,
)

data class TransactionListItem(
    val id: String,
    val merchantName: String,
    val categoryName: String,
    val categoryId: String?,
    val amountMinor: Long,
    val isCredit: Boolean,
    val timestamp: String,
    val dateGroup: String,
    val provider: String,
    val reference: String?,
    val balanceAfterMinor: Long?,
    val currency: String,
    val movementType: MoneyMovementType,
) {
    val isUnknownCategory: Boolean =
        categoryName.equals("Unknown", ignoreCase = true) || categoryId == null
}

data class TransactionCategoryOption(
    val id: String,
    val name: String,
)
