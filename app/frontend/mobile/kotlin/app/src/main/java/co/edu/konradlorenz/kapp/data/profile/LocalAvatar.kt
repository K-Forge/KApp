package co.edu.konradlorenz.kapp.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/**
 * The profile photo kept on this phone (issue #178). user.openapi.yaml takes a photo only as a URL
 * and has nowhere to upload one, so a photo picked here stays here until there is: in every build,
 * not only the demo, and the sheet that changes it says so.
 *
 * One pair of files per account, so whoever signs in next on the same phone does not inherit the
 * last person's face: the [photo] as cropped, and the [original] it was cropped from, so Ajustar
 * can start again from the whole picture.
 */
class LocalAvatar(context: Context) {

    private val folder = File(context.filesDir, "avatars")

    fun photo(userId: String): Bitmap? = read(file(userId, "photo"))

    fun original(userId: String): Bitmap? = read(file(userId, "original"))

    fun save(userId: String, photo: Bitmap, original: Bitmap) {
        folder.mkdirs()
        write(file(userId, "original"), original)
        write(file(userId, "photo"), photo)
    }

    fun remove(userId: String) {
        file(userId, "photo").delete()
        file(userId, "original").delete()
    }

    /** The id is the API's, a UUID or `visitor:<id>`: only its letters, digits and dashes are kept. */
    private fun file(userId: String, kind: String) =
        File(folder, "${userId.filter { it.isLetterOrDigit() || it == '-' }}-$kind.jpg")

    private fun read(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    private fun write(file: File, bitmap: Bitmap) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}

/**
 * A picture the person picked, at most [maxSide] pixels on its longer side - plenty for a 512-pixel
 * photo, and small enough to keep in memory while it is adjusted. Software-backed, so it can be
 * drawn onto a Bitmap's canvas when cropped. `null` if it cannot be read.
 */
fun decodePicture(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}.getOrNull()

/** The square [square] (left, top, side) of [original], as a [size]-pixel photo. */
fun cropPhoto(original: Bitmap, square: Triple<Int, Int, Int>, size: Int = 512): Bitmap {
    val (left, top, side) = square
    val photo = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(photo).drawBitmap(
        original,
        Rect(left, top, left + side, top + side),
        Rect(0, 0, size, size),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
    return photo
}
