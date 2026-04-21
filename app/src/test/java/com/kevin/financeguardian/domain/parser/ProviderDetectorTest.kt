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
    fun detectsTelecelMessages() {
        val body = "0001 Confirmed. GHS17.00 sent to 0240000000 SAMPLE NAME on MTN MOBILE MONEY. Your Telecel Cash balance is GHS76.39."

        assertEquals(Provider.TELECEL_CASH, detect("Telecel", body))
    }

    @Test
    fun detectsGcbTransactionalMessages() {
        val body = "Hi Customer Your A/C No:XXXX0000 has been debited GHS12.00 Desc: VISA Card Top Up Date: 2026-04-08 14:13 Bal: GHS 217.41"

        assertEquals(Provider.GCB, detect("GCB", body))
    }

    @Test
    fun nonTransactionalSecurityTextIsUnknown() {
        val body = "Dear Valued Customer, GCB Bank will NEVER call, text, or send a link to request your PIN."

        assertEquals(Provider.UNKNOWN, detect("GCB", body))
    }
}
