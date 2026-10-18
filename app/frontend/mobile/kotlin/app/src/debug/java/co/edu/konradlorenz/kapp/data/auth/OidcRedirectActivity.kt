package co.edu.konradlorenz.kapp.data.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import co.edu.konradlorenz.kapp.MainActivity

/**
 * Where the browser lands after the fake OIDC login page: `kapp-dev://oidc/callback?code=...`.
 * It hands the address to the sign-in that is waiting for it and brings the app back to the front,
 * because the browser would otherwise stay on top. Debug builds only.
 */
class OidcRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { DevOidc.pending?.complete(it) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
