package co.edu.konradlorenz.kapp

import androidx.activity.ComponentActivity

/**
 * The release side of src/debug's LocalNetworkAccess: a release build talks to a public host, so
 * it never needs local network access and never asks for it.
 */
internal object LocalNetworkAccess {
    fun requestIfNeeded(activity: ComponentActivity) = Unit
}
