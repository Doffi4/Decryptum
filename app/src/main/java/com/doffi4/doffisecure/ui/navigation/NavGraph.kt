package com.doffi4.doffisecure.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import com.doffi4.doffisecure.ui.password.TotpScreen
import com.doffi4.doffisecure.ui.security.SecurityCenterRoute

sealed class Screen(val route: String) {
    object PasswordList : Screen("password_list")
    object TotpList : Screen("totp_list")
    object Generator : Screen("generator")
    object Settings : Screen("settings")
    object SecurityCenter : Screen("security_center")
    object PasswordDetail : Screen("password_detail/{passwordId}") {
        fun createRoute(passwordId: Long) = "password_detail/$passwordId"
    }
}

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.PasswordList.route,
        modifier = modifier,
        // Keep the animated tab indicator, but never crossfade overlapping vault screens.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(route = Screen.PasswordList.route) {
            PasswordScreen(
                onNavigateToSecurityCenter = { navController.navigate(Screen.SecurityCenter.route) },
                onNavigateToDetail = { id -> navController.navigate(Screen.PasswordDetail.createRoute(id)) },
            )
        }
        composable(route = Screen.TotpList.route) {
            TotpScreen { id ->
                navController.navigate(Screen.PasswordDetail.createRoute(id))
            }
        }
        composable(route = Screen.Generator.route) {
            GeneratorScreen()
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen()
        }
        composable(route = Screen.SecurityCenter.route) {
            SecurityCenterRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(Screen.PasswordDetail.createRoute(id)) },
                onNavigateToGenerator = {
                    navController.navigateToMainTab(Screen.Generator.route)
                },
            )
        }
        composable(
            route = Screen.PasswordDetail.route,
            arguments = listOf(navArgument("passwordId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getLong("passwordId") ?: return@composable
            PasswordDetailScreen(
                passwordId = passwordId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSecurityCenter = { navController.navigate(Screen.SecurityCenter.route) { launchSingleTop = true } },
            )
        }
    }
}
