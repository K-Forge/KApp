package co.edu.konradlorenz.kapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Light and dark, following the system with no toggle in the app (issue #204), and no dynamic
// colour: the mockups define the appearance and the brand palette is the point of it. The slot
// each light colour fills is the one Tokens.dc.html assigns it.
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

// The greys are the dark values of docs/design/map-style.json, so the map and the rest of the app
// are one surface. The pink is the style's dark selection colour (5.6:1 on the background); the
// purple and the red are the brand's own, made light enough to read on these greys (toned).
private val DarkBackground = Color(0xFF1C2128)
private val DarkSurface = Color(0xFF2A2F36)
private val DarkText = Color(0xFFF1F3F5)
private val DarkTextSoft = Color(0xFF9AA4B0)

private val KAppDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF06A9E),
    onPrimary = DarkBackground,
    secondary = Brand.toned(dark = true),
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSoft,
    outline = DarkTextSoft,
    outlineVariant = Color(0xFF353B43),
    error = ErrorRed.toned(dark = true),
    onError = DarkBackground,
)

/** The few tones Material's scheme has no slot for, in light and in dark. */
class KAppTones(
    /** A line too quiet to be a border, and the paler skeleton bars. */
    val borderSoft: Color,
    /** Hint text inside an empty field. */
    val placeholder: Color,
    val dark: Boolean,
)

private val LightTones = KAppTones(borderSoft = BorderSoft, placeholder = Placeholder, dark = false)
private val DarkTones = KAppTones(
    borderSoft = Color(0xFF30363E),
    // 4.6:1 on the dark surface.
    placeholder = Color(0xFF8E97A2),
    dark = true,
)

private val LocalTones = staticCompositionLocalOf { LightTones }

/** The current theme's [KAppTones]. */
val tones: KAppTones
    @Composable
    @ReadOnlyComposable
    get() = LocalTones.current

/**
 * A brand colour as it reads on the current theme's surfaces: itself in light, and half way to
 * white in dark, where the K's deep purple, brown and red would sink into the grey (4.8:1
 * instead of 1.2:1 for the purple on the dark surface). For text, icons and the tints behind them.
 */
@Composable
@ReadOnlyComposable
fun Color.toned(): Color = toned(LocalTones.current.dark)

private fun Color.toned(dark: Boolean): Color = if (dark) lerp(this, Color.White, 0.5f) else this

/** The app's theme, light or dark as the system is. */
@Composable
fun KAppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTones provides if (dark) DarkTones else LightTones) {
        MaterialTheme(
            colorScheme = if (dark) KAppDarkColorScheme else KAppColorScheme,
            typography = KAppTypography,
            content = content,
        )
    }
}
