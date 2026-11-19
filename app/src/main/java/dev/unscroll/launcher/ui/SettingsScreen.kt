package dev.unscroll.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.unscroll.launcher.BuildConfig
import dev.unscroll.launcher.LauncherViewModel
import dev.unscroll.launcher.Screen
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.Backdrop
import dev.unscroll.launcher.data.Gesture
import dev.unscroll.launcher.data.GestureAction
import dev.unscroll.launcher.data.IconStyle
import dev.unscroll.launcher.data.ScreenTime
import dev.unscroll.launcher.data.ThemeStyle
import dev.unscroll.launcher.system.LockService
import dev.unscroll.launcher.system.NotificationsService
import dev.unscroll.launcher.system.SystemActions

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var editingGesture by remember { mutableStateOf<Gesture?>(null) }
    var pickingAppFor by remember { mutableStateOf<Gesture?>(null) }

    BackHandler { vm.show(Screen.Home) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("Settings".label(), style = Type.Title.copy(fontSize = Type.Title.fontSize * 1.2f))

        // ---- Home ---------------------------------------------------------------------
        SectionTitle("Home screen")
        if (!status.isDefaultLauncher) {
            SettingRow(
                title = "Make Unscroll your home screen",
                subtitle = "Pick it under Default apps > Home app",
                onClick = { SystemActions.openDefaultLauncherSettings(context) },
            )
        }
        Text("Theme", style = Type.Body, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        Choices(
            options = listOf(ThemeStyle.SUNSET to "Sunset", ThemeStyle.CLASSIC to "Classic"),
            selected = s.theme,
            onSelect = { v -> vm.updateSettings { it.copy(theme = v) } },
        )
        Text("Background", style = Type.Body, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        Choices(
            options = listOf(
                Backdrop.WALLPAPER to "Wallpaper",
                Backdrop.DIM to "Dimmed",
                Backdrop.BLACK to if (s.theme == ThemeStyle.SUNSET) "Night" else "Black",
            ),
            selected = s.backdrop,
            onSelect = { v -> vm.updateSettings { it.copy(backdrop = v) } },
        )
        Text("App icons", style = Type.Body, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        Choices(
            options = listOf(IconStyle.NONE to "Off", IconStyle.MONO to "Grayscale", IconStyle.COLOR to "Color"),
            selected = s.iconStyle,
            onSelect = { v -> vm.updateSettings { it.copy(iconStyle = v) } },
        )
        Text(
            "Grayscale icons are less tempting than colorful ones.",
            style = Type.Caption,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        SettingRow(
            title = "Screen time on home",
            subtitle = if (s.showScreenTime && !status.hasUsageAccess) "Tap to allow usage access" else "How long you've used your phone today",
            trailing = {
                Switch(
                    checked = s.showScreenTime && status.hasUsageAccess,
                    onCheckedChange = { on ->
                        vm.updateSettings { it.copy(showScreenTime = on) }
                        if (on && !status.hasUsageAccess) ScreenTime.requestAccess(context)
                    },
                )
            },
            onClick = { if (!status.hasUsageAccess) ScreenTime.requestAccess(context) },
        )

        // ---- Distracting apps -------------------------------------------------------------
        SectionTitle("Mindful pause")
        Text("Wait before a distracting app opens", style = Type.Body, modifier = Modifier.padding(bottom = 10.dp))
        Choices(
            options = listOf(5, 10, 20, 30).map { it to "${it}s" },
            selected = s.pauseSeconds,
            onSelect = { v -> vm.updateSettings { it.copy(pauseSeconds = v) } },
        )
        Spacer(Modifier.height(12.dp))
        val distractingApps = apps.filter { it.packageName in s.distracting }.distinctBy { it.packageName }
        if (distractingApps.isEmpty()) {
            Text("Long-press any app in the list to mark it as distracting.", style = Type.Caption)
        } else {
            distractingApps.forEach { app ->
                SettingRow(
                    title = vm.labelOf(app),
                    trailing = { Text("Remove", style = Type.Caption.copy(color = MaterialTheme.colorScheme.primary)) },
                    onClick = { vm.toggleDistracting(app.packageName) },
                )
            }
        }

        // ---- Notifications ------------------------------------------------------------------
        SectionTitle("Notifications")
        if (!status.hasNotificationAccess) {
            SettingRow(
                title = "Allow notification access",
                subtitle = "Needed for dots on home and for batching",
                onClick = { NotificationsService.requestAccess(context) },
            )
        }
        Text("Deliver distracting apps' notifications", style = Type.Body, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        Choices(
            options = listOf(0 to "Right away", 1 to "Hourly", 3 to "Every 3 hours"),
            selected = s.notificationBatchHours,
            onSelect = { v ->
                vm.updateSettings { it.copy(notificationBatchHours = v) }
                if (v > 0 && !status.hasNotificationAccess) NotificationsService.requestAccess(context)
            },
        )
        Text(
            if (s.notificationBatchHours == 0) {
                "Pinned apps show a dot when they have notifications. Distracting apps never do."
            } else {
                "Held back and delivered together on the hour" +
                    (if (s.notificationBatchHours > 1) ", every ${s.notificationBatchHours} hours" else "") +
                    ". Calls, alarms and reminders always come through."
            },
            style = Type.Caption,
            modifier = Modifier.padding(top = 8.dp),
        )

        // ---- Gestures ----------------------------------------------------------------------
        SectionTitle("Gestures")
        Gesture.entries.forEach { g ->
            SettingRow(
                title = g.title,
                trailing = {
                    Text(
                        describe(s.gestures[g] ?: GestureAction.None, apps, vm),
                        style = Type.Caption.copy(color = MaterialTheme.colorScheme.primary),
                    )
                },
                onClick = { editingGesture = g },
            )
        }
        SettingRow(title = "Long press", trailing = { Text("Settings", style = Type.Caption) })
        if (s.gestures.containsValue(GestureAction.LockScreen) && LockService.isSupported && !status.hasLockAccess) {
            SettingRow(
                title = "Allow locking from a gesture",
                subtitle = "Turn on “Unscroll lock gesture” in Accessibility",
                onClick = { LockService.requestAccess(context) },
            )
        }

        // ---- Hidden ------------------------------------------------------------------------
        val hiddenApps = apps.filter { it.key in s.hidden }
        if (hiddenApps.isNotEmpty()) {
            SectionTitle("Hidden apps")
            hiddenApps.forEach { app ->
                SettingRow(
                    title = vm.labelOf(app),
                    trailing = { Text("Show", style = Type.Caption.copy(color = MaterialTheme.colorScheme.primary)) },
                    onClick = { vm.toggleHidden(app) },
                )
            }
        }

        Spacer(Modifier.height(40.dp))
        Text("Unscroll ${BuildConfig.VERSION_NAME}", style = Type.Caption.copy(color = Ink.Faint))
        Spacer(Modifier.height(32.dp))
    }

    editingGesture?.let { gesture ->
        val options = buildList {
            add(GestureAction.None to "Nothing")
            add(GestureAction.AppList to "App list")
            add(GestureAction.Notifications to "Notifications")
            add(GestureAction.QuickSettings to "Quick settings")
            if (LockService.isSupported) add(GestureAction.LockScreen to "Lock screen")
        }
        AlertDialog(
            onDismissRequest = { editingGesture = null },
            containerColor = Ink.Sheet,
            title = { Text(gesture.title) },
            text = {
                Column {
                    options.forEach { (action, label) ->
                        DialogOption(label, selected = s.gestures[gesture] == action) {
                            vm.setGesture(gesture, action)
                            editingGesture = null
                        }
                    }
                    DialogOption("Open an app…", selected = s.gestures[gesture] is GestureAction.OpenApp) {
                        pickingAppFor = gesture
                        editingGesture = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingGesture = null }) { Text("Close") } },
        )
    }

    pickingAppFor?.let { gesture ->
        AlertDialog(
            onDismissRequest = { pickingAppFor = null },
            containerColor = Ink.Sheet,
            title = { Text("${gesture.title} opens") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.key }) { app ->
                        DialogOption(vm.labelOf(app), selected = false) {
                            vm.setGesture(gesture, GestureAction.OpenApp(app.key))
                            pickingAppFor = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingAppFor = null }) { Text("Cancel") } },
        )
    }
}

private fun describe(action: GestureAction, apps: List<AppInfo>, vm: LauncherViewModel): String = when (action) {
    GestureAction.None -> "Nothing"
    GestureAction.AppList -> "App list"
    GestureAction.Notifications -> "Notifications"
    GestureAction.QuickSettings -> "Quick settings"
    GestureAction.LockScreen -> "Lock screen"
    is GestureAction.OpenApp -> apps.firstOrNull { it.key == action.key }?.let(vm::labelOf) ?: "App not found"
}

@Composable
private fun DialogOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Type.Body.copy(color = if (selected) MaterialTheme.colorScheme.primary else Ink.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}
