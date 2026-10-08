package com.doffi4.doffisecure

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.doffi4.doffisecure.ui.navigation.Screen
import com.doffi4.doffisecure.ui.navigation.navigateToMainTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class MainTabNavigationTest {
    private fun controller() = NavHostController(RuntimeEnvironment.getApplication()).apply {
        setViewModelStore(ViewModelStore())
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = Screen.PasswordList.route) {
            listOf(Screen.PasswordList, Screen.TotpList, Screen.Generator, Screen.Settings,
                Screen.SecurityCenter, Screen.PasswordDetail).forEach { screen -> composable(screen.route) {} }
        }
    }

    @Test fun `password tab returns to vault instead of restoring another tab`() {
        val nav = controller()
        nav.navigateToMainTab(Screen.Generator.route)
        nav.navigateToMainTab(Screen.PasswordList.route)
        assertEquals(Screen.PasswordList.route, nav.currentDestination?.route)
    }

    @Test fun `security shortcut followed by all tabs resolves every requested destination`() {
        val nav = controller()
        nav.navigateToMainTab(Screen.Settings.route)
        nav.navigateToMainTab(Screen.PasswordList.route)
        nav.navigate(Screen.SecurityCenter.route)
        nav.navigateToMainTab(Screen.Generator.route)
        repeat(3) {
            listOf(Screen.PasswordList, Screen.TotpList, Screen.Settings, Screen.Generator).forEach {
                nav.navigateToMainTab(it.route)
                assertEquals(it.route, nav.currentDestination?.route)
            }
        }
    }

    @Test fun `reselecting a tab does not duplicate it and back reaches vault`() {
        val nav = controller()
        repeat(4) { nav.navigateToMainTab(Screen.Settings.route) }
        nav.popBackStack()
        assertEquals(Screen.PasswordList.route, nav.currentDestination?.route)
    }

    @Test fun `legacy security to generator shortcut cannot poison tab restoration`() {
        val nav = controller()
        nav.navigateToMainTab(Screen.Settings.route)
        nav.navigateToMainTab(Screen.PasswordList.route)
        nav.navigate(Screen.SecurityCenter.route)
        nav.navigate(Screen.Generator.route) {
            popUpTo(Screen.PasswordList.route)
            launchSingleTop = true
        }
        repeat(3) {
            listOf(Screen.PasswordList, Screen.TotpList, Screen.Settings, Screen.Generator).forEach {
                nav.navigateToMainTab(it.route)
                assertEquals(it.route, nav.currentDestination?.route)
            }
        }
    }

    @Test fun `2fa tab restores its root after detail and security shortcut`() {
        val nav = controller()
        nav.navigateToMainTab(Screen.TotpList.route)
        nav.navigate(Screen.PasswordDetail.createRoute(7))
        nav.navigate(Screen.SecurityCenter.route)
        nav.navigateToMainTab(Screen.Generator.route)
        nav.navigateToMainTab(Screen.TotpList.route)
        assertEquals(Screen.TotpList.route, nav.currentDestination?.route)
        nav.popBackStack()
        assertEquals(Screen.PasswordList.route, nav.currentDestination?.route)
    }
}
