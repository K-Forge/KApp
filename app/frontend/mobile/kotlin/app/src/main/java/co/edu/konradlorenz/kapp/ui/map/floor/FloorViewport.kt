package co.edu.konradlorenz.kapp.ui.map.floor

import co.edu.konradlorenz.kapp.data.map.MapBounds
import kotlin.math.min

/**
 * Where a floor's drawing sits on screen: a drawing point `(x, y)` is at `(x * scale + offsetX,
 * y * scale + offsetY)` in pixels. Every floor is in its own units, so a floor always starts
 * fitted to the screen and zoom is measured against that fit, never against the units.
 */
data class FloorViewport(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    /** The scale that fits the whole drawing: zoom 1. */
    val fitScale: Float = scale,
) {
    /** How far in from the fit: 1 at the start, up to [MAX_ZOOM]. */
    val zoom: Float get() = scale / fitScale

    fun screenX(x: Float) = x * scale + offsetX
    fun screenY(y: Float) = y * scale + offsetY

    fun panned(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /**
     * Zoomed by [factor] around the screen point ([pivotX], [pivotY]), which stays where it is
     * under the fingers. Never further out than the fit, nor further in than [MAX_ZOOM].
     */
    fun zoomed(factor: Float, pivotX: Float, pivotY: Float): FloorViewport {
        val next = (scale * factor).coerceIn(fitScale, fitScale * MAX_ZOOM)
        val applied = next / scale
        return copy(
            scale = next,
            offsetX = pivotX - (pivotX - offsetX) * applied,
            offsetY = pivotY - (pivotY - offsetY) * applied,
        )
    }

    /**
     * A double tap at ([x], [y]): twice as close around that point, or back to [fit] once it is as
     * close as it goes, so double taps alone can always get back out.
     */
    fun doubleTapped(x: Float, y: Float, fit: FloorViewport): FloorViewport =
        if (zoom >= MAX_ZOOM - ZOOM_EPSILON) fit else zoomed(DOUBLE_TAP_ZOOM, x, y)

    companion object {
        const val MAX_ZOOM = 8f

        /** How much a double tap zooms in. */
        const val DOUBLE_TAP_ZOOM = 2f

        /** How much the + and - buttons zoom in and out. */
        const val BUTTON_ZOOM = 1.5f

        private const val ZOOM_EPSILON = 0.01f

        /**
         * [bounds] of a drawing in a [viewWidth] x [viewHeight] view, [padding] pixels in from
         * every edge and centred.
         */
        fun fit(bounds: MapBounds, viewWidth: Float, viewHeight: Float, padding: Float): FloorViewport {
            val roomX = (viewWidth - 2 * padding).coerceAtLeast(1f)
            val roomY = (viewHeight - 2 * padding).coerceAtLeast(1f)
            val scale = min(roomX / bounds.width.coerceAtLeast(1), roomY / bounds.height.coerceAtLeast(1))
            return FloorViewport(
                scale = scale,
                offsetX = (viewWidth - bounds.width * scale) / 2 - bounds.x * scale,
                offsetY = (viewHeight - bounds.height * scale) / 2 - bounds.y * scale,
            )
        }
    }
}
