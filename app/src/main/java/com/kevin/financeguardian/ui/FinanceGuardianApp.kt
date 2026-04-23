package com.kevin.financeguardian.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.financeguardian.core.notifications.InAppNoticeManager
import com.kevin.financeguardian.feature.categories.CategoriesRoute
import com.kevin.financeguardian.feature.categories.CategoryDetailRoute
import com.kevin.financeguardian.feature.insights.InsightsRoute
import com.kevin.financeguardian.feature.onboarding.OnboardingRoute
import com.kevin.financeguardian.feature.security.AppLockRoute
import com.kevin.financeguardian.feature.settings.SettingsRoute
import com.kevin.financeguardian.feature.transactions.TransactionsRoute
import com.kevin.financeguardian.ui.components.InAppNoticeHost
import com.kevin.financeguardian.ui.theme.spacing
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

typealias AuthenticateAppLock = (
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onError: (String) -> Unit,
) -> Unit

private enum class AppScreen {
    Onboarding,
    Lock,
    Main,
}

@Composable
fun FinanceGuardianApp(
    modifier: Modifier = Modifier,
    viewModel: AppShellViewModel = hiltViewModel(),
    onAuthenticate: AuthenticateAppLock = { onSuccess, _, _ -> onSuccess() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val noticeManager = rememberInAppNoticeManager()
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onSmsPermissionResult()
        viewModel.refreshPermissions()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.lock()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val currentScreen = when {
        uiState.shouldShowOnboarding -> AppScreen.Onboarding
        uiState.shouldShowLock -> AppScreen.Lock
        else -> AppScreen.Main
    }

    fun authenticate() {
        onAuthenticate(
            { viewModel.unlock() },
            { },
            { },
        )
    }

    LaunchedEffect(uiState.shouldShowLock) {
        if (uiState.shouldShowLock) {
            authenticate()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (fadeIn(tween(300))).togetherWith(fadeOut(tween(200)))
            },
            label = "app_screen_transition",
        ) { screen ->
            when (screen) {
                AppScreen.Onboarding -> {
                    OnboardingRoute(
                        modifier = Modifier.fillMaxSize(),
                        onRequestSmsPermission = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.refreshPermissions()
                            }
                        },
                        onSetUpLater = viewModel::completeOnboarding,
                    )
                }
                AppScreen.Lock -> {
                    AppLockRoute(
                        modifier = Modifier.fillMaxSize(),
                        onUnlockClick = ::authenticate,
                    )
                }
                AppScreen.Main -> {
                    MainAppContent(
                        modifier = Modifier.fillMaxSize(),
                        smsPermissionLauncher = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        },
                        notificationPermissionLauncher = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.refreshPermissions()
                            }
                        },
                    )
                }
            }
        }

        InAppNoticeHost(
            noticeManager = noticeManager,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = MaterialTheme.spacing.md,
                    vertical = MaterialTheme.spacing.sm,
                ),
        )
    }
}

@Composable
private fun rememberInAppNoticeManager(): InAppNoticeManager {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            InAppNoticeManagerEntryPoint::class.java,
        ).inAppNoticeManager()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface InAppNoticeManagerEntryPoint {
    fun inAppNoticeManager(): InAppNoticeManager
}

@Composable
private fun MainAppContent(
    modifier: Modifier = Modifier,
    smsPermissionLauncher: () -> Unit,
    notificationPermissionLauncher: () -> Unit,
) {
    val navController = rememberNavController()
    val destinations = listOf(
        FinanceGuardianDestination.Home,
        FinanceGuardianDestination.Insights,
        FinanceGuardianDestination.Categories,
        FinanceGuardianDestination.Settings,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = MaterialTheme.spacing.xxs,
            ) {
                destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route }
                        ?: false ||
                        (destination == FinanceGuardianDestination.Categories &&
                            currentDestination?.route?.startsWith("categories/") == true)

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigateToTopLevelDestination(destination)
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FinanceGuardianDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(250)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth / 8 },
                        animationSpec = tween(250),
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 8 },
                        animationSpec = tween(250),
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) +
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth / 8 },
                        animationSpec = tween(200),
                    )
            },
        ) {
            composable(FinanceGuardianDestination.Home.route) {
                TransactionsRoute(
                    onViewInsightsClick = {
                        navController.navigateToTopLevelDestination(FinanceGuardianDestination.Insights)
                    },
                )
            }
            composable(FinanceGuardianDestination.Insights.route) {
                InsightsRoute()
            }
            composable(FinanceGuardianDestination.Categories.route) {
                CategoriesRoute(
                    onCategoryClick = { categoryId ->
                        navController.navigate("categories/$categoryId")
                    },
                )
            }
            composable(
                route = "categories/{categoryId}",
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType },
                ),
            ) {
                CategoryDetailRoute(
                    onBackClick = { navController.popBackStack() },
                )
            }
            composable(FinanceGuardianDestination.Settings.route) {
                SettingsRoute(
                    onRequestSmsPermission = smsPermissionLauncher,
                    onRequestNotificationPermission = notificationPermissionLauncher,
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevelDestination(
    destination: FinanceGuardianDestination,
) {
    if (currentDestination?.route == destination.route) return

    if (destination == FinanceGuardianDestination.Home &&
        popBackStack(FinanceGuardianDestination.Home.route, inclusive = false, saveState = true)
    ) {
        return
    }

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}


private sealed class FinanceGuardianDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    data object Home : FinanceGuardianDestination(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    )

    data object Insights : FinanceGuardianDestination(
        route = "insights",
        label = "Insights",
        icon = Icons.Outlined.PieChart,
        selectedIcon = Icons.Filled.PieChart,
    )

    data object Categories : FinanceGuardianDestination(
        route = "categories",
        label = "Categories",
        icon = Icons.Outlined.Category,
        selectedIcon = Icons.Filled.Category,
    )

    data object Settings : FinanceGuardianDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    )
}
