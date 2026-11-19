package dev.unscroll.launcher.ui

import android.text.format.DateFormat
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

@Composable
fun HomeScreen(
    state: HomeState,
    labelOf: (AppInfo) -> String,
    iconStyle: IconStyle,
    screenTimeMs: Long?,
    showAppListHint: Boolean,
    onOpen: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onGesture: (Gesture) -> Unit,
    onOpenSettings: () -> Unit,
    onClockClick: () -> Unit,
) {
    val gesture by rememberUpdatedState(onGesture)
    val settings by rememberUpdatedState(onOpenSettings)

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
                    onDoubleTap = { gesture(Gesture.DOUBLE_TAP) },
                    onLongPress = { settings() },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Clock(screenTimeMs, onClockClick)
            Spacer(Modifier.weight(1f))

            // Apps sit in the lower half of the screen, where your thumb already is.
            if (state.favorites.isEmpty()) {
                Text(
                    "Long-press any app in the list\nto pin it here.",
                    style = Type.Caption.readable(),
                    modifier = Modifier.padding(bottom = 16.dp),
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
                    verticalPadding = 9.dp,
                )
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                if (showAppListHint) {
                    // A Pixel-style handle hinting that the app list is one swipe away.
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
}

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
