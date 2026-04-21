package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsFixtureJsonParserTest {
    @Test
    fun parsesSingleFixtureObject() {
        val fixtures = SmsFixtureJsonParser.parseMany(
            """
            {
              "provider": "MTN_MOMO",
              "sender": "MobileMoney",
              "body": "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
              "receivedAt": "2026-04-21T18:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(1, fixtures.size)
        assertEquals(Provider.MTN_MOMO, fixtures.single().provider)
        assertEquals("MobileMoney", fixtures.single().sender)
        assertEquals(Instant.parse("2026-04-21T18:00:00Z"), fixtures.single().receivedAt)
    }

    @Test
    fun parsesFixtureArray() {
        val fixtures = SmsFixtureJsonParser.parseMany(
            """
            [
              {
                "provider": "MTN_MOMO",
                "sender": "MobileMoney",
                "body": "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
                "receivedAt": "2026-04-21T18:00:00Z"
              },
              {
                "provider": "UNKNOWN",
                "sender": "Unknown",
                "body": "Your OTP is 123456. Do not share it.",
                "receivedAt": "2026-04-21T18:01:00Z"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(listOf(Provider.MTN_MOMO, Provider.UNKNOWN), fixtures.map { it.provider })
    }

    @Test
    fun missingRequiredFieldThrowsClearException() {
        val error = assertFixtureError(
            """
            {
              "provider": "MTN_MOMO",
              "body": "Missing sender",
              "receivedAt": "2026-04-21T18:00:00Z"
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("missing required field: sender"))
    }

    @Test
    fun invalidProviderThrowsClearException() {
        val error = assertFixtureError(
            """
            {
              "provider": "BAD_PROVIDER",
              "sender": "MobileMoney",
              "body": "Payment received",
              "receivedAt": "2026-04-21T18:00:00Z"
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("invalid provider"))
    }

    @Test
    fun invalidReceivedAtThrowsClearException() {
        val error = assertFixtureError(
            """
            {
              "provider": "MTN_MOMO",
              "sender": "MobileMoney",
              "body": "Payment received",
              "receivedAt": "not-a-date"
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("invalid receivedAt"))
    }

    private fun assertFixtureError(json: String): SmsFixtureParseException {
        return try {
            SmsFixtureJsonParser.parseMany(json)
            throw AssertionError("Expected fixture parser to throw")
        } catch (error: SmsFixtureParseException) {
            error
        }
    }
}
