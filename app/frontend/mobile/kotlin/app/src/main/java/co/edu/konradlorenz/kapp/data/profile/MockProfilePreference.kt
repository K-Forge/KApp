package co.edu.konradlorenz.kapp.data.profile

import android.content.Context
import androidx.core.content.edit

/**
 * The named example of `GET /api/users/me` a debug build asks the mocks for: `student`,
 * `professor` or `staffAdmin`. Picked on the login, and kept so the app reopened later still shows
 * the same person - the mocks themselves remember nothing.
 *
 * Plain preferences, not the encrypted store: it is a developer's setting, not a credential.
 */
class MockProfilePreference(context: Context) {

    private val prefs = context.getSharedPreferences("kapp.debug", Context.MODE_PRIVATE)

    var example: String
        get() = prefs.getString(KEY, null) ?: DEFAULT
        set(value) = prefs.edit { putString(KEY, value) }

    private companion object {
        const val KEY = "mockProfile"
        const val DEFAULT = "student"
    }
}
