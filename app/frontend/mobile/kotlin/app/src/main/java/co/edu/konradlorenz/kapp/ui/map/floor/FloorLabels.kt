package co.edu.konradlorenz.kapp.ui.map.floor

import co.edu.konradlorenz.kapp.data.map.FloorSpace

/**
 * Which labels a floor shows at a zoom, following the label rules in docs/design/MAP-STYLE.md.
 * The canvas measures the text; everything else is decided here, without Android.
 */
object FloorLabels {

    /**
     * `doorCode`, else `name`. Never `code`: a generated code looks exactly like a real one. A space
     * of the `OTHER` category with no door code has no label: it is drawn but not yet known, its
     * name says only that, and its colour already does.
     */
    fun text(space: FloorSpace): String? {
        space.doorCode?.takeIf { it.isNotBlank() }?.let { return it }
        if (space.category == UNIDENTIFIED) return null
        return space.name.takeIf { it.isNotBlank() }
    }

    /** `labelPoint` when the answer has it, else the centre of the space's bounds. */
    fun anchor(space: FloorSpace): Pair<Float, Float>? =
        space.labelPoint?.let { it.x.toFloat() to it.y.toFloat() }
            ?: space.box?.let { it.centreX to it.centreY }

    /** The text size at [zoom]: the smallest at the fit, growing to the largest by zoom 4. */
    fun sizeSp(zoom: Float, style: LabelsStyle): Float {
        val grown = ((zoom - 1f) / (FULL_SIZE_ZOOM - 1f)).coerceIn(0f, 1f)
        return style.minSizeSp + (style.maxSizeSp - style.minSizeSp) * grown
    }

    /**
     * The labels to draw out of [candidates]: in priority order, a label wider than its space is
     * left out, so is one not wholly inside [view] (the screen) rather than cut at its edge, and
     * so is one that would overlap a label already placed.
     */
    fun <T> place(
        candidates: List<LabelCandidate<T>>,
        hideWhenWiderThanSpace: Boolean = true,
        view: LabelBox? = null,
    ): List<LabelCandidate<T>> {
        val placed = mutableListOf<LabelCandidate<T>>()
        for (candidate in candidates.sortedBy { it.rank }) {
            if (hideWhenWiderThanSpace && candidate.box.width > candidate.spaceWidth) continue
            if (view != null && !view.contains(candidate.box)) continue
            if (placed.any { it.box.overlaps(candidate.box) }) continue
            placed += candidate
        }
        return placed
    }

    private const val FULL_SIZE_ZOOM = 4f

    /** The contract's category for a space drawn on a plan whose use nobody knows yet. */
    private const val UNIDENTIFIED = "OTHER"
}

/** A label that could be drawn: its [box] on screen, the width of its space there, and its rank. */
data class LabelCandidate<T>(val item: T, val box: LabelBox, val spaceWidth: Float, val rank: Int)

/** A rectangle on screen, in pixels. */
data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left

    fun overlaps(other: LabelBox) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** Whether [other] lies wholly inside this box. */
    fun contains(other: LabelBox) =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    companion object {
        /** A [width] x [height] box centred on ([x], [y]). */
        fun centredOn(x: Float, y: Float, width: Float, height: Float) =
            LabelBox(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
    }
}
