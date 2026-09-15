package co.edu.konradlorenz.kapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.theme.Brand

// Read off the band of docs/design/mobile/HomeAndroid.dc.html, which is drawn on a 360x800 canvas.
// The pixels there are dp here.

/** The side margin every screen inside the bar uses. */
val ScreenPadding = 16.dp

/** The band below the status bar. The 24 dp a card climbs back into it is the caller's business. */
val BandContentHeight = 80.dp

/**
 * The purple band at the top of every screen behind the bottom bar.
 *
 * Inicio draws it with the greeting and the student's initials; the other four have only their
 * name to put in it. It runs to the very top of the display and the system paints the status bar
 * over it, which is why MainActivity forces white status icons: they are always on purple.
 */
@Composable
fun BrandBand(title: String, trailing: @Composable (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand)
            .statusBarsPadding()
            .height(BandContentHeight)
            .padding(horizontal = ScreenPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.konrad_logo),
                    contentDescription = stringResource(R.string.app_logo_description),
                    modifier = Modifier.size(30.dp),
                    // The crest keeps its own colours.
                    tint = Color.Unspecified,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = Color.White,
                maxLines = 1,
            )
            trailing?.invoke()
        }
    }
}
