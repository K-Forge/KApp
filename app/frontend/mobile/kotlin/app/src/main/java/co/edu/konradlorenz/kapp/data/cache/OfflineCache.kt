package co.edu.konradlorenz.kapp.data.cache

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
 * The last good answer of the calls Inicio is drawn from, one JSON file each in [dir] (#232).
 *
 * Saved on every success, read only when a call failed for lack of network: someone who opens the
 * app in airplane mode sees what they saw last time, with a note saying from when, instead of
 * "No pudimos cargar...". Cleared when the session ends, so it never shows one person's data to
 * the next.
 *
 * Never throws: a copy that cannot be written is simply not there next time, and one that cannot
 * be read - a model that changed between versions - is as good as none.
 */
class OfflineCache(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {

    fun <T> save(name: String, serializer: KSerializer<T>, value: T) {
        runCatching {
            dir.mkdirs()
            val saved = buildJsonObject {
                put(SAVED_AT, now())
                put(VALUE, KAppApi.json.encodeToJsonElement(serializer, value))
            }
            file(name).writeText(saved.toString())
        }
    }

    fun <T> read(name: String, serializer: KSerializer<T>): Cached<T>? = runCatching {
        val saved = KAppApi.json.parseToJsonElement(file(name).readText()).jsonObject
        Cached(
            value = KAppApi.json.decodeFromJsonElement(serializer, saved.getValue(VALUE)),
            savedAt = saved.getValue(SAVED_AT).jsonPrimitive.long,
        )
    }.getOrNull()

    fun clear() {
        dir.deleteRecursively()
    }

    private fun file(name: String) = File(dir, "$name.json")

    private companion object {
        const val SAVED_AT = "savedAt"
        const val VALUE = "value"
    }
}
