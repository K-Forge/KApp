package co.edu.konradlorenz.kapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Android and iOS share the scale and differ only in the family, and on Android that family is
// Roboto, which is already the default - so nothing has to be bundled or declared. Sizes and
// weights come from docs/design/mobile/Tokens.dc.html.
val KAppTypography = Typography(
    // "KApp" on the login band.
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    // A course name.
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    // A section heading.
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    // What is typed into a field, and a row's main line.
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    // Body copy.
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    // A row label.
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    // The caption above a field.
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    // The copyright line.
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)
