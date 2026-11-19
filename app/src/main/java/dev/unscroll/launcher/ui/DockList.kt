package dev.unscroll.launcher.ui

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextLayoutResult
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

/** App icon size at scale 1, and the gap between icon and name. */
private val IconSize = 36.dp
private val IconGap = 10.dp

/** The A–Z rail along the right edge, where your thumb scrubs through the apps. */
private val RailWidth = 44.dp

/** How tall the row under your finger gets, from subtle (strength 0) to huge (strength 1). */
private val FocusHeightMin = 56.dp
private val FocusHeightMax = 120.dp

private fun focusHeight(strength: Float) =
    FocusHeightMin + (FocusHeightMax - FocusHeightMin) * strength.coerceIn(0f, 1f)

/** Rows smaller than this (as a fraction of [RowHeight]) fade from text into a thin bar. */
private const val TEXT_FULL = 0.5f
private const val TEXT_GONE = 0.3f

/**
 * An app list that never scrolls. Every app fits on screen, packed against the bottom edge.
 *
 * Slide your thumb along the A–Z rail (on the right, or the left for left-handed use) and the apps beside it magnify like the
 * macOS Dock: they grow, their icons fade in, and they pop out toward the centre. Lift to open
 * the highlighted app, hold still for its menu, slide off the list to cancel. The list itself
 * is tap-to-open for any app that's readable.
 *
 * Without the rail ([showIndex] false, e.g. for search results) the whole list scrubs instead.
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
    /** Show the A–Z rail (only makes sense when the list is alphabetical). */
    showIndex: Boolean = true,
    /** 0 = subtle, 1 = huge. */
    strength: Float = 0.5f,
    /** Put the A–Z rail on the left edge instead of the right. */
    railOnLeft: Boolean = false,
    @VisibleForTesting previewFingerY: Float? = null,
) {
    val look = LocalLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    val menu by rememberUpdatedState(onLongPress)
    val currentApps by rememberUpdatedState(apps)
    val focusHeight = focusHeight(strength)
    val currentFocusHeight by rememberUpdatedState(focusHeight)
    val railWidth = if (showIndex) RailWidth else 0.dp
    // Where the app list starts: after the rail when it's on the left.
    val listStart = if (railOnLeft) railWidth else 0.dp
    // With icons switched off, they still appear on the apps you magnify.
    val iconsAtRest = iconStyle != IconStyle.NONE
    val drawnIconStyle = if (iconStyle == IconStyle.MONO) IconStyle.MONO else IconStyle.COLOR

    // Finger position in px within this layout; negative when no finger is down.
    var fingerY by remember { mutableFloatStateOf(previewFingerY ?: -1f) }
    // 0 = resting list, 1 = fully magnified around the finger.
    val magnify = remember { Animatable(if (previewFingerY != null) 1f else 0f) }
    var focused by remember { mutableIntStateOf(-1) }

    val layoutLens = remember(apps.size) { Fisheye(apps.size) }
    val drawLens = remember(apps.size) { Fisheye(apps.size) }
    val touchLens = remember(apps.size) { Fisheye(apps.size) }
    val labelWidths = remember(apps.size) { FloatArray(apps.size) }

    // First letter of each alphabetical group, pre-measured (normal and highlighted).
    val textMeasurer = rememberTextMeasurer()
    val letterStyle = Type.Section.copy(color = look.highlight, fontSize = 12.sp, letterSpacing = 0.sp)
    val activeStyle = Type.Section.copy(color = look.onAccent, fontSize = 17.sp, letterSpacing = 0.sp)
    val letters = remember(apps, labelOf, showIndex, letterStyle, activeStyle) {
        if (!showIndex) return@remember RailLetters(IntArray(apps.size) { -1 }, emptyMap())
        val groupOf = IntArray(apps.size)
        val layouts = HashMap<Int, Pair<TextLayoutResult, TextLayoutResult>>()
        var previous: Char? = null
        var groupStart = -1
        apps.forEachIndexed { i, app ->
            val c = labelOf(app).firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
            if (c != previous) {
                groupStart = i
                layouts[i] = textMeasurer.measure(c.toString(), letterStyle) to textMeasurer.measure(c.toString(), activeStyle)
            }
            groupOf[i] = groupStart
            previous = c
        }
        RailLetters(groupOf, layouts)
    }

    Layout(
        modifier = modifier
            .drawBehind {
                val n = apps.size
                if (n == 0) return@drawBehind
                drawLens.solve(size.height, RowHeight.toPx(), focusHeight.toPx(), magnify.value, fingerY)
                val pad = 4.dp.toPx()
                val rail = railWidth.toPx()

                // Rows too small to read are drawn as thin bars, like a dock seen from afar.
                for (i in 0 until n) {
                    val scale = drawLens.heights[i] / RowHeight.toPx()
                    // Bars clear out before the text fades in, so the two never overlap.
                    val barAlpha = (1f - textAlpha(scale) * 2f).coerceAtLeast(0f)
                    if (barAlpha <= 0f) continue
                    val h = (drawLens.heights[i] * 0.34f).coerceIn(1.5.dp.toPx(), 5.dp.toPx())
                    val w = (labelWidths[i] * 0.55f).coerceIn(18.dp.toPx(), size.width - rail - pad * 2)
                    drawRoundRect(
                        color = look.faint,
                        alpha = barAlpha * (if (apps[i].packageName in distracting) 0.45f else 0.9f),
                        topLeft = Offset(listStart.toPx() + pad, drawLens.tops[i] + (drawLens.heights[i] - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                }

                if (rail <= 0f) return@drawBehind
                // The rail: a soft track that lights up while your thumb is on it...
                val railX = if (railOnLeft) 0f else size.width - rail
                val top = drawLens.tops[0]
                drawRoundRect(
                    brush = look.accentBrush,
                    alpha = 0.06f + 0.10f * magnify.value,
                    topLeft = Offset(railX + rail * 0.15f, top),
                    size = Size(rail * 0.7f, size.height - top),
                    cornerRadius = CornerRadius(rail * 0.35f),
                )
                // ...and a letter beside the first app of each group, following the magnification.
                val activeGroup = if (focused in 0 until n) letters.groupOf[focused] else -1
                var lastY = Float.NEGATIVE_INFINITY
                for ((start, layouts) in letters.layouts.entries.sortedBy { it.key }) {
                    val centre = drawLens.tops[start] + drawLens.heights[start] / 2f
                    if (start == activeGroup) continue
                    if (centre - lastY < layouts.first.size.height) continue
                    drawText(
                        layouts.first,
                        topLeft = Offset(railX + (rail - layouts.first.size.width) / 2f, centre - layouts.first.size.height / 2f),
                        alpha = 1f - 0.3f * magnify.value,
                    )
                    lastY = centre
                }
                // The current letter rides with your thumb in a lit bubble.
                letters.layouts[activeGroup]?.second?.let { big ->
                    val centre = if (fingerY >= 0f) fingerY else drawLens.tops[focused] + drawLens.heights[focused] / 2f
                    val d = maxOf(big.size.width, big.size.height) + 12.dp.toPx()
                    val cx = railX + rail / 2f
                    drawRoundRect(
                        brush = look.accentBrush,
                        topLeft = Offset(cx - d / 2f, centre - d / 2f),
                        size = Size(d, d),
                        cornerRadius = CornerRadius(d / 2f),
                    )
                    drawText(big, topLeft = Offset(cx - big.size.width / 2f, centre - big.size.height / 2f))
                }
            }
            .pointerInput(apps.size, showIndex, railOnLeft) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val height = size.height.toFloat()
                    val rowPx = RowHeight.toPx()
                    val focusPx = currentFocusHeight.toPx()
                    val readableAtRest = touchLens.restScale(height, rowPx) >= TEXT_FULL
                    val onRail = !showIndex || if (railOnLeft) {
                        down.position.x <= RailWidth.toPx()
                    } else {
                        down.position.x >= size.width - RailWidth.toPx()
                    }

                    if (!onRail) {
                        // The list itself: tap a readable app to open it, hold for its menu.
                        val index = touchLens.solve(height, rowPx, focusPx, 0f, down.position.y)
                        val target = currentApps.getOrNull(index)
                        val readable = readableAtRest && down.position.y >= touchLens.tops[0]
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var result: Boolean? = null
                            while (result == null) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                result = when {
                                    change == null -> false
                                    change.changedToUp() -> true
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> false
                                    else -> null
                                }
                            }
                            result
                        }
                        if (target != null && readable) {
                            if (up == true) {
                                open(target)
                            } else if (up == null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu(target)
                            }
                        }
                        return@awaitEachGesture
                    }

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
                            // Held still: show the menu for the app under your thumb.
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
                val dimmed = app.packageName in distracting && !isFocused
                Box(Modifier.alpha(if (dimmed) 0.5f else 1f)) { AppIcon(app, drawnIconStyle, IconSize) }
                DockRow(
                    app = app,
                    label = labelOf(app),
                    style = Type.ListApp,
                    dimmed = dimmed,
                    focused = isFocused,
                    onOpen = { onOpen(app) },
                    onLongPress = { onLongPress(app) },
                )
            }
        },
    ) { measurables, constraints ->
        val rowPx = RowHeight.roundToPx()
        val iconPx = IconSize.roundToPx()
        val gapPx = IconGap.toPx()
        val focusPx = focusHeight.toPx()
        val maxScale = focusPx / RowHeight.toPx()
        val listWidth = (constraints.maxWidth - railWidth.toPx()).coerceAtLeast(0f)
        // Leave room so a magnified icon + name still fits beside the rail.
        val textConstraints = Constraints(
            maxWidth = ((listWidth / maxScale) - iconPx - gapPx).roundToInt().coerceAtLeast(0),
            minHeight = rowPx,
            maxHeight = rowPx,
        )
        val iconConstraints = Constraints.fixed(iconPx, iconPx)
        val icons = ArrayList<androidx.compose.ui.layout.Placeable>(apps.size)
        val rows = ArrayList<androidx.compose.ui.layout.Placeable>(apps.size)
        measurables.forEachIndexed { i, m ->
            if (i % 2 == 0) icons += m.measure(iconConstraints) else rows += m.measure(textConstraints)
        }
        rows.forEachIndexed { i, p -> if (i < labelWidths.size) labelWidths[i] = p.width.toFloat() }

        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else rowPx * rows.size
        layout(constraints.maxWidth, height) {
            val under = layoutLens.solve(height.toFloat(), rowPx.toFloat(), focusPx, magnify.value, fingerY)
            if (previewFingerY != null && focused != under) focused = under
            val restScale = layoutLens.restScale(height.toFloat(), rowPx.toFloat())
            rows.forEachIndexed { i, row ->
                val h = layoutLens.heights[i]
                val scale = h / rowPx
                val centreY = layoutLens.tops[i] + h / 2f
                // How magnified this row is, 0..1, eased into a rounder arc.
                val grown = if (maxScale > restScale) ((scale - restScale) / (maxScale - restScale)).coerceIn(0f, 1f) else 0f
                val pop = grown * grown * (3f - 2f * grown)
                val iconShown = if (iconsAtRest) 1f else pop
                val iconSpace = (iconPx + gapPx) * scale * iconShown
                // Pop out: the more a row is magnified, the further it slides toward the centre.
                val groupWidth = iconSpace + row.width * scale
                val x = listStart.toPx() + ((listWidth - groupWidth) / 2f).coerceAtLeast(0f) * pop
                val fade = textAlpha(scale)

                icons[i].placeWithLayer(x.roundToInt(), (centreY - iconPx / 2f).roundToInt()) {
                    // Grow into exactly the space reserved for it, so it never overlaps the name.
                    val s = scale * iconShown
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = fade * iconShown
                }
                row.placeWithLayer((x + iconSpace).roundToInt(), (centreY - rowPx / 2f).roundToInt()) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = fade
                }
            }
        }
    }
}

/** For each app, the index of the first app in its letter group; and each group's letter. */
private class RailLetters(
    val groupOf: IntArray,
    val layouts: Map<Int, Pair<TextLayoutResult, TextLayoutResult>>,
)

@Composable
private fun DockRow(
    app: AppInfo,
    label: String,
    style: TextStyle,
    dimmed: Boolean,
    focused: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val look = LocalLook.current
    val text = (if (app.isWorkProfile) "$label  ·  work" else label).label()
    Box(
        Modifier
            .height(RowHeight)
            .padding(horizontal = 4.dp)
            // Touch is handled by the list, but TalkBack can still open each app directly.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = "Open") { onOpen(); true }
                onLongClick(label = "Options") { onLongPress(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = (if (focused) style.merge(TextStyle(brush = look.accentBrush)) else style).readable(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (dimmed) 0.55f else 1f),
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
