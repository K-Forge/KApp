package co.edu.konradlorenz.kapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Debug builds only: asks for local network access, which Android 17 requires of apps that target
 * API 37 before they can open a connection to a private address. A debug build always talks to one:
 * the mocks or the local backend on 10.0.2.2, or on the computer's LAN address from a phone.
 *
 * Without the permission nothing says "permission": the connection times out, and the login shows
 * "No pudimos conectar con el servidor". A release build talks to a public host and never asks;
 * src/release has an object of the same name that does nothing.
 */
internal object LocalNetworkAccess {

    fun requestIfNeeded(activity: ComponentActivity) {
        // Only Android 17 enforces it; on older versions the INTERNET permission is enough.
        if (Build.VERSION.SDK_INT < 37) return
        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) return
        // Registered in onCreate, as the activity result API requires. A refusal is not handled:
        // the next connection fails as offline, and the README says to grant it in the settings.
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
    }
}
