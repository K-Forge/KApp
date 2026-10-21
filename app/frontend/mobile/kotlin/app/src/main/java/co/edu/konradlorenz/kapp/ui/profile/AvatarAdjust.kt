package co.edu.konradlorenz.kapp.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.theme.Brand

/** The side of Ajustar's frame. The circle inside it is the photo as Perfil will show it. */
private val FrameSize = 300.dp

/**
 * Ajustar (issue #178): the picked [picture] inside a round frame, to drag and pinch - or zoom with
 * the bar - until the face sits right. Guardar hands back the framing; AvatarCrop says which part of
 * the picture that keeps.
 */
@Composable
fun AvatarAdjustDialog(picture: Bitmap, onSave: (AvatarCrop) -> Unit, onCancel: () -> Unit) {
    val image = remember(picture) { picture.asImageBitmap() }
    val framePx = with(LocalDensity.current) { FrameSize.toPx() }
    var crop by remember(picture, framePx) { mutableStateOf(AvatarCrop(picture.width, picture.height, framePx)) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C142B))
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.avatar_adjust_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                stringResource(R.string.avatar_adjust_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Canvas(
                modifier = Modifier
                    .size(FrameSize)
                    // A canvas draws past its bounds; the enlarged picture must not cover the rest.
                    .clipToBounds()
                    .pointerInput(framePx) {
                        detectTransformGestures { _, pan, zoom, _ -> crop = crop.moved(zoom, pan.x, pan.y) }
                    },
            ) {
                val width = picture.width * crop.scale
                val height = picture.height * crop.scale
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        ((size.width - width) / 2 + crop.offsetX).toInt(),
                        ((size.height - height) / 2 + crop.offsetY).toInt(),
                    ),
                    dstSize = IntSize(width.toInt(), height.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
                // Everything outside the circle is dimmed: the photo is round on Perfil.
                val radius = size.minDimension / 2
                val circle = Rect(center = Offset(size.width / 2, size.height / 2), radius = radius)
                drawPath(
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(circle)
                    },
                    Color.Black.copy(alpha = 0.55f),
                )
                drawCircle(Color.White, radius, style = Stroke(width = 2.dp.toPx()))
            }
            Slider(
                value = crop.zoom,
                onValueChange = { crop = crop.moved(zoomBy = it / crop.zoom) },
                valueRange = 1f..AvatarCrop.MAX_ZOOM,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.avatar_adjust_cancel), color = Color.White)
                }
                Button(onClick = { onSave(crop) }, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.avatar_adjust_save))
                }
            }
        }
    }
}
