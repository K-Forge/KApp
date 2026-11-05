package co.edu.konradlorenz.kapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.map.floor.FloorCanvas
import co.edu.konradlorenz.kapp.ui.map.floor.MapStyle
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding

/**
 * A building's floors: the purple band with the building's name, as on every tab (so the white
 * status bar icons stay readable), its levels to choose from, and the chosen one drawn by
 * [FloorCanvas] over all the room left above the bottom bar. Back returns to the list of buildings.
 * The screen follows the system theme, as the shared style asks; KAppNavHost darkens the bar too.
 */
@Composable
fun FloorScreen(opened: OpenedBuilding, style: MapStyle, onFloor: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val back = stringResource(R.string.map_back)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrandBand(
            title = opened.building.name,
            leading = {
                TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                    Text("‹", fontSize = 26.sp, color = Color.White)
                }
            },
        )
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(opened.building.floors, key = { it.code }) { level ->
                FilterChip(
                    selected = level.code == opened.floorCode,
                    onClick = { onFloor(level.code) },
                    label = { Text(level.name) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
                .padding(bottom = BarContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val floor = opened.floor) {
                FloorState.Loading -> CircularProgressIndicator()
                FloorState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.map_floor_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onFloor(opened.floorCode) }) { Text(stringResource(R.string.map_retry)) }
                }
                is FloorState.Ready -> {
                    FloorCanvas(floor.plan, style, isSystemInDarkTheme(), Modifier.fillMaxSize().clipToBounds())
                    if (floor.plan.placed.isEmpty()) {
                        Text(
                            stringResource(R.string.map_floor_not_drawn),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(ScreenPadding),
                        )
                    }
                }
            }
        }
    }
}
