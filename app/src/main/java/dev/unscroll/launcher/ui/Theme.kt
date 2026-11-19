package dev.unscroll.launcher.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.unscroll.launcher.R
import dev.unscroll.launcher.data.ThemeStyle

/** Everything visual that differs between themes. Read it through [Ink] and [Type]. */
@Immutable
data class Look(
    val primary: Color,
    val dim: Color,
    val faint: Color,
    val sheet: Color,
    /** Solid accent, for switches, cursors and other small things. */
    val accent: Color,
    /** The hero gradient: big buttons, selected choices, the breathing ring. */
    val accentBrush: Brush,
    val onAccent: Color,
    /** Secondary accent: date, section titles, notification dots. */
    val highlight: Color,
    /** Tertiary accent: the screen-time line. */
    val warm: Color,
    /** Full-screen background behind everything (drawn over the wallpaper as a scrim). */
    val night: Brush,
    /** Optional outline for the search pill. */
    val outline: Brush?,
    /** Themed icon tile and the glyph drawn on it. */
    val iconBg: Brush,
    val iconFg: Color,
    /** Game-menu style: labels and titles in capitals. */
    val uppercase: Boolean,
    val clock: TextStyle,
    val date: TextStyle,
    val caption: TextStyle,
    val homeApp: TextStyle,
    val listApp: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val button: TextStyle,
)

val LocalLook = staticCompositionLocalOf { classicLook(Color(0xFFA8C7FA)) }

/** Soft shadow so text stays readable on top of a bright wallpaper. */
val LocalTextShadow = staticCompositionLocalOf<Shadow?> { null }

// ---- Sunset: hot pink to orange dusk, violet night, aqua neon -----------------------------

private object Sunset {
    val Flamingo = Color(0xFFFF2E7E)
    val Tangerine = Color(0xFFFF8A3D)
    val Peach = Color(0xFFFFC371)
    val Violet = Color(0xFF8B3DFF)
    val Aqua = Color(0xFF3EF0E8)
    val NightTop = Color(0xFF1A0933)
    val NightMid = Color(0xFF0E0520)
    val NightBottom = Color(0xFF2E0A33)
    val Text = Color(0xFFFFF3EC)
    val TextDim = Color(0xFFD8C2D8)
    val TextFaint = Color(0xFF8C7396)
    val Sheet = Color(0xFF1D0C31)
}

private val anton = FontFamily(Font(R.font.anton))
private val barlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)
private val barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)

private fun sunsetLook(): Look {
    val dusk = Brush.linearGradient(listOf(Sunset.Flamingo, Sunset.Tangerine, Sunset.Peach))
    return Look(
        primary = Sunset.Text,
        dim = Sunset.TextDim,
        faint = Sunset.TextFaint,
        sheet = Sunset.Sheet,
        accent = Sunset.Flamingo,
        accentBrush = Brush.horizontalGradient(listOf(Sunset.Flamingo, Sunset.Tangerine)),
        onAccent = Color.White,
        highlight = Sunset.Aqua,
        warm = Sunset.Peach,
        night = Brush.verticalGradient(
            0f to Sunset.NightTop,
            0.55f to Sunset.NightMid,
            1f to Sunset.NightBottom,
        ),
        outline = Brush.horizontalGradient(listOf(Sunset.Flamingo, Sunset.Violet, Sunset.Aqua)),
        iconBg = Brush.linearGradient(listOf(Sunset.Flamingo, Sunset.Tangerine)),
        iconFg = Color.White,
        uppercase = true,
        clock = TextStyle(
            fontFamily = anton,
            fontSize = 92.sp,
            letterSpacing = 1.sp,
            brush = dusk,
        ),
        date = TextStyle(fontFamily = barlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 3.sp, color = Sunset.Aqua),
        caption = TextStyle(fontFamily = barlow, fontSize = 13.5.sp, color = Sunset.TextDim),
        homeApp = TextStyle(fontFamily = barlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, letterSpacing = 1.4.sp, color = Sunset.Text),
        listApp = TextStyle(fontFamily = barlowCondensed, fontWeight = FontWeight.Medium, fontSize = 23.sp, letterSpacing = 1.2.sp, color = Sunset.Text),
        title = TextStyle(fontFamily = anton, fontSize = 34.sp, letterSpacing = 1.sp, color = Sunset.Text),
        section = TextStyle(fontFamily = barlowCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp),
        body = TextStyle(fontFamily = barlow, fontSize = 17.sp, color = Sunset.Text),
        button = TextStyle(fontFamily = barlowCondensed, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.5.sp, color = Sunset.Text),
    )
}

