package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.InstrumentOrigin
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.OwnedInstrument
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Duration
import javax.inject.Inject

enum class TransactionFlowType {
    EXPENSE,
    INCOME,
    INTERNAL_TRANSFER,
    CASH_DEPOSIT,
    CARD_SPEND,
    UNKNOWN,
}

enum class TransactionFlowStatus {
    COMPLETE,
    PENDING_MATCH,
    UNMATCHED,
    NEEDS_REVIEW,
}

data class TransactionFlowDraft(
    val flowType: TransactionFlowType,
    val status: TransactionFlowStatus,
    val amountMinor: Long,
    val currency: String,
    val plannedUse: String?,
    val moneyMovementType: MoneyMovementType,
    val includedInSpendingTotals: Boolean,
    val includedInIncomeTotals: Boolean,
    val confidence: Float,
    val matchingWindowHours: Long = TransactionFlowClassifier.MATCHING_WINDOW.toHours(),
    val visibleAfterHours: Long = TransactionFlowClassifier.VISIBLE_AFTER.toHours(),
)

class TransactionFlowClassifier @Inject constructor() {
    fun classify(
        event: ParsedTransactionEvent,
        userConfirmedInstruments: List<OwnedInstrument> = emptyList(),
        systemInferredInstruments: List<OwnedInstrument> = emptyList(),
    ): TransactionFlowDraft {
        val instruments = userConfirmedInstruments + systemInferredInstruments
        val hasOwnershipContext = instruments.isNotEmpty()
        val sourceOwned = event.sourceInstrument?.isOwnedBy(instruments) == true
        val destinationOwned = event.destinationInstrument?.isOwnedBy(instruments) == true
        val hasUserConfirmedProof = listOfNotNull(event.sourceInstrument, event.destinationInstrument).any { parsed ->
            userConfirmedInstruments.any { owned -> parsed.matches(owned) }
        }
        val hasStrongSystemProof = listOfNotNull(event.sourceInstrument, event.destinationInstrument).any { parsed ->
            systemInferredInstruments.any { owned -> owned.origin == InstrumentOrigin.SYSTEM_INFERRED && parsed.matches(owned) }
        }

        val flowType = when {
            event.channel == MoneyMovementChannel.CASH_IN ||
                event.channel == MoneyMovementChannel.CASH_DEPOSIT -> TransactionFlowType.CASH_DEPOSIT
            event.channel == MoneyMovementChannel.CARD_SPEND -> TransactionFlowType.CARD_SPEND
            (sourceOwned && destinationOwned) || ownSideProvesInternal(event, sourceOwned, destinationOwned) ->
                TransactionFlowType.INTERNAL_TRANSFER
            event.directionFromProviderPerspective == TransactionDirection.CREDIT -> TransactionFlowType.INCOME
            event.directionFromProviderPerspective == TransactionDirection.DEBIT -> TransactionFlowType.EXPENSE
            else -> TransactionFlowType.UNKNOWN
        }

        val status = when {
            flowType == TransactionFlowType.INTERNAL_TRANSFER && event.isPairableTransfer() -> TransactionFlowStatus.PENDING_MATCH
            flowType == TransactionFlowType.UNKNOWN -> TransactionFlowStatus.NEEDS_REVIEW
            !hasOwnershipContext && !hasUserConfirmedProof && !hasStrongSystemProof && event.channel in reviewChannels ->
                TransactionFlowStatus.NEEDS_REVIEW
            else -> TransactionFlowStatus.COMPLETE
        }

        return TransactionFlowDraft(
            flowType = flowType,
            status = status,
            amountMinor = event.amountMinor,
            currency = event.currency,
            plannedUse = event.plannedUse,
            moneyMovementType = flowType.toMoneyMovementType(event),
            includedInSpendingTotals = flowType == TransactionFlowType.EXPENSE || flowType == TransactionFlowType.CARD_SPEND,
            includedInIncomeTotals = flowType == TransactionFlowType.INCOME,
            confidence = when {
                flowType == TransactionFlowType.INTERNAL_TRANSFER && hasUserConfirmedProof -> 0.96f
                flowType == TransactionFlowType.INTERNAL_TRANSFER && hasStrongSystemProof -> 0.88f
                status == TransactionFlowStatus.NEEDS_REVIEW -> 0.55f
                else -> event.confidence
            },
        )
    }

    private fun ownSideProvesInternal(
        event: ParsedTransactionEvent,
        sourceOwned: Boolean,
        destinationOwned: Boolean,
    ): Boolean =
        when (event.channel) {
            MoneyMovementChannel.BANK_TO_WALLET -> destinationOwned
            MoneyMovementChannel.WALLET_TO_BANK -> sourceOwned
            MoneyMovementChannel.CARD_TOP_UP -> sourceOwned || destinationOwned
            MoneyMovementChannel.WALLET_TO_WALLET -> when (event.directionFromProviderPerspective) {
                TransactionDirection.DEBIT -> destinationOwned || (sourceOwned && destinationOwned)
                TransactionDirection.CREDIT -> sourceOwned && destinationOwned
            }
            else -> false
        }

    private fun ParsedTransactionEvent.isPairableTransfer(): Boolean =
        channel in setOf(
            MoneyMovementChannel.BANK_TO_WALLET,
            MoneyMovementChannel.WALLET_TO_BANK,
            MoneyMovementChannel.WALLET_TO_WALLET,
            MoneyMovementChannel.CARD_TOP_UP,
        )

    private fun ParsedInstrument.isOwnedBy(instruments: List<OwnedInstrument>): Boolean =
        instruments.any { matches(it) }

    private fun ParsedInstrument.matches(owned: OwnedInstrument): Boolean {
        if (owned.type != type && owned.type != InstrumentType.UNKNOWN && type != InstrumentType.UNKNOWN) return false
        return owned.matchesIdentifier(identifier)
    }

    private fun TransactionFlowType.toMoneyMovementType(event: ParsedTransactionEvent): MoneyMovementType =
        when (this) {
            TransactionFlowType.EXPENSE,
            TransactionFlowType.CARD_SPEND,
            -> MoneyMovementType.EXPENSE
            TransactionFlowType.INCOME,
            TransactionFlowType.CASH_DEPOSIT,
            -> MoneyMovementType.INCOME
            TransactionFlowType.INTERNAL_TRANSFER -> MoneyMovementType.INTERNAL_TRANSFER
            TransactionFlowType.UNKNOWN -> event.directionFromProviderPerspective.let {
                if (it == TransactionDirection.CREDIT) MoneyMovementType.INCOME else MoneyMovementType.EXPENSE
            }
        }

    companion object {
        val MATCHING_WINDOW: Duration = Duration.ofHours(10)
        val VISIBLE_AFTER: Duration = Duration.ofHours(1)
        private val reviewChannels = setOf(
            MoneyMovementChannel.BANK_TO_WALLET,
            MoneyMovementChannel.WALLET_TO_BANK,
            MoneyMovementChannel.CARD_TOP_UP,
        )
    }
}
