package com.example.animewiki.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.animewiki.ui.details.AnimeDetailsScreen
import com.example.animewiki.ui.top.TopAnimeScreen

object Destinations {
    const val TOP = "top"
    const val DETAILS = "details/{id}"
    fun details(id: Int) = "details/$id"
}

@Composable
fun AnimeWikiNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.TOP
    ) {
        composable(Destinations.TOP) {
            TopAnimeScreen(
                onAnimeClick = { id -> navController.navigate(Destinations.details(id)) }
            )
        }
        composable(
            route = Destinations.DETAILS,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            AnimeDetailsScreen(onBack = { navController.popBackStack() })
        }
    }
}