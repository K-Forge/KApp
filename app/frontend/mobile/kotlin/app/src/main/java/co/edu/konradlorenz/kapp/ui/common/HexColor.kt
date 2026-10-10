package co.edu.konradlorenz.kapp.ui.common

import androidx.compose.ui.graphics.Color

/**
 * A `#RRGGBB` the API sends - a subject's colour, an area's - as a colour to paint, or [fallback]
 * when it is not one. The client paints these; it never picks them.
 */
fun hexColor(hex: String?, fallback: Color): Color = hex
    ?.removePrefix("#")
    ?.takeIf { it.length == 6 }
    ?.toLongOrNull(16)
    ?.let { Color(0xFF000000 or it) }
    ?: fallback
