package co.edu.konradlorenz.kapp.data.map

import kotlinx.serialization.Serializable

/**
 * `FloorDetail` in docs/api/map.openapi.yaml, as far as the floor renderer reads it: what
 * `GET /api/map/buildings/{code}/floors/{floorCode}` answers. A drawing [width] x [height] units,
 * origin at the top left, `y` down. Units are not metres and differ from floor to floor.
 */
@Serializable
data class FloorPlan(
    val code: String,
    val level: Double,
    val name: String,
    val width: Int,
    val height: Int,
    /** The building's walls around the floor. Empty until somebody traces them. */
    val outline: List<MapPoint> = emptyList(),
    val corridors: List<FloorCorridor> = emptyList(),
    /** Every space on the floor, placed or not. */
    val spaces: List<FloorSpace> = emptyList(),
    val version: Long = 0,
) {
    /** The spaces with a shape to draw. The others are listed beside the drawing. */
    val placed: List<FloorSpace> get() = spaces.filter { it.shape.size >= 3 }

    /** The spaces on the floor that are not drawn yet: listed beside the drawing, never dropped. */
    val unplaced: List<FloorSpace> get() = spaces.filter { it.shape.size < 3 }

    /**
     * The placed space under the drawing point ([x], [y]), or null when there is none. Where shapes
     * overlap, the smallest wins: a room drawn over a bigger one is the one meant.
     */
    fun spaceAt(x: Float, y: Float): FloorSpace? =
        placed.filter { it.contains(x, y) }.minByOrNull { space -> space.box?.let { it.width.toLong() * it.height } ?: 0L }

    /**
     * The part of the drawing there is something on: the outline's box once it is traced, else the
     * box around every placed space, else the whole drawing. What the screen is fitted to, so a
     * floor drawn in a corner of a large photograph still fills the view.
     */
    val drawnBounds: MapBounds
        get() {
            val points = if (outline.size >= 3) outline else placed.flatMap { it.shape }
            if (points.isEmpty()) return MapBounds(0, 0, width, height)
            val left = points.minOf { it.x }
            val top = points.minOf { it.y }
            return MapBounds(left, top, points.maxOf { it.x } - left, points.maxOf { it.y } - top)
        }
}

/** A point on a floor's drawing, in the floor's own units. */
@Serializable
data class MapPoint(val x: Int, val y: Int)

/** The rectangle around a shape, in the floor's units. */
@Serializable
data class MapBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    val centreX: Float get() = x + width / 2f
    val centreY: Float get() = y + height / 2f
}

/** A door: the stretch of its space's outline it takes up, jamb to jamb. */
@Serializable
data class SpaceDoor(val from: MapPoint, val to: MapPoint)

/** A walkable route across the floor, through points in walking order. */
@Serializable
data class FloorCorridor(val code: String, val path: List<MapPoint> = emptyList())

/**
 * A space on the floor. Colours come from [category] and icons from [typeCode], never from
 * [code], which is not for display either: the label is [doorCode], else [name].
 */
@Serializable
data class FloorSpace(
    val code: String,
    val name: String,
    val typeCode: String,
    val doorCode: String? = null,
    val typeName: String? = null,
    /** One of the contract's closed list. A value this client does not know is drawn as `OTHER`. */
    val category: String? = null,
    /** The corners in order. Empty while the space is inventoried but not drawn. */
    val shape: List<MapPoint> = emptyList(),
    val bounds: MapBounds? = null,
    val doors: List<SpaceDoor> = emptyList(),
    /** Where the label and icon go: the point inside [shape] farthest from its walls (map 3.7.0). */
    val labelPoint: MapPoint? = null,
) {
    /** Whether the drawing point ([x], [y]) is inside [shape], by even-odd ray casting. */
    fun contains(x: Float, y: Float): Boolean {
        if (shape.size < 3) return false
        var inside = false
        shape.indices.forEach { i ->
            val a = shape[i]
            val b = shape[(i + shape.size - 1) % shape.size]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x).toFloat() * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }

    /** [bounds], or worked out from [shape] when the answer leaves it out. */
    val box: MapBounds?
        get() = bounds ?: shape.takeIf { it.isNotEmpty() }?.let { corners ->
            val left = corners.minOf { it.x }
            val top = corners.minOf { it.y }
            MapBounds(left, top, corners.maxOf { it.x } - left, corners.maxOf { it.y } - top)
        }
}
