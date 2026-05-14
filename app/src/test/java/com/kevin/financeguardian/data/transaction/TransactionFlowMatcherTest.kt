package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.ParsedInstrument
import com.kevin.financeguardian.domain.parser.ParsedTransactionEvent
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFlowMatcherTest {
    private val matcher = TransactionFlowMatcher()
    private val now = Instant.parse("2026-04-03T12:57:00Z")

    @Test
    fun matchesWalletTransferWithinTenHours() {
        assertTrue(matcher.match(telecelDebit(now), mtnCredit(now.plusHours(9))).matched)
    }

    @Test
    fun firstSideBecomesVisibleAfterOneHourAndWindowStaysOpenForTen() {
        assertFalse(matcher.visibleAsSingleFlow(Duration.ofMinutes(59)))
        assertTrue(matcher.visibleAsSingleFlow(Duration.ofHours(1)))
        assertTrue(matcher.matchingOpen(Duration.ofHours(10)))
        assertFalse(matcher.matchingOpen(Duration.ofHours(11)))
    }

    @Test
    fun doesNotMatchBeyondTenHoursWithoutExactReference() {
        assertFalse(matcher.match(telecelDebit(now), mtnCredit(now.plusHours(11))).matched)
    }

    private fun telecelDebit(at: Instant) = ParsedTransactionEvent(
        provider = Provider.TELECEL_CASH,
        occurredAt = at,
        amountMinor = 2000,
        directionFromProviderPerspective = TransactionDirection.DEBIT,
        channel = MoneyMovementChannel.WALLET_TO_WALLET,
        destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907"),
        counterpartyPhone = "233549037907",
        providerReference = "Data",
        confidence = 0.93f,
    )

    private fun mtnCredit(at: Instant) = ParsedTransactionEvent(
        provider = Provider.MTN_MOMO,
        occurredAt = at,
        amountMinor = 2000,
        directionFromProviderPerspective = TransactionDirection.CREDIT,
        channel = MoneyMovementChannel.WALLET_TO_WALLET,
        destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907"),
        counterpartyPhone = "233549037907",
        providerReference = "Other",
        confidence = 0.9f,
    )
}
