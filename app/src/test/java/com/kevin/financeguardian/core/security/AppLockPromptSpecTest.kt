package com.kevin.financeguardian.core.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPromptSpecTest {
    @Test
    fun defaultPromptAllowsBiometricOrDeviceCredential() {
        val spec = AppLockPromptSpec.default()

        assertEquals("Unlock Finance Guardian", spec.title)
        assertTrue(
            spec.allowedAuthenticators and BiometricManager.Authenticators.BIOMETRIC_STRONG != 0,
        )
        assertTrue(
            spec.allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0,
        )
    }
}
