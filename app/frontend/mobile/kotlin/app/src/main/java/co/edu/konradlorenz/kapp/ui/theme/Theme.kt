package co.edu.konradlorenz.kapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light only, and no dynamic colour: the mockups define one appearance and the brand palette is
// the point of it. The slot each colour fills is the one Tokens.dc.html assigns it.
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

@Composable
fun KAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KAppColorScheme,
        typography = KAppTypography,
        content = content,
    )
}
