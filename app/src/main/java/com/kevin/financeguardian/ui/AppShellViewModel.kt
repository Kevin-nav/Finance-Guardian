package com.kevin.financeguardian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.security.AppLockState
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionStatusChecker: PermissionStatusChecker,
) : ViewModel() {
    private val appLockState = MutableStateFlow(AppLockState.Locked)
    private val permissionRefreshes = MutableStateFlow(0)

    val uiState: StateFlow<AppShellUiState> = combine(
        userPreferencesRepository.preferences,
        appLockState,
        permissionRefreshes,
    ) { preferences, lockState, _ ->
        AppShellUiState(
            onboardingCompleted = preferences.onboardingCompleted,
            permissions = permissionStatusChecker.currentStatuses(),
            appLockEnabled = preferences.appLockEnabled,
            appLockState = if (preferences.appLockEnabled) lockState else AppLockState.Disabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppShellUiState(),
    )

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
            refreshPermissions()
        }
    }

    fun onSmsPermissionResult() {
        refreshPermissions()
    }

    fun refreshPermissions() {
        permissionRefreshes.value += 1
    }

    fun unlock() {
        appLockState.value = AppLockState.Unlocked
    }

    fun lock() {
        appLockState.value = AppLockState.Locked
    }
}

data class AppShellUiState(
    val onboardingCompleted: Boolean = false,
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
    val appLockEnabled: Boolean = true,
    val appLockState: AppLockState = AppLockState.Locked,
) {
    val shouldShowOnboarding: Boolean = !onboardingCompleted
    val shouldShowLock: Boolean =
        onboardingCompleted && appLockState == AppLockState.Locked
}
