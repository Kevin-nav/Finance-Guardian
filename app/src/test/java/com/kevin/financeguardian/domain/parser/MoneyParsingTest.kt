package com.kevin.financeguardian.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParsingTest {
    @Test
    fun parsesGhanaCediFormatsToMinorUnits() {
        assertEquals(900L, parseAmountMinor("GHS 9.00"))
        assertEquals(5865L, parseAmountMinor("GHS58.65"))
        assertEquals(100648L, parseAmountMinor("GHS1,006.48"))
        assertEquals(1200L, parseAmountMinor("12 GHANA CEDIS"))
        assertEquals(1200L, parseAmountMinor("12 GHS"))
    }
}
