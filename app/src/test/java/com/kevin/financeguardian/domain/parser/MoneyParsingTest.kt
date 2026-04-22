package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun findAmountMinorPrefersCurrencyBoundAmountsOverOtherNumbers() {
        assertEquals(
            4250L,
            findAmountMinor("Transaction ID: 123456. Your wallet was debited GHS42.50 for service."),
        )
    }

    @Test
    fun parsedResultNormalizesBlankTextFieldsToNull() {
        val parsed = parsedResult(
            provider = Provider.UNKNOWN,
            input = SmsParseInput("sender", "body", Instant.parse("2026-04-21T18:00:00Z")),
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.UNKNOWN,
            amountMinor = 100,
            counterpartyName = " . , ",
            counterpartyPhone = "   ",
            reference = "-",
            confidence = 0.45f,
        ) as SmsParseResult.Parsed

        assertNull(parsed.transaction.counterpartyName)
        assertNull(parsed.transaction.counterpartyPhone)
        assertNull(parsed.transaction.reference)
    }
}
