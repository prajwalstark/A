package dev.unscroll.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.IconStyle

/** Loads (and caches) an app icon at the given pixel size. */
val LocalIconLoader = staticCompositionLocalOf<suspend (AppInfo, Int) -> Bitmap?> { { _, _ -> null } }

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AppIcon(app: AppInfo, style: IconStyle, size: Dp) {
    val loader = LocalIconLoader.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, app.key, px) { value = loader(app, px) }
    Box(Modifier.size(size)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                colorFilter = if (style == IconStyle.MONO) grayscale else null,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A single app as text (and optionally an icon). The whole row is the touch target. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    label: String,
    style: TextStyle,
    iconStyle: IconStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp,
    dimmed: Boolean = false,
    dot: Boolean = false,
    verticalPadding: Dp = 10.dp,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 4.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconStyle != IconStyle.NONE) {
            Box(Modifier.alpha(if (dimmed) 0.5f else 1f)) { AppIcon(app, iconStyle, iconSize) }
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = (if (app.isWorkProfile) "$label  ·  work" else label).label(),
            style = style.readable(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .alpha(if (dimmed) 0.55f else 1f),
        )
        if (dot) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LocalLook.current.highlight),
            )
        }
    }
}

/** Pixel-style segmented choice: a row of pills, the selected one filled with the accent tone. */
@Composable
fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val look = LocalLook.current
            Text(
                text = label.label(),
                style = Type.Button.copy(
                    fontSize = 15.sp,
                    color = if (isSelected) look.onAccent else Ink.Dim,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) look.accentBrush else SolidColor(Ink.Sheet))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = Type.Section.copy(color = LocalLook.current.highlight),
        modifier = Modifier.padding(top = 32.dp, bottom = 12.dp),
    )
}

/** A tappable settings row with a title and an optional one-line explanation. */
@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            if (subtitle != null) Text(subtitle, style = Type.Caption, modifier = Modifier.padding(top = 2.dp))
        }
        trailing?.let {
            Spacer(Modifier.width(16.dp))
            it()
        }
    }
}
