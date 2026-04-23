package com.kevin.financeguardian.feature.categories

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-04-22T10:15:30Z")
    private val categoryDao = FakeCategoryDao()
    private val transactionRepository = FakeTransactionRepository()
    private val idGenerator = FakeIdGenerator(listOf("custom-1"))
    private val clock = FixedClock(now)

    @Test
    fun uiStateShowsActiveCategoriesWithTransactionCounts() = runTest {
        categoryDao.replace(
            listOf(
                category(id = "food", name = "Food", type = CategoryType.EXPENSE),
                category(id = "custom-rent", name = "Rent", type = CategoryType.EXPENSE),
                category(id = "archived", name = "Archived", type = CategoryType.EXPENSE, isArchived = true),
            ),
        )
        transactionRepository.replace(
            listOf(
                transaction(id = "txn-1", categoryId = "food"),
                transaction(id = "txn-2", categoryId = "custom-rent"),
                transaction(id = "txn-3", categoryId = "custom-rent"),
            ),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Food", "Rent"), viewModel.uiState.value.categories.map { it.name })
        assertEquals(1, viewModel.uiState.value.categories.first { it.id == "food" }.transactionCount)
        assertEquals(2, viewModel.uiState.value.categories.first { it.id == "custom-rent" }.transactionCount)
        assertFalse(viewModel.uiState.value.categories.first { it.id == "food" }.canEdit)
        assertTrue(viewModel.uiState.value.categories.first { it.id == "custom-rent" }.canEdit)
    }

    @Test
    fun saveCategoryCreatesTrimmedCustomCategory() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startAddCategory()
        viewModel.saveCategory("  Petty   Cash  ", CategoryType.TRANSFER)
        advanceUntilIdle()

        val saved = categoryDao.getById("custom-1")
        assertEquals("Petty Cash", saved?.name)
        assertEquals(CategoryType.TRANSFER, saved?.type)
        assertEquals(now, saved?.createdAt)
        assertEquals(now, saved?.updatedAt)
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun saveCategoryRejectsDuplicateName() = runTest {
        categoryDao.replace(
            listOf(category(id = "food", name = "Food", type = CategoryType.EXPENSE)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startAddCategory()
        viewModel.saveCategory("food", CategoryType.EXPENSE)
        advanceUntilIdle()

        assertEquals("A category with this name already exists.", viewModel.uiState.value.editor?.errorMessage)
        assertNull(categoryDao.getById("custom-1"))
    }

    @Test
    fun saveCategoryEditsCustomCategoryAndPreservesCreatedAt() = runTest {
        val createdAt = Instant.parse("2026-04-21T09:00:00Z")
        categoryDao.replace(
            listOf(
                category(
                    id = "custom-rent",
                    name = "Rent",
                    type = CategoryType.EXPENSE,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startEditCategory("custom-rent")
        viewModel.saveCategory("Housing", CategoryType.SAVINGS)
        advanceUntilIdle()

        val updated = categoryDao.getById("custom-rent")
        assertEquals("Housing", updated?.name)
        assertEquals(CategoryType.SAVINGS, updated?.type)
        assertEquals(createdAt, updated?.createdAt)
        assertEquals(now, updated?.updatedAt)
    }

    @Test
    fun archiveEditingCategoryHidesCustomCategoryWithNoTransactions() = runTest {
        categoryDao.replace(
            listOf(category(id = "custom-rent", name = "Rent", type = CategoryType.EXPENSE)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startEditCategory("custom-rent")
        viewModel.archiveEditingCategory()
        advanceUntilIdle()

        assertTrue(categoryDao.getById("custom-rent")?.isArchived == true)
        assertTrue(viewModel.uiState.value.categories.none { it.id == "custom-rent" })
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun archiveEditingCategoryRejectsCategoryWithTransactions() = runTest {
        categoryDao.replace(
            listOf(category(id = "custom-rent", name = "Rent", type = CategoryType.EXPENSE)),
        )
        transactionRepository.replace(listOf(transaction(categoryId = "custom-rent")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startEditCategory("custom-rent")
        viewModel.archiveEditingCategory()
        advanceUntilIdle()

        assertFalse(categoryDao.getById("custom-rent")?.isArchived == true)
        assertEquals(
            "Archive is only available for categories with no transactions.",
            viewModel.uiState.value.editor?.errorMessage,
        )
    }

    private fun viewModel(): CategoriesViewModel =
        CategoriesViewModel(
            categoryDao = categoryDao,
            transactionRepository = transactionRepository,
            idGenerator = idGenerator,
            clock = clock,
        )

    private fun category(
        id: String,
        name: String,
        type: CategoryType,
        isArchived: Boolean = false,
        createdAt: Instant = now,
        updatedAt: Instant = now,
    ): CategoryEntity =
        CategoryEntity(
            id = id,
            name = name,
            type = type,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun transaction(
        id: String = "txn-1",
        categoryId: String?,
    ): Transaction =
        Transaction(
            id = id,
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MobileMoney",
            rawBodyHash = "hash-$id",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 1_000,
            currency = "GHS",
            counterpartyName = "Shop",
            counterpartyPhone = null,
            reference = null,
            balanceAfterMinor = null,
            categoryId = categoryId,
            confidence = 0.95f,
            createdAt = now,
            updatedAt = now,
        )

    private class FakeCategoryDao : CategoryDao {
        private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<CategoryEntity>> =
            categories.map { values ->
                values.filterNot { it.isArchived }.sortedBy { it.name }
            }

        override suspend fun getAllOnce(): List<CategoryEntity> =
            categories.value.sortedBy { it.name }

        override suspend fun getById(categoryId: String): CategoryEntity? =
            categories.value.firstOrNull { it.id == categoryId }

        override suspend fun upsert(category: CategoryEntity) {
            replace(categories.value.filterNot { it.id == category.id } + category)
        }

        override suspend fun upsertAll(categories: List<CategoryEntity>) {
            replace(categories)
        }

        override suspend fun archive(categoryId: String, updatedAt: Instant) {
            replace(
                categories.value.map {
                    if (it.id == categoryId) {
                        it.copy(isArchived = true, updatedAt = updatedAt)
                    } else {
                        it
                    }
                },
            )
        }

        override suspend fun deleteAll() {
            replace(emptyList())
        }

        fun replace(next: List<CategoryEntity>) {
            categories.value = next.sortedBy { it.name }
        }
    }

    private class FakeTransactionRepository : TransactionRepository {
        private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeTransactions(): Flow<List<Transaction>> = transactions

        override suspend fun getTransaction(id: String): Transaction? =
            transactions.value.firstOrNull { it.id == id }

        override suspend fun insertTransaction(transaction: Transaction) {
            transactions.update { it + transaction }
        }

        override suspend fun updateCategory(transactionId: String, categoryId: String?) {
            transactions.update { current ->
                current.map {
                    if (it.id == transactionId) it.copy(categoryId = categoryId) else it
                }
            }
        }

        override suspend fun updateMoneyMovementType(transactionId: String, type: MoneyMovementType) {
            transactions.update { current ->
                current.map {
                    if (it.id == transactionId) it.copy(moneyMovementType = type) else it
                }
            }
        }

        fun replace(next: List<Transaction>) {
            transactions.value = next
        }
    }

    private class FakeIdGenerator(ids: List<String>) : IdGenerator {
        private val ids = ArrayDeque(ids)

        override fun newId(): String = ids.removeFirst()
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
