package co.edu.konradlorenz.kapp.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.common.BandContentHeight
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination
import co.edu.konradlorenz.kapp.ui.navigation.MainShell
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme

/**
 * Semaforo, Horario, Mapa and Perfil, until each one is built.
 *
 * One screen for the four of them on purpose: there is nothing to tell apart yet, and four
 * identical files would only be four files to delete. When a real screen is written it replaces
 * this destination's entry in KAppNavHost and the others keep using this.
 *
 * It borrows the band and the card of Inicio rather than inventing a look of its own: no mockup
 * covers these four, and a screen invented here would be one more thing to undo when one arrives.
 * The contract underneath is printed because it is the useful thing to know while it is empty.
 */
@Composable
fun PlaceholderScreen(destination: KAppDestination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrandBand(title = stringResource(destination.label))

        // The card climbs 24 dp back into the band, exactly as the one on Inicio does.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = BandContentHeight - 24.dp)
                .padding(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = BarContentPadding,
                )
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, CardShape, ambientColor = Brand, spotColor = Brand)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(destination.colour.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = destination.colour,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.placeholder_pending),
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(contractOf(destination)),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val CardShape = RoundedCornerShape(18.dp)

/**
 * The call each screen will be built on, out of docs/api/.
 *
 * Inicio is in the list because the enum is, not because it is ever drawn by this screen: it has
 * had a real screen since the mockup was translated.
 */
@StringRes
private fun contractOf(destination: KAppDestination): Int = when (destination) {
    KAppDestination.Profile -> R.string.placeholder_profile_contract
    KAppDestination.Semaphore -> R.string.placeholder_semaphore_contract
    KAppDestination.Map -> R.string.placeholder_map_contract
    KAppDestination.Schedule -> R.string.placeholder_schedule_contract
    KAppDestination.Home -> R.string.placeholder_home_contract
}

@Preview(name = "Semáforo", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun PlaceholderScreenPreview() {
    KAppTheme {
        MainShell(current = KAppDestination.Semaphore, onSelect = {}) {
            PlaceholderScreen(KAppDestination.Semaphore)
        }
    }
}
