package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ParsedTransactionEventTest {
    private val now = Instant.parse("2026-04-03T12:57:00Z")

    @Test
    fun representsGcbBankToWalletWithPlannedUse() {
        val event = ParsedTransactionEvent(
            provider = Provider.GCB,
            occurredAt = now,
            amountMinor = 6000,
            directionFromProviderPerspective = TransactionDirection.DEBIT,
            channel = MoneyMovementChannel.BANK_TO_WALLET,
            destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907"),
            plannedUse = "food",
            confidence = 0.94f,
        )

        assertEquals(MoneyMovementChannel.BANK_TO_WALLET, event.channel)
        assertEquals("233549037907", event.destinationInstrument?.identifier)
        assertEquals("food", event.plannedUse)
    }

    @Test
    fun representsTelecelToMtnWalletTransfer() {
        val event = ParsedTransactionEvent(
            provider = Provider.TELECEL_CASH,
            occurredAt = now,
            amountMinor = 2000,
            directionFromProviderPerspective = TransactionDirection.DEBIT,
            channel = MoneyMovementChannel.WALLET_TO_WALLET,
            destinationInstrument = ParsedInstrument(InstrumentType.WALLET, InstrumentProvider.MTN, "233549037907"),
            confidence = 0.93f,
        )

        assertEquals(InstrumentProvider.MTN, event.destinationInstrument?.provider)
    }

    @Test
    fun representsGcbCardTopUpToken() {
        val event = ParsedTransactionEvent(
            provider = Provider.GCB,
            occurredAt = now,
            amountMinor = 1200,
            directionFromProviderPerspective = TransactionDirection.DEBIT,
            channel = MoneyMovementChannel.CARD_TOP_UP,
            destinationInstrument = ParsedInstrument(InstrumentType.CARD, InstrumentProvider.GCB, "LZDXAGEE 902125000"),
            confidence = 0.94f,
        )

        assertEquals("LZDXAGEE 902125000", event.destinationInstrument?.identifier)
    }
}
