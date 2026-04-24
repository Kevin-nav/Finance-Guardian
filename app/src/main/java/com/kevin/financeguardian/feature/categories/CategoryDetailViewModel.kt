package com.kevin.financeguardian.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.domain.model.Category
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.DefaultCategories
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.effectiveIsCredit
import com.kevin.financeguardian.ui.components.TransactionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository,
    private val transactionCorrectionApplier: TransactionCorrectionApplier,
    private val clock: AppClock,
) : ViewModel() {

    private val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    private val selectedTransactionId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoryDetailUiState> = combine(
        categoryDao.observeAll(),
        transactionRepository.observeTransactions(),
        selectedTransactionId,
    ) { allCategories, allTransactions, selectedTxnId ->
        val category = allCategories.firstOrNull { it.id == categoryId }
        val categories = allCategories.map { it.toDomain() }
        val categoryOptions = categories.map { CategoryDetailCategoryOption(it.id, it.name) }

        if (category == null) {
            return@combine CategoryDetailUiState(isNotFound = true)
        }

        val categoryTransactions = allTransactions.filter { it.categoryId == categoryId }
        val now = clock.now()

        val items = categoryTransactions.map { it.toListItem(categories.associateBy { c -> c.id }, now) }
        val groups = items.toDateGroups()

        val totalAmountMinor = categoryTransactions.sumOf { it.amountMinor }
        val selectedItem = items.firstOrNull { it.id == selectedTxnId }

        val isDefault = categoryId in defaultCategoryIds

        CategoryDetailUiState(
            categoryName = category.name,
            categoryType = category.type,
            typeLabel = category.type.toLabel(),
            totalAmountMinor = totalAmountMinor,
            transactionCount = items.size,
            groups = groups,
            selectedTransaction = selectedItem?.toDetail(),
            categoryOptions = categoryOptions,
            canEdit = !isDefault,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CategoryDetailUiState(),
    )

    fun selectTransaction(transactionId: String) {
        selectedTransactionId.value = transactionId
    }

    fun dismissTransaction() {
        selectedTransactionId.value = null
    }

    fun saveCorrection(
        selectedCategory: String,
        selectedType: String,
    ) {
        val transactionId = selectedTransactionId.value ?: return
        val catId = uiState.value.categoryOptions
            .firstOrNull { it.name.equals(selectedCategory, ignoreCase = true) }
            ?.id
        val movementType = selectedType.toMoneyMovementType()
        viewModelScope.launch {
            transactionCorrectionApplier.applyCorrection(
                transactionId = transactionId,
                categoryId = catId,
                moneyMovementType = movementType,
                saveMerchantDefault = true,
            )
            selectedTransactionId.value = null
        }
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    private fun Transaction.toListItem(
        categoryById: Map<String, Category>,
        now: Instant,
    ): CategoryDetailTransactionItem {
        val categoryName = categoryId
            ?.let { categoryById[it]?.name }
            ?: "Unknown"
        return CategoryDetailTransactionItem(
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

    private fun List<CategoryDetailTransactionItem>.toDateGroups(): List<CategoryDetailTransactionGroup> {
        val groups = linkedMapOf<String, MutableList<CategoryDetailTransactionItem>>()
        forEach { item ->
            groups.getOrPut(item.dateGroup) { mutableListOf() }.add(item)
        }
        return groups.map { (dateGroup, transactions) ->
            CategoryDetailTransactionGroup(dateGroup, transactions)
        }
    }

    private fun CategoryDetailTransactionItem.toDetail(): TransactionDetail =
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

    private fun CategoryType.toLabel(): String =
        when (this) {
            CategoryType.EXPENSE -> "Expense"
            CategoryType.INCOME -> "Income"
            CategoryType.TRANSFER -> "Transfer"
            CategoryType.SAVINGS -> "Savings"
        }

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

    private companion object {
        val defaultCategoryIds = DefaultCategories.values.map { it.id }.toSet()
    }
}

// ── UI State ────────────────────────────────────────────────────────────────

data class CategoryDetailUiState(
    val categoryName: String = "",
    val categoryType: CategoryType = CategoryType.EXPENSE,
    val typeLabel: String = "Expense",
    val totalAmountMinor: Long = 0L,
    val transactionCount: Int = 0,
    val groups: List<CategoryDetailTransactionGroup> = emptyList(),
    val selectedTransaction: TransactionDetail? = null,
    val categoryOptions: List<CategoryDetailCategoryOption> = emptyList(),
    val canEdit: Boolean = false,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
)

data class CategoryDetailTransactionGroup(
    val dateGroup: String,
    val transactions: List<CategoryDetailTransactionItem>,
)

data class CategoryDetailTransactionItem(
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

data class CategoryDetailCategoryOption(
    val id: String,
    val name: String,
)
