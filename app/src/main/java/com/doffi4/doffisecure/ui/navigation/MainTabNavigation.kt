package com.doffi4.doffisecure.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Shared entry point for the bottom bar and shortcuts into a main tab. */
internal fun NavHostController.navigateToMainTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // The start destination already remains on the stack. Non-inclusive popUpTo
        // aliases the popped stack to it, so restoring it can reopen a different tab.
        restoreState = route != Screen.PasswordList.route
    }
    // A restored tab may include Detail/Security opened from it. A tab press
    // means its root screen; retain that root's state while removing its children.
    if (currentDestination?.route != route) popBackStack(route, inclusive = false)
}
