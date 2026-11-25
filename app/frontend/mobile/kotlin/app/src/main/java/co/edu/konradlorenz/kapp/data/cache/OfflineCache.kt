package co.edu.konradlorenz.kapp.data.cache

import co.edu.konradlorenz.kapp.data.crypto.BlobCipher
import co.edu.konradlorenz.kapp.data.network.KAppApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/** A copy read back from [OfflineCache], with when it was saved (epoch milliseconds). */
data class Cached<T>(val value: T, val savedAt: Long)

/**
 * The last good answer of the calls Inicio is drawn from, one sealed file each in [dir] (#232).
 *
 * Saved on every success, read only when a call failed for lack of network: someone who opens the
 * app in airplane mode sees what they saw last time, with a note saying from when, instead of
 * "No pudimos cargar...". Cleared when the session ends, so it never shows one person's data to
 * the next.
 *
 * The copies hold a profile, a timetable and grades, so each is sealed with [cipher] - in the app,
 * a key of the Android Keystore, as the session is - and never lands on disk as plain JSON.
 *
 * Never throws: a copy that cannot be written is simply not there next time, and one that cannot
 * be opened or read - the key was lost to a reinstall, or the model changed between versions - is
 * deleted and is as good as none.
 */
class OfflineCache(
    private val dir: File,
    private val cipher: BlobCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun <T> save(name: String, serializer: KSerializer<T>, value: T) {
        dropPlain(name)
        runCatching {
            dir.mkdirs()
            val saved = buildJsonObject {
                put(SAVED_AT, now())
                put(VALUE, KAppApi.json.encodeToJsonElement(serializer, value))
            }
            file(name).writeBytes(cipher.seal(saved.toString().encodeToByteArray()))
        }.onFailure { file(name).delete() }
    }

    fun <T> read(name: String, serializer: KSerializer<T>): Cached<T>? {
        dropPlain(name)
        val file = file(name)
        if (!file.exists()) return null
        return runCatching {
            val saved = KAppApi.json.parseToJsonElement(cipher.open(file.readBytes()).decodeToString()).jsonObject
            Cached(
                value = KAppApi.json.decodeFromJsonElement(serializer, saved.getValue(VALUE)),
                savedAt = saved.getValue(SAVED_AT).jsonPrimitive.long,
            )
        }.onFailure { file.delete() }.getOrNull()
    }

    fun clear() {
        dir.deleteRecursively()
    }

    private fun file(name: String) = File(dir, "$name.sealed")

    /** The plain JSON copy a build before the encryption may have left. */
    private fun dropPlain(name: String) {
        File(dir, "$name.json").delete()
    }

    private companion object {
        const val SAVED_AT = "savedAt"
        const val VALUE = "value"
    }
}
