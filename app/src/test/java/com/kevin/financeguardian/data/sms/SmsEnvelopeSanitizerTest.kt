package com.kevin.financeguardian.data.sms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsEnvelopeSanitizerTest {
    private val receivedAt = Instant.parse("2026-04-21T18:00:00Z")

    @Test
    fun sanitizeTrimsSenderAndBody() {
        val envelope = SmsEnvelopeSanitizer.sanitize(
            sender = "  MobileMoney  ",
            body = "  Payment received for GHS 77.00.  ",
            receivedAt = receivedAt,
        )

        assertEquals(
            SmsMessageEnvelope(
                sender = "MobileMoney",
                body = "Payment received for GHS 77.00.",
                receivedAt = receivedAt,
            ),
            envelope,
        )
    }

    @Test
    fun sanitizeRejectsBlankValues() {
        assertNull(SmsEnvelopeSanitizer.sanitize("MobileMoney", "   ", receivedAt))
        assertNull(SmsEnvelopeSanitizer.sanitize("   ", "Payment received", receivedAt))
    }

    @Test
    fun sanitizeRejectsExcessiveValues() {
        assertNull(
            SmsEnvelopeSanitizer.sanitize(
                sender = "M".repeat(SmsEnvelopeSanitizer.MAX_SENDER_CHARS + 1),
                body = "Payment received",
                receivedAt = receivedAt,
            ),
        )
        assertNull(
            SmsEnvelopeSanitizer.sanitize(
                sender = "MobileMoney",
                body = "A".repeat(SmsEnvelopeSanitizer.MAX_BODY_CHARS + 1),
                receivedAt = receivedAt,
            ),
        )
    }
}
