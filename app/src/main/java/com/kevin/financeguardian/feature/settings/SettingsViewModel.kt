package com.kevin.financeguardian.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
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
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionStatusChecker: PermissionStatusChecker,
) : ViewModel() {
    private val permissionRefreshes = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.preferences,
        permissionRefreshes,
    ) { preferences, _ ->
            SettingsUiState(
                appLockEnabled = preferences.appLockEnabled,
                screenPrivacyEnabled = preferences.screenPrivacyEnabled,
                debugParserModeEnabled = preferences.debugParserModeEnabled,
                permissions = permissionStatusChecker.currentStatuses(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setAppLockEnabled(enabled) }
    }

    fun setScreenPrivacyEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setScreenPrivacyEnabled(enabled) }
    }

    fun setDebugParserModeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDebugParserModeEnabled(enabled) }
    }

    fun refreshPermissions() {
        permissionRefreshes.value += 1
    }
}

data class SettingsUiState(
    val appLockEnabled: Boolean = true,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
)
