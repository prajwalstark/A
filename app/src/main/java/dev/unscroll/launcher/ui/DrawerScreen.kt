package dev.unscroll.launcher.ui

import android.app.SearchManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Velocity
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
    onOpen: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(apps, query, labelOf) { search(apps, query, labelOf) }
    val searching = query.isNotBlank()

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Typing is the default: you came here for one specific app.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }
    LaunchedEffect(searching) { listState.scrollToItem(0) }
    BackHandler(onBack = onClose)

    val open = { app: AppInfo ->
        keyboard?.hide()
        onOpen(app)
    }

    // Pull down past the top of the list to close it, like Pixel's app drawer.
    val close by rememberUpdatedState(onClose)
    val pullThreshold = with(LocalDensity.current) { 110.dp.toPx() }
    val pullToClose = remember(searching) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only the full list closes this way; search results are bottom-anchored instead.
                if (searching || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0) {
                    pulled += available.y
                    if (pulled > pullThreshold) {
                        pulled = Float.NEGATIVE_INFINITY
                        close()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled != Float.NEGATIVE_INFINITY) pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            // When searching, the best match sits right above the search box, next to your thumb.
            reverseLayout = searching,
            verticalArrangement = Arrangement.Bottom,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullToClose),
        ) {
            items(results, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    label = labelOf(app),
                    style = Type.ListApp,
                    iconStyle = iconStyle,
                    dimmed = app.packageName in distracting,
                    onClick = { open(app) },
                    onLongClick = { onLongPressApp(app) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (searching && results.isEmpty()) {
                item(key = "web") {
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
            }
        }

        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Ink.Sheet)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Type.Body.copy(fontSize = Type.ListApp.fontSize),
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
                        Text("Search apps", style = Type.Body.copy(fontSize = Type.ListApp.fontSize, color = Ink.Faint))
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
