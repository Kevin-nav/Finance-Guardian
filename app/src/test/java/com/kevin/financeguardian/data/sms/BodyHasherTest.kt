package com.kevin.financeguardian.data.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyHasherTest {
    @Test
    fun sha256HexReturnsLowercaseHexDigest() {
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            BodyHasher.sha256Hex("test"),
        )
    }
}
