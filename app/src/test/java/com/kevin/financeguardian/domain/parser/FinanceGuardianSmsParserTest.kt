package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.Instant
import org.junit.Test

class FinanceGuardianSmsParserTest {
    private val parser = FinanceGuardianSmsParser()
    private val receivedAt = Instant.parse("2026-04-21T18:00:00Z")

    @Test
    fun dispatchesMtnTelecelAndGcbMessages() {
        assertEquals(
            Provider.MTN_MOMO,
            parse("MTN MoMo", "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.").parsed().transaction.provider,
        )
        assertEquals(
            Provider.TELECEL_CASH,
            parse("Telecel", "000001 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Your Telecel Cash balance is GHS0.62.").parsed().transaction.provider,
        )
        assertEquals(
            Provider.GCB,
            parse("GCB", "Hi Customer Your A/C No:XXXX0000 has been debited GHS25.83 Desc: Spotify Stockholm Date: 2026-03-30 06:43 Bal: GHS 12.61").parsed().transaction.provider,
        )
    }

    @Test
    fun ignoresFailedAndPromotionalMessages() {
        assertTrue(parse("Telecel", "Failed. Your Transfer of GHS30.00 sent to 0240000000 SAMPLE NAME was unsuccessful.") is SmsParseResult.Ignored)
        assertTrue(parse("GCB", "Dear Valued Customer, GCB Bank will NEVER call to request your PIN.") is SmsParseResult.Ignored)
    }

    @Test
    fun genericParserHandlesUnknownFinancialTextWithLowConfidence() {
        val parsed = parse("Unknown", "Alert: Your wallet was debited GHS42.50 for a sample service.").parsed()

        assertEquals(Provider.UNKNOWN, parsed.transaction.provider)
        assertEquals(MoneyMovementType.UNKNOWN, parsed.transaction.moneyMovementType)
        assertEquals(4250, parsed.transaction.amountMinor)
        assertEquals(0.45f, parsed.confidence)
    }

    @Test
    fun unknownNonFinancialMessageIsIgnored() {
        assertTrue(parse("Unknown", "Your OTP is 123456. Do not share it.") is SmsParseResult.Ignored)
    }

    private fun parse(sender: String, body: String): SmsParseResult =
        parser.parse(SmsParseInput(sender, body, receivedAt))

    private fun SmsParseResult.parsed(): SmsParseResult.Parsed = this as SmsParseResult.Parsed
}
