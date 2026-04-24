package com.kevin.financeguardian.feature.insights

import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.core.notifications.InsightEvaluator
import com.kevin.financeguardian.data.learning.RecurringPatternDetector
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-04-22T12:00:00Z")
    private val repository = FakeTransactionRepository()
    private val categoryDao = FakeCategoryDao()
    private val learningSignalDao = FakeLearningSignalDao()

    @Test
    fun emptyStateHasNoInsights() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.hasData)
        assertEquals(0L, state.incomeMinor)
        assertEquals(0L, state.spendingMinor)
        assertEquals(0L, state.netCashFlowMinor)
    }

    @Test
    fun computesSummaryAndExcludesInternalTransfersFromSpending() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "income",
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INCOME,
                    amountMinor = 120_00,
                    categoryId = "income",
                ),
                transaction(
                    id = "food",
                    amountMinor = 30_00,
                    categoryId = "food",
                ),
                transaction(
                    id = "transfer",
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    amountMinor = 50_00,
                    categoryId = "transfers",
                ),
                transaction(
                    id = "savings",
                    movement = MoneyMovementType.SAVINGS_CONTRIBUTION,
                    amountMinor = 20_00,
                    categoryId = "savings",
                ),
            ),
        )

        val state = viewModel().uiState.value

        assertTrue(state.hasData)
        assertEquals(120_00, state.incomeMinor)
        assertEquals(50_00, state.spendingMinor)
        assertEquals(70_00, state.netCashFlowMinor)
        assertEquals(listOf("Food", "Savings"), state.categorySpending.map { it.name })
    }

    @Test
    fun largestTransactionsAreRealTransactionsSortedByAmount() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(id = "small", counterpartyName = "Small Shop", amountMinor = 10_00),
                transaction(id = "large", counterpartyName = "Big Shop", amountMinor = 80_00),
            ),
        )

        val state = viewModel().uiState.value

        assertEquals(listOf("Big Shop", "Small Shop"), state.largeTransactions.map { it.merchantName })
    }

    @Test
    fun outgoingBurstAddsHighlightInsight() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(id = "history-1", occurredAt = Instant.parse("2026-04-21T10:00:00Z")),
                transaction(id = "history-2", occurredAt = Instant.parse("2026-04-21T12:00:00Z")),
                transaction(id = "history-3", occurredAt = Instant.parse("2026-04-22T09:00:00Z")),
                transaction(id = "today-1", occurredAt = Instant.parse("2026-04-22T23:30:00Z")),
                transaction(id = "today-2", occurredAt = Instant.parse("2026-04-22T22:30:00Z")),
                transaction(id = "today-3", occurredAt = Instant.parse("2026-04-22T21:30:00Z")),
                transaction(id = "today-4", occurredAt = Instant.parse("2026-04-22T20:30:00Z")),
            ),
        )

        val state = viewModel().uiState.value

        assertEquals("Spending is higher than usual today", state.highlightInsight?.title)
    }

    private fun seedCategories() {
        categoryDao.replace(
            listOf(
                CategoryEntity(
                    id = "food",
                    name = "Food",
                    type = CategoryType.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
                CategoryEntity(
                    id = "income",
                    name = "Income",
                    type = CategoryType.INCOME,
                    createdAt = now,
                    updatedAt = now,
                ),
                CategoryEntity(
                    id = "savings",
                    name = "Savings",
                    type = CategoryType.SAVINGS,
                    createdAt = now,
                    updatedAt = now,
                ),
                CategoryEntity(
                    id = "transfers",
                    name = "Transfers",
                    type = CategoryType.TRANSFER,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun viewModel(): InsightsViewModel =
        InsightsViewModel(
            transactionRepository = repository,
            categoryDao = categoryDao,
            learningSignalDao = learningSignalDao,
            recurringPatternDetector = RecurringPatternDetector(),
            insightEvaluator = InsightEvaluator(),
            clock = FixedClock(now),
        )

    private fun transaction(
        id: String,
        counterpartyName: String? = "Sample Merchant",
        categoryId: String? = null,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        movement: MoneyMovementType = MoneyMovementType.EXPENSE,
        amountMinor: Long = 20_00,
        occurredAt: Instant = now,
    ): Transaction =
        Transaction(
            id = id,
            sourceMessageId = null,
            provider = Provider.MTN_MOMO,
            rawSender = "MobileMoney",
            rawBodyHash = "hash-$id",
            occurredAt = occurredAt,
            direction = direction,
            moneyMovementType = movement,
            amountMinor = amountMinor,
            currency = "GHS",
            counterpartyName = counterpartyName,
            counterpartyPhone = null,
            reference = "R-$id",
            balanceAfterMinor = null,
            categoryId = categoryId,
            confidence = 0.9f,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )

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
            transactions.value = next.sortedByDescending { it.occurredAt }
        }
    }

    private class FakeCategoryDao : CategoryDao {
        private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<CategoryEntity>> = categories

        override suspend fun getAllOnce(): List<CategoryEntity> = categories.value

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
                    if (it.id == categoryId) it.copy(isArchived = true, updatedAt = updatedAt) else it
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

    private class FakeLearningSignalDao : LearningSignalDao() {
        private val signals = MutableStateFlow<List<LearningSignalEntity>>(emptyList())

        override suspend fun getBySignalKey(signalKey: String): LearningSignalEntity? =
            signals.value.firstOrNull { it.signalKey == signalKey }

        override suspend fun getAllOnce(): List<LearningSignalEntity> = signals.value

        override fun observeAll(): Flow<List<LearningSignalEntity>> = signals

        override fun observeCountBySignalType(signalType: String): Flow<Int> =
            MutableStateFlow(signals.value.count { it.signalType == signalType })

        override suspend fun findByNormalizedMerchantName(
            normalizedMerchantName: String,
        ): List<LearningSignalEntity> =
            signals.value.filter { it.normalizedMerchantName == normalizedMerchantName }

        override suspend fun findByNormalizedPhone(
            normalizedPhone: String,
        ): List<LearningSignalEntity> =
            signals.value.filter { it.normalizedPhone == normalizedPhone }

        override suspend fun findByNormalizedReference(
            normalizedReference: String,
        ): List<LearningSignalEntity> =
            signals.value.filter { it.normalizedReference == normalizedReference }

        override suspend fun upsert(entity: LearningSignalEntity) {
            signals.update { current ->
                current.filterNot { it.signalKey == entity.signalKey } + entity
            }
        }

        override suspend fun insert(entity: LearningSignalEntity) {
            signals.update { current -> current + entity }
        }

        override suspend fun update(entity: LearningSignalEntity) {
            signals.update { current ->
                current.map { existing ->
                    if (existing.id == entity.id) entity else existing
                }
            }
        }
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
