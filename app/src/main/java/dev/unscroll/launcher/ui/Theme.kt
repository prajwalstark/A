package dev.unscroll.launcher.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Ink {
    val Primary = Color(0xFFF2F2F2)
    val Dim = Color(0xFFB5B5B5)
    val Faint = Color(0xFF7A7A7A)
    val Sheet = Color(0xFF151515)
}

/** Soft shadow so white text stays readable on top of a bright wallpaper. */
val LocalTextShadow = staticCompositionLocalOf<Shadow?> { null }

private val readableShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), 6f)

@Composable
fun UnscrollTheme(onWallpaper: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Material You: pick up the accent from the wallpaper on Android 12+, like Pixel does.
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(background = Color.Black, onBackground = Ink.Primary)
    } else {
        darkColorScheme(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF062E6F),
            secondaryContainer = Color(0xFF2A2F38),
            onSecondaryContainer = Color(0xFFDDE3F0),
            background = Color.Black,
            onBackground = Ink.Primary,
        )
    }
    CompositionLocalProvider(LocalTextShadow provides if (onWallpaper) readableShadow else null) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

object Type {
    val Clock = TextStyle(fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, color = Ink.Primary)
    val Date = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Ink.Primary)
    val Caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Ink.Dim)
    val HomeApp = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, color = Ink.Primary)
    val ListApp = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Light, color = Ink.Primary)
    val Title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, color = Ink.Primary)
    val Section = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    val Body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, color = Ink.Primary)
}

@Composable
fun TextStyle.readable(): TextStyle = LocalTextShadow.current?.let { copy(shadow = it) } ?: this
