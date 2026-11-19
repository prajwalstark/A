package dev.unscroll.launcher.ui

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.IconStyle
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Height of one row at scale 1. Rows are laid out once at this size and scaled on the GPU. */
private val RowHeight = 48.dp

/** How tall the row under your finger gets. */
private val FocusHeight = 88.dp

/** Rows smaller than this (as a fraction of [RowHeight]) fade from text into a thin bar. */
private const val TEXT_FULL = 0.5f
private const val TEXT_GONE = 0.3f

/**
 * An app list that never scrolls. Every app fits on screen; slide a finger over it and the
 * apps near your finger magnify like the macOS Dock. Lift to open the highlighted app,
 * hold still for its menu, slide off the list to cancel.
 *
 * Apps are drawn top to bottom in [apps] order, packed against the bottom edge.
 */
@Composable
fun DockList(
    apps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    distracting: Set<String>,
    iconStyle: IconStyle,
    onOpen: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    /** Show A–Z markers beside the resting list (only makes sense when it's alphabetical). */
    showIndex: Boolean = true,
    @VisibleForTesting previewFingerY: Float? = null,
) {
    val look = LocalLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    val menu by rememberUpdatedState(onLongPress)
    val currentApps by rememberUpdatedState(apps)

    // Finger position in px within this layout; negative when no finger is down.
    var fingerY by remember { mutableFloatStateOf(previewFingerY ?: -1f) }
    // 0 = resting list, 1 = fully magnified around the finger.
    val magnify = remember { Animatable(if (previewFingerY != null) 1f else 0f) }
    var focused by remember { mutableIntStateOf(-1) }

    val layoutLens = remember(apps.size) { Fisheye(apps.size) }
    val drawLens = remember(apps.size) { Fisheye(apps.size) }
    val touchLens = remember(apps.size) { Fisheye(apps.size) }
    val labelWidths = remember(apps.size) { FloatArray(apps.size) }

    // First letter of each alphabetical group, pre-measured so drawing them is cheap.
    val textMeasurer = rememberTextMeasurer()
    val indexStyle = Type.Section.copy(color = look.highlight, fontSize = 11.sp, letterSpacing = 0.sp)
    val indexLetters = remember(apps, labelOf, showIndex, indexStyle) {
        if (!showIndex) return@remember emptyMap()
        var previous: Char? = null
        buildMap {
            apps.forEachIndexed { i, app ->
                val c = labelOf(app).firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' }
                if (c != null && c != previous) put(i, textMeasurer.measure(c.toString(), indexStyle))
                previous = c
            }
        }
    }

    Layout(
        modifier = modifier
            .drawBehind {
                // Rows too small to read are drawn as thin bars, like a dock seen from afar.
                val n = apps.size
                if (n == 0) return@drawBehind
                drawLens.solve(size.height, RowHeight.toPx(), FocusHeight.toPx(), magnify.value, fingerY)
                val pad = 4.dp.toPx()
                var lastLetterY = Float.NEGATIVE_INFINITY
                for (i in 0 until n) {
                    val scale = drawLens.heights[i] / RowHeight.toPx()
                    // Bars clear out before the text fades in, so the two never overlap.
                    val barAlpha = (1f - textAlpha(scale) * 2f).coerceAtLeast(0f)
                    if (barAlpha <= 0f) continue
                    indexLetters[i]?.let { letter ->
                        val centre = drawLens.tops[i] + drawLens.heights[i] / 2f
                        if (centre - lastLetterY >= letter.size.height) {
                            drawText(
                                letter,
                                topLeft = Offset(size.width - letter.size.width - pad, centre - letter.size.height / 2f),
                                alpha = barAlpha,
                            )
                            lastLetterY = centre
                        }
                    }
                    val h = (drawLens.heights[i] * 0.34f).coerceIn(1.5.dp.toPx(), 5.dp.toPx())
                    val w = (labelWidths[i] * 0.55f).coerceAtLeast(18.dp.toPx())
                    drawRoundRect(
                        color = look.faint,
                        alpha = barAlpha * (if (apps[i].packageName in distracting) 0.45f else 0.9f),
                        topLeft = Offset(pad, drawLens.tops[i] + (drawLens.heights[i] - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                }
            }
            .pointerInput(apps.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val height = size.height.toFloat()
                    val rowPx = RowHeight.toPx()
                    val focusPx = FocusHeight.toPx()
                    // Was this app list readable before you touched it? Then a plain tap opens.
                    val readableAtRest = touchLens.restScale(height, rowPx) >= TEXT_FULL

                    fun track(y: Float) {
                        fingerY = y
                        val index = touchLens.solve(height, rowPx, focusPx, 1f, y)
                        if (index != focused) {
                            if (focused != -1 && index != -1) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            focused = index
                        }
                    }

                    track(down.position.y)
                    scope.launch { magnify.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }

                    var moved = false
                    var menuShown = false
                    var lastY = down.position.y
                    var lastX = down.position.x
                    while (true) {
                        val waitFor = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis - SystemClock.uptimeMillis()
                        val event = if (!moved && !menuShown && waitFor > 0) {
                            withTimeoutOrNull(waitFor) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Held still: show the menu for the app under your finger.
                            menuShown = true
                            currentApps.getOrNull(focused)?.let {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu(it)
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastX = change.position.x
                        lastY = change.position.y
                        if (change.changedToUp()) break
                        change.consume()
                        if (menuShown) continue
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        track(change.position.y)
                    }

                    val inside = lastY in 0f..height && lastX in 0f..size.width.toFloat()
                    val deliberate = moved || SystemClock.uptimeMillis() - down.uptimeMillis > 180
                    val target = currentApps.getOrNull(focused)
                    if (!menuShown && inside && target != null && (readableAtRest || deliberate)) open(target)

                    fingerY = -1f
                    focused = -1
                    scope.launch { magnify.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 320f)) }
                }
            },
        content = {
            apps.forEachIndexed { index, app ->
                val isFocused by remember(index) { derivedStateOf { focused == index } }
                DockRow(
                    app = app,
                    label = labelOf(app),
                    style = Type.ListApp,
                    iconStyle = iconStyle,
                    dimmed = app.packageName in distracting,
                    focused = isFocused,
                    onOpen = { onOpen(app) },
                    onLongPress = { onLongPress(app) },
                )
            }
        },
    ) { measurables, constraints ->
        val rowPx = RowHeight.roundToPx()
        val maxScale = FocusHeight.toPx() / RowHeight.toPx()
        // Leave room so a magnified row still fits the width.
        val rowConstraints = Constraints(
            maxWidth = (constraints.maxWidth / maxScale).roundToInt(),
            minHeight = rowPx,
            maxHeight = rowPx,
        )
        val placeables = measurables.map { it.measure(rowConstraints) }
        placeables.forEachIndexed { i, p -> if (i < labelWidths.size) labelWidths[i] = p.width.toFloat() }

        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else rowPx * placeables.size
        layout(constraints.maxWidth, height) {
            val under = layoutLens.solve(height.toFloat(), rowPx.toFloat(), FocusHeight.toPx(), magnify.value, fingerY)
            if (previewFingerY != null && focused != under) focused = under
            placeables.forEachIndexed { i, placeable ->
                val h = layoutLens.heights[i]
                val scale = h / rowPx
                val y = layoutLens.tops[i] + h / 2f - rowPx / 2f
                placeable.placeWithLayer(0, y.roundToInt()) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = textAlpha(scale)
                }
            }
        }
    }
}

@Composable
private fun DockRow(
    app: AppInfo,
    label: String,
    style: TextStyle,
    iconStyle: IconStyle,
    dimmed: Boolean,
    focused: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val look = LocalLook.current
    val text = (if (app.isWorkProfile) "$label  ·  work" else label).label()
    Row(
        Modifier
            .height(RowHeight)
            .padding(horizontal = 4.dp)
            // The row itself isn't clickable (the list handles touch), but TalkBack can still open it.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = "Open") { onOpen(); true }
                onLongClick(label = "Options") { onLongPress(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconStyle != IconStyle.NONE) {
            Row(Modifier.alpha(if (dimmed && !focused) 0.5f else 1f)) { AppIcon(app, iconStyle, 30.dp) }
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = text,
            style = (if (focused) style.merge(TextStyle(brush = look.accentBrush)) else style).readable(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (dimmed && !focused) 0.55f else 1f),
        )
    }
}

private fun textAlpha(scale: Float) = ((scale - TEXT_GONE) / (TEXT_FULL - TEXT_GONE)).coerceIn(0f, 1f)

/**
 * The Dock's geometry. Each row gets a weight of 1 plus a bump centred on the focus point;
 * rows share the available height in proportion to their weight, so the list never grows or
 * scrolls. The focus point is solved so that it sits exactly under the finger.
 */
private class Fisheye(private val n: Int) {
    val tops = FloatArray(n)
    val heights = FloatArray(n)
    private val weights = FloatArray(n)
    private var lastFocus = n / 2f

    /** Height the list occupies: everything fits, but rows never exceed [rowPx] at rest. */
    private fun used(height: Float, rowPx: Float) = min(height, n * rowPx)

    fun restScale(height: Float, rowPx: Float): Float = if (n == 0) 1f else used(height, rowPx) / n / rowPx

    /**
     * Fills [tops] and [heights]. [amount] blends between resting (0) and fully magnified (1).
     * Returns the index of the row under [fingerY], or -1 if no finger is down.
     */
    fun solve(height: Float, rowPx: Float, focusPx: Float, amount: Float, fingerY: Float): Int {
        if (n == 0) return -1
        val total = used(height, rowPx)
        val top = height - total
        val sigma = if (n > 30) 1.6f else 1.2f
        val spread = sigma * 2.5066f // area under the bump, in rows

        // Pick the bump height so the focused row lands near [focusPx], however many apps there are.
        val room = total - focusPx * spread
        val fullBump = if (room <= rowPx) 25f else ((focusPx * n - total) / room).coerceIn(0.6f, 25f)
        val bump = fullBump * amount

        if (fingerY >= 0f && bump > 0f) {
            // Binary search for the focus point that ends up under the finger.
            var lo = 0f
            var hi = n.toFloat()
            repeat(22) {
                val mid = (lo + hi) / 2f
                if (positionOf(mid, bump, sigma, top, total) < fingerY) lo = mid else hi = mid
            }
            lastFocus = ((lo + hi) / 2f).coerceIn(0f, n - 0.001f)
        } else if (fingerY >= 0f) {
            lastFocus = ((fingerY - top) / total * n).coerceIn(0f, n - 0.001f)
        }

        fillWeights(lastFocus, bump, sigma)
        val sum = weights.sum()
        var y = top
        for (i in 0 until n) {
            val h = total * weights[i] / sum
            tops[i] = y
            heights[i] = h
            y += h
        }
        return if (fingerY >= 0f) floor(lastFocus).toInt().coerceIn(0, n - 1) else -1
    }

    private fun fillWeights(focus: Float, bump: Float, sigma: Float) {
        val twoSigmaSq = 2f * sigma * sigma
        for (i in 0 until n) {
            val d = i + 0.5f - focus
            weights[i] = 1f + bump * exp(-(d * d) / twoSigmaSq)
        }
    }

    /** Screen y of the fractional row position [focus] when the bump is centred there. */
    private fun positionOf(focus: Float, bump: Float, sigma: Float, top: Float, total: Float): Float {
        fillWeights(focus, bump, sigma)
        val sum = weights.sum()
        val whole = floor(focus).toInt().coerceIn(0, n - 1)
        var before = 0f
        for (i in 0 until whole) before += weights[i]
        before += (focus - whole) * weights[whole]
        return top + total * before / sum
    }
}
