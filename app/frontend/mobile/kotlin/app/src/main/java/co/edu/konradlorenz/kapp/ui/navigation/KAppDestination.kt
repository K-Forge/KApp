package co.edu.konradlorenz.kapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.Passed
import co.edu.konradlorenz.kapp.ui.theme.Person
import co.edu.konradlorenz.kapp.ui.theme.Subject

/**
 * The five screens behind the floating bar.
 *
 * Declared in the order docs/design/mobile/HomeAndroid.dc.html draws them, which is the order the
 * bar iterates: Inicio in the middle, not at the left edge. The shortcuts on Inicio use the same
 * four entries in the order that card draws them instead.
 *
 * [colour] is the colour of the K each destination answers to. It is not decoration: it is what
 * tells the four apart on the shortcut tiles, where the icon is the only thing distinguishing
 * them. Pink is absent on purpose - it means "you can touch this" and nothing else, which is why
 * the active tab is the one thing in the bar that is pink.
 */
enum class KAppDestination(
    val route: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val colour: Color,
) {
    Profile("profile", R.drawable.ic_person, R.string.home_shortcut_profile, Person),
    Semaphore("semaphore", R.drawable.ic_grid, R.string.home_shortcut_semaphore, Passed),
    Home("home", R.drawable.ic_home, R.string.home_tab_home, Brand),
    Map("map", R.drawable.ic_pin, R.string.home_shortcut_map, Brand),
    Schedule("schedule", R.drawable.ic_calendar, R.string.home_shortcut_schedule, Subject),
}
