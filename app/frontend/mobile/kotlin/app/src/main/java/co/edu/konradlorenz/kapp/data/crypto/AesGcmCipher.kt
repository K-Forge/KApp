package co.edu.konradlorenz.kapp.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals bytes so only this app on this device can open them again. */
interface BlobCipher {

    /** A fresh IV, then the ciphertext and its tag. */
    fun seal(plain: ByteArray): ByteArray

    /** Throws when [sealed] was not sealed with this key, or was changed since. */
    fun open(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a fresh 12-byte IV per seal, stored in front of the ciphertext. The key comes
 * from [key], asked for on every call so a lost key is noticed at once.
 */
class AesGcmCipher(private val key: () -> SecretKey) : BlobCipher {

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "Too short to be sealed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    companion object {

        /**
         * With a key that never leaves the Android Keystore, made the first time [alias] is used.
         * A reinstall or a restore onto another phone loses the key, and every copy sealed with it
         * stops opening: the callers treat that as having nothing saved.
         */
        fun keystore(alias: String) = AesGcmCipher { keystoreKey(alias) }

        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        private fun keystoreKey(alias: String): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        alias,
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
    }
}
