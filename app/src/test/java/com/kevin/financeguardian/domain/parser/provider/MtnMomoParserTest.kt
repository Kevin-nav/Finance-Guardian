package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.normalizeWhitespace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MtnMomoParserTest {
    private val parser = MtnMomoParser()
    private val receivedAt = Instant.parse("2026-04-21T18:00:00Z")

    @Test
    fun parsesCompletedMerchantPayment() {
        val result = parse(
            "Your payment of GHS 18.50 to SAMPLE ENTERPRISE has been completed at 2026-04-21 15:55:04. Reference: snacks. Your new balance: GHS 449.61. Fee was GHS 0.50 Tax charged: GHS0. Financial Transaction Id: 123. External Transaction Id: -.",
        )

        val parsed = result.parsed()
        assertEquals(Provider.MTN_MOMO, parsed.transaction.provider)
        assertEquals(TransactionDirection.DEBIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.EXPENSE, parsed.transaction.moneyMovementType)
        assertEquals(1850, parsed.transaction.amountMinor)
        assertEquals("SAMPLE ENTERPRISE", parsed.transaction.counterpartyName)
        assertEquals("snacks", parsed.transaction.reference)
        assertEquals("123", parsed.transaction.providerTransactionId)
        assertEquals(44961L, parsed.transaction.balanceAfterMinor)
        assertEquals(Instant.parse("2026-04-21T15:55:04Z"), parsed.transaction.occurredAt)
        assertEquals(MoneyMovementChannel.MERCHANT_PAYMENT, parsed.transaction.event?.channel)
        assertTrue(parsed.confidence >= 0.85f)
    }

    @Test
    fun parsesPaymentMadeMessage() {
        val parsed = parse(
            "Payment made for GHS 30.00 to SAMPLE PERSON. Current Balance: GHS 468.61. Available Balance: GHS 468.61. Reference: support. Transaction ID: 123. Fee charged: GHS 0.00 TAX charged: GHS 0.00.",
        ).parsed()

        assertEquals(3000, parsed.transaction.amountMinor)
        assertEquals("SAMPLE PERSON", parsed.transaction.counterpartyName)
        assertEquals("support", parsed.transaction.reference)
        assertEquals(46861L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun parsesPaymentForMessage() {
        val parsed = parse(
            "Payment for GHS58.65 to Paystack Ghana Limited .Current Balance: GHS 381.96. Transaction Id: 123. Fee charged: GHS0.00,Tax Charged 0.",
        ).parsed()

        assertEquals(5865, parsed.transaction.amountMinor)
        assertEquals("Paystack Ghana Limited", parsed.transaction.counterpartyName)
        assertEquals(38196L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun parsesPaymentForMessageWithMerchantIdAsDetails() {
        val parsed = parse(
            "Payment for GHS50.50 to Bills.INV ..Current Balance: GHS 113.41. Transaction Id: 79891168722. Fee charged: GHS0.00,Tax Charged 0.",
        ).parsed()

        assertEquals(TransactionDirection.DEBIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.EXPENSE, parsed.transaction.moneyMovementType)
        assertEquals(5050, parsed.transaction.amountMinor)
        assertEquals("Bills", parsed.transaction.counterpartyName)
        assertEquals("Merchant ID: INV", parsed.transaction.reference)
        assertEquals("79891168722", parsed.transaction.providerTransactionId)
        assertEquals(11341L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun parsesYelloMerchantMessage() {
        val parsed = parse(
            "Y'ello. You have Paid GHS 18.5 to Merchant 004501 on your mobile money account at 202621155504. Message from sender: snacks. Your new balance: GHS 449.61 . Fee was GHS 0.50 . Financial Transaction Id: 123.",
        ).parsed()

        assertEquals(1850, parsed.transaction.amountMinor)
        assertEquals("Merchant 004501", parsed.transaction.counterpartyName)
        assertEquals("snacks", parsed.transaction.reference)
        assertEquals(Instant.parse("2026-04-21T15:55:04Z"), parsed.transaction.occurredAt)
    }

    @Test
    fun parsesIncomingPayment() {
        val parsed = parse(
            "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01 . Available Balance: GHS 538.01. Reference: rent. Transaction ID: 123. TRANSACTION FEE: 0.00",
        ).parsed()

        assertEquals(TransactionDirection.CREDIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.INCOME, parsed.transaction.moneyMovementType)
        assertEquals(7700, parsed.transaction.amountMinor)
        assertEquals("SAMPLE SENDER", parsed.transaction.counterpartyName)
        assertEquals(53801L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun extractsCashInAndAirtimeChannels() {
        val cashIn = parse(
            "Cash In received for GHS 100.00 from SAMPLE AGENT. Current Balance: GHS 538.01. Transaction ID: 123.",
        ).parsed()
        val airtime = parse(
            "Your payment of GHS 9.00 to MTN AIRTIME has been completed at 2026-04-21 17:35:03. Your new balance: GHS 372.96. Fee was GHS 0.00 Tax was GHS -. Reference: -. Financial Transaction Id: 79737177545.",
        ).parsed()

        assertEquals(MoneyMovementChannel.CASH_IN, cashIn.transaction.event?.channel)
        assertEquals(MoneyMovementChannel.AIRTIME_DATA, airtime.transaction.event?.channel)
    }

    private fun parse(body: String): SmsParseResult? =
        parser.parse(SmsParseInput("MTN MoMo", body, receivedAt), normalizeWhitespace(body))

    private fun SmsParseResult?.parsed(): SmsParseResult.Parsed = this as SmsParseResult.Parsed
}
