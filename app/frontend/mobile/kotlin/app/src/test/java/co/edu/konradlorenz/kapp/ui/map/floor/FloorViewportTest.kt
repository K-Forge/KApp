package co.edu.konradlorenz.kapp.ui.map.floor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fitting a floor to the screen, and moving it with the fingers. */
class FloorViewportTest {

    // 400 x 240 units, as the contract's example floor, in a 1080 x 1920 phone with 40 px padding.
    private val fit = FloorViewport.fit(400, 240, 1080f, 1920f, padding = 40f)

    @Test
    fun `a wide floor fills the width and is centred`() {
        assertEquals(2.5f, fit.scale, DELTA)
        assertEquals(40f, fit.screenX(0f), DELTA)
        assertEquals(1040f, fit.screenX(400f), DELTA)
        assertEquals(960f, fit.screenY(120f), DELTA) // the middle of the floor, the middle of the view
    }

    @Test
    fun `a tall floor fills the height`() {
        val tall = FloorViewport.fit(300, 1110, 1080f, 1920f, padding = 40f)
        assertEquals(1840f / 1110, tall.scale, DELTA)
        assertEquals(40f, tall.screenY(0f), DELTA)
    }

    @Test
    fun `the units do not matter, only the shape`() {
        val small = FloorViewport.fit(40, 24, 1080f, 1920f, padding = 40f)
        assertEquals(fit.screenX(400f), small.screenX(40f), DELTA)
        assertEquals(fit.screenY(240f), small.screenY(24f), DELTA)
    }

    @Test
    fun `zooming keeps the point under the fingers where it is`() {
        val zoomed = fit.zoomed(2f, pivotX = 300f, pivotY = 900f)
        val x = (300f - fit.offsetX) / fit.scale
        val y = (900f - fit.offsetY) / fit.scale
        assertEquals(300f, zoomed.screenX(x), DELTA)
        assertEquals(900f, zoomed.screenY(y), DELTA)
        assertEquals(2f, zoomed.zoom, DELTA)
    }

    @Test
    fun `zoom stops at the fit and at eight times it`() {
        assertEquals(1f, fit.zoomed(0.2f, 0f, 0f).zoom, DELTA)
        assertEquals(FloorViewport.MAX_ZOOM, fit.zoomed(100f, 0f, 0f).zoom, DELTA)
    }

    @Test
    fun `panning moves the drawing by the fingers' distance`() {
        val panned = fit.panned(-50f, 30f)
        assertEquals(fit.screenX(10f) - 50f, panned.screenX(10f), DELTA)
        assertEquals(fit.screenY(10f) + 30f, panned.screenY(10f), DELTA)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