private fun sunsetColors(): ColorScheme = darkColorScheme(
    primary = Sunset.Flamingo,
    onPrimary = Color.White,
    secondary = Sunset.Aqua,
    secondaryContainer = Color(0xFF3A1650),
    onSecondaryContainer = Color(0xFFFFD9EC),
    surface = Sunset.Sheet,
    surfaceContainerHigh = Sunset.Sheet,
    onSurface = Sunset.Text,
    onSurfaceVariant = Sunset.TextDim,
    background = Sunset.NightMid,
    onBackground = Sunset.Text,
    outline = Sunset.TextFaint,
)

// ---- Classic: quiet Pixel-like minimalism, Material You accent ----------------------------

private fun classicLook(accent: Color, tile: Color = accent.copy(alpha = 0.3f), onTile: Color = Color.White): Look {
    val primary = Color(0xFFF2F2F2)
    val dim = Color(0xFFB5B5B5)
    return Look(
        primary = primary,
        dim = dim,
        faint = Color(0xFF7A7A7A),
        sheet = Color(0xFF151515),
        accent = accent,
        accentBrush = SolidColor(accent),
        onAccent = Color.Black,
        highlight = accent,
        warm = accent,
        night = SolidColor(Color.Black),
        outline = null,
        iconBg = SolidColor(tile),
        iconFg = onTile,
        uppercase = false,
        clock = TextStyle(fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, color = primary),
        date = TextStyle(fontSize = 16.sp, color = primary),
        caption = TextStyle(fontSize = 13.sp, color = dim),
        homeApp = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, color = primary),
        listApp = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Light, color = primary),
        title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, color = primary),
        section = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
        body = TextStyle(fontSize = 17.sp, color = primary),
        button = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = primary),
    )
}

private val readableShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), 6f)

@Composable
fun UnscrollTheme(style: ThemeStyle, onWallpaper: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val (colors, look) = when (style) {
        ThemeStyle.SUNSET -> sunsetColors() to sunsetLook()
        ThemeStyle.CLASSIC -> {
            // Material You: take the accent from the wallpaper on Android 12+, like Pixel does.
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme(
                    primary = Color(0xFFA8C7FA),
                    onPrimary = Color(0xFF062E6F),
                    secondaryContainer = Color(0xFF2A2F38),
                    onSecondaryContainer = Color(0xFFDDE3F0),
                )
            }.copy(background = Color.Black, onBackground = Color(0xFFF2F2F2))
            scheme to classicLook(scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer).copy(onAccent = scheme.onPrimary)
        }
    }
    CompositionLocalProvider(
        LocalLook provides look,
        LocalTextShadow provides if (onWallpaper) readableShadow else null,
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Theme colors. */
object Ink {
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalLook.current.primary
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalLook.current.dim
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalLook.current.faint
    val Sheet: Color @Composable @ReadOnlyComposable get() = LocalLook.current.sheet
}

/** Theme text styles. */
object Type {
    val Clock: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.clock
    val Date: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.date
    val Caption: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.caption
    val HomeApp: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.homeApp
    val ListApp: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.listApp
    val Title: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.title
    val Section: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.section
    val Body: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.body
    val Button: TextStyle @Composable @ReadOnlyComposable get() = LocalLook.current.button
}

@Composable
@ReadOnlyComposable
fun TextStyle.readable(): TextStyle = LocalTextShadow.current?.let { copy(shadow = it) } ?: this

/** Labels and titles: capitals in themes that want a game-menu feel. */
@Composable
@ReadOnlyComposable
fun String.label(): String = if (LocalLook.current.uppercase) uppercase() else this
