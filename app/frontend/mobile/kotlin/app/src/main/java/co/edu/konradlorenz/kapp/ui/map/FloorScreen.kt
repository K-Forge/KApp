package co.edu.konradlorenz.kapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.map.FloorSpace
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.map.floor.FloorCanvas
import co.edu.konradlorenz.kapp.ui.map.floor.MapStyle
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.theme.tones

/**
 * A building's floors: the purple band with the building's name, as on every tab (so the white
 * status bar icons stay readable), its levels to choose from, and the chosen one drawn by
 * [FloorCanvas] over all the room left above the bottom bar. Tapping a space selects it and shows
 * its name, type and door code over the drawing. Back clears the selection first, then returns to
 * the list of buildings.
 * The drawing follows the app's theme, which follows the system, as the shared style asks.
 */
@Composable
fun FloorScreen(opened: OpenedBuilding, style: MapStyle, onFloor: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // By code, so it survives a rotation; a new floor starts with nothing selected.
    var selected by rememberSaveable(opened.building.code, opened.floorCode) { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
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
        (opened.floor as? FloorState.Ready)?.plan?.unplaced?.takeIf { it.isNotEmpty() }?.let { UnplacedSpaces(it) }
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
                    FloorCanvas(
                        floor.plan,
                        style,
                        tones.dark,
                        Modifier.fillMaxSize().clipToBounds(),
                        selected = selected,
                        onSelect = { selected = it?.code },
                    )
                    floor.plan.placed.firstOrNull { it.code == selected }?.let { space ->
                        SelectedSpace(space, onClose = { selected = null }, modifier = Modifier.align(Alignment.TopCenter))
                    }
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

/**
 * The selected space: its name, its type unless the name already says it, and its door code when
 * it has one, on a card over the top of the drawing, clear of the zoom buttons. Read out when it
 * changes.
 */
@Composable
private fun SelectedSpace(space: FloorSpace, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val close = stringResource(R.string.map_space_close)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(space.name, style = MaterialTheme.typography.titleMedium)
                // A cafeteria named "Cafetería" says it once.
                space.typeName?.takeIf { it.isNotBlank() && !it.equals(space.name, ignoreCase = true) }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                space.doorCode?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.map_space_door, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = close }) {
                Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The spaces on this floor that are not drawn yet, which the contract says to list rather than
 * drop: a line under the levels, and the list itself behind it, labelled as the drawing is.
 */
@Composable
private fun UnplacedSpaces(spaces: List<FloorSpace>) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(pluralStringResource(R.plurals.map_unplaced, spaces.size, spaces.size))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.map_unplaced_close)) } },
            title = { Text(stringResource(R.string.map_unplaced_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(spaces, key = { it.code }) { space ->
                        Column {
                            Text(space.doorCode?.takeIf { it.isNotBlank() } ?: space.name, style = MaterialTheme.typography.bodyLarge)
                            space.typeName?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
        )
    }
}
