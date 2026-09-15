package co.edu.konradlorenz.kapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.edu.konradlorenz.kapp.ui.home.HomeScreen
import co.edu.konradlorenz.kapp.ui.invitation.InvitationScreen
import co.edu.konradlorenz.kapp.ui.login.LoginScreen
import co.edu.konradlorenz.kapp.ui.placeholder.PlaceholderScreen

// The two routes outside the bar. The other five are in KAppDestination, which is also what the
// bar iterates, so a destination cannot be added to one and forgotten in the other.
private const val LOGIN = "login"
private const val INVITATION = "invitation"

@Composable
fun KAppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = LOGIN) {
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

        // The five tabs, each inside the shell that draws the bar over it. Only Inicio has a
        // screen of its own so far; the other four share the placeholder.
        KAppDestination.entries.forEach { destination ->
            composable(destination.route) {
                MainShell(
                    current = destination,
                    onSelect = { navController.openTab(it) },
                ) {
                    when (destination) {
                        KAppDestination.Home -> HomeScreen(onOpen = { navController.openTab(it) })
                        else -> PlaceholderScreen(destination)
                    }
                }
            }
        }
    }
}

/**
 * Moves to one of the five tabs.
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
