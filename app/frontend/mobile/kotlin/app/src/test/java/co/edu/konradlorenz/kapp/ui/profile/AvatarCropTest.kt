package co.edu.konradlorenz.kapp.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ajustar's arithmetic: what part of the picture the photo keeps. */
class AvatarCropTest {

    @Test
    fun `at first the picture's centred square is kept`() {
        // A landscape 2000 x 1000: its centred 1000 x 1000.
        assertEquals(Triple(500, 0, 1000), AvatarCrop(2000, 1000, frame = 300f).sourceSquare())
        // A portrait 1000 x 1600: its centred 1000 x 1000.
        assertEquals(Triple(0, 300, 1000), AvatarCrop(1000, 1600, frame = 300f).sourceSquare())
    }

    @Test
    fun `zooming in keeps a smaller square around the same centre`() {
        val crop = AvatarCrop(1000, 1000, frame = 300f).moved(zoomBy = 2f)
        assertEquals(Triple(250, 250, 500), crop.sourceSquare())
    }

    @Test
    fun `dragging the picture right shows more of its left`() {
        // At zoom 2, a frame pixel is 1/0.6 picture pixels; 60 frame pixels are 100 picture pixels.
        val crop = AvatarCrop(1000, 1000, frame = 300f).moved(zoomBy = 2f, dx = 60f)
        assertEquals(Triple(150, 250, 500), crop.sourceSquare())
    }

    @Test
    fun `the picture never moves past the frame's edge`() {
        val crop = AvatarCrop(1000, 1000, frame = 300f).moved(zoomBy = 2f, dx = 10_000f, dy = -10_000f)
        assertEquals(Triple(0, 500, 500), crop.sourceSquare())
        // At zoom 1 a square picture fills the frame and cannot move at all.
        assertEquals(0f, AvatarCrop(1000, 1000, frame = 300f).moved(dx = 50f).offsetX)
    }

    @Test
    fun `zoom stays between 1 and the maximum`() {
        assertEquals(1f, AvatarCrop(800, 600, frame = 300f).moved(zoomBy = 0.2f).zoom)
        assertEquals(AvatarCrop.MAX_ZOOM, AvatarCrop(800, 600, frame = 300f).moved(zoomBy = 50f).zoom)
    }
}
