package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
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
            parse("MobileMoney", "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.").parsed().transaction.provider,
        )
        assertEquals(
            Provider.TELECEL_CASH,
            parse("T-CASH", "000001 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Your Telecel Cash balance is GHS0.62.").parsed().transaction.provider,
        )
        assertEquals(
            Provider.GCB,
            parse("GCB Bank", "Hi Customer Your A/C No:XXXX0000 has been debited GHS25.83 Desc: Spotify Stockholm Date: 2026-03-30 06:43 Bal: GHS 12.61").parsed().transaction.provider,
        )
    }

    @Test
    fun ignoresFailedAndPromotionalMessages() {
        assertTrue(parse("Telecel", "Failed. Your Transfer of GHS30.00 sent to 0240000000 SAMPLE NAME was unsuccessful.") is SmsParseResult.Ignored)
        assertTrue(parse("GCB Bank", "Dear Valued Customer, GCB Bank will NEVER call to request your PIN.") is SmsParseResult.Ignored)
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
    fun ignoresMerchantReceiptConfirmationFromUnknownSender() {
        val result = parse(
            sender = "Seritex",
            body = "Hi KEVIN, Your payment of GHS 50.5 has been received. Thank you. View full receipt here: https://r.hbtl.co/6bce074827ad4c29a371184aabc9f989.",
        )

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun genericParserDoesNotTreatMerchantReceiptReceivedTextAsCredit() {
        val result = parse(
            sender = "Merchant",
            body = "Thank you, payment of GHS 50.50 has been received. Receipt: https://example.com/r/123",
        )

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun genericParserCanParseUnknownSenderWithTransactionEvidence() {
        val parsed = parse(
            sender = "Unknown",
            body = "Alert: You received GHS 25.00 from SAMPLE CUSTOMER. Balance: GHS 100.00.",
        ).parsed()

        assertEquals(TransactionDirection.CREDIT, parsed.transaction.direction)
        assertEquals(2500, parsed.transaction.amountMinor)
    }

    @Test
    fun unknownNonFinancialMessageIsIgnored() {
        assertTrue(parse("Unknown", "Your OTP is 123456. Do not share it.") is SmsParseResult.Ignored)
    }

    private fun parse(sender: String, body: String): SmsParseResult =
        parser.parse(SmsParseInput(sender, body, receivedAt))

    private fun SmsParseResult.parsed(): SmsParseResult.Parsed = this as SmsParseResult.Parsed
}
