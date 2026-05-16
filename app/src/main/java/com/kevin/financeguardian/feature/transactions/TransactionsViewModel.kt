package com.kevin.financeguardian.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.domain.model.Category
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.OwnedInstrument
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.model.effectiveIsCredit
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
import com.kevin.financeguardian.ui.components.AccountingImpactUi
import com.kevin.financeguardian.ui.components.EvidenceEventUi
import com.kevin.financeguardian.ui.components.FlowStatusUi
import com.kevin.financeguardian.ui.components.FlowTypeUi
import com.kevin.financeguardian.ui.components.InstrumentUi
import com.kevin.financeguardian.ui.components.MatchingStateUi
import com.kevin.financeguardian.ui.components.OwnershipUi
import com.kevin.financeguardian.ui.components.TransactionFlowDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    private val userPreferencesRepository: UserPreferencesRepository? = null,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(TransactionFilter.All)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val permissionRefreshes = MutableStateFlow(0)
    private val transactionInputs = combine(
        transactionRepository.observeTransactions(),
        categoryDao.observeAll(),
        userPreferencesRepository?.preferences ?: flowOf(com.kevin.financeguardian.data.preferences.UserPreferences()),
    ) { transactions, categoryEntities, preferences ->
        TransactionUiInputs(transactions, categoryEntities, preferences.balancesVisible, preferences.ownedWallets)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionInputs,
        selectedFilter,
        selectedTransactionId,
        permissionRefreshes,
    ) { inputs, filter, selectedId, _ ->
        val categories = inputs.categoryEntities.map { it.toDomain() }
        buildUiState(
            transactions = inputs.transactions,
            categories = categories,
            selectedFilter = filter,
            selectedTransactionId = selectedId,
            receiveSmsGranted = permissionStatusChecker.currentStatuses().receiveSmsGranted,
            balancesVisible = inputs.balancesVisible,
            ownedWallets = inputs.ownedWallets,
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

    fun setBalancesVisible(visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository?.setBalancesVisible(visible)
        }
    }

    fun unlinkFlow() {
        val flowId = selectedTransactionId.value ?: return
        viewModelScope.launch {
            transactionCorrectionApplier.unlinkFlow(flowId)
            selectedTransactionId.value = null
        }
    }

    fun saveCorrection(
        selectedCategory: String,
        selectedType: String,
        plannedUse: String? = null,
        updatePlannedUse: Boolean = false,
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
                plannedUse = plannedUse,
                updatePlannedUse = updatePlannedUse,
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
        balancesVisible: Boolean,
        ownedWallets: List<OwnedInstrument>,
        now: Instant,
    ): TransactionsUiState {
        val categoryById = categories.associateBy { it.id }
        val categoryOptions = categories.map { TransactionCategoryOption(it.id, it.name) }
        val flowGroups = transactions.toTransactionFlowGroups()
        val items = flowGroups.map { flowGroup ->
            flowGroup.primary.toListItem(
                categoryById = categoryById,
                now = now,
                flowEventCount = flowGroup.transactions.size,
            )
        }
        val filteredItems = items.filter { it.matches(selectedFilter) }
        val selectedFlowGroup = flowGroups.firstOrNull { it.id == selectedTransactionId }
        val providerBalances = transactions.latestProviderBalances()

        return TransactionsUiState(
            filters = TransactionFilter.entries.toList(),
            selectedFilter = selectedFilter,
            groups = filteredItems.toGroups(),
            categoryOptions = categoryOptions,
            selectedFlow = selectedFlowGroup?.toFlowDetail(
                categoryById = categoryById,
                ownedWallets = ownedWallets,
                now = now,
            ),
            totalBalanceMinor = providerBalances.sumOf { it.balanceMinor },
            providerBalances = providerBalances,
            incomeMinor = transactions
                .filter { it.includedInIncomeTotals }
                .sumOf { it.amountMinor },
            expensesMinor = transactions
                .filter { it.includedInSpendingTotals }
                .sumOf { it.amountMinor },
            savingsMinor = transactions
                .filter { it.moneyMovementType == MoneyMovementType.SAVINGS_CONTRIBUTION }
                .sumOf { it.amountMinor },
            balancesVisible = balancesVisible,
            receiveSmsGranted = receiveSmsGranted,
            isEmpty = transactions.isEmpty(),
        )
    }

    private fun List<Transaction>.latestProviderBalances(): List<ProviderBalanceSnapshot> =
        asSequence()
            .filter {
                it.balanceAfterMinor != null &&
                    it.provider != Provider.UNKNOWN &&
                    it.balanceReliability != BalanceReliability.SUSPICIOUS
            }
            .groupBy { it.provider }
            .mapNotNull { (provider, providerTransactions) ->
                val latest = providerTransactions.maxByOrNull { it.occurredAt } ?: return@mapNotNull null
                ProviderBalanceSnapshot(
                    provider = provider.toDisplayName(),
                    balanceMinor = latest.balanceAfterMinor ?: return@mapNotNull null,
                    currency = latest.currency,
                )
            }
            .sortedWith(
                compareBy<ProviderBalanceSnapshot> { it.provider.providerSortOrder() }
                    .thenBy { it.provider },
            )

    private fun List<Transaction>.toTransactionFlowGroups(): List<TransactionFlowGroup> =
        groupBy { it.flowId ?: it.id }
            .values
            .map { flowTransactions ->
                val sortedTransactions = flowTransactions.sortedBy { it.occurredAt }
                val flowId = flowTransactions.firstNotNullOfOrNull { it.flowId } ?: flowTransactions.first().id
                val primary = if (flowTransactions.size == 1) {
                    flowTransactions.first()
                } else {
                    flowTransactions.toCollapsedInternalFlow()
                }
                TransactionFlowGroup(
                    id = flowId,
                    primary = primary,
                    transactions = sortedTransactions,
                )
            }
            .sortedByDescending { it.primary.occurredAt }

    private fun List<Transaction>.toCollapsedInternalFlow(): Transaction {
        val flowId = firstNotNullOfOrNull { it.flowId }
        val primary = firstOrNull { it.id == flowId } ?: maxBy { it.occurredAt }
        val debitProvider = firstOrNull { it.direction == com.kevin.financeguardian.domain.model.TransactionDirection.DEBIT }
            ?.provider
            ?.toDisplayName()
        val creditProvider = firstOrNull { it.direction == com.kevin.financeguardian.domain.model.TransactionDirection.CREDIT }
            ?.provider
            ?.toDisplayName()
        val displayName = when {
            debitProvider != null && creditProvider != null && debitProvider != creditProvider -> "$debitProvider -> $creditProvider"
            else -> "Internal transfer"
        }
        return primary.copy(
            amountMinor = maxOf { it.amountMinor },
            counterpartyName = displayName,
            balanceAfterMinor = null,
            categoryId = primary.categoryId ?: "transfers",
            moneyMovementType = MoneyMovementType.INTERNAL_TRANSFER,
            plannedUse = firstNotNullOfOrNull { it.plannedUse },
            includedInSpendingTotals = false,
            includedInIncomeTotals = false,
        )
    }

    private fun String.providerSortOrder(): Int =
        when (this) {
            "MTN MoMo" -> 0
            "Telecel Cash" -> 1
            "GCB Bank" -> 2
            "Unknown Bank" -> 3
            else -> 4
        }

    private fun Transaction.toListItem(
        categoryById: Map<String, Category>,
        now: Instant,
        flowEventCount: Int = 1,
    ): TransactionListItem {
        val categoryName = categoryId
            ?.let { categoryById[it]?.name }
            ?: if (moneyMovementType == MoneyMovementType.INTERNAL_TRANSFER) "Transfers" else null
            ?: "Unknown"
        val flowStatusUi = when {
            flowEventCount > 1 && moneyMovementType == MoneyMovementType.INTERNAL_TRANSFER -> FlowStatusUi.MATCHED
            else -> flowStatus.toFlowStatusUi()
        }
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
            plannedUse = plannedUse,
            includedInSpendingTotals = includedInSpendingTotals,
            flowStatus = flowStatusUi,
            flowType = moneyMovementType.toFlowTypeUi(flowType),
            flowEventCount = flowEventCount,
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
            TransactionFilter.Expenses -> includedInSpendingTotals && !isUnknownCategory
            TransactionFilter.Transfers -> movementType == MoneyMovementType.INTERNAL_TRANSFER
            TransactionFilter.Unknown -> isUnknownCategory || movementType == MoneyMovementType.UNKNOWN
        }

    private fun TransactionFlowGroup.toFlowDetail(
        categoryById: Map<String, Category>,
        ownedWallets: List<OwnedInstrument>,
        now: Instant,
    ): TransactionFlowDetail {
        val item = primary.toListItem(
            categoryById = categoryById,
            now = now,
            flowEventCount = transactions.size,
        )
        val effectiveFlowType = item.flowType ?: FlowTypeUi.UNKNOWN
        val effectiveFlowStatus = item.flowStatus ?: FlowStatusUi.COMPLETE

        val accountingImpact = when (effectiveFlowType) {
            FlowTypeUi.INTERNAL_TRANSFER -> AccountingImpactUi(
                label = "Excluded from spending and income",
                description = "This transfer moves money between your own accounts",
                isExcluded = true,
            )
            FlowTypeUi.EXPENSE, FlowTypeUi.CARD_SPEND -> AccountingImpactUi(
                label = "Counts as spending",
                description = "This flow is included in your expense totals",
                isExcluded = false,
            )
            FlowTypeUi.INCOME, FlowTypeUi.CASH_DEPOSIT -> AccountingImpactUi(
                label = "Counts as income",
                description = "This flow is included in your income totals",
                isExcluded = false,
            )
            else -> AccountingImpactUi(
                label = "Uncategorized",
                description = "Classify this flow to include it in your reports",
                isExcluded = false,
            )
        }

        val matchingState = when (effectiveFlowStatus) {
            FlowStatusUi.MATCHED -> MatchingStateUi(
                label = "Matched from 2 messages",
                detail = null,
                isWaiting = false,
            )
            FlowStatusUi.PENDING_MATCH -> MatchingStateUi(
                label = "Waiting for matching SMS",
                detail = "The app is still watching for a pair",
                isWaiting = true,
            )
            FlowStatusUi.UNMATCHED -> MatchingStateUi(
                label = "No matching SMS found",
                detail = null,
                isWaiting = false,
            )
            else -> null
        }

        return TransactionFlowDetail(
            flowId = id,
            title = item.merchantName,
            flowType = effectiveFlowType,
            flowStatus = effectiveFlowStatus,
            accountingImpact = accountingImpact,
            amountMinor = item.amountMinor,
            currency = item.currency,
            categoryName = item.categoryName,
            categoryId = item.categoryId,
            plannedUse = item.plannedUse,
            sourceInstrument = findSourceInstrument(ownedWallets, effectiveFlowType),
            destinationInstrument = findDestinationInstrument(ownedWallets, effectiveFlowType),
            events = transactions.map { it.toEvidenceEventUi() },
            matchingState = matchingState,
            dateGroup = item.dateGroup,
            timestamp = item.timestamp,
            flowEventCount = transactions.size,
            provider = item.provider,
            reference = item.reference,
            balanceAfterMinor = item.balanceAfterMinor,
        )
    }

    private fun TransactionFlowGroup.findSourceInstrument(
        ownedWallets: List<OwnedInstrument>,
        flowType: FlowTypeUi,
    ): InstrumentUi? {
        val debitSource = transactions
            .firstOrNull { it.direction == TransactionDirection.DEBIT }
            ?.toSourceInstrumentUi(ownedWallets, flowType)
        return debitSource ?: transactions.firstNotNullOfOrNull {
            it.toSourceInstrumentUi(ownedWallets, flowType)
        }
    }

    private fun TransactionFlowGroup.findDestinationInstrument(
        ownedWallets: List<OwnedInstrument>,
        flowType: FlowTypeUi,
    ): InstrumentUi? {
        val creditDestination = transactions
            .firstOrNull { it.direction == TransactionDirection.CREDIT }
            ?.toDestinationInstrumentUi(ownedWallets, flowType)
        return creditDestination ?: transactions.firstNotNullOfOrNull {
            it.toDestinationInstrumentUi(ownedWallets, flowType)
        }
    }

    private fun Transaction.toSourceInstrumentUi(
        ownedWallets: List<OwnedInstrument>,
        flowType: FlowTypeUi,
    ): InstrumentUi? =
        toInstrumentUi(
            type = eventSourceInstrumentType,
            provider = eventSourceInstrumentProvider,
            identifier = eventSourceInstrumentIdentifier,
            ownedWallets = ownedWallets,
            flowType = flowType,
        )

    private fun Transaction.toDestinationInstrumentUi(
        ownedWallets: List<OwnedInstrument>,
        flowType: FlowTypeUi,
    ): InstrumentUi? =
        toInstrumentUi(
            type = eventDestinationInstrumentType,
            provider = eventDestinationInstrumentProvider,
            identifier = eventDestinationInstrumentIdentifier,
            ownedWallets = ownedWallets,
            flowType = flowType,
        )

    private fun toInstrumentUi(
        type: InstrumentType?,
        provider: InstrumentProvider?,
        identifier: String?,
        ownedWallets: List<OwnedInstrument>,
        flowType: FlowTypeUi,
    ): InstrumentUi? {
        if (type == null && provider == null && identifier.isNullOrBlank()) return null
        val confirmedInstrument = ownedWallets.firstOrNull { owned ->
            owned.matchesIdentifier(identifier) &&
                (provider == null || owned.provider == provider || provider == InstrumentProvider.UNKNOWN)
        }
        val ownership = when {
            confirmedInstrument != null -> OwnershipUi.USER_CONFIRMED
            flowType == FlowTypeUi.INTERNAL_TRANSFER -> OwnershipUi.STRONGLY_INFERRED
            flowType == FlowTypeUi.EXPENSE && identifier != null -> OwnershipUi.EXTERNAL
            else -> OwnershipUi.UNKNOWN
        }
        return InstrumentUi(
            provider = provider.toDisplayName(type),
            userLabel = confirmedInstrument?.label,
            maskedIdentifier = identifier?.maskInstrumentIdentifier(),
            ownership = ownership,
        )
    }

    private fun Transaction.toEvidenceEventUi(): EvidenceEventUi =
        EvidenceEventUi(
            provider = provider.toDisplayName(),
            direction = direction.toDisplayName(),
            time = occurredAt.formatTime(),
            amountMinor = amountMinor,
            currency = currency,
            channel = eventChannel.toDisplayName(moneyMovementType),
            sourceIdentifier = eventSourceInstrumentIdentifier?.maskInstrumentIdentifier(),
            destinationIdentifier = eventDestinationInstrumentIdentifier?.maskInstrumentIdentifier(),
            reference = eventProviderReference ?: plannedUse ?: reference,
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

    private fun Provider.toDisplayName(): String =
        when (this) {
            Provider.MTN_MOMO -> "MTN MoMo"
            Provider.TELECEL_CASH -> "Telecel Cash"
            Provider.GCB -> "GCB Bank"
            Provider.UNKNOWN_BANK -> "Unknown Bank"
            Provider.UNKNOWN -> "Unknown"
        }

    private fun InstrumentProvider?.toDisplayName(type: InstrumentType?): String =
        when (this) {
            InstrumentProvider.MTN -> "MTN MoMo"
            InstrumentProvider.TELECEL -> "Telecel Cash"
            InstrumentProvider.GCB -> when (type) {
                InstrumentType.CARD -> "GCB Card"
                InstrumentType.BANK_ACCOUNT -> "GCB Account"
                else -> "GCB Bank"
            }
            InstrumentProvider.OTHER -> "Other"
            InstrumentProvider.UNKNOWN, null -> when (type) {
                InstrumentType.WALLET -> "Wallet"
                InstrumentType.BANK_ACCOUNT -> "Bank account"
                InstrumentType.CARD -> "Card"
                else -> "Unknown"
            }
        }

    private fun TransactionDirection.toDisplayName(): String =
        when (this) {
            TransactionDirection.DEBIT -> "Debit"
            TransactionDirection.CREDIT -> "Credit"
        }

    private fun MoneyMovementChannel?.toDisplayName(fallbackType: MoneyMovementType): String =
        when (this) {
            MoneyMovementChannel.MERCHANT_PAYMENT -> "Merchant payment"
            MoneyMovementChannel.WALLET_TO_WALLET -> "Wallet to wallet"
            MoneyMovementChannel.WALLET_TO_BANK -> "Wallet to bank"
            MoneyMovementChannel.BANK_TO_WALLET -> "Bank to wallet"
            MoneyMovementChannel.CARD_TOP_UP -> "Card top up"
            MoneyMovementChannel.CARD_SPEND -> "Card spend"
            MoneyMovementChannel.CASH_IN -> "Cash in"
            MoneyMovementChannel.CASH_DEPOSIT -> "Cash deposit"
            MoneyMovementChannel.AIRTIME_DATA -> "Airtime/data"
            MoneyMovementChannel.UNKNOWN, null -> fallbackType.toDisplayLabel()
        }

    private fun MoneyMovementType.toDisplayLabel(): String =
        when (this) {
            MoneyMovementType.EXPENSE -> "Expense"
            MoneyMovementType.INCOME -> "Income"
            MoneyMovementType.INTERNAL_TRANSFER -> "Internal transfer"
            MoneyMovementType.SAVINGS_CONTRIBUTION -> "Savings"
            MoneyMovementType.SUBSCRIPTION_CANDIDATE -> "Subscription"
            MoneyMovementType.UNKNOWN -> "Unknown"
        }

    private fun String.maskInstrumentIdentifier(): String {
        val compact = filter { it.isLetterOrDigit() }
        if (compact.startsWith("233") && compact.length == 12) {
            val local = "0${compact.drop(3)}"
            return "${local.take(3)} *** ${local.takeLast(4)}"
        }
        if (compact.length == 10 && compact.startsWith("0")) {
            return "${compact.take(3)} *** ${compact.takeLast(4)}"
        }
        return when {
            length <= 4 -> this
            length <= 8 -> "***${takeLast(4)}"
            else -> "${take(4)}...${takeLast(4)}"
        }
    }

    private fun String.toMoneyMovementType(): MoneyMovementType? =
        when (this) {
            "Expense" -> MoneyMovementType.EXPENSE
            "Income" -> MoneyMovementType.INCOME
            "Internal transfer", "Internal Transfer" -> MoneyMovementType.INTERNAL_TRANSFER
            "Cash deposit" -> MoneyMovementType.INCOME
            "Card spend" -> MoneyMovementType.EXPENSE
            "Savings" -> MoneyMovementType.SAVINGS_CONTRIBUTION
            "Ignore", "Unknown" -> MoneyMovementType.UNKNOWN
            else -> null
        }

    private fun TransactionFlowStatus?.toFlowStatusUi(): FlowStatusUi? =
        when (this) {
            TransactionFlowStatus.COMPLETE -> FlowStatusUi.COMPLETE
            TransactionFlowStatus.PENDING_MATCH -> FlowStatusUi.PENDING_MATCH
            TransactionFlowStatus.UNMATCHED -> FlowStatusUi.UNMATCHED
            TransactionFlowStatus.NEEDS_REVIEW -> FlowStatusUi.NEEDS_REVIEW
            null -> null
        }

    private fun MoneyMovementType.toFlowTypeUi(
        flowType: TransactionFlowType? = null,
    ): FlowTypeUi = when (this) {
        MoneyMovementType.EXPENSE -> if (flowType == TransactionFlowType.CARD_SPEND) {
            FlowTypeUi.CARD_SPEND
        } else {
            FlowTypeUi.EXPENSE
        }
        MoneyMovementType.INCOME -> if (flowType == TransactionFlowType.CASH_DEPOSIT) {
            FlowTypeUi.CASH_DEPOSIT
        } else {
            FlowTypeUi.INCOME
        }
        MoneyMovementType.INTERNAL_TRANSFER -> FlowTypeUi.INTERNAL_TRANSFER
        MoneyMovementType.SAVINGS_CONTRIBUTION -> FlowTypeUi.INCOME
        MoneyMovementType.SUBSCRIPTION_CANDIDATE -> FlowTypeUi.EXPENSE
        MoneyMovementType.UNKNOWN -> FlowTypeUi.UNKNOWN
    }
}

data class TransactionsUiState(
    val filters: List<TransactionFilter> = TransactionFilter.entries.toList(),
    val selectedFilter: TransactionFilter = TransactionFilter.All,
    val groups: List<TransactionGroup> = emptyList(),
    val categoryOptions: List<TransactionCategoryOption> = emptyList(),
    val selectedFlow: TransactionFlowDetail? = null,
    val totalBalanceMinor: Long = 0L,
    val providerBalances: List<ProviderBalanceSnapshot> = emptyList(),
    val incomeMinor: Long = 0L,
    val expensesMinor: Long = 0L,
    val savingsMinor: Long = 0L,
    val balancesVisible: Boolean = true,
    val receiveSmsGranted: Boolean = false,
    val isEmpty: Boolean = true,
)

private data class TransactionUiInputs(
    val transactions: List<Transaction>,
    val categoryEntities: List<com.kevin.financeguardian.data.local.entity.CategoryEntity>,
    val balancesVisible: Boolean,
    val ownedWallets: List<OwnedInstrument>,
)

private data class TransactionFlowGroup(
    val id: String,
    val primary: Transaction,
    val transactions: List<Transaction>,
)

data class ProviderBalanceSnapshot(
    val provider: String,
    val balanceMinor: Long,
    val currency: String,
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
    val plannedUse: String?,
    val includedInSpendingTotals: Boolean = movementType != MoneyMovementType.INTERNAL_TRANSFER && !isCredit,
    val flowStatus: FlowStatusUi? = null,
    val flowType: FlowTypeUi? = null,
    val flowEventCount: Int = 1,
    val sourceProvider: String? = null,
    val destinationProvider: String? = null,
) {
    val isUnknownCategory: Boolean =
        categoryName.equals("Unknown", ignoreCase = true) || categoryId == null
    val isInternalTransfer: Boolean =
        movementType == MoneyMovementType.INTERNAL_TRANSFER
}

data class TransactionCategoryOption(
    val id: String,
    val name: String,
)
