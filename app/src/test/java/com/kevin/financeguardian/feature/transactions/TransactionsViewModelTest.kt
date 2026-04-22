package com.kevin.financeguardian.feature.transactions

import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.FinanceGuardianPermission
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.data.transaction.TransactionCorrectionResult
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.DefaultCategories
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-04-22T12:00:00Z")
    private val checker = FakePermissionStatusChecker()
    private val repository = FakeTransactionRepository()
    private val categoryDao = FakeCategoryDao()
    private val correctionApplier = FakeTransactionCorrectionApplier()

    @Test
    fun emptyStateReportsPermissionStatus() = runTest {
        checker.statuses = AppPermissionStatuses(
            receiveSmsGranted = false,
            postNotificationsGranted = false,
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertFalse(state.receiveSmsGranted)
    }

    @Test
    fun allFilterReturnsTransactionsGroupedByDate() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "income-1",
                    categoryId = "income",
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INCOME,
                ),
                transaction(id = "expense-1", counterpartyName = "Shop", categoryId = "food"),
            ),
        )

        val state = viewModel().uiState.value

        assertEquals(TransactionFilter.All, state.selectedFilter)
        assertEquals(2, state.groups.sumOf { it.transactions.size })
        assertEquals("Today", state.groups.first().dateGroup)
    }

    @Test
    fun filtersReturnExpectedTransactionTypes() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "income-1",
                    categoryId = "income",
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INCOME,
                ),
                transaction(id = "expense-1", counterpartyName = "Shop", categoryId = "food"),
                transaction(
                    id = "transfer-1",
                    counterpartyName = "Wallet",
                    categoryId = "transfers",
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                ),
                transaction(
                    id = "savings-1",
                    counterpartyName = "Savings",
                    categoryId = "savings",
                    movement = MoneyMovementType.SAVINGS_CONTRIBUTION,
                ),
                transaction(
                    id = "unknown-1",
                    counterpartyName = "Unknown",
                    categoryId = "unknown",
                    movement = MoneyMovementType.UNKNOWN,
                ),
            ),
        )
        val viewModel = viewModel()

        viewModel.selectFilter(TransactionFilter.Income)
        assertEquals(listOf("income-1"), viewModel.visibleTransactionIds())

        viewModel.selectFilter(TransactionFilter.Expenses)
        assertEquals(listOf("expense-1"), viewModel.visibleTransactionIds())

        viewModel.selectFilter(TransactionFilter.Transfers)
        assertEquals(listOf("transfer-1"), viewModel.visibleTransactionIds())

        viewModel.selectFilter(TransactionFilter.Unknown)
        assertEquals(listOf("unknown-1"), viewModel.visibleTransactionIds())
    }

    @Test
    fun summaryComputesIncomeSpendingSavingsAndLatestBalance() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "income-1",
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INCOME,
                    amountMinor = 100_00,
                    balanceAfterMinor = 900_00,
                    occurredAt = now.minusSeconds(120),
                ),
                transaction(
                    id = "expense-1",
                    amountMinor = 25_00,
                    balanceAfterMinor = 875_00,
                    occurredAt = now.minusSeconds(60),
                ),
                transaction(
                    id = "transfer-1",
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    amountMinor = 40_00,
                    balanceAfterMinor = 835_00,
                    occurredAt = now.minusSeconds(30),
                ),
                transaction(
                    id = "savings-1",
                    movement = MoneyMovementType.SAVINGS_CONTRIBUTION,
                    amountMinor = 10_00,
                    balanceAfterMinor = 825_00,
                    occurredAt = now,
                ),
            ),
        )

        val state = viewModel().uiState.value

        assertEquals(100_00, state.incomeMinor)
        assertEquals(25_00, state.expensesMinor)
        assertEquals(10_00, state.savingsMinor)
        assertEquals(825_00, state.totalBalanceMinor)
    }

    @Test
    fun saveCorrectionCallsBackendWithSelectedCategoryAndMovementType() = runTest {
        seedCategories()
        repository.replace(
            listOf(transaction(id = "expense-1", counterpartyName = "Shop", categoryId = "unknown")),
        )
        val viewModel = viewModel()

        viewModel.selectTransaction("expense-1")
        viewModel.saveCorrection("Transfers", "Internal Transfer")
        advanceUntilIdle()

        assertEquals(
            CorrectionCall(
                transactionId = "expense-1",
                categoryId = "transfers",
                moneyMovementType = MoneyMovementType.INTERNAL_TRANSFER,
                saveMerchantDefault = true,
            ),
            correctionApplier.lastCall,
        )
        assertEquals(null, viewModel.uiState.value.selectedTransaction)
    }

    private fun TransactionsViewModel.visibleTransactionIds(): List<String> =
        uiState.value.groups.flatMap { group -> group.transactions.map { it.id } }

    private fun seedCategories() {
        categoryDao.replace(
            DefaultCategories.values.map { category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    type = category.type,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    private fun viewModel(): TransactionsViewModel =
        TransactionsViewModel(
            transactionRepository = repository,
            categoryDao = categoryDao,
            transactionCorrectionApplier = correctionApplier,
            permissionStatusChecker = checker,
            clock = FixedClock(now),
        )

    private fun transaction(
        id: String,
        counterpartyName: String? = "Sample Merchant",
        categoryId: String? = null,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        movement: MoneyMovementType = MoneyMovementType.EXPENSE,
        amountMinor: Long = 20_00,
        balanceAfterMinor: Long? = null,
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
            balanceAfterMinor = balanceAfterMinor,
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

        override suspend fun upsertAll(categories: List<CategoryEntity>) {
            replace(categories)
        }

        fun replace(next: List<CategoryEntity>) {
            categories.value = next.sortedBy { it.name }
        }
    }

    private class FakeTransactionCorrectionApplier : TransactionCorrectionApplier {
        var lastCall: CorrectionCall? = null

        override suspend fun applyCorrection(
            transactionId: String,
            categoryId: String?,
            moneyMovementType: MoneyMovementType?,
            saveMerchantDefault: Boolean,
        ): TransactionCorrectionResult {
            lastCall = CorrectionCall(
                transactionId = transactionId,
                categoryId = categoryId,
                moneyMovementType = moneyMovementType,
                saveMerchantDefault = saveMerchantDefault,
            )
            return TransactionCorrectionResult.Applied
        }
    }

    private data class CorrectionCall(
        val transactionId: String,
        val categoryId: String?,
        val moneyMovementType: MoneyMovementType?,
        val saveMerchantDefault: Boolean,
    )

    private class FakePermissionStatusChecker : PermissionStatusChecker {
        var statuses = AppPermissionStatuses(
            receiveSmsGranted = true,
            postNotificationsGranted = false,
        )

        override fun isGranted(permission: FinanceGuardianPermission): Boolean =
            when (permission) {
                FinanceGuardianPermission.ReceiveSms -> statuses.receiveSmsGranted
                FinanceGuardianPermission.PostNotifications -> statuses.postNotificationsGranted
            }

        override fun currentStatuses(): AppPermissionStatuses = statuses
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }
}
