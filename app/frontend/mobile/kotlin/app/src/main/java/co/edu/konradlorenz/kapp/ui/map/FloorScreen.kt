package co.edu.konradlorenz.kapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.map.floor.FloorCanvas
import co.edu.konradlorenz.kapp.ui.map.floor.MapStyle
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding

/**
 * A building's floors: its levels to choose from, and the chosen one drawn by [FloorCanvas]. Back
 * returns to the list of buildings. The map follows the system theme, as the shared style asks.
 */
@Composable
fun FloorScreen(opened: OpenedBuilding, style: MapStyle, onFloor: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val back = stringResource(R.string.map_back)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                Text("‹", fontSize = 22.sp)
            }
            Text(
                opened.building.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        LazyRow(
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
