package co.edu.konradlorenz.kapp.data.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The app's cipher with a plain JCE key, since a unit test has no Android Keystore. */
fun testCipher(key: SecretKey = newTestKey()) = AesGcmCipher { key }

fun newTestKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
