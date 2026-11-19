package dev.unscroll.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.unscroll.launcher.PauseRequest
import dev.unscroll.launcher.data.ScreenTime
import kotlinx.coroutines.delay

private const val BREATH_MS = 4000

/**
 * The mindful pause: a short breathing exercise before a distracting app opens.
 * "Not now" is the big, easy button. Opening the app is possible, just not effortless.
 */
@Composable
fun PauseScreen(request: PauseRequest, seconds: Int, onOpen: () -> Unit, onCancel: () -> Unit) {
    var remaining by remember(request) { mutableIntStateOf(seconds) }
    var inhale by remember(request) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(request) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    LaunchedEffect(request) {
        while (true) {
            inhale = !inhale
            delay(BREATH_MS.toLong())
        }
    }
    val breath by animateFloatAsState(
        targetValue = if (inhale) 1f else 0.45f,
        animationSpec = tween(BREATH_MS, easing = FastOutSlowInEasing),
        label = "breath",
    )
    BackHandler(onBack = onCancel)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow touches so nothing underneath is tappable.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("Opening ${request.label}", style = Type.Caption)
            Spacer(Modifier.height(32.dp))

            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(accent.copy(alpha = 0.10f), radius = r * breath)
                    drawCircle(accent.copy(alpha = 0.7f), radius = r * breath, style = Stroke(1.5.dp.toPx()))
                }
                Text(
                    if (remaining > 0) "$remaining" else "",
                    style = Type.Clock.copy(fontSize = 44.sp, color = Ink.Primary.copy(alpha = 0.85f)),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(if (inhale) "Breathe in" else "Breathe out", style = Type.Title.copy(fontSize = 26.sp))
            Spacer(Modifier.height(16.dp))
            Text(
                stats(request),
                style = Type.Caption.copy(fontSize = 15.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))

            // Bottom: the easy choice is the big one, under your thumb.
            Column(
                Modifier.fillMaxWidth().height(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                AnimatedVisibility(remaining == 0, enter = fadeIn(tween(400)), exit = fadeOut()) {
                    Text(
                        "Open ${request.label} anyway",
                        style = Type.Body.copy(color = Ink.Dim),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Not now",
                    style = Type.Body.copy(color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable(onClick = onCancel)
                        .padding(vertical = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun stats(request: PauseRequest): String {
    val opens = when (request.opensToday) {
        0 -> "First time today."
        1 -> "You've opened it once today."
        else -> "You've opened it ${request.opensToday} times today."
    }
    val time = request.timeTodayMs?.takeIf { it >= 60_000 }?.let { "\n${ScreenTime.format(it)} spent in it so far." }
    return opens + time.orEmpty()
}
