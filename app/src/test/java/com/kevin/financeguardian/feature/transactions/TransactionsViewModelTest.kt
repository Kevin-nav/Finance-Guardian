package com.kevin.financeguardian.feature.transactions

import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
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
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
import com.kevin.financeguardian.testing.MainDispatcherRule
import com.kevin.financeguardian.ui.components.FlowStatusUi
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
    private val notificationDispatcher = RecordingNotificationDispatcher()

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
    fun expenseMovementOverridesCreditDirectionForRenderingAndFiltering() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "expense-corrected",
                    counterpartyName = "Bills",
                    categoryId = "food",
                    direction = com.kevin.financeguardian.domain.model.TransactionDirection.CREDIT,
                    movement = MoneyMovementType.EXPENSE,
                ),
            ),
        )
        val viewModel = viewModel()

        assertEquals(false, viewModel.uiState.value.groups.first().transactions.first().isCredit)

        viewModel.selectFilter(TransactionFilter.Income)
        assertEquals(emptyList<String>(), viewModel.visibleTransactionIds())

        viewModel.selectFilter(TransactionFilter.Expenses)
        assertEquals(listOf("expense-corrected"), viewModel.visibleTransactionIds())
    }

    @Test
    fun summaryComputesIncomeSpendingSavingsAndLatestProviderBalances() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "income-1",
                    provider = Provider.MTN_MOMO,
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INCOME,
                    amountMinor = 100_00,
                    balanceAfterMinor = 900_00,
                    occurredAt = now.minusSeconds(120),
                ),
                transaction(
                    id = "expense-1",
                    provider = Provider.MTN_MOMO,
                    amountMinor = 25_00,
                    balanceAfterMinor = 875_00,
                    occurredAt = now.minusSeconds(60),
                ),
                transaction(
                    id = "transfer-1",
                    provider = Provider.TELECEL_CASH,
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    amountMinor = 40_00,
                    balanceAfterMinor = 40_00,
                    occurredAt = now.minusSeconds(30),
                ),
                transaction(
                    id = "savings-1",
                    provider = Provider.GCB,
                    movement = MoneyMovementType.SAVINGS_CONTRIBUTION,
                    amountMinor = 10_00,
                    balanceAfterMinor = 500_00,
                    occurredAt = now,
                ),
            ),
        )

        val state = viewModel().uiState.value

        assertEquals(100_00, state.incomeMinor)
        assertEquals(25_00, state.expensesMinor)
        assertEquals(10_00, state.savingsMinor)
        assertEquals(1_415_00, state.totalBalanceMinor)
        assertEquals(
            listOf(
                ProviderBalanceSnapshot("MTN MoMo", 875_00, "GHS"),
                ProviderBalanceSnapshot("Telecel Cash", 40_00, "GHS"),
                ProviderBalanceSnapshot("GCB Bank", 500_00, "GHS"),
            ),
            state.providerBalances,
        )
    }

    @Test
    fun transactionListCollapsesLinkedInternalFlowRows() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "telecel-side",
                    provider = Provider.TELECEL_CASH,
                    direction = TransactionDirection.DEBIT,
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    amountMinor = 20_00,
                    categoryId = "transfers",
                    flowId = "telecel-side",
                    flowType = TransactionFlowType.INTERNAL_TRANSFER,
                    flowStatus = TransactionFlowStatus.COMPLETE,
                    occurredAt = now.minusSeconds(60),
                ),
                transaction(
                    id = "mtn-side",
                    provider = Provider.MTN_MOMO,
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    amountMinor = 20_00,
                    categoryId = "transfers",
                    flowId = "telecel-side",
                    flowType = TransactionFlowType.INTERNAL_TRANSFER,
                    flowStatus = TransactionFlowStatus.COMPLETE,
                    occurredAt = now,
                ),
            ),
        )

        val row = viewModel().uiState.value.groups.single().transactions.single()

        assertEquals("telecel-side", row.id)
        assertEquals("Telecel Cash -> MTN MoMo", row.merchantName)
        assertEquals(20_00, row.amountMinor)
        assertEquals(false, row.includedInSpendingTotals)
    }

    @Test
    fun providerBalancesIgnoreSuspiciousBalances() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "valid-gcb",
                    provider = Provider.GCB,
                    amountMinor = 10_00,
                    balanceAfterMinor = 500_00,
                    balanceReliability = BalanceReliability.RELIABLE,
                    occurredAt = now.minusSeconds(60),
                ),
                transaction(
                    id = "bad-gcb",
                    provider = Provider.GCB,
                    amountMinor = 20_00,
                    balanceAfterMinor = -21_46,
                    balanceReliability = BalanceReliability.SUSPICIOUS,
                    occurredAt = now,
                ),
            ),
        )

        assertEquals(
            listOf(ProviderBalanceSnapshot("GCB Bank", 500_00, "GHS")),
            viewModel().uiState.value.providerBalances,
        )
    }

    @Test
    fun flowDetailUsesUnderlyingTransactionsForInstrumentsAndEvidence() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "telecel-side",
                    provider = Provider.TELECEL_CASH,
                    direction = TransactionDirection.DEBIT,
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    categoryId = "transfers",
                    flowId = "telecel-side",
                    flowType = TransactionFlowType.INTERNAL_TRANSFER,
                    flowStatus = TransactionFlowStatus.COMPLETE,
                    plannedUse = "Data",
                    eventChannel = MoneyMovementChannel.WALLET_TO_WALLET,
                    eventSourceInstrumentType = InstrumentType.WALLET,
                    eventSourceInstrumentProvider = InstrumentProvider.TELECEL,
                    eventSourceInstrumentIdentifier = "233505600861",
                    eventDestinationInstrumentType = InstrumentType.WALLET,
                    eventDestinationInstrumentProvider = InstrumentProvider.MTN,
                    eventDestinationInstrumentIdentifier = "233549037907",
                    eventProviderReference = "Data",
                    occurredAt = now.minusSeconds(60),
                ),
                transaction(
                    id = "mtn-side",
                    provider = Provider.MTN_MOMO,
                    direction = TransactionDirection.CREDIT,
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                    categoryId = "transfers",
                    flowId = "telecel-side",
                    flowType = TransactionFlowType.INTERNAL_TRANSFER,
                    flowStatus = TransactionFlowStatus.COMPLETE,
                    eventChannel = MoneyMovementChannel.WALLET_TO_WALLET,
                    eventSourceInstrumentType = InstrumentType.WALLET,
                    eventSourceInstrumentProvider = InstrumentProvider.TELECEL,
                    eventSourceInstrumentIdentifier = "233505600861",
                    eventDestinationInstrumentType = InstrumentType.WALLET,
                    eventDestinationInstrumentProvider = InstrumentProvider.MTN,
                    eventDestinationInstrumentIdentifier = "233549037907",
                    eventProviderReference = "Data",
                    occurredAt = now,
                ),
            ),
        )
        val viewModel = viewModel()

        viewModel.selectTransaction("telecel-side")
        val detail = viewModel.uiState.value.selectedFlow

        assertEquals(FlowStatusUi.MATCHED, detail?.flowStatus)
        assertEquals("Telecel Cash", detail?.sourceInstrument?.provider)
        assertEquals("050 *** 0861", detail?.sourceInstrument?.maskedIdentifier)
        assertEquals("MTN MoMo", detail?.destinationInstrument?.provider)
        assertEquals("054 *** 7907", detail?.destinationInstrument?.maskedIdentifier)
        assertEquals(2, detail?.events?.size)
        assertEquals("Wallet to wallet", detail?.events?.first()?.channel)
    }

    @Test
    fun saveCorrectionCallsBackendWithSelectedCategoryAndMovementType() = runTest {
        seedCategories()
        repository.replace(
            listOf(transaction(id = "expense-1", counterpartyName = "Shop", categoryId = "unknown")),
        )
        val viewModel = viewModel()

        viewModel.selectTransaction("expense-1")
        viewModel.saveCorrection("Transfers", "Internal Transfer", plannedUse = "Food", updatePlannedUse = true)
        advanceUntilIdle()

        assertEquals(
            CorrectionCall(
                transactionId = "expense-1",
                categoryId = "transfers",
                moneyMovementType = MoneyMovementType.INTERNAL_TRANSFER,
                saveMerchantDefault = true,
                plannedUse = "Food",
                updatePlannedUse = true,
            ),
            correctionApplier.lastCall,
        )
        assertEquals(
            listOf(
                NotificationEvent.CorrectionSaved(
                    transactionId = "expense-1",
                    occurredAt = now,
                ),
            ),
            notificationDispatcher.events,
        )
        assertEquals(null, viewModel.uiState.value.selectedFlow)
    }

    @Test
    fun unlinkFlowCallsBackendWithSelectedFlowId() = runTest {
        seedCategories()
        repository.replace(
            listOf(
                transaction(
                    id = "flow-root",
                    categoryId = "transfers",
                    flowId = "flow-root",
                    movement = MoneyMovementType.INTERNAL_TRANSFER,
                ),
            ),
        )
        val viewModel = viewModel()

        viewModel.selectTransaction("flow-root")
        viewModel.unlinkFlow()
        advanceUntilIdle()

        assertEquals("flow-root", correctionApplier.lastUnlinkFlowId)
        assertEquals(null, viewModel.uiState.value.selectedFlow)
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
            notificationDispatcher = notificationDispatcher,
            permissionStatusChecker = checker,
            clock = FixedClock(now),
        )

    private fun transaction(
        id: String,
        provider: Provider = Provider.MTN_MOMO,
        counterpartyName: String? = "Sample Merchant",
        categoryId: String? = null,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        movement: MoneyMovementType = MoneyMovementType.EXPENSE,
        amountMinor: Long = 20_00,
        balanceAfterMinor: Long? = null,
        balanceReliability: BalanceReliability = BalanceReliability.UNKNOWN,
        flowId: String? = null,
        flowType: TransactionFlowType? = null,
        flowStatus: TransactionFlowStatus? = null,
        plannedUse: String? = null,
        eventChannel: MoneyMovementChannel? = null,
        eventSourceInstrumentType: InstrumentType? = null,
        eventSourceInstrumentProvider: InstrumentProvider? = null,
        eventSourceInstrumentIdentifier: String? = null,
        eventDestinationInstrumentType: InstrumentType? = null,
        eventDestinationInstrumentProvider: InstrumentProvider? = null,
        eventDestinationInstrumentIdentifier: String? = null,
        eventProviderReference: String? = null,
        includedInSpendingTotals: Boolean = movement == MoneyMovementType.EXPENSE ||
            movement == MoneyMovementType.SUBSCRIPTION_CANDIDATE ||
            (movement == MoneyMovementType.UNKNOWN && direction == TransactionDirection.DEBIT),
        includedInIncomeTotals: Boolean = movement == MoneyMovementType.INCOME ||
            (movement == MoneyMovementType.UNKNOWN && direction == TransactionDirection.CREDIT),
        occurredAt: Instant = now,
    ): Transaction =
        Transaction(
            id = id,
            sourceMessageId = null,
            provider = provider,
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
            balanceReliability = balanceReliability,
            categoryId = categoryId,
            flowId = flowId,
            flowType = flowType,
            flowStatus = flowStatus,
            plannedUse = plannedUse,
            eventChannel = eventChannel,
            eventSourceInstrumentType = eventSourceInstrumentType,
            eventSourceInstrumentProvider = eventSourceInstrumentProvider,
            eventSourceInstrumentIdentifier = eventSourceInstrumentIdentifier,
            eventDestinationInstrumentType = eventDestinationInstrumentType,
            eventDestinationInstrumentProvider = eventDestinationInstrumentProvider,
            eventDestinationInstrumentIdentifier = eventDestinationInstrumentIdentifier,
            eventProviderReference = eventProviderReference,
            includedInSpendingTotals = includedInSpendingTotals,
            includedInIncomeTotals = includedInIncomeTotals,
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

    private class FakeTransactionCorrectionApplier : TransactionCorrectionApplier {
        var lastCall: CorrectionCall? = null
        var lastUnlinkFlowId: String? = null

        override suspend fun applyCorrection(
            transactionId: String,
            categoryId: String?,
            moneyMovementType: MoneyMovementType?,
            saveMerchantDefault: Boolean,
            plannedUse: String?,
            updatePlannedUse: Boolean,
        ): TransactionCorrectionResult {
            lastCall = CorrectionCall(
                transactionId = transactionId,
                categoryId = categoryId,
                moneyMovementType = moneyMovementType,
                saveMerchantDefault = saveMerchantDefault,
                plannedUse = plannedUse,
                updatePlannedUse = updatePlannedUse,
            )
            return TransactionCorrectionResult.Applied
        }

        override suspend fun unlinkFlow(flowId: String): TransactionCorrectionResult {
            lastUnlinkFlowId = flowId
            return TransactionCorrectionResult.Applied
        }
    }

    private data class CorrectionCall(
        val transactionId: String,
        val categoryId: String?,
        val moneyMovementType: MoneyMovementType?,
        val saveMerchantDefault: Boolean,
        val plannedUse: String? = null,
        val updatePlannedUse: Boolean = false,
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

    private class RecordingNotificationDispatcher : NotificationDispatcher {
        val events = mutableListOf<NotificationEvent>()

        override suspend fun dispatch(event: NotificationEvent) {
            events += event
        }
    }
}
