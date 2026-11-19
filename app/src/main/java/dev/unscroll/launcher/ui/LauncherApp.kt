package dev.unscroll.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.unscroll.launcher.LauncherViewModel
import dev.unscroll.launcher.PauseRequest
import dev.unscroll.launcher.Screen
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.Backdrop
import dev.unscroll.launcher.data.GestureAction

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val pause by vm.pause.collectAsStateWithLifecycle()
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }

    // Keep the last request around so the pause screen can fade out instead of vanishing.
    var lastPause by remember { mutableStateOf<PauseRequest?>(null) }
    if (pause != null) lastPause = pause

    val homeScrim = when (settings.backdrop) {
        Backdrop.WALLPAPER -> 0.12f
        Backdrop.DIM -> 0.55f
        Backdrop.BLACK -> 1f
    }
    val scrim by animateFloatAsState(
        targetValue = if (screen == Screen.Home) homeScrim else maxOf(homeScrim, 0.88f),
        animationSpec = tween(300),
        label = "scrim",
    )
    val labelOf = remember(settings.renamed) { { app: AppInfo -> settings.renamed[app.key] ?: app.label } }

    UnscrollTheme(style = settings.theme, onWallpaper = settings.backdrop != Backdrop.BLACK) {
        CompositionLocalProvider(LocalIconLoader provides vm::icon) {
            val night = LocalLook.current.night
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(night, alpha = scrim) },
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = fadeIn(tween(220, delayMillis = 40)) +
                            slideInVertically(tween(280)) { h -> if (forward) h / 14 else -h / 20 }
                        val exit = fadeOut(tween(140)) +
                            slideOutVertically(tween(280)) { h -> if (forward) -h / 20 else h / 14 }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { current ->
                    when (current) {
                        Screen.Home -> {
                            BackHandler { /* Home is the bottom of the stack: nothing to go back to. */ }
                            HomeScreen(
                                state = home,
                                labelOf = labelOf,
                                iconStyle = settings.iconStyle,
                                screenTimeMs = usage?.values?.sum()?.takeIf { settings.showScreenTime },
                                showAppListHint = settings.gestures.values.contains(GestureAction.AppList),
                                onOpen = vm::open,
                                onLongPressApp = { sheetApp = it },
                                onGesture = vm::perform,
                                onOpenSettings = { vm.show(Screen.Settings) },
                                onClockClick = { vm.openAlarms() },
                            )
                        }
                        Screen.Drawer -> DrawerScreen(
                            apps = apps.filterNot { it.key in settings.hidden },
                            labelOf = labelOf,
                            distracting = settings.distracting,
                            iconStyle = settings.iconStyle,
                            openKeyboard = settings.openKeyboard,
                            onOpen = vm::open,
                            onLongPressApp = { sheetApp = it },
                            onClose = { vm.show(Screen.Home) },
                        )
                        Screen.Settings -> SettingsScreen(vm)
                    }
                }

                AnimatedVisibility(
                    visible = pause != null,
                    enter = fadeIn(tween(250)) + scaleIn(tween(350), initialScale = 0.96f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.98f),
                ) {
                    lastPause?.let { request ->
                        PauseScreen(
                            request = request,
                            seconds = settings.pauseSeconds,
                            onOpen = vm::confirmPause,
                            onCancel = vm::cancelPause,
                        )
                    }
                }

                sheetApp?.let { app -> AppSheet(app, vm) { sheetApp = null } }
            }
        }
    }
}
