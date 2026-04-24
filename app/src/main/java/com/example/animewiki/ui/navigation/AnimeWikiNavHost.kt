package com.example.animewiki.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.animewiki.R
import com.example.animewiki.ui.screens.details.AnimeDetailsScreen
import com.example.animewiki.ui.screens.favorites.FavoritesScreen
import com.example.animewiki.ui.screens.settings.SettingsScreen
import com.example.animewiki.ui.screens.topAnime.TopAnimeScreen

object Routes {
    const val MAIN = "main"
    const val DETAILS = "details/{id}"
    const val SETTINGS = "settings"
    fun details(id: Int) = "details/$id"
}

object Tabs {
    const val TOP = "top"
    const val FAVORITES = "favorites"
}

private data class TabItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val tabs = listOf(
    TabItem(Tabs.TOP, R.string.tab_top, Icons.Default.Star),
    TabItem(Tabs.FAVORITES, R.string.tab_favorites, Icons.Default.Favorite)
)

@Composable
fun AnimeWikiNavHost() {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainTabs(
                onAnimeClick = { id -> rootNavController.navigate(Routes.details(id)) },
                onSettingsClick = { rootNavController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.DETAILS,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            AnimeDetailsScreen(onBack = { rootNavController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { rootNavController.popBackStack() })
        }
    }
}

@Composable
private fun MainTabs(
    onAnimeClick: (Int) -> Unit,
    onSettingsClick: () -> Unit
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = { MainBottomBar(tabNavController) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = Tabs.TOP,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tabs.TOP) {
                TopAnimeScreen(
                    onAnimeClick = onAnimeClick,
                    onSettingsClick = onSettingsClick
                )
            }
            composable(Tabs.FAVORITES) {
                FavoritesScreen(onAnimeClick = onAnimeClick)
            }
        }
    }
}

@Composable
private fun MainBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy
        ?.firstOrNull()?.route

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
