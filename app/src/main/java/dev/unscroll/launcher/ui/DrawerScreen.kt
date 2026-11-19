package dev.unscroll.launcher.ui

import android.app.SearchManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.IconStyle
import dev.unscroll.launcher.system.startFirst
import java.text.Normalizer

@Composable
fun DrawerScreen(
    apps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    distracting: Set<String>,
    iconStyle: IconStyle,
    openKeyboard: Boolean,
    magnification: Float,
    railOnLeft: Boolean,
    onOpen: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onClose: () -> Unit,
    previewFingerY: Float? = null,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(apps, query, labelOf) { search(apps, query, labelOf) }
    val searching = query.isNotBlank()

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (openKeyboard) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    BackHandler(onBack = onClose)

    val open = { app: AppInfo ->
        keyboard?.hide()
        onOpen(app)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Empty space above a short list closes the app list.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (searching && results.isEmpty()) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    SettingRow(
                        title = "Search the web for “${query.trim()}”",
                        onClick = {
                            keyboard?.hide()
                            startFirst(
                                context,
                                listOf(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query.trim())),
                            )
                        },
                    )
                }
            } else {
                DockList(
                    // Search results: best match at the bottom, right above the search box.
                    apps = if (searching) results.asReversed() else results,
                    labelOf = labelOf,
                    distracting = distracting,
                    iconStyle = iconStyle,
                    onOpen = open,
                    onLongPress = onLongPressApp,
                    showIndex = !searching,
                    strength = magnification,
                    railOnLeft = railOnLeft,
                    previewFingerY = previewFingerY,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Ink.Sheet)
                .then(LocalLook.current.outline?.let { Modifier.border(1.2.dp, it, RoundedCornerShape(50)) } ?: Modifier)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Type.ListApp,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(open) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                decorationBox = { field ->
                    if (query.isEmpty()) {
                        Text("Search apps".label(), style = Type.ListApp.copy(color = Ink.Faint))
                    }
                    field()
                },
            )
        }
    }
}

private fun normalize(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Prefix matches first, then word-start matches, then anything containing the query. */
private fun search(apps: List<AppInfo>, query: String, labelOf: (AppInfo) -> String): List<AppInfo> {
    val q = normalize(query.trim())
    if (q.isEmpty()) return apps
    return apps.mapNotNull { app ->
        val label = normalize(labelOf(app))
        val rank = when {
            label.startsWith(q) -> 0
            label.split(' ', '-', '.').any { it.startsWith(q) } -> 1
            label.contains(q) -> 2
            else -> return@mapNotNull null
        }
        rank to app
    }.sortedBy { it.first }.map { it.second }
}
