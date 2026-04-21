package com.kevin.financeguardian.data.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantNormalizerTest {
    @Test
    fun normalizeNameLowercasesTrimsPunctuationAndCollapsesWhitespace() {
        assertEquals("shoprite osu mall", MerchantNormalizer.normalizeName("  Shoprite - OSU, Mall!! "))
    }

    @Test
    fun normalizeNameReturnsNullForBlank() {
        assertNull(MerchantNormalizer.normalizeName("   "))
    }

    @Test
    fun normalizePhoneKeepsDigitsAndMaskCharactersOnly() {
        assertEquals("23324***1234", MerchantNormalizer.normalizePhone("+233 24-***-1234"))
    }

    @Test
    fun normalizePhoneReturnsNullForBlank() {
        assertNull(MerchantNormalizer.normalizePhone(" - + "))
    }
}
