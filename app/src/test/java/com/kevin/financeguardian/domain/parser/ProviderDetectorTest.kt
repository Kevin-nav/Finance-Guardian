package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDetectorTest {
    @Test
    fun detectsMtnMessages() {
        val body = "Your payment of GHS 9.00 to MTN AIRTIME has been completed. Financial Transaction Id: 123."

        assertEquals(Provider.MTN_MOMO, detect("MTN MoMo", body))
    }

    @Test
    fun detectsMtnMobileMoneySenderName() {
        val body = "Confirmed. You received GHS77.00 from SAMPLE SENDER. Balance is GHS538.01."

        assertEquals(Provider.MTN_MOMO, detect("MobileMoney", body))
    }

    @Test
    fun detectsTelecelMessages() {
        val body = "0001 Confirmed. GHS17.00 sent to 0240000000 SAMPLE NAME on MTN MOBILE MONEY. Your Telecel Cash balance is GHS76.39."

        assertEquals(Provider.TELECEL_CASH, detect("Telecel", body))
    }

    @Test
    fun detectsTelecelTCashSenderName() {
        val body = "000001 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Balance is GHS0.62."

        assertEquals(Provider.TELECEL_CASH, detect("T-CASH", body))
    }

    @Test
    fun detectsGcbTransactionalMessages() {
        val body = "Hi Customer Your A/C No:XXXX0000 has been debited GHS12.00 Desc: VISA Card Top Up Date: 2026-04-08 14:13 Bal: GHS 217.41"

        assertEquals(Provider.GCB, detect("GCB", body))
    }

    @Test
    fun detectsGcbBankSenderName() {
        val body = "Hi Customer, your transaction of GHS12.00 was posted on 2026-04-08 14:13. Balance: GHS217.41"

        assertEquals(Provider.GCB, detect("GCB Bank", body))
    }

    @Test
    fun nonTransactionalSecurityTextIsUnknown() {
        val body = "Dear Valued Customer, GCB Bank will NEVER call, text, or send a link to request your PIN."

        assertEquals(Provider.UNKNOWN, detect("GCB", body))
    }
}
