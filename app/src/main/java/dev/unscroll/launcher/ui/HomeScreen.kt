package dev.unscroll.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.unscroll.launcher.HomeState
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.Gesture
import dev.unscroll.launcher.data.IconStyle
import dev.unscroll.launcher.data.ScreenTime
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Width of the wave alphabet strip on the home screen edge. */
private val RailWidth = 32.dp

@Composable
fun HomeScreen(
    state: HomeState,
    apps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    iconStyle: IconStyle,
    distracting: Set<String>,
    screenTimeMs: Long?,
    showAppListHint: Boolean,
    railOnLeft: Boolean,
    resetKey: Int,
    onOpen: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onGesture: (Gesture) -> Unit,
    onOpenSettings: () -> Unit,
    onClockClick: () -> Unit,
    previewLetter: Char? = null,
    previewFingerY: Float? = null,
) {
    val gesture by rememberUpdatedState(onGesture)
    val settings by rememberUpdatedState(onOpenSettings)

    // The letter picked on the wave rail; null shows the clock and favorites.
    var letter by remember { mutableStateOf(previewLetter) }
    var anchorY by remember { mutableFloatStateOf(previewFingerY ?: 0f) }
    LaunchedEffect(resetKey) { if (previewLetter == null) letter = null }
    BackHandler(enabled = letter != null) { letter = null }

    val groups = remember(apps, labelOf) { apps.groupBy { initialOf(labelOf(it)) } }
    val rail = remember(groups) { listOf(FAVORITES_MARK) + groups.keys.sorted() }
    val sidePadding = Modifier.padding(
        start = if (railOnLeft) RailWidth + 20.dp else 28.dp,
        end = if (railOnLeft) 28.dp else RailWidth + 20.dp,
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 56.dp.toPx()
                var total = Offset.Zero
                var fired = false
                detectDragGestures(
                    onDragStart = { total = Offset.Zero; fired = false },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                        if (fired) return@detectDragGestures
                        // Fire as soon as the swipe is clearly in one direction: feels instant.
                        val (x, y) = total
                        val g = when {
                            abs(y) > threshold && abs(y) > abs(x) * 1.2f -> if (y < 0) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
                            abs(x) > threshold && abs(x) > abs(y) * 1.2f -> if (x < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT
                            else -> null
                        }
                        if (g != null) {
                            fired = true
                            gesture(g)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { letter = null },
                    onDoubleTap = { gesture(Gesture.DOUBLE_TAP) },
                    onLongPress = { settings() },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            // Resting state: clock, then favorites right below it.
            AnimatedVisibility(
                visible = letter == null,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(120)),
            ) {
                Column(Modifier.fillMaxSize().then(sidePadding)) {
                    Spacer(Modifier.weight(0.42f))
                    Clock(screenTimeMs, onClockClick)
                    Spacer(Modifier.height(28.dp))
                    if (state.favorites.isEmpty()) {
                        Text(
                            "Long-press any app\nto pin it here.",
                            style = Type.Caption.readable(),
                        )
                    }
                    state.favorites.forEach { app ->
                        AppRow(
                            app = app,
                            label = labelOf(app),
                            style = Type.HomeApp,
                            iconStyle = iconStyle,
                            iconSize = 34.dp,
                            dot = app.packageName in state.notificationDots,
                            onClick = { onOpen(app) },
                            onLongClick = { onLongPressApp(app) },
                            verticalPadding = 8.dp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                        if (showAppListHint) {
                            // A handle hinting that the full app list is one swipe away.
                            Box(
                                Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(LocalLook.current.outline ?: SolidColor(Ink.Primary.copy(alpha = 0.35f))),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // A letter is picked: its apps appear beside your thumb.
            AnimatedContent(
                targetState = letter,
                transitionSpec = {
                    (fadeIn(tween(160)) + slideInHorizontally(tween(200)) { w -> if (railOnLeft) -w / 10 else w / 10 }) togetherWith
                        fadeOut(tween(100))
                },
                label = "letter",
                modifier = Modifier.fillMaxSize().then(sidePadding),
            ) { picked ->
                val group = picked?.let { groups[it] }.orEmpty()
                if (picked == null || group.isEmpty()) return@AnimatedContent
                Column(
                    Modifier
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minHeight = 0))
                            val y = (anchorY - p.height / 2f).roundToInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0))
                            layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, y) }
                        }
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        picked.toString(),
                        style = Type.Section.copy(color = LocalLook.current.highlight, fontSize = 16.sp).readable(),
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    group.forEach { app ->
                        AppRow(
                            app = app,
                            label = labelOf(app),
                            style = Type.HomeApp,
                            iconStyle = iconStyle,
                            iconSize = 34.dp,
                            dimmed = app.packageName in distracting,
                            dot = app.packageName in state.notificationDots,
                            onClick = { onOpen(app) },
                            onLongClick = { onLongPressApp(app) },
                            verticalPadding = 8.dp,
                        )
                    }
                }
            }

            WaveRail(
                letters = rail,
                onLeft = railOnLeft,
                onPick = { picked, y ->
                    anchorY = y
                    letter = if (picked == FAVORITES_MARK) null else picked
                },
                previewFingerY = previewFingerY,
                modifier = Modifier
                    .align(if (railOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 4.dp)
                    .width(RailWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

/** The rail letter an app files under: its first letter, or # for digits and symbols. */
fun initialOf(label: String): Char =
    label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

/** Ends the layout [gap] below the text's baseline, dropping the font's built-in bottom space. */
private fun Modifier.trimBelowBaseline(gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val baseline = placeable[LastBaseline]
    val height = if (baseline == AlignmentLine.Unspecified) {
        placeable.height
    } else {
        minOf(placeable.height, baseline + gap.roundToPx())
    }
    layout(placeable.width, height) { placeable.place(0, 0) }
}

@Composable
private fun Clock(screenTimeMs: Long?, onClick: () -> Unit) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake up right at the next minute boundary.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val is24h = DateFormat.is24HourFormat(context)
    val timeFormat = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }

    Column(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        // Display fonts carry a lot of empty space under the digits; keep the date snug.
        Text(now.format(timeFormat), style = Type.Clock.readable(), modifier = Modifier.trimBelowBaseline(12.dp))
        Text(now.format(dateFormat).label(), style = Type.Date.readable(), modifier = Modifier.padding(top = 2.dp))
        if (screenTimeMs != null) {
            Text(
                "${ScreenTime.format(screenTimeMs)} on your phone today",
                style = Type.Caption.copy(color = LocalLook.current.warm).readable(),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
