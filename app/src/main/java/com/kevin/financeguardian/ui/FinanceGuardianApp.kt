package com.kevin.financeguardian.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kevin.financeguardian.feature.categories.CategoriesRoute
import com.kevin.financeguardian.feature.insights.InsightsRoute
import com.kevin.financeguardian.feature.settings.SettingsRoute
import com.kevin.financeguardian.feature.transactions.TransactionsRoute
import com.kevin.financeguardian.ui.theme.spacing

@Composable
fun FinanceGuardianApp(modifier: Modifier = Modifier) {
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
                        ?: false

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
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
                fadeIn(animationSpec = tween(200)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth / 6 },
                        animationSpec = tween(200),
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(150))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(200)) +
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 6 },
                        animationSpec = tween(200),
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth / 6 },
                        animationSpec = tween(150),
                    )
            },
        ) {
            composable(FinanceGuardianDestination.Home.route) {
                TransactionsRoute()
            }
            composable(FinanceGuardianDestination.Insights.route) {
                InsightsRoute()
            }
            composable(FinanceGuardianDestination.Categories.route) {
                CategoriesRoute()
            }
            composable(FinanceGuardianDestination.Settings.route) {
                SettingsRoute()
            }
        }
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
