package dev.unscroll.launcher.ui

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.floor

/** Favorites entry at the top of the rail. */
const val FAVORITES_MARK = '★'

/** The rail's letters live between these fractions of its height. */
private const val TOP = 0.16f
private const val BOTTOM = 0.95f

/** How far the wave pushes letters sideways at its peak, and how many letters it spans. */
private val WaveReach = 58.dp
private const val WAVE_SIGMA = 2.4f

/**
 * A thin A–Z strip on the edge of the home screen. Touch it and the letters bulge sideways in a
 * wave around your thumb, with the current letter in a lit bubble. [onPick] fires as your thumb
 * moves from letter to letter (with the letter's y position, for placing its apps nearby).
 */
@Composable
fun WaveRail(
    letters: List<Char>,
    onLeft: Boolean,
    onPick: (letter: Char, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    @VisibleForTesting previewFingerY: Float? = null,
) {
    val look = LocalLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onPick)
    val currentLetters by rememberUpdatedState(letters)

    var fingerY by remember { mutableFloatStateOf(previewFingerY ?: -1f) }
    val wave = remember { Animatable(if (previewFingerY != null) 1f else 0f) }
    var selected by remember { mutableIntStateOf(-1) }

    val measurer = rememberTextMeasurer()
    val restStyle = Type.Section.copy(color = look.primary, fontSize = 12.sp, letterSpacing = 0.sp).readable()
    val bubbleStyle = Type.Section.copy(color = look.onAccent, fontSize = 20.sp, letterSpacing = 0.sp)
    val small = remember(letters, restStyle) { letters.map { measurer.measure(it.toString(), restStyle) } }
    val big = remember(letters, bubbleStyle) { letters.map { measurer.measure(it.toString(), bubbleStyle) } }

    fun spacing(height: Float) = height * (BOTTOM - TOP) / letters.size.coerceAtLeast(1)
    fun centreOf(i: Int, height: Float) = height * TOP + spacing(height) * (i + 0.5f)
    fun indexAt(y: Float, height: Float) =
        floor((y - height * TOP) / spacing(height)).toInt().coerceIn(0, letters.lastIndex)

    Spacer(
        modifier
            .drawBehind {
                if (letters.isEmpty()) return@drawBehind
                val h = size.height
                val step = spacing(h)
                val dir = if (onLeft) 1f else -1f
                val reach = WaveReach.toPx() * wave.value
                val cx = size.width / 2f
                val active = if (fingerY >= 0f) indexAt(fingerY, h) else -1
                if (previewFingerY != null && selected != active) selected = active

                letters.indices.forEach { i ->
                    val y = centreOf(i, h)
                    // Gaussian bump: letters near the thumb swing out, the rest stay on the edge.
                    val d = if (fingerY >= 0f) (y - fingerY) / step else 99f
                    val g = exp(-(d * d) / (2f * WAVE_SIGMA * WAVE_SIGMA))
                    if (i == active && wave.value > 0.05f) return@forEach
                    val layout = small[i]
                    val x = cx + dir * reach * g
                    val s = 1f + 0.45f * g * wave.value
                    scale(s, pivot = Offset(x, y)) {
                        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f), alpha = 0.92f)
                    }
                }

                if (active >= 0 && wave.value > 0.05f) {
                    val layout = big[active]
                    val r = 22.dp.toPx() * wave.value
                    val bx = cx + dir * reach
                    drawCircle(look.accentBrush, radius = r, center = Offset(bx, fingerY))
                    drawText(
                        layout,
                        topLeft = Offset(bx - layout.size.width / 2f, fingerY - layout.size.height / 2f),
                        alpha = wave.value,
                    )
                }
            }
            .pointerInput(letters.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val h = size.height.toFloat()

                    fun track(y: Float) {
                        fingerY = y.coerceIn(h * TOP, h * BOTTOM - 1f)
                        val i = indexAt(fingerY, h)
                        if (i != selected) {
                            if (selected != -1) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selected = i
                            currentLetters.getOrNull(i)?.let { pick(it, centreOf(i, h)) }
                        }
                    }

                    selected = -1
                    track(down.position.y)
                    scope.launch { wave.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (change.changedToUp()) break
                        track(change.position.y)
                    }
                    scope.launch {
                        wave.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f))
                        fingerY = -1f
                    }
                }
            },
    )
}
