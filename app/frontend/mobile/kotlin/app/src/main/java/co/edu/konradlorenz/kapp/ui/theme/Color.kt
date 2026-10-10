package co.edu.konradlorenz.kapp.ui.theme

import androidx.compose.ui.graphics.Color

// The seven brand colours are the ones in docs/K-COLORS.md - they are the colours of the K, and
// nothing outside that file is invented here. The greys and tints below are the ones
// docs/design/mobile/Tokens.dc.html derives from them.

/** Pink. Actions, and nothing else: buttons, links, the active tab. */
val Action = Color(0xFFD51A65)

/** Pink over blue. The brand: the login band and the header on Inicio. */
val Brand = Color(0xFF522567)

/** Green. A course currently being taken. Never put white on it - 1.5:1. */
val InProgress = Color(0xFFC9D329)

/** Blue. A course, on the rail of its card. */
val Subject = Color(0xFF539392)

/** Green over blue. A course already passed. */
val Passed = Color(0xFF3E823E)

/** Pink over green. Errors. */
val ErrorRed = Color(0xFFB62325)

/** All three at once. The person: the profile shortcut and the profile tab. */
val Person = Color(0xFF592E2A)

val Background = Color(0xFFF7F4F8)
val Surface = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1C1420)
val TextSoft = Color(0xFF6B6472)
val Border = Color(0xFFE7E2EA)

/** A line too quiet to be a border: the edge of the floating bar, and the paler skeleton bars. */
val BorderSoft = Color(0xFFEFEBF1)

/** A course not taken yet. Same value as [Border]; the two roles are named apart on purpose. */
val Pending = Color(0xFFE7E2EA)

/**
 * A postponed course (*aplazada*), new in semaphore 2.0.0. Not one of the seven: issue #44 sends
 * it to docs/design/orchid-palette.md, whose active-section pair this is - the orchid background
 * and its dark text, 6.7:1. Orchid rather than the K's pink, which means "you can touch this".
 */
val Postponed = Color(0xFFE8A5D4)
val OnPostponed = Color(0xFF59134A)

// Login-only tints, read straight off LoginAndroid.dc.html.

/** Hint text inside an empty field. */
val Placeholder = Color(0xFFA29BA9)

/** White at 74%, for the subtitle sitting on the purple band. */
val OnBrandSoft = Color(0xBDFFFFFF)
