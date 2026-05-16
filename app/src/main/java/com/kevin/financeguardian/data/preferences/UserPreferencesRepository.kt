package com.kevin.financeguardian.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kevin.financeguardian.domain.model.InstrumentOrigin
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.OwnedInstrument
import com.kevin.financeguardian.domain.parser.GhanaPhoneNumberNormalizer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { values ->
        UserPreferences(
            appLockEnabled = values[APP_LOCK_ENABLED] ?: false,
            screenPrivacyEnabled = values[SCREEN_PRIVACY_ENABLED] ?: false,
            debugParserModeEnabled = values[DEBUG_PARSER_MODE_ENABLED] ?: false,
            onboardingCompleted = values[ONBOARDING_COMPLETED] ?: false,
            notificationsEnabled = values[NOTIFICATIONS_ENABLED] ?: true,
            proactiveInsightsEnabled = values[PROACTIVE_INSIGHTS_ENABLED] ?: true,
            balancesVisible = values[BALANCES_VISIBLE] ?: true,
            showAmountsOnLockScreen = values[SHOW_AMOUNTS_ON_LOCK_SCREEN] ?: true,
            ownedWallets = decodeWallets(values[OWNED_WALLETS].orEmpty()),
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[SCREEN_PRIVACY_ENABLED] = enabled }
    }

    suspend fun setDebugParserModeEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[DEBUG_PARSER_MODE_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { values -> values[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setProactiveInsightsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[PROACTIVE_INSIGHTS_ENABLED] = enabled }
    }

    suspend fun setBalancesVisible(visible: Boolean) {
        dataStore.edit { values -> values[BALANCES_VISIBLE] = visible }
    }

    suspend fun setShowAmountsOnLockScreen(enabled: Boolean) {
        dataStore.edit { values -> values[SHOW_AMOUNTS_ON_LOCK_SCREEN] = enabled }
    }

    suspend fun setOwnedWallets(wallets: List<OwnedInstrument>) {
        dataStore.edit { values -> values[OWNED_WALLETS] = encodeWallets(wallets.mapNotNull(::normalizeWallet)) }
    }

    suspend fun addOrUpdateOwnedWallet(wallet: OwnedInstrument) {
        val normalized = normalizeWallet(wallet) ?: return
        val current = preferences.first().ownedWallets
        val updated = current
            .filterNot { it.id == normalized.id || it.matchesIdentifier(normalized.identifier) }
            .plus(normalized)
        setOwnedWallets(updated)
    }

    suspend fun removeOwnedWallet(id: String) {
        val current = preferences.first().ownedWallets
        setOwnedWallets(current.filterNot { it.id == id })
    }

    private fun normalizeWallet(wallet: OwnedInstrument): OwnedInstrument? {
        if (wallet.type != InstrumentType.WALLET) return wallet
        val normalized = GhanaPhoneNumberNormalizer.normalize(wallet.identifier)?.takeUnless { it.masked } ?: return null
        return wallet.copy(
            id = wallet.id.ifBlank { normalized.canonical },
            identifier = normalized.canonical,
            displayIdentifier = wallet.displayIdentifier.ifBlank { wallet.identifier },
        )
    }

    private fun encodeWallets(wallets: List<OwnedInstrument>): String =
        wallets.joinToString("\n") { wallet ->
            listOf(
                wallet.id.escape(),
                wallet.label.escape(),
                wallet.provider.name,
                wallet.identifier.escape(),
                wallet.displayIdentifier.escape(),
                wallet.origin.name,
            ).joinToString("|")
        }

    private fun decodeWallets(encoded: String): List<OwnedInstrument> =
        encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 6) return@mapNotNull null
                OwnedInstrument(
                    id = parts[0].unescape(),
                    label = parts[1].unescape(),
                    type = InstrumentType.WALLET,
                    provider = runCatching { InstrumentProvider.valueOf(parts[2]) }.getOrDefault(InstrumentProvider.UNKNOWN),
                    identifier = parts[3].unescape(),
                    displayIdentifier = parts[4].unescape(),
                    origin = runCatching { InstrumentOrigin.valueOf(parts[5]) }.getOrDefault(InstrumentOrigin.USER_CONFIRMED),
                )
            }
            .toList()

    private fun String.escape(): String = replace("%", "%25").replace("|", "%7C").replace("\n", "%0A")

    private fun String.unescape(): String = replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

    private companion object {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SCREEN_PRIVACY_ENABLED = booleanPreferencesKey("screen_privacy_enabled")
        val DEBUG_PARSER_MODE_ENABLED = booleanPreferencesKey("debug_parser_mode_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PROACTIVE_INSIGHTS_ENABLED = booleanPreferencesKey("proactive_insights_enabled")
        val BALANCES_VISIBLE = booleanPreferencesKey("balances_visible")
        val SHOW_AMOUNTS_ON_LOCK_SCREEN = booleanPreferencesKey("show_amounts_on_lock_screen")
        val OWNED_WALLETS = stringPreferencesKey("owned_wallets")
    }
}
