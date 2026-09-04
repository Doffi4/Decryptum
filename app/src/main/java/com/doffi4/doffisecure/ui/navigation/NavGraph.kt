package com.doffi4.doffisecure.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.doffi4.doffisecure.ui.password.GeneratorScreen
import com.doffi4.doffisecure.ui.password.PasswordDetailScreen
import com.doffi4.doffisecure.ui.password.PasswordScreen
import com.doffi4.doffisecure.ui.password.SettingsScreen

sealed class Screen(val route: String) {
    object PasswordList : Screen("password_list")
    object Generator : Screen("generator")
    object Settings : Screen("settings")
    object PasswordDetail : Screen("password_detail/{passwordId}") {
        fun createRoute(passwordId: Long) = "password_detail/$passwordId"
    }
}

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.PasswordList.route,
        modifier = modifier
    ) {
        composable(route = Screen.PasswordList.route) {
            PasswordScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.PasswordDetail.createRoute(id))
                }
            )
        }
        composable(route = Screen.Generator.route) {
            GeneratorScreen()
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen()
        }
        composable(
            route = Screen.PasswordDetail.route,
            arguments = listOf(navArgument("passwordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getLong("passwordId") ?: return@composable
            PasswordDetailScreen(
                passwordId = passwordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
