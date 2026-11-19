package dev.unscroll.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.unscroll.launcher.LauncherViewModel
import dev.unscroll.launcher.data.AppInfo
import kotlinx.coroutines.launch

/** Long-press menu for an app: pin, reorder, mark as distracting, rename, hide, info, uninstall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(app: AppInfo, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val label = vm.labelOf(app)

    val close: (() -> Unit) -> Unit = { action ->
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(label.label(), style = Type.Title, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            val favorites = settings.favorites
            val index = favorites.indexOf(app.key)
            if (index >= 0) {
                if (index > 0) SheetItem("Move up") { vm.moveFavorite(app, -1) }
                if (index < favorites.lastIndex) SheetItem("Move down") { vm.moveFavorite(app, +1) }
                SheetItem("Remove from home") { close { vm.toggleFavorite(app) } }
            } else if (vm.canAddFavorite()) {
                SheetItem("Add to home") { close { vm.toggleFavorite(app) } }
            } else {
                SheetItem("Home is full", "Up to ${LauncherViewModel.MAX_FAVORITES} apps. Fewer is calmer.")
            }

            if (app.packageName in settings.distracting) {
                SheetItem("Not distracting", "Open instantly, deliver its notifications right away") {
                    close { vm.toggleDistracting(app.packageName) }
                }
            } else {
                SheetItem("Mark as distracting", "Pause before opening, hold back its notifications") {
                    close { vm.toggleDistracting(app.packageName) }
                }
            }

            SheetItem("Rename") { renaming = true }
            if (app.key in settings.hidden) {
                SheetItem("Show in app list") { close { vm.toggleHidden(app) } }
            } else {
                SheetItem("Hide from app list", "Find it again in Settings") { close { vm.toggleHidden(app) } }
            }
            SheetItem("App info") { close { vm.appInfo(app) } }
            if (!app.isSystemApp) SheetItem("Uninstall") { close { vm.uninstall(app) } }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Ink.Sheet,
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    supportingText = { Text("Leave empty to use “${app.label}”") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(app, name)
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetItem(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(title, style = Type.Body.copy(color = if (onClick != null) Ink.Primary else Ink.Faint))
        if (subtitle != null) {
            Text(subtitle, style = Type.Caption.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
