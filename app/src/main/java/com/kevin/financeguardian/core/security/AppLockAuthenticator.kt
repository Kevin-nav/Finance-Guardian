package com.kevin.financeguardian.core.security

import androidx.fragment.app.FragmentActivity

interface AppLockAuthenticator {
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onError: (String) -> Unit,
    )
}
