package com.kevin.financeguardian.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhanaPhoneNumberNormalizerTest {
    @Test
    fun normalizesLocalAndInternationalGhanaNumbers() {
        assertEquals("233549037907", GhanaPhoneNumberNormalizer.normalize("0549037907")?.canonical)
        assertEquals("233549037907", GhanaPhoneNumberNormalizer.normalize("+233 54 903 7907")?.canonical)
        assertEquals("233549037907", GhanaPhoneNumberNormalizer.normalize("233549037907")?.canonical)
        assertTrue(GhanaPhoneNumberNormalizer.sameNumber("054 903 7907", "233549037907"))
    }

    @Test
    fun treatsMaskedNumbersAsWeakEvidence() {
        val normalized = GhanaPhoneNumberNormalizer.normalize("****4127")

        assertTrue(GhanaPhoneNumberNormalizer.isMasked("****4127"))
        assertEquals("****4127", normalized?.canonical)
        assertTrue(normalized?.masked == true)
        assertFalse(GhanaPhoneNumberNormalizer.sameNumber("****4127", "233549037907"))
    }

    @Test
    fun rejectsNonGhanaLookingInput() {
        assertNull(GhanaPhoneNumberNormalizer.normalize("12345"))
        assertNull(GhanaPhoneNumberNormalizer.normalize("447700900123"))
    }
}
