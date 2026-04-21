package com.kevin.financeguardian.data.sms

import android.content.Intent
import android.provider.Telephony
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsIntentExtractorTest {
    private val extractor = SmsIntentExtractor()
    private val fallback = Instant.parse("2026-04-21T18:00:00Z")

    @Test
    fun ignoresUnrelatedIntentActions() {
        val result = extractor.extract(Intent("com.example.UNRELATED"), fallback)

        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSmsActionWithoutMessages() {
        val result = extractor.extract(Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), fallback)

        assertTrue(result.isEmpty())
    }
}
