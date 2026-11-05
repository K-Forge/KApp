package co.edu.konradlorenz.kapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light everywhere but on a floor of Mapa (below), and no dynamic colour: the mockups define one
// appearance and the brand palette is the point of it. The slot each colour fills is the one
// Tokens.dc.html assigns it.
private val KAppColorScheme = lightColorScheme(
    primary = Action,
    onPrimary = Surface,
    secondary = Brand,
    onSecondary = Surface,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Background,
    onSurfaceVariant = TextSoft,
    outline = TextSoft,
    outlineVariant = Border,
    error = ErrorRed,
    onError = Surface,
)

// Dark, for the one screen that follows the system theme: a floor of Mapa, whose drawing does
// (docs/design/MAP-STYLE.md). The greys are the dark values of docs/design/map-style.json, so the
// chips, the buttons and the bar around the map are the map's own colours; the pink is the
// style's dark selection colour.
private val KAppDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF06A9E),
    onPrimary = Color(0xFF1C2128),
    secondary = Brand,
    onSecondary = Surface,
    background = Color(0xFF1C2128),
    onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF2A2F36),
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFF9AA4B0),
    outline = Color(0xFF9AA4B0),
    outlineVariant = Color(0xFF353B43),
    error = ErrorRed,
    onError = Surface,
)

/** The app's theme: light, except where a screen asks for [dark] (see [KAppDarkColorScheme]). */
@Composable
fun KAppTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) KAppDarkColorScheme else KAppColorScheme,
        typography = KAppTypography,
        content = content,
    )
}
