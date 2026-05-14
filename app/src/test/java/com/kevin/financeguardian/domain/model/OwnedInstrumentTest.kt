package com.kevin.financeguardian.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedInstrumentTest {
    @Test
    fun walletCarriesProviderLabelAndCanonicalPhone() {
        val wallet = OwnedInstrument(
            id = "mtn-1",
            label = "My MTN",
            type = InstrumentType.WALLET,
            provider = InstrumentProvider.MTN,
            identifier = "233549037907",
        )

        assertEquals("My MTN", wallet.label)
        assertEquals(InstrumentProvider.MTN, wallet.provider)
        assertTrue(wallet.matchesIdentifier("0549037907"))
    }

    @Test
    fun userMayOwnAnySubsetOfProviders() {
        val wallets = listOf(
            OwnedInstrument("telecel-1", "Telecel", InstrumentType.WALLET, InstrumentProvider.TELECEL, "233505600861"),
        )

        assertEquals(1, wallets.size)
        assertEquals(InstrumentProvider.TELECEL, wallets.single().provider)
    }

    @Test
    fun inferredAndConfirmedInstrumentsAreDistinct() {
        val inferred = OwnedInstrument(
            id = "card",
            label = "Virtual card",
            type = InstrumentType.CARD,
            provider = InstrumentProvider.GCB,
            identifier = "LZDXAGEE 902125000",
            origin = InstrumentOrigin.SYSTEM_INFERRED,
        )

        assertEquals(InstrumentOrigin.SYSTEM_INFERRED, inferred.origin)
    }
}
