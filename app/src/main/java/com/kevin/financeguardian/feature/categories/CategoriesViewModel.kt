package com.kevin.financeguardian.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.DefaultCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) : ViewModel() {
    private val editorMode = MutableStateFlow<CategoryEditorMode?>(null)
    private val editorError = MutableStateFlow<String?>(null)
    private val _selectedFilter = MutableStateFlow(CategoryTypeFilter.All)

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryDao.observeAll(),
        transactionRepository.observeTransactions(),
        editorMode,
        editorError,
        _selectedFilter,
    ) { categories, transactions, editor, error, filter ->
        val transactionCounts = transactions
            .mapNotNull { it.categoryId }
            .groupingBy { it }
            .eachCount()
        val items = categories.map { category ->
            val count = transactionCounts[category.id] ?: 0
            category.toListItem(count)
        }
        val editorState = editor?.toEditorState(categories, transactionCounts, error)

        val filteredItems = when (filter) {
            CategoryTypeFilter.All -> items
            CategoryTypeFilter.Expense -> items.filter { it.type == CategoryType.EXPENSE }
            CategoryTypeFilter.Income -> items.filter { it.type == CategoryType.INCOME }
            CategoryTypeFilter.Transfer -> items.filter { it.type == CategoryType.TRANSFER }
            CategoryTypeFilter.Savings -> items.filter { it.type == CategoryType.SAVINGS }
        }

        CategoriesUiState(
            categories = items,
            filteredCategories = filteredItems,
            totalCount = items.size,
            expenseCount = items.count { it.type == CategoryType.EXPENSE },
            incomeCount = items.count { it.type == CategoryType.INCOME },
            transferCount = items.count { it.type == CategoryType.TRANSFER },
            savingsCount = items.count { it.type == CategoryType.SAVINGS },
            selectedFilter = filter,
            editor = editorState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CategoriesUiState(),
    )

    fun selectFilter(filter: CategoryTypeFilter) {
        _selectedFilter.value = filter
    }

    fun startAddCategory() {
        editorError.value = null
        editorMode.value = CategoryEditorMode.Add
    }

    fun startEditCategory(categoryId: String) {
        val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return
        if (!category.canEdit) return
        editorError.value = null
        editorMode.value = CategoryEditorMode.Edit(categoryId)
    }

    fun dismissEditor() {
        editorMode.value = null
        editorError.value = null
    }

    fun saveCategory(name: String, type: CategoryType) {
        val mode = editorMode.value ?: return
        viewModelScope.launch {
            val normalizedName = name.trim().replace(Regex("\\s+"), " ")
            if (normalizedName.isBlank()) {
                editorError.value = "Enter a category name."
                return@launch
            }

            val editingId = (mode as? CategoryEditorMode.Edit)?.categoryId
            val allCategories = categoryDao.getAllOnce()
            val duplicate = allCategories.any {
                it.id != editingId && it.name.equals(normalizedName, ignoreCase = true)
            }
            if (duplicate) {
                editorError.value = "A category with this name already exists."
                return@launch
            }

            val existing = editingId?.let { categoryDao.getById(it) }
            if (editingId != null && existing == null) {
                editorError.value = "Category no longer exists."
                return@launch
            }
            if (editingId in defaultCategoryIds) {
                editorError.value = "Default categories cannot be edited."
                return@launch
            }

            val now = clock.now()
            categoryDao.upsert(
                CategoryEntity(
                    id = editingId ?: idGenerator.newId(),
                    name = normalizedName,
                    type = type,
                    isArchived = false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            dismissEditor()
        }
    }

    fun archiveEditingCategory() {
        val categoryId = (editorMode.value as? CategoryEditorMode.Edit)?.categoryId ?: return
        viewModelScope.launch {
            val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return@launch
            if (!category.canArchive) {
                editorError.value = when {
                    !category.canEdit -> "Default categories cannot be archived."
                    category.transactionCount > 0 -> "Archive is only available for categories with no transactions."
                    else -> "This category cannot be archived."
                }
                return@launch
            }

            categoryDao.archive(categoryId, clock.now())
            dismissEditor()
        }
    }

    private fun CategoryEntity.toListItem(transactionCount: Int): CategoryListItem {
        val isDefault = id in defaultCategoryIds
        return CategoryListItem(
            id = id,
            name = name,
            type = type,
            typeLabel = type.toLabel(),
            transactionCount = transactionCount,
            canEdit = !isDefault,
            canArchive = !isDefault && transactionCount == 0,
        )
    }

    private fun CategoryEditorMode.toEditorState(
        categories: List<CategoryEntity>,
        transactionCounts: Map<String, Int>,
        error: String?,
    ): CategoryEditorUiState? =
        when (this) {
            CategoryEditorMode.Add -> CategoryEditorUiState(
                id = null,
                title = "Add Category",
                name = "",
                selectedType = CategoryType.EXPENSE,
                canArchive = false,
                errorMessage = error,
            )
            is CategoryEditorMode.Edit -> {
                val category = categories.firstOrNull { it.id == categoryId } ?: return null
                val transactionCount = transactionCounts[category.id] ?: 0
                val isDefault = category.id in defaultCategoryIds
                CategoryEditorUiState(
                    id = category.id,
                    title = "Edit Category",
                    name = category.name,
                    selectedType = category.type,
                    canArchive = !isDefault && transactionCount == 0,
                    errorMessage = error,
                )
            }
        }

    private fun CategoryType.toLabel(): String =
        when (this) {
            CategoryType.EXPENSE -> "Expense"
            CategoryType.INCOME -> "Income"
            CategoryType.TRANSFER -> "Transfer"
            CategoryType.SAVINGS -> "Savings"
        }

    private sealed interface CategoryEditorMode {
        data object Add : CategoryEditorMode
        data class Edit(val categoryId: String) : CategoryEditorMode
    }

    private companion object {
        val defaultCategoryIds = DefaultCategories.values.map { it.id }.toSet()
    }
}

// ── Filter Enum ─────────────────────────────────────────────────────────────

enum class CategoryTypeFilter(val label: String) {
    All("All"),
    Expense("Expenses"),
    Income("Income"),
    Transfer("Transfers"),
    Savings("Savings"),
}

// ── UI State ────────────────────────────────────────────────────────────────

data class CategoriesUiState(
    val categories: List<CategoryListItem> = emptyList(),
    val filteredCategories: List<CategoryListItem> = emptyList(),
    val totalCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
    val transferCount: Int = 0,
    val savingsCount: Int = 0,
    val selectedFilter: CategoryTypeFilter = CategoryTypeFilter.All,
    val editor: CategoryEditorUiState? = null,
)

data class CategoryListItem(
    val id: String,
    val name: String,
    val type: CategoryType,
    val typeLabel: String,
    val transactionCount: Int,
    val canEdit: Boolean,
    val canArchive: Boolean,
)

data class CategoryEditorUiState(
    val id: String?,
    val title: String,
    val name: String,
    val selectedType: CategoryType,
    val canArchive: Boolean,
    val errorMessage: String?,
    val typeOptions: List<CategoryTypeOption> = CategoryType.entries.map {
        CategoryTypeOption(it, it.toEditorLabel())
    },
)

data class CategoryTypeOption(
    val type: CategoryType,
    val label: String,
)

private fun CategoryType.toEditorLabel(): String =
    when (this) {
        CategoryType.EXPENSE -> "Expense"
        CategoryType.INCOME -> "Income"
        CategoryType.TRANSFER -> "Transfer"
        CategoryType.SAVINGS -> "Savings"
    }
