package dev.unscroll.launcher

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.unscroll.launcher.data.AppInfo
import dev.unscroll.launcher.data.AppRepository
import dev.unscroll.launcher.data.Gesture
import dev.unscroll.launcher.data.GestureAction
import dev.unscroll.launcher.data.IconStyle
import dev.unscroll.launcher.data.LauncherSettings
import dev.unscroll.launcher.data.Prefs
import dev.unscroll.launcher.data.ScreenTime
import dev.unscroll.launcher.system.LockService
import dev.unscroll.launcher.system.NotificationsService
import dev.unscroll.launcher.system.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Drawer, Settings }

data class PauseRequest(
    val app: AppInfo,
    val label: String,
    val opensToday: Int,
    val timeTodayMs: Long?,
)

/** Everything the home screen shows, already resolved against the app list. */
data class HomeState(
    val favorites: List<AppInfo> = emptyList(),
    val notificationDots: Set<String> = emptySet(),
)

/** Snapshot of things that live outside the app and can change while we're in the background. */
data class SystemStatus(
    val isDefaultLauncher: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasLockAccess: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repo = AppRepository(application)
    private val prefs = Prefs(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val settings: StateFlow<LauncherSettings> = prefs.settings

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _pause = MutableStateFlow<PauseRequest?>(null)
    val pause: StateFlow<PauseRequest?> = _pause.asStateFlow()

    /** Today's foreground time by package; null when usage access isn't granted. */
    private val _usage = MutableStateFlow<Map<String, Long>?>(null)
    val usage: StateFlow<Map<String, Long>?> = _usage.asStateFlow()

    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    val home: StateFlow<HomeState> = combine(_apps, settings, NotificationsService.counts) { apps, s, counts ->
        val byKey = apps.associateBy { it.key }
        HomeState(
            favorites = s.favorites.mapNotNull(byKey::get),
            // Distracting apps never get a dot: nothing on the home screen should pull you in.
            notificationDots = counts.keys - s.distracting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val icons = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val stopObserving = repo.observeChanges { refreshApps() }

    init {
        // The Niagara-style redesign makes themed icons the default; switch existing installs once.
        if (prefs.consumeOnce("themed_icons_default")) prefs.update { it.copy(iconStyle = IconStyle.THEMED) }
        prefs.pruneOldCounters()
        refreshApps()
    }

    override fun onCleared() = stopObserving()

    // ---- lifecycle ------------------------------------------------------------------------

    fun onResume() {
        _status.value = SystemStatus(
            isDefaultLauncher = SystemActions.isDefaultLauncher(app),
            hasUsageAccess = ScreenTime.hasAccess(app),
            hasNotificationAccess = NotificationsService.hasAccess(app),
            hasLockAccess = LockService.isEnabled(app),
        )
        prefs.pruneOldCounters()
        viewModelScope.launch {
            _usage.value = withContext(Dispatchers.IO) {
                if (ScreenTime.hasAccess(app)) ScreenTime.todayByPackage(app) else null
            }
        }
    }

    /** The home button was pressed, or we left the screen while the app list was open. */
    fun goHome() {
        _homeReset.value++
        _screen.value = Screen.Home
        _pause.value = null
    }

    fun onStop() {
        _homeReset.value++
        if (_screen.value == Screen.Drawer) _screen.value = Screen.Home
        _pause.value = null
    }

    fun show(screen: Screen) {
        _screen.value = screen
    }

    // ---- apps ----------------------------------------------------------------------------

    fun refreshApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadApps() }
            _apps.value = list
            if (list.isNotEmpty() && prefs.consumeFirstRun()) seedFirstRun(list)
        }
    }

    private suspend fun seedFirstRun(list: List<AppInfo>) {
        val essentials = withContext(Dispatchers.IO) { repo.essentialPackages() }
        val personal = list.filterNot { it.isWorkProfile }
        val favorites = essentials.mapNotNull { pkg -> personal.firstOrNull { it.packageName == pkg }?.key }
        val installed = list.map { it.packageName }.toSet()
        prefs.update { s ->
            s.copy(
                favorites = (s.favorites + favorites).distinct().take(MAX_FAVORITES),
                distracting = s.distracting + (KNOWN_DISTRACTING intersect installed),
            )
        }
    }

    fun labelOf(app: AppInfo): String = settings.value.renamed[app.key] ?: app.label

    private val noThemedIcon = HashSet<String>()

    suspend fun icon(app: AppInfo, sizePx: Int, themed: Boolean): Bitmap? {
        val cacheKey = "${app.key}@$sizePx${if (themed) "t" else ""}"
        icons.get(cacheKey)?.let { return it }
        if (themed && cacheKey in noThemedIcon) return null
        val bitmap = withContext(Dispatchers.IO) { repo.loadIcon(app, sizePx, themed) }
        if (bitmap != null) icons.put(cacheKey, bitmap) else if (themed) noThemedIcon += cacheKey
        return bitmap
    }

    private val _homeReset = MutableStateFlow(0)
    /** Bumps whenever home should go back to its resting state (favorites, no letter picked). */
    val homeReset: StateFlow<Int> = _homeReset.asStateFlow()

    /** Opens [app], or shows the mindful pause first if it's marked as distracting. */
    fun open(app: AppInfo) {
        if (app.packageName in settings.value.distracting) {
            _pause.value = PauseRequest(
                app = app,
                label = labelOf(app),
                opensToday = prefs.opensToday(app.packageName),
                timeTodayMs = _usage.value?.get(app.packageName),
            )
        } else {
            launch(app)
        }
    }

    fun confirmPause() {
        val request = _pause.value ?: return
        prefs.recordOpen(request.app.packageName)
        launch(request.app)
    }

    fun cancelPause() {
        _pause.value = null
        _screen.value = Screen.Home
    }

    private fun launch(target: AppInfo) {
        try {
            repo.launch(target)
        } catch (_: Exception) {
            toast("Couldn't open ${labelOf(target)}")
        }
    }

    fun appInfo(target: AppInfo) = runCatching { repo.openAppInfo(target) }
    fun uninstall(target: AppInfo) = runCatching { repo.uninstall(target) }
    fun openAlarms() = repo.openAlarms()

    // ---- customisation -------------------------------------------------------------------

    fun isFavorite(target: AppInfo) = target.key in settings.value.favorites
    fun canAddFavorite() = settings.value.favorites.size < MAX_FAVORITES

    fun toggleFavorite(target: AppInfo) = prefs.update { s ->
        if (target.key in s.favorites) s.copy(favorites = s.favorites - target.key)
        else if (s.favorites.size < MAX_FAVORITES) s.copy(favorites = s.favorites + target.key)
        else s
    }

    fun moveFavorite(target: AppInfo, delta: Int) = prefs.update { s ->
        val list = s.favorites.toMutableList()
        val from = list.indexOf(target.key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update s
        list.add(to, list.removeAt(from))
        s.copy(favorites = list)
    }

    fun toggleDistracting(packageName: String) = prefs.update { s ->
        s.copy(
            distracting = if (packageName in s.distracting) s.distracting - packageName else s.distracting + packageName,
        )
    }

    fun toggleHidden(target: AppInfo) = prefs.update { s ->
        if (target.key in s.hidden) s.copy(hidden = s.hidden - target.key)
        else s.copy(hidden = s.hidden + target.key, favorites = s.favorites - target.key)
    }

    fun rename(target: AppInfo, name: String) = prefs.update { s ->
        val clean = name.trim()
        if (clean.isEmpty() || clean == target.label) s.copy(renamed = s.renamed - target.key)
        else s.copy(renamed = s.renamed + (target.key to clean))
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = prefs.update(transform)

    fun setGesture(gesture: Gesture, action: GestureAction) =
        prefs.update { it.copy(gestures = it.gestures + (gesture to action)) }

    // ---- gestures ------------------------------------------------------------------------

    fun perform(gesture: Gesture) {
        when (val action = settings.value.gestures[gesture] ?: GestureAction.None) {
            GestureAction.None -> Unit
            GestureAction.AppList -> show(Screen.Drawer)
            GestureAction.Notifications -> SystemActions.expandNotifications(app)
            GestureAction.QuickSettings -> SystemActions.expandQuickSettings(app)
            GestureAction.LockScreen -> when {
                !LockService.isSupported -> toast("Locking from a gesture needs Android 9 or newer")
                !LockService.lock() -> {
                    toast("Turn on \"Unscroll lock gesture\" to lock with ${gesture.title.lowercase()}")
                    LockService.requestAccess(app)
                }
            }
            is GestureAction.OpenApp -> _apps.value.firstOrNull { it.key == action.key }?.let(::open)
        }
    }

    private fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    companion object {
        const val MAX_FAVORITES = 7

        /** Pre-marked as distracting on first run, if installed. You can unmark any of them. */
        private val KNOWN_DISTRACTING = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill", // TikTok (some regions)
            "com.google.android.youtube",
            "com.twitter.android",
            "com.facebook.katana",
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.pinterest",
            "tv.twitch.android.app",
            "com.instagram.barcelona", // Threads
        )
    }
}
