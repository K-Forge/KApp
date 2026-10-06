package co.edu.konradlorenz.kapp.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import co.edu.konradlorenz.kapp.data.network.KAppApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The session on disk, encrypted with a key that never leaves the Android Keystore.
 *
 * Issue #46 asks for the refresh token in encrypted storage, never in plain preferences. The whole
 * session is encrypted as one blob - AES-256-GCM, a fresh IV per write - and the ciphertext is what
 * lands in SharedPreferences. EncryptedSharedPreferences would do the same, but androidx.security
 * is deprecated, and this is the few lines it amounts to.
 *
 * Anything that cannot be read back is treated as no session: the key was lost (the app data was
 * restored onto another device, where the key does not exist), or the format changed. The person
 * signs in again, which is the right answer in both cases.
 */
class KeystoreSessionStore(context: Context) : SessionStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): Session? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
            )
            val json = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
            KAppApi.json.decodeFromString(Session.serializer(), json)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun write(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = KAppApi.json.encodeToString(Session.serializer(), session)
        val bytes = cipher.iv + cipher.doFinal(json.encodeToByteArray())
        prefs.edit { putString(KEY_SESSION, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_SESSION) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kapp.session"
        const val PREFS = "kapp.session"
        const val KEY_SESSION = "session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
