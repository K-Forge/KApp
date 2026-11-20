package co.edu.konradlorenz.kapp.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the phone has a network that reaches the internet right now.
 *
 * Asked before Microsoft's sign-in opens: with no network, MSAL opens Chrome on its own offline
 * page instead of failing, and the person never sees KApp's message (test case L-05, #232).
 */
fun interface NetworkStatus {
    fun isOnline(): Boolean

    companion object {
        fun of(context: Context): NetworkStatus {
            val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            return NetworkStatus {
                val network = connectivity?.activeNetwork ?: return@NetworkStatus false
                connectivity.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
    }
}
