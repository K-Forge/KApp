package co.edu.konradlorenz.kapp.data.session

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import co.edu.konradlorenz.kapp.data.crypto.AesGcmCipher
import co.edu.konradlorenz.kapp.data.network.KAppApi

/**
 * The session on disk, encrypted with a key that never leaves the Android Keystore.
 *
 * Issue #46 asks for the refresh token in encrypted storage, never in plain preferences. The whole
 * session is encrypted as one blob - AES-256-GCM, a fresh IV per write, see [AesGcmCipher] - and
 * the ciphertext is what lands in SharedPreferences. EncryptedSharedPreferences would do the same,
 * but androidx.security is deprecated, and this is the few lines it amounts to.
 *
 * Anything that cannot be read back is treated as no session: the key was lost (the app data was
 * restored onto another device, where the key does not exist), or the format changed. The person
 * signs in again, which is the right answer in both cases.
 */
class KeystoreSessionStore(context: Context) : SessionStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val cipher = AesGcmCipher.keystore(KEY_ALIAS)

    override fun read(): Session? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val json = cipher.open(Base64.decode(stored, Base64.NO_WRAP)).decodeToString()
            KAppApi.json.decodeFromString(Session.serializer(), json)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun write(session: Session) {
        val json = KAppApi.json.encodeToString(Session.serializer(), session)
        val bytes = cipher.seal(json.encodeToByteArray())
        prefs.edit { putString(KEY_SESSION, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_SESSION) }
    }

    private companion object {
        const val KEY_ALIAS = "kapp.session"
        const val PREFS = "kapp.session"
        const val KEY_SESSION = "session"
    }
}
