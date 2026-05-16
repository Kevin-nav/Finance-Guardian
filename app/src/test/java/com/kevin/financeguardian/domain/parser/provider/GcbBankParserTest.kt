package com.kevin.financeguardian.domain.parser.provider

import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.FinanceGuardianSmsParser
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.normalizeWhitespace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GcbBankParserTest {
    private val parser = GcbBankParser()
    private val registry = FinanceGuardianSmsParser()
    private val receivedAt = Instant.parse("2026-04-08T15:00:00Z")

    @Test
    fun parsesAccountDebit() {
        val parsed = parse(
            """
            Hi Customer
            Your A/C No:XXXX0000
            has been debited GHS12.00
            Desc: VISA Card Top Up SAMPLE 000000000
            Date: 2026-04-08 14:13
            Bal: GHS 217.41
            """.trimIndent(),
        ).parsed()

        assertEquals(TransactionDirection.DEBIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.EXPENSE, parsed.transaction.moneyMovementType)
        assertEquals(1200, parsed.transaction.amountMinor)
        assertEquals("VISA Card Top Up SAMPLE 000000000", parsed.transaction.counterpartyName)
        assertEquals("VISA Card Top Up SAMPLE 000000000", parsed.transaction.reference)
        assertEquals(21741L, parsed.transaction.balanceAfterMinor)
        assertEquals(Instant.parse("2026-04-08T14:13:00Z"), parsed.transaction.occurredAt)
        assertEquals(MoneyMovementChannel.CARD_TOP_UP, parsed.transaction.event?.channel)
        assertEquals("SAMPLE 000000000", parsed.transaction.event?.destinationInstrument?.identifier)
    }

    @Test
    fun parsesAccountDebitWithFees() {
        val parsed = parse(
            """
            Hi Customer
            Your A/C No:XXXX0000
            has been debited GHS270.00
            Fees: GHS 2.70
            Desc: Bank to Wallet 0240000000 groceries
            Date: 2026-04-07 16:27
            Bal: GHS 229.41
            """.trimIndent(),
        ).parsed()

        assertEquals(27000, parsed.transaction.amountMinor)
        assertEquals(MoneyMovementType.EXPENSE, parsed.transaction.moneyMovementType)
        assertEquals("Bank to Wallet 0240000000 groceries", parsed.transaction.counterpartyName)
        assertEquals(MoneyMovementChannel.BANK_TO_WALLET, parsed.transaction.event?.channel)
        assertEquals("groceries", parsed.transaction.event?.plannedUse)
    }

    @Test
    fun parsesAccountCreditWithCommaAmount() {
        val parsed = parse(
            """
            Hi Customer
            Your A/C No:XXXX0000
            has been credited GHS1,050.00
            Desc: Transfer to 0000000000000 Food
            Date: 2026-03-31 15:55
            Bal: GHS 1,062.61
            """.trimIndent(),
        ).parsed()

        assertEquals(TransactionDirection.CREDIT, parsed.transaction.direction)
        assertEquals(MoneyMovementType.INCOME, parsed.transaction.moneyMovementType)
        assertEquals(105000, parsed.transaction.amountMinor)
        assertEquals(106261L, parsed.transaction.balanceAfterMinor)
    }

    @Test
    fun parsesSubscriptionCandidate() {
        val parsed = parse(
            """
            Hi Customer
            Your A/C No:XXXX0000
            has been debited GHS236.29
            Desc: OPENAI CHATGPT SUBSCR SAN FRANCISC
            Date: 2026-04-01 14:16
            Bal: GHS 624.32
            """.trimIndent(),
        ).parsed()

        assertEquals(MoneyMovementType.SUBSCRIPTION_CANDIDATE, parsed.transaction.moneyMovementType)
        assertEquals(23629, parsed.transaction.amountMinor)
    }

    @Test
    fun parsesPrepaidCardDebitAndCredit() {
        val debit = parse(
            "Dear Customer. Your Prepaid Card VISA VPREPAID CLASSIC has been debited with amount of : 12.00 GHANA CEDIS. Your balance is 2.00 GHANA CEDIS.",
        ).parsed()
        val credit = parse(
            "Dear Customer. Your Prepaid Card has been credited with an amount of : 12 GHS. Your balance is 14 GHS.",
        ).parsed()

        assertEquals(TransactionDirection.DEBIT, debit.transaction.direction)
        assertEquals(1200, debit.transaction.amountMinor)
        assertEquals(200L, debit.transaction.balanceAfterMinor)
        assertEquals("Prepaid Card", debit.transaction.reference)
        assertEquals(TransactionDirection.CREDIT, credit.transaction.direction)
        assertEquals(1200, credit.transaction.amountMinor)
        assertEquals(1400L, credit.transaction.balanceAfterMinor)
        assertEquals("Prepaid Card", credit.transaction.reference)
    }

    @Test
    fun extractsGcbBankToWalletAndWalletToBankFacts() {
        val bankToWallet = parse(
            """
            Hi Customer
            Your A/C No:XXXX4127
            has been debited GHS60.00
            Fees: GHS 0.60
            Desc: Bank to Wallet 0549037907 food T260
            Date: 2026-04-03 12:57
            Bal: GHS 502.11
            """.trimIndent(),
        ).parsed()
        val walletToBank = parse(
            """
            Hi Customer
            Your A/C No:XXXX4127
            has been credited GHS60.00
            Desc: Wallet to Bank 0549037907 09FG04301
            Date: 2026-04-03 13:57
            Bal: GHS 562.11
            """.trimIndent(),
        ).parsed()

        assertEquals(MoneyMovementChannel.BANK_TO_WALLET, bankToWallet.transaction.event?.channel)
        assertEquals("233549037907", bankToWallet.transaction.event?.destinationInstrument?.identifier)
        assertEquals("food", bankToWallet.transaction.event?.plannedUse)
        assertEquals(60L, bankToWallet.transaction.event?.feeMinor)
        assertEquals(MoneyMovementChannel.WALLET_TO_BANK, walletToBank.transaction.event?.channel)
        assertEquals("233549037907", walletToBank.transaction.event?.sourceInstrument?.identifier)
    }

    @Test
    fun marksNegativeGcbBalanceSuspicious() {
        val parsed = parse(
            """
            Hi Customer
            Your A/C No:XXXX4127
            has been debited GHS12.00
            Desc: SAMPLE
            Date: 2026-04-08 14:13
            Bal: GHS -1.00
            """.trimIndent(),
        ).parsed()

        assertEquals(-100L, parsed.transaction.balanceAfterMinor)
        assertEquals(BalanceReliability.SUSPICIOUS, parsed.transaction.event?.balanceReliability)
    }

    @Test
    fun registryIgnoresGcbSecurityAndHolidayMessages() {
        assertIgnored("Dear Valued Customer, GCB Bank will NEVER call, text, or send a link to request your Ghana Card or PIN.")
        assertIgnored("Dear Cherished Customer, Wishing you a peaceful and joyful Easter season. GCB, your bank for life.")
    }

    private fun parse(body: String): SmsParseResult? =
        parser.parse(SmsParseInput("GCB", body, receivedAt), normalizeWhitespace(body))

    private fun assertIgnored(body: String) {
        val result = registry.parse(SmsParseInput("GCB", body, receivedAt))
        assertEquals(SmsParseResult.Ignored::class, result::class)
    }

    private fun SmsParseResult?.parsed(): SmsParseResult.Parsed = this as SmsParseResult.Parsed
}
