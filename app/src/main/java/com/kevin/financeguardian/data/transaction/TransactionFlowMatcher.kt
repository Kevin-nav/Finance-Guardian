package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.ParsedTransactionEvent
import com.kevin.financeguardian.domain.parser.TransactionFlowClassifier
import java.time.Duration
import javax.inject.Inject

data class TransactionFlowMatch(
    val matched: Boolean,
    val score: Int,
    val reason: String,
)

class TransactionFlowMatcher @Inject constructor() {
    private val matchingWindow: Duration = TransactionFlowClassifier.MATCHING_WINDOW
    private val visibleAfter: Duration = TransactionFlowClassifier.VISIBLE_AFTER

    fun match(left: ParsedTransactionEvent, right: ParsedTransactionEvent): TransactionFlowMatch {
        val elapsed = Duration.between(left.occurredAt, right.occurredAt).abs()
        val exactReference = left.providerReference != null &&
            left.providerReference.equals(right.providerReference, ignoreCase = true)
        if (elapsed > matchingWindow && !exactReference) {
            return TransactionFlowMatch(false, 0, "outside matching window")
        }

        var score = 0
        if (left.amountMinor == right.amountMinor) score += 35
        if (left.directionFromProviderPerspective != right.directionFromProviderPerspective) score += 20
        if (compatibleChannels(left.channel, right.channel)) score += 20
        if (sharedInstrumentEvidence(left, right)) score += 20
        if (elapsed <= Duration.ofHours(1)) score += 10 else if (elapsed <= matchingWindow) score += 5
        if (exactReference) score += 15

        val matched = score >= 60
        return TransactionFlowMatch(matched, score, if (matched) "matched transfer flow" else "insufficient evidence")
    }

    fun visibleAsSingleFlow(firstEventReceivedAgo: Duration): Boolean =
        firstEventReceivedAgo >= visibleAfter

    fun matchingOpen(firstEventReceivedAgo: Duration): Boolean =
        firstEventReceivedAgo <= matchingWindow

    private fun compatibleChannels(left: MoneyMovementChannel, right: MoneyMovementChannel): Boolean {
        val pair = setOf(left, right)
        return pair == setOf(MoneyMovementChannel.WALLET_TO_WALLET) ||
            pair == setOf(MoneyMovementChannel.BANK_TO_WALLET, MoneyMovementChannel.WALLET_TO_BANK) ||
            pair == setOf(MoneyMovementChannel.CARD_TOP_UP) ||
            pair == setOf(MoneyMovementChannel.CARD_TOP_UP, MoneyMovementChannel.UNKNOWN)
    }

    private fun sharedInstrumentEvidence(left: ParsedTransactionEvent, right: ParsedTransactionEvent): Boolean {
        val leftIds = listOfNotNull(
            left.sourceInstrument?.identifier,
            left.destinationInstrument?.identifier,
            left.counterpartyPhone,
        ) + left.inferredIdentifiers
        val rightIds = listOfNotNull(
            right.sourceInstrument?.identifier,
            right.destinationInstrument?.identifier,
            right.counterpartyPhone,
        ) + right.inferredIdentifiers
        return leftIds.any { leftId ->
            rightIds.any { rightId ->
                leftId.equals(rightId, ignoreCase = true) ||
                    com.kevin.financeguardian.domain.parser.GhanaPhoneNumberNormalizer.sameNumber(leftId, rightId)
            }
        }
    }

    private fun Duration.abs(): Duration =
        if (isNegative) multipliedBy(-1) else this
}
