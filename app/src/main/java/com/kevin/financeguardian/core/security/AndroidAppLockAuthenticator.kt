package com.kevin.financeguardian.core.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

class AndroidAppLockAuthenticator @Inject constructor() : AppLockAuthenticator {
    override fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val promptSpec = AppLockPromptSpec.default()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptSpec.title)
            .setSubtitle(promptSpec.subtitle)
            .setAllowedAuthenticators(promptSpec.allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
