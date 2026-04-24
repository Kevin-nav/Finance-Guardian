package com.kevin.financeguardian

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kevin.financeguardian.core.security.AndroidAppLockAuthenticator
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.ui.FinanceGuardianApp
import com.kevin.financeguardian.ui.theme.FinanceGuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var appLockAuthenticator: AndroidAppLockAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeScreenPrivacy()
        setContent {
            FinanceGuardianTheme {
                FinanceGuardianApp(
                    onAuthenticate = { onSuccess, onFailure, onUnavailable, onError ->
                        appLockAuthenticator.authenticate(
                            activity = this,
                            onSuccess = onSuccess,
                            onFailure = onFailure,
                            onUnavailable = onUnavailable,
                            onError = onError,
                        )
                    },
                )
            }
        }
    }

    private fun observeScreenPrivacy() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesRepository.preferences
                    .map { it.screenPrivacyEnabled }
                    .distinctUntilChanged()
                    .collect(::setScreenPrivacyEnabled)
            }
        }
    }

    private fun setScreenPrivacyEnabled(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
