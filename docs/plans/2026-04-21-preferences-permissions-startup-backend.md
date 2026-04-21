# Preferences Permissions Startup Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add backend app-state foundations for persisted user preferences, permission status checks, and startup category seeding.

**Architecture:** Use DataStore Preferences as the source of truth for app-level flags, expose a small permission checker abstraction backed by Android runtime permission checks, and add an injectable startup runner called from `FinanceGuardianApplication`. Keep this slice backend-only; no Compose UI or navigation changes.

**Tech Stack:** Kotlin, DataStore Preferences, Hilt, AndroidX Core permission APIs, Room, Robolectric, JUnit 4, coroutines test, Turbine.

---

### Task 1: Add User Preferences Repository

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferences.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepository.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt`

**Step 1: Write failing repository tests**

Cover:

- Defaults are `appLockEnabled = true`, `debugParserModeEnabled = false`, `onboardingCompleted = false`.
- `setAppLockEnabled(false)` persists.
- `setDebugParserModeEnabled(true)` persists.
- `setOnboardingCompleted(true)` persists.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
```

Expected: fails because repository/model do not exist.

**Step 2: Implement preferences model**

Create `UserPreferences.kt`:

```kotlin
package com.kevin.financeguardian.data.preferences

data class UserPreferences(
    val appLockEnabled: Boolean = true,
    val debugParserModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
)
```

**Step 3: Implement repository**

Create `UserPreferencesRepository.kt`:

```kotlin
package com.kevin.financeguardian.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { values ->
        UserPreferences(
            appLockEnabled = values[APP_LOCK_ENABLED] ?: true,
            debugParserModeEnabled = values[DEBUG_PARSER_MODE_ENABLED] ?: false,
            onboardingCompleted = values[ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setDebugParserModeEnabled(enabled: Boolean) {
        dataStore.edit { it[DEBUG_PARSER_MODE_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val DEBUG_PARSER_MODE_ENABLED = booleanPreferencesKey("debug_parser_mode_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
```

**Step 4: Run repository tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferences.kt app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepository.kt app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt
git commit -m "feat: add user preferences repository"
```

---

### Task 2: Bind Preferences DataStore

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/di/PreferencesModule.kt`

**Step 1: Add Hilt module**

Create `PreferencesModule.kt`:

```kotlin
package com.kevin.financeguardian.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("user_preferences") },
        )
}
```

**Step 2: Run compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile succeeds.

**Step 3: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/di/PreferencesModule.kt
git commit -m "feat: bind user preferences datastore"
```

---

### Task 3: Add Permission Status Backend

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/permissions/AppPermissionStatuses.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/permissions/PermissionStatusChecker.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/permissions/AndroidPermissionStatusChecker.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/di/PermissionsModule.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/permissions/AndroidPermissionStatusCheckerTest.kt`

**Step 1: Write failing tests**

Use Robolectric and `ShadowApplication` to grant/deny permissions.

Cover:

- `currentStatuses()` reports denied permissions.
- Granting `RECEIVE_SMS` reports SMS granted.
- Granting `POST_NOTIFICATIONS` reports notifications granted.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AndroidPermissionStatusCheckerTest"
```

Expected: fails because checker classes do not exist.

**Step 2: Implement permission types**

Create `AppPermissionStatuses.kt`:

```kotlin
package com.kevin.financeguardian.core.permissions

data class AppPermissionStatuses(
    val receiveSmsGranted: Boolean,
    val postNotificationsGranted: Boolean,
)
```

Create `PermissionStatusChecker.kt`:

```kotlin
package com.kevin.financeguardian.core.permissions

interface PermissionStatusChecker {
    fun isGranted(permission: FinanceGuardianPermission): Boolean
    fun currentStatuses(): AppPermissionStatuses
}
```

Create `AndroidPermissionStatusChecker.kt`:

```kotlin
package com.kevin.financeguardian.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissionStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionStatusChecker {
    override fun isGranted(permission: FinanceGuardianPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.androidPermission) == PackageManager.PERMISSION_GRANTED

    override fun currentStatuses(): AppPermissionStatuses =
        AppPermissionStatuses(
            receiveSmsGranted = isGranted(FinanceGuardianPermission.ReceiveSms),
            postNotificationsGranted = isGranted(FinanceGuardianPermission.PostNotifications),
        )
}
```

**Step 3: Add Hilt binding**

Create `PermissionsModule.kt`:

```kotlin
package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.permissions.AndroidPermissionStatusChecker
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {
    @Binds
    @Singleton
    abstract fun bindPermissionStatusChecker(
        checker: AndroidPermissionStatusChecker,
    ): PermissionStatusChecker
}
```

**Step 4: Run permission tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AndroidPermissionStatusCheckerTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/permissions/AppPermissionStatuses.kt app/src/main/java/com/kevin/financeguardian/core/permissions/PermissionStatusChecker.kt app/src/main/java/com/kevin/financeguardian/core/permissions/AndroidPermissionStatusChecker.kt app/src/main/java/com/kevin/financeguardian/di/PermissionsModule.kt app/src/test/java/com/kevin/financeguardian/core/permissions/AndroidPermissionStatusCheckerTest.kt
git commit -m "feat: add permission status backend"
```

---

### Task 4: Add Startup Category Seeding Runner

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/startup/AppStartupRunner.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/FinanceGuardianApplication.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt`

**Step 1: Write failing startup test**

Use in-memory Room and `DefaultCategorySeeder`.

Cover:

- `runStartupTasks()` seeds default categories.
- Running twice does not duplicate categories.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppStartupRunnerTest"
```

Expected: fails because runner does not exist.

**Step 2: Implement startup runner**

Create `AppStartupRunner.kt`:

```kotlin
package com.kevin.financeguardian.core.startup

import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppStartupRunner @Inject constructor(
    private val defaultCategorySeeder: DefaultCategorySeeder,
) {
    fun launch(scope: CoroutineScope) {
        scope.launch { runStartupTasks() }
    }

    suspend fun runStartupTasks() {
        defaultCategorySeeder.seedIfEmpty()
    }
}
```

**Step 3: Wire application startup**

Update `FinanceGuardianApplication.kt`:

```kotlin
package com.kevin.financeguardian

import android.app.Application
import com.kevin.financeguardian.core.startup.AppStartupRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class FinanceGuardianApplication : Application() {
    @Inject lateinit var appStartupRunner: AppStartupRunner

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appStartupRunner.launch(applicationScope)
    }
}
```

**Step 4: Run startup tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppStartupRunnerTest"
```

Expected: passes.

**Step 5: Compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Hilt/application wiring compiles.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/startup/AppStartupRunner.kt app/src/main/java/com/kevin/financeguardian/FinanceGuardianApplication.kt app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt
git commit -m "feat: seed defaults on app startup"
```

---

### Task 5: Verify Preferences Permission Startup Backend

**Files:**
- No edits expected.

**Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UserPreferencesRepositoryTest" --tests "*AndroidPermissionStatusCheckerTest" --tests "*AppStartupRunnerTest"
```

Expected: passes.

**Step 2: Run database and ingestion regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest" --tests "*SmsIngestionServiceTest"
```

Expected: passes.

**Step 3: Run full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: passes.

**Step 4: Confirm clean git state**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: working tree is clean after commits.

---

## Deferred Follow-Up

- Wire onboarding UI to `UserPreferencesRepository` and runtime permission requests.
- Wire settings UI to `UserPreferencesRepository` and `PermissionStatusChecker`.
- Add app-lock implementation using the persisted app lock preference.
- Add Room-backed transaction list UI.

