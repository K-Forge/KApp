package co.edu.konradlorenz.kapp.ui.map.floor

import co.edu.konradlorenz.kapp.data.map.FloorSpace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * docs/design/map-style.json, the style every KApp client draws a floor with. The app keeps a copy
 * in src/main/assets/map-style.json; MapStyleTest fails when the two drift apart. Colours stay
 * `#RRGGBB` strings here, so this file has no Android in it and is tested on the JVM.
 */
@Serializable
data class MapStyle(
    val version: Int,
    val layers: List<String>,
    val floor: Themed<FloorColors>,
    val outline: StrokeStyle,
    val spaces: SpacesStyle,
    val categories: Map<String, Themed<String>>,
    val fallbackCategory: String,
    val typeIcons: Map<String, TypeIcon> = emptyMap(),
    val icons: IconsStyle,
    val labels: LabelsStyle,
    val doors: StrokeStyle,
    val corridors: StrokeStyle,
    val selection: SelectionStyle,
) {
    /** The fill of a space of [category]: an unknown or missing one is [fallbackCategory]. */
    fun categoryColor(category: String?, dark: Boolean): String =
        (categories[category] ?: categories.getValue(fallbackCategory)).pick(dark)

    /**
     * Where a space's label stands in [LabelsStyle.priority]: lower wins a collision. A type with
     * an icon ranks as `typeIcons`; any other space by its category.
     */
    fun labelRank(space: FloorSpace): Int {
        val category = space.category
        val key = when {
            space.typeCode in typeIcons -> "typeIcons"
            category != null && category in categories -> category
            else -> fallbackCategory
        }
        return labels.priority.indexOf(key).takeIf { it >= 0 } ?: labels.priority.size
    }

    companion object {
        /** Where the app keeps its copy, in src/main/assets. */
        const val ASSET = "map-style.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): MapStyle = json.decodeFromString(text)
    }
}

/** A value with a light and a dark version. Clients follow the system theme. */
@Serializable
data class Themed<T>(val light: T, val dark: T) {
    fun pick(dark: Boolean): T = if (dark) this.dark else light
}

@Serializable
data class FloorColors(val fill: String, val background: String)

@Serializable
data class StrokeColor(val stroke: String)

@Serializable
data class StrokeStyle(val light: StrokeColor, val dark: StrokeColor, val widthDp: Float) {
    fun color(dark: Boolean) = (if (dark) this.dark else light).stroke
}

@Serializable
data class SpacesStyle(val light: StrokeColor, val dark: StrokeColor, val strokeWidthDp: Float) {
    fun stroke(dark: Boolean) = (if (dark) this.dark else light).stroke
}

@Serializable
data class TypeIcon(val material: String, val sfSymbol: String)

@Serializable
data class IconColors(val fill: String, val glyph: String)

/** The round mark a typed space carries: [sizeDp] across, a [IconColors.glyph] on a [IconColors.fill]. */
@Serializable
data class IconsStyle(val sizeDp: Float, val light: IconColors, val dark: IconColors) {
    fun colors(dark: Boolean) = if (dark) this.dark else light
}

@Serializable
data class LabelColors(val color: String, val halo: String)

@Serializable
data class LabelsStyle(
    val light: LabelColors,
    val dark: LabelColors,
    val minSizeSp: Float,
    val maxSizeSp: Float,
    val hideWhenWiderThanSpace: Boolean = true,
    val priority: List<String> = emptyList(),
) {
    fun colors(dark: Boolean) = if (dark) this.dark else light
}

@Serializable
data class SelectionColors(val stroke: String, val fillAlpha: Float)

@Serializable
data class SelectionStyle(val light: SelectionColors, val dark: SelectionColors, val widthDp: Float) {
    fun colors(dark: Boolean) = if (dark) this.dark else light
}

/** `#RRGGBB` as an opaque ARGB value, or null when it is not one. */
fun argbOf(hex: String): Long? = hex.removePrefix("#")
    .takeIf { it.length == 6 }
    ?.toLongOrNull(16)
    ?.let { 0xFF000000 or it }
