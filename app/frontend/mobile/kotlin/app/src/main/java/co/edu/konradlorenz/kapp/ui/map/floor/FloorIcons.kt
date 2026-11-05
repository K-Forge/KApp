package co.edu.konradlorenz.kapp.ui.map.floor

import androidx.annotation.DrawableRes
import co.edu.konradlorenz.kapp.R

/**
 * The app's drawable for each Material icon name `typeIcons` in map-style.json uses. The style
 * names the icon, not the type's drawable, so a new type that reuses an icon needs no code; a name
 * with no drawable here draws no icon, as a type with no entry in `typeIcons` does.
 */
object FloorIcons {

    private val drawables = mapOf(
        "accessible" to R.drawable.ic_map_accessible,
        "child_friendly" to R.drawable.ic_map_child_friendly,
        "computer" to R.drawable.ic_map_computer,
        "door_front" to R.drawable.ic_map_door_front,
        "elevator" to R.drawable.ic_map_elevator,
        "fitness_center" to R.drawable.ic_map_fitness_center,
        "local_library" to R.drawable.ic_map_local_library,
        "local_parking" to R.drawable.ic_map_local_parking,
        "medical_services" to R.drawable.ic_map_medical_services,
        "restaurant" to R.drawable.ic_map_restaurant,
        "stairs" to R.drawable.ic_map_stairs,
        "support_agent" to R.drawable.ic_map_support_agent,
        "theater_comedy" to R.drawable.ic_map_theater_comedy,
        "wc" to R.drawable.ic_map_wc,
    )

    @DrawableRes
    fun drawable(material: String): Int? = drawables[material]

    /** Each type in [style] that has an icon, with its drawable. */
    fun forTypes(style: MapStyle): Map<String, Int> =
        style.typeIcons.mapNotNull { (type, icon) -> drawable(icon.material)?.let { type to it } }.toMap()
}
