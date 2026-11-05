package co.edu.konradlorenz.kapp.ui.map.floor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.map.FloorPlan
import co.edu.konradlorenz.kapp.data.map.FloorSpace
import co.edu.konradlorenz.kapp.data.map.MapPoint
import co.edu.konradlorenz.kapp.ui.common.hexColor

/**
 * Any floor, drawn from what the map API answers with the shared style (docs/design/MAP-STYLE.md):
 * nothing here knows a building, a floor or a room. It starts fitted to what is drawn, and pans,
 * pinch-zooms, zooms in on a double tap and with its + and - buttons. The layers follow the
 * style's order and any it does not know are skipped, so a later one, such as a route, only needs
 * its name in the style and its case here. A tap on a space hands it to [onSelect], a tap on
 * nothing hands null, and the space whose code is [selected] is outlined on top of everything.
 */
@Composable
fun FloorCanvas(
    plan: FloorPlan,
    style: MapStyle,
    dark: Boolean,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: (FloorSpace?) -> Unit = {},
) {
    // The tap handler outlives a recomposition when the fit stays the same, as it does between
    // floors that share a traced outline, so it reads the floor and the callback as they are now.
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(plan)
    val measurer = rememberTextMeasurer()
    val padding = with(LocalDensity.current) { FloorPadding.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(plan, size) {
        if (size == IntSize.Zero) null
        else FloorViewport.fit(plan.drawnBounds, size.width.toFloat(), size.height.toFloat(), padding)
    }
    // Back to the fit whenever the floor or the view changes.
    var viewport by remember(fit) { mutableStateOf(fit) }
    val palette = remember(style, dark) { FloorPalette(style, dark) }
    val icons = FloorIcons.forTypes(style).mapValues { (_, drawable) ->
        rememberVectorPainter(ImageVector.vectorResource(drawable))
    }
    val zoomAroundCentre = { factor: Float ->
        viewport = viewport?.zoomed(factor, size.width / 2f, size.height / 2f)
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(fit) {
                    detectTapGestures(
                        onTap = { at ->
                            val view = viewport ?: return@detectTapGestures
                            select(current.spaceAt(view.drawingX(at.x), view.drawingY(at.y)))
                        },
                        onDoubleTap = { at ->
                            val start = fit ?: return@detectTapGestures
                            viewport = viewport?.doubleTapped(at.x, at.y, start)
                        },
                    )
                }
                .pointerInput(fit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport?.zoomed(zoom, centroid.x, centroid.y)?.panned(pan.x, pan.y)
                    }
                },
        ) {
            // A floor nobody has traced yet has no outside: all of it is floor, not a box on a
            // background.
            drawRect(if (plan.outline.size >= 3) palette.background else palette.floor)
            val view = viewport ?: return@Canvas
            // Icons and labels make room for each other, so both layers draw from one placement.
            val marks = if ("icons" in style.layers || "labels" in style.layers) {
                placeMarks(plan, view, style, measurer, icons)
            } else {
                emptyList()
            }
            style.layers.forEach { layer ->
                when (layer) {
                    "floor" -> if (plan.outline.size >= 3) drawPath(path(plan.outline, view), palette.floor)
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
                    "icons" -> marks.forEach { mark ->
                        val box = mark.icon ?: return@forEach
                        mark.item.icon?.let { drawIcon(it, box, palette) }
                    }
                    "labels" -> drawLabels(marks, palette)
                    "selection" -> plan.placed.firstOrNull { it.code == selected }?.let { space ->
                        val shape = path(space.shape, view)
                        drawPath(shape, palette.selection.copy(alpha = palette.selectionFillAlpha))
                        drawPath(shape, palette.selection, style = Stroke(style.selection.widthDp.dp.toPx(), join = StrokeJoin.Round))
                    }
                }
            }
        }
        // Pinching is not the only way in: + and - for whoever cannot pinch.
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton("+", stringResource(R.string.map_zoom_in)) { zoomAroundCentre(FloorViewport.BUTTON_ZOOM) }
            ZoomButton("−", stringResource(R.string.map_zoom_out)) { zoomAroundCentre(1 / FloorViewport.BUTTON_ZOOM) }
        }
    }
}

