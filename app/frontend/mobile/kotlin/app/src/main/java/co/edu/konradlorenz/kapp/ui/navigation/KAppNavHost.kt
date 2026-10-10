package co.edu.konradlorenz.kapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.edu.konradlorenz.kapp.AppContainer
import co.edu.konradlorenz.kapp.ui.home.HomeScreen
import co.edu.konradlorenz.kapp.ui.invitation.InvitationScreen
import co.edu.konradlorenz.kapp.ui.login.LoginScreen
import co.edu.konradlorenz.kapp.ui.placeholder.PlaceholderScreen
import co.edu.konradlorenz.kapp.ui.profile.ProfileScreen

// The two routes outside the bar. The other five are in KAppDestination, which is also what the
// bar iterates, so a destination cannot be added to one and forgotten in the other.
private const val LOGIN = "login"
private const val INVITATION = "invitation"

/**
 * Every screen, and the one rule that crosses them: **no session, no tabs.**
 *
 * A saved session opens straight on Inicio, which is the point of keeping it - somebody who uses
 * the app at least once a month never sees the login again. When the session ends, whether by
 * signing out or because a renewal was refused, whatever is on screen gives way to the login.
 */
@Composable
fun KAppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val session by container.session.session.collectAsState()
    val destinations = destinationsFor(session?.profileRole)

    // Decided once: afterwards the session moves the screens, not the start destination.
    val start = remember { if (session != null) KAppDestination.Home.route else LOGIN }

    val signedIn = session != null
    LaunchedEffect(signedIn) {
        val route = navController.currentDestination?.route
        if (!signedIn && route != null && route != LOGIN && route != INVITATION) {
            navController.navigate(LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(LOGIN) {
            LoginScreen(
                onSignIn = {
                    // Nobody goes back to the login with the back button once they are in.
                    navController.navigate(KAppDestination.Home.route) {
                        popUpTo(LOGIN) { inclusive = true }
                    }
                },
                onUseInvitationCode = { navController.navigate(INVITATION) },
            )
        }

        composable(INVITATION) {
            InvitationScreen(onBack = { navController.popBackStack() })
        }

        // The tabs, each inside the shell that draws the bar over it. Inicio and Perfil have screens
        // of their own; the other three share the placeholder.
        KAppDestination.entries.forEach { destination ->
            composable(destination.route) {
                MainShell(
                    current = destination,
                    destinations = destinations,
                    onSelect = { navController.openTab(it) },
                ) {
                    when (destination) {
                        KAppDestination.Home -> HomeScreen(
                            destinations = destinations,
                            onOpen = { navController.openTab(it) },
                        )
                        KAppDestination.Profile -> ProfileScreen()
                        else -> PlaceholderScreen(destination)
                    }
                }
            }
        }
    }
}

/**
 * Moves to one of the tabs.
 *
 * The tabs sit side by side rather than stacking: every move pops back to Inicio first, so the bar
 * never builds a history of itself and Back from any tab leaves for Inicio rather than retracing
 * which ones were visited. `saveState` and `restoreState` keep what a tab had on it - a scroll
 * position, later a loaded list - so returning to one is not a reload.
 *
 * Tapping the tab you are already on lands on the same destination and `launchSingleTop` keeps it
 * rather than putting a second copy on the stack.
 */
private fun NavHostController.openTab(destination: KAppDestination) {
    navigate(destination.route) {
        popUpTo(KAppDestination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
