package co.edu.konradlorenz.kapp.ui.profile

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a picture sits inside Ajustar's square frame, and which part of it the photo keeps. Pure,
 * so the arithmetic is unit-tested without a screen.
 *
 * At [zoom] 1 the picture just covers the frame, its shorter side edge to edge, centred. [zoom]
 * enlarges it from there, and [offsetX]/[offsetY] move it, in frame pixels, from centred.
 */
data class AvatarCrop(
    val imageWidth: Int,
    val imageHeight: Int,
    val frame: Float,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    /** Frame pixels per picture pixel. */
    val scale: Float get() = frame / min(imageWidth, imageHeight) * zoom

    /**
     * The same crop after a pinch or a drag, kept within bounds: zoom between 1 and [MAX_ZOOM],
     * and never moved so far that the frame shows past the picture's edge.
     */
    fun moved(zoomBy: Float = 1f, dx: Float = 0f, dy: Float = 0f): AvatarCrop {
        val zoomed = copy(zoom = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM))
        val maxX = (imageWidth * zoomed.scale - frame) / 2
        val maxY = (imageHeight * zoomed.scale - frame) / 2
        return zoomed.copy(
            offsetX = (offsetX + dx).coerceIn(-maxX, maxX),
            offsetY = (offsetY + dy).coerceIn(-maxY, maxY),
        )
    }

    /** The part of the picture inside the frame, in picture pixels: left, top and side. */
    fun sourceSquare(): Triple<Int, Int, Int> {
        val side = frame / scale
        val left = (imageWidth - side) / 2 - offsetX / scale
        val top = (imageHeight - side) / 2 - offsetY / scale
        val clampedSide = min(side, min(imageWidth, imageHeight).toFloat())
        // Rounded, not truncated: float arithmetic lands a pixel short (999.99) as often as not.
        return Triple(
            left.coerceIn(0f, max(0f, imageWidth - clampedSide)).roundToInt(),
            top.coerceIn(0f, max(0f, imageHeight - clampedSide)).roundToInt(),
            clampedSide.roundToInt(),
        )
    }

    companion object {
        const val MAX_ZOOM = 4f
    }
}
