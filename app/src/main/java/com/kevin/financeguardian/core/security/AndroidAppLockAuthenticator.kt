package com.kevin.financeguardian.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

class AndroidAppLockAuthenticator @Inject constructor() : AppLockAuthenticator {
    override fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onUnavailable: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val promptSpec = AppLockPromptSpec.default()
        val biometricManager = BiometricManager.from(activity)
        val capability = biometricManager.canAuthenticate(promptSpec.allowedAuthenticators)
        if (capability != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable(capability.toUserMessage())
            return
        }

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
        runCatching {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptSpec.title)
                .setSubtitle(promptSpec.subtitle)
                .setAllowedAuthenticators(promptSpec.allowedAuthenticators)
                .build()

            prompt.authenticate(promptInfo)
        }.onFailure { throwable ->
            onUnavailable(throwable.message ?: "Device authentication is unavailable on this device.")
        }
    }

    private fun Int.toUserMessage(): String =
        when (this) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device does not support biometric or device-credential authentication."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Device authentication is temporarily unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set up a screen lock or biometrics on this device to use app lock."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A security update is required before device authentication can be used."
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                "This device does not support the current app lock authentication method."
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                "Unable to verify whether device authentication is available."
            else ->
                "Device authentication is unavailable on this device."
        }
}
