package co.edu.konradlorenz.kapp.ui.map.floor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.data.map.FloorPlan
import co.edu.konradlorenz.kapp.data.map.MapPoint
import co.edu.konradlorenz.kapp.ui.common.hexColor

/**
 * Any floor, drawn from what the map API answers with the shared style (docs/design/MAP-STYLE.md):
 * nothing here knows a building, a floor or a room. It starts fitted to the view, and pans and
 * pinch-zooms. The layers follow the style's order; icons and selection come in a later change.
 */
@Composable
fun FloorCanvas(plan: FloorPlan, style: MapStyle, dark: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val padding = with(LocalDensity.current) { FloorPadding.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(plan, size) {
        if (size == IntSize.Zero) null
        else FloorViewport.fit(plan.width, plan.height, size.width.toFloat(), size.height.toFloat(), padding)
    }
    // Back to the fit whenever the floor or the view changes.
    var viewport by remember(fit) { mutableStateOf(fit) }
    val palette = remember(style, dark) { FloorPalette(style, dark) }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(fit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport = viewport?.zoomed(zoom, centroid.x, centroid.y)?.panned(pan.x, pan.y)
                }
            },
    ) {
        drawRect(palette.background)
        val view = viewport ?: return@Canvas
        style.layers.forEach { layer ->
            when (layer) {
                "floor" -> drawFloor(plan, view, palette)
                "spaces" -> drawSpaces(plan, view, palette, style)
                "corridors" -> plan.corridors.filter { it.path.size >= 2 }.forEach {
                    drawPath(
                        path(it.path, view, close = false),
                        palette.corridor,
                        style = Stroke(style.corridors.widthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                "doors" -> plan.placed.flatMap { it.doors }.forEach {
                    drawLine(palette.door, view.at(it.from), view.at(it.to), strokeWidth = style.doors.widthDp.dp.toPx())
                }
                "outline" -> if (plan.outline.size >= 3) {
                    drawPath(path(plan.outline, view), palette.outline, style = Stroke(style.outline.widthDp.dp.toPx()))
                }
                "labels" -> drawLabels(plan, view, palette, style, measurer)
            }
        }
    }
}

/** The floor's outline filled; a floor nobody has traced yet is its whole drawing. */
private fun DrawScope.drawFloor(plan: FloorPlan, view: FloorViewport, palette: FloorPalette) {
    if (plan.outline.size >= 3) {
        drawPath(path(plan.outline, view), palette.floor)
    } else {
        drawRect(
            palette.floor,
            topLeft = Offset(view.screenX(0f), view.screenY(0f)),
            size = Size(plan.width * view.scale, plan.height * view.scale),
        )
    }
}

/** Each placed space filled by its category, with a thin stroke so neighbours stay apart. */
private fun DrawScope.drawSpaces(plan: FloorPlan, view: FloorViewport, palette: FloorPalette, style: MapStyle) {
    val stroke = Stroke(style.spaces.strokeWidthDp.dp.toPx())
    plan.placed.forEach { space ->
        val shape = path(space.shape, view)
        drawPath(shape, palette.category(space.category))
        drawPath(shape, palette.spaceStroke, style = stroke)
    }
}

/** The labels that fit at this zoom, with a halo, in the style's priority. */
private fun DrawScope.drawLabels(
    plan: FloorPlan,
    view: FloorViewport,
    palette: FloorPalette,
    style: MapStyle,
    measurer: TextMeasurer,
) {
    val text = TextStyle(fontSize = FloorLabels.sizeSp(view.zoom, style.labels).sp, fontWeight = FontWeight.Medium)
    val candidates = plan.placed.mapNotNull { space ->
        val label = FloorLabels.text(space) ?: return@mapNotNull null
        val (x, y) = FloorLabels.anchor(space) ?: return@mapNotNull null
        val box = space.box ?: return@mapNotNull null
        val layout = measurer.measure(label, style = text, maxLines = 1, softWrap = false)
        LabelCandidate(
            item = layout,
            box = LabelBox.centredOn(
                view.screenX(x), view.screenY(y), layout.size.width.toFloat(), layout.size.height.toFloat(),
            ),
            spaceWidth = box.width * view.scale,
            rank = style.labelRank(space),
        )
    }
    val halo = Stroke(width = HaloWidth.toPx(), join = StrokeJoin.Round)
    FloorLabels.place(candidates, style.labels.hideWhenWiderThanSpace).forEach { label ->
        val topLeft = Offset(label.box.left, label.box.top)
        drawText(label.item, color = palette.labelHalo, topLeft = topLeft, drawStyle = halo)
        // Fill said outright: without it the text keeps the halo's stroke, and the label is a blot.
        drawText(label.item, color = palette.label, topLeft = topLeft, drawStyle = Fill)
    }
}

private fun FloorViewport.at(point: MapPoint) = Offset(screenX(point.x.toFloat()), screenY(point.y.toFloat()))

private fun path(points: List<MapPoint>, view: FloorViewport, close: Boolean = true) = Path().apply {
    points.forEachIndexed { i, point ->
        val at = view.at(point)
        if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
    }
    if (close) close()
}

/** The style's colours for one theme, read once rather than on every frame. */
private class FloorPalette(style: MapStyle, dark: Boolean) {
    val background = color(style.floor.pick(dark).background)
    val floor = color(style.floor.pick(dark).fill)
    val spaceStroke = color(style.spaces.stroke(dark))
    val corridor = color(style.corridors.color(dark))
    val door = color(style.doors.color(dark))
    val outline = color(style.outline.color(dark))
    val label = color(style.labels.colors(dark).color)
    val labelHalo = color(style.labels.colors(dark).halo)
    private val categories = style.categories.mapValues { color(it.value.pick(dark)) }
    private val fallback = color(style.categoryColor(null, dark))

    fun category(category: String?): Color = category?.let { categories[it] } ?: fallback

    private fun color(hex: String) = hexColor(hex, Color.Magenta)
}

private val FloorPadding = 16.dp
private val HaloWidth = 3.dp
