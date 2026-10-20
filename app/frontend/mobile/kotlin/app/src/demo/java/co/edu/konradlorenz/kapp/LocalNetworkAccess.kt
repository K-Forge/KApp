package co.edu.konradlorenz.kapp

import androidx.activity.ComponentActivity

/**
 * The demo side of src/debug's LocalNetworkAccess: the demo answers every call from its own assets
 * and never opens a connection, so it never asks for local network access.
 */
internal object LocalNetworkAccess {
    fun requestIfNeeded(activity: ComponentActivity) = Unit
}
