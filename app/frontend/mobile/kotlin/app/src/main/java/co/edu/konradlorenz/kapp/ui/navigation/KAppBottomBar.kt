package co.edu.konradlorenz.kapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.theme.BorderSoft
import co.edu.konradlorenz.kapp.ui.theme.Brand

// Read off the bar of docs/design/mobile/HomeAndroid.dc.html, drawn on a 360x800 canvas.
private val BarHeight = 62.dp
private val BarBottomInset = 24.dp
private val BarShape = RoundedCornerShape(22.dp)
private val TabShape = RoundedCornerShape(16.dp)

/**
 * What a screen has to leave free at its bottom so the bar does not cover its last row.
 *
 * The bar floats over the content rather than pushing it up, which is the whole point of the
 * shape: a card can scroll under it and still be reachable.
 */
val BarContentPadding = BarHeight + BarBottomInset + 14.dp

/**
 * A screen with the floating bar over it.
 *
 * The bar is drawn after the content and inside the same box, so it sits on top of whatever
 * scrolls under it. Login and the invitation code are outside this: they are not places the bar
 * can take you, and showing it there would offer four destinations to somebody who has not signed
 * in yet.
 */
@Composable
fun MainShell(
    current: KAppDestination,
    onSelect: (KAppDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        KAppBottomBar(
            current = current,
            onSelect = onSelect,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun KAppBottomBar(
    current: KAppDestination,
    onSelect: (KAppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = ScreenPadding, end = ScreenPadding, bottom = BarBottomInset)
            .fillMaxWidth()
            .height(BarHeight)
            .shadow(12.dp, BarShape, ambientColor = Brand, spotColor = Brand)
            .clip(BarShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, BorderSoft, BarShape)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KAppDestination.entries.forEach { destination ->
            Tab(
                destination = destination,
                active = destination == current,
                onClick = { onSelect(destination) },
            )
        }
    }
}

/** The active tab carries Material's pill; the other four are the icon and its label. */
@Composable
private fun RowScope.Tab(
    destination: KAppDestination,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colour = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(TabShape)
            // selectable rather than clickable: a tab is one of a set, and this is what makes
            // TalkBack say "selected" instead of reading five buttons that all look alike.
            .selectable(selected = active, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The pill is 56 wide; an inactive tab is the same 32 dp box with nothing painted in it.
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(if (active) 56.dp else 32.dp)
                .clip(TabShape)
                .background(if (active) colour.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(destination.icon),
                // The label below says the same thing.
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colour,
            )
        }
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = colour,
            maxLines = 1,
        )
    }
}
