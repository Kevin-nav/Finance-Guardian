package com.kevin.financeguardian.core.security

import androidx.biometric.BiometricManager

data class AppLockPromptSpec(
    val title: String,
    val subtitle: String,
    val allowedAuthenticators: Int,
) {
    companion object {
        fun default(): AppLockPromptSpec =
            AppLockPromptSpec(
                title = "Unlock Finance Guardian",
                subtitle = "Confirm it is you before viewing financial data.",
                allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
    }
}