@Composable
private fun ZoomButton(symbol: String, description: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

/** What a space shows at its anchor: its type's icon, its label, or both. */
private class Mark(val icon: Painter?, val label: TextLayoutResult?)

/**
 * The icons and labels that fit at this zoom, in the style's priority: an icon at the anchor with
 * the label just below it, or the label alone centred there.
 */
private fun DrawScope.placeMarks(
    plan: FloorPlan,
    view: FloorViewport,
    style: MapStyle,
    measurer: TextMeasurer,
    icons: Map<String, Painter>,
): List<LabelCandidate<Mark>> {
    val text = TextStyle(fontSize = FloorLabels.sizeSp(view.zoom, style.labels).sp, fontWeight = FontWeight.Medium)
    val iconSize = style.icons.sizeDp.dp.toPx()
    val gap = LabelGap.toPx()
    val candidates = plan.placed.mapNotNull { space ->
        val (x, y) = FloorLabels.anchor(space) ?: return@mapNotNull null
        val box = space.box ?: return@mapNotNull null
        val painter = icons[space.typeCode]
        val layout = FloorLabels.text(space)?.let { measurer.measure(it, style = text, maxLines = 1, softWrap = false) }
        if (painter == null && layout == null) return@mapNotNull null
        val icon = painter?.let { LabelBox.centredOn(view.screenX(x), view.screenY(y), iconSize, iconSize) }
        val label = layout?.let {
            val width = it.size.width.toFloat()
            val height = it.size.height.toFloat()
            if (icon != null) FloorLabels.below(icon, width, height, gap)
            else LabelBox.centredOn(view.screenX(x), view.screenY(y), width, height)
        }
        LabelCandidate(Mark(painter, layout), label, box.width * view.scale, style.labelRank(space), icon)
    }
    val screen = LabelBox(0f, 0f, size.width, size.height)
    return FloorLabels.place(candidates, style.labels.hideWhenWiderThanSpace, screen)
}

/** A round mark in the icons' fill with the type's glyph on it, tinted. */
private fun DrawScope.drawIcon(painter: Painter, box: LabelBox, palette: FloorPalette) {
    val centre = Offset((box.left + box.right) / 2, (box.top + box.bottom) / 2)
    drawCircle(palette.iconFill, radius = box.width / 2, center = centre)
    val glyph = box.width * GlyphShare
    translate(centre.x - glyph / 2, centre.y - glyph / 2) {
        with(painter) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(palette.iconGlyph)) }
    }
}

/** The placed labels, with a halo. */
private fun DrawScope.drawLabels(marks: List<LabelCandidate<Mark>>, palette: FloorPalette) {
    val halo = Stroke(width = HaloWidth.toPx(), join = StrokeJoin.Round)
    marks.forEach { mark ->
        val box = mark.box ?: return@forEach
        val layout = mark.item.label ?: return@forEach
        val topLeft = Offset(box.left, box.top)
        drawText(layout, color = palette.labelHalo, topLeft = topLeft, drawStyle = halo)
        // Fill said outright: without it the text keeps the halo's stroke, and the label is a blot.
        drawText(layout, color = palette.label, topLeft = topLeft, drawStyle = Fill)
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
    val iconFill = color(style.icons.colors(dark).fill)
    val iconGlyph = color(style.icons.colors(dark).glyph)
    val selection = color(style.selection.colors(dark).stroke)
    val selectionFillAlpha = style.selection.colors(dark).fillAlpha
    private val categories = style.categories.mapValues { color(it.value.pick(dark)) }
    private val fallback = color(style.categoryColor(null, dark))

    fun category(category: String?): Color = category?.let { categories[it] } ?: fallback

    private fun color(hex: String) = hexColor(hex, Color.Magenta)
}

private val FloorPadding = 16.dp
private val HaloWidth = 3.dp
private val LabelGap = 2.dp

/** How much of an icon's round mark its glyph takes up. */
private const val GlyphShare = 0.7f
