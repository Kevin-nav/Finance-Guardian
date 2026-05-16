package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.FinanceGuardianSmsParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.normalizeWhitespace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TelecelCashParserTest {
    private val parser = TelecelCashParser()
    private val registry = FinanceGuardianSmsParser()
    private val receivedAt = Instant.parse("2026-04-13T20:00:00Z")

    @Test
    fun parsesSuccessfulSendToMtn() {
        val parsed = parse(
            "000001 Confirmed. GHS17.00 sent to 0240000000 SAMPLE RECIPIENT on MTN MOBILE MONEY on 2026-04-12 at 21:03:42. Your Telecel Cash balance is GHS76.39. You were charged GHS0.09. Your E-levy charge is GHS0.00. Reference: snacks. Sendi k3k3!",
        ).parsed()

        assertEquals(TransactionDirection.DEBIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.EXPENSE, parsed.transaction.moneyMovementType)
        assertEquals(1700, parsed.transaction.amountMinor)
        assertEquals("SAMPLE RECIPIENT", parsed.transaction.counterpartyName)
        assertEquals("233240000000", parsed.transaction.counterpartyPhone)
        assertEquals("snacks", parsed.transaction.reference)
        assertEquals("000001", parsed.transaction.providerTransactionId)
        assertEquals(7639L, parsed.transaction.balanceAfterMinor)
        assertEquals(MoneyMovementChannel.WALLET_TO_WALLET, parsed.transaction.event?.channel)
        assertEquals("snacks", parsed.transaction.event?.plannedUse)
    }

    @Test
    fun parsesMerchantPayment() {
        val parsed = parse(
            "000002 Confirmed. GHS276.00 paid to 125012 - PAYSTACK II on 2026-03-27 at 13:57:04. Your new Telecel Cash balance is GHS93.48. You were charged GHS0.00. Your E-levy charge is GHS0.00. Reference: electronics. Sendi k3k3!",
        ).parsed()

        assertEquals(27600, parsed.transaction.amountMinor)
        assertEquals("125012 - PAYSTACK II", parsed.transaction.counterpartyName)
        assertEquals(9348L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun parsesIncomingTransfer() {
        val parsed = parse(
            "000003 Confirmed. You have received GHS252.00 from MTN MOBILE MONEY with transaction reference: Transfer From: 233000000000-SAMPLE SENDER on 2026-03-18 at 12:02:34. Your Telecel Cash balance is GHS1,006.48. Ref: support",
        ).parsed()

        assertEquals(TransactionDirection.CREDIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.INCOME, parsed.transaction.moneyMovementType)
        assertEquals(25200, parsed.transaction.amountMinor)
        assertEquals("SAMPLE SENDER", parsed.transaction.counterpartyName)
        assertEquals("233000000000", parsed.transaction.counterpartyPhone)
        assertEquals(100648L, parsed.transaction.balanceAfterMinor)
        assertEquals(MoneyMovementChannel.WALLET_TO_WALLET, parsed.transaction.event?.channel)
    }

    @Test
    fun parsesSameNetworkIncomingTransfer() {
        val parsed = parse(
            "000004 Confirmed. You have received GHS250.20 from 233000000000 - SAMPLE PERSON on 2026-03-15 at 13:43:11. Your Telecel Cash balance is GHS503.98. Reference: sample.",
        ).parsed()

        assertEquals(25020, parsed.transaction.amountMinor)
        assertEquals("SAMPLE PERSON", parsed.transaction.counterpartyName)
        assertEquals("233000000000", parsed.transaction.counterpartyPhone)
    }

    @Test
    fun parsesBundlePurchase() {
        val parsed = parse(
            "000005 confirmed. Your bundle purchase request of GHS5.50 on 2026-01-10 has been received at 16:33:34. Kindly wait for confirmation. Your new Telecel Cash balance is GHS1.62.",
        ).parsed()

        assertEquals(TransactionDirection.DEBIT, parsed.transaction.direction)
        assertEquals(550, parsed.transaction.amountMinor)
        assertEquals("Data Bundle", parsed.transaction.counterpartyName)
        assertEquals("Bundle purchase", parsed.transaction.reference)
    }

    @Test
    fun parsesAirtimePurchase() {
        val parsed = parse(
            "000006 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Your Telecel Cash balance is GHS0.62.",
        ).parsed()

        assertEquals(100, parsed.transaction.amountMinor)
        assertEquals("Airtime", parsed.transaction.counterpartyName)
        assertEquals("233000000000", parsed.transaction.counterpartyPhone)
        assertEquals("Airtime purchase", parsed.transaction.reference)
        assertEquals(MoneyMovementChannel.AIRTIME_DATA, parsed.transaction.event?.channel)
    }

    @Test
    fun parsesInterestCredit() {
        val parsed = parse(
            "Dear customer, you have received GHS0.16 from Telecel Cash as interest earned on your mobile money wallet for the period October 2025 to December 2025. Your new balance is GHS0.78.",
        ).parsed()

        assertEquals(TransactionDirection.CREDIT, parsed.transaction.direction)
        assertEquals(16, parsed.transaction.amountMinor)
        assertEquals("Telecel Cash Interest", parsed.transaction.counterpartyName)
    }

    @Test
    fun registryIgnoresFailedAndNonTransactionalTelecelMessages() {
        assertIgnored("Failed. Your Transfer of GHS30.00 sent to 0240000000 SAMPLE NAME on MTN MOBILE MONEY on 2026-04-13 at 19:44:46 was unsuccessful. Your Telecel Cash balance is GHS76.39. Please try again.")
        assertIgnored("000007 Confirmed. Your Telecel Cash wallet balance is GHS93.48. Stay alert. Never share your PIN or OTP with anyone.")
        assertIgnored("Transaction failed. You do not have enough money in your Telecel Cash account to perform this transaction. Your Telecel Cash balance is GHS1.62.")
        assertIgnored("Sending money from Telecel Cash to Telecel Cash remains FREE on the Telecel Play App.")
    }

    private fun parse(body: String): SmsParseResult? =
        parser.parse(SmsParseInput("Telecel", body, receivedAt), normalizeWhitespace(body))

    private fun assertIgnored(body: String) {
        val result = registry.parse(SmsParseInput("Telecel", body, receivedAt))
        assertEquals(SmsParseResult.Ignored::class, result::class)
    }

    private fun SmsParseResult?.parsed(): SmsParseResult.Parsed = this as SmsParseResult.Parsed
}
