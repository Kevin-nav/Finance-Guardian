package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.OwnedInstrument
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFlowClassifierTest {
    private val classifier = TransactionFlowClassifier()
    private val ownedMtn = OwnedInstrument("mtn", "MTN", InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907")

    @Test
    fun ownedBankToWalletIsInternalWithPlannedUseExcludedFromTotals() {
        val flow = classifier.classify(bankToWallet("233549037907", "food"), listOf(ownedMtn))

        assertEquals(TransactionFlowType.INTERNAL_TRANSFER, flow.flowType)
        assertEquals("food", flow.plannedUse)
        assertFalse(flow.includedInSpendingTotals)
        assertFalse(flow.includedInIncomeTotals)
    }

    @Test
    fun externalBankToWalletIsExpenseWithPlannedUse() {
        val flow = classifier.classify(bankToWallet("233596447662", "laundry"), listOf(ownedMtn))

        assertEquals(TransactionFlowType.EXPENSE, flow.flowType)
        assertEquals(MoneyMovementType.EXPENSE, flow.moneyMovementType)
        assertEquals("laundry", flow.plannedUse)
        assertTrue(flow.includedInSpendingTotals)
    }

    @Test
    fun cashInIsSeparateFromOrdinaryIncome() {
        val flow = classifier.classify(
            ParsedTransactionEvent(
                provider = Provider.MTN_MOMO,
                occurredAt = Instant.parse("2026-04-03T12:57:00Z"),
                amountMinor = 10000,
                directionFromProviderPerspective = TransactionDirection.CREDIT,
                channel = MoneyMovementChannel.CASH_IN,
                confidence = 0.9f,
            ),
        )

        assertEquals(TransactionFlowType.CASH_DEPOSIT, flow.flowType)
    }

    @Test
    fun telecelDebitToOwnedMtnIsPendingInternalCandidate() {
        val flow = classifier.classify(
            ParsedTransactionEvent(
                provider = Provider.TELECEL_CASH,
                occurredAt = Instant.parse("2026-04-03T12:57:00Z"),
                amountMinor = 2000,
                directionFromProviderPerspective = TransactionDirection.DEBIT,
                channel = MoneyMovementChannel.WALLET_TO_WALLET,
                destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907"),
                plannedUse = "Data",
                confidence = 0.93f,
            ),
            listOf(ownedMtn),
        )

        assertEquals(TransactionFlowType.INTERNAL_TRANSFER, flow.flowType)
        assertEquals(TransactionFlowStatus.PENDING_MATCH, flow.status)
        assertFalse(flow.includedInSpendingTotals)
    }

    private fun bankToWallet(phone: String, plannedUse: String) = ParsedTransactionEvent(
        provider = Provider.GCB,
        occurredAt = Instant.parse("2026-04-03T12:57:00Z"),
        amountMinor = 6000,
        directionFromProviderPerspective = TransactionDirection.DEBIT,
        channel = MoneyMovementChannel.BANK_TO_WALLET,
        destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, phone),
        plannedUse = plannedUse,
        confidence = 0.94f,
    )
}
