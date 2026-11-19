package dev.unscroll.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import java.time.LocalDate

enum class ThemeStyle { SUNSET, CLASSIC }

/** NONE = text only; THEMED = one-colour glyph on an accent tile (Android 13 themed icons). */
enum class IconStyle { NONE, THEMED, COLOR, MONO }

enum class Backdrop { WALLPAPER, DIM, BLACK }

enum class Gesture(val title: String) {
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    DOUBLE_TAP("Double tap"),
}

/** What a home-screen gesture does. Stored as a short string, e.g. "drawer" or "app:<key>". */
sealed interface GestureAction {
    data object None : GestureAction
    data object AppList : GestureAction
    data object Notifications : GestureAction
    data object QuickSettings : GestureAction
    data object LockScreen : GestureAction
    data class OpenApp(val key: String) : GestureAction

    fun encode(): String = when (this) {
        None -> "none"
        AppList -> "drawer"
        Notifications -> "notifications"
        QuickSettings -> "quick_settings"
        LockScreen -> "lock"
        is OpenApp -> "app:$key"
    }

    companion object {
        fun decode(value: String?): GestureAction? = when {
            value == null -> null
            value == "none" -> None
            value == "drawer" -> AppList
            value == "notifications" -> Notifications
            value == "quick_settings" -> QuickSettings
            value == "lock" -> LockScreen
            value.startsWith("app:") -> OpenApp(value.removePrefix("app:"))
            else -> null
        }

        val defaults = mapOf(
            Gesture.SWIPE_UP to AppList,
            Gesture.SWIPE_DOWN to Notifications,
            Gesture.SWIPE_LEFT to None,
            Gesture.SWIPE_RIGHT to None,
            Gesture.DOUBLE_TAP to LockScreen,
        )
    }
}

data class LauncherSettings(
    /** [AppInfo.key]s, in display order. */
    val favorites: List<String> = emptyList(),
    /** Package names that get a mindful pause and batched notifications. */
    val distracting: Set<String> = emptySet(),
    /** [AppInfo.key]s left out of the app list. */
    val hidden: Set<String> = emptySet(),
    /** Custom names, by [AppInfo.key]. */
    val renamed: Map<String, String> = emptyMap(),
    val pauseSeconds: Int = 10,
    val showScreenTime: Boolean = true,
    /** Pop up the keyboard as soon as the app list opens. */
    val openKeyboard: Boolean = false,
    /** App list Dock effect: 0 = subtle, 1 = huge. */
    val magnification: Float = 0.5f,
    /** A–Z rail on the left edge, for left-handed use. */
    val railOnLeft: Boolean = false,
    val iconStyle: IconStyle = IconStyle.THEMED,
    val theme: ThemeStyle = ThemeStyle.SUNSET,
    val backdrop: Backdrop = Backdrop.DIM,
    /** 0 = deliver distracting apps' notifications immediately. */
    val notificationBatchHours: Int = 0,
    val gestures: Map<Gesture, GestureAction> = GestureAction.defaults,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun read(): LauncherSettings {
        val all = sp.all
        return LauncherSettings(
            favorites = sp.getString(KEY_FAVORITES, "").orEmpty().split(SEP).filter { it.isNotEmpty() },
            distracting = sp.getStringSet(KEY_DISTRACTING, null)?.toSet() ?: emptySet(),
            hidden = sp.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet(),
            renamed = all.mapNotNull { (k, v) ->
                if (k.startsWith(LABEL) && v is String) k.removePrefix(LABEL) to v else null
            }.toMap(),
            pauseSeconds = sp.getInt(KEY_PAUSE, 10),
            showScreenTime = sp.getBoolean(KEY_SCREEN_TIME, true),
            openKeyboard = sp.getBoolean(KEY_KEYBOARD, false),
            magnification = sp.getFloat(KEY_MAGNIFICATION, 0.5f),
            railOnLeft = sp.getBoolean(KEY_RAIL_LEFT, false),
            iconStyle = enumOrDefault(sp.getString(KEY_ICONS, null), IconStyle.THEMED),
            theme = enumOrDefault(sp.getString(KEY_THEME, null), ThemeStyle.SUNSET),
            backdrop = enumOrDefault(sp.getString(KEY_BACKDROP, null), Backdrop.DIM),
            notificationBatchHours = sp.getInt(KEY_BATCH, 0),
            gestures = Gesture.entries.associateWith { g ->
                GestureAction.decode(sp.getString(GESTURE + g.name, null)) ?: GestureAction.defaults.getValue(g)
            },
        )
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val old = _settings.value
        val s = _settings.updateAndGet(transform)
        sp.edit {
            putString(KEY_FAVORITES, s.favorites.joinToString(SEP))
            putStringSet(KEY_DISTRACTING, HashSet(s.distracting))
            putStringSet(KEY_HIDDEN, HashSet(s.hidden))
            (old.renamed.keys - s.renamed.keys).forEach { remove(LABEL + it) }
            s.renamed.forEach { (k, v) -> putString(LABEL + k, v) }
            putInt(KEY_PAUSE, s.pauseSeconds)
            putBoolean(KEY_SCREEN_TIME, s.showScreenTime)
            putBoolean(KEY_KEYBOARD, s.openKeyboard)
            putFloat(KEY_MAGNIFICATION, s.magnification)
            putBoolean(KEY_RAIL_LEFT, s.railOnLeft)
            putString(KEY_ICONS, s.iconStyle.name)
            putString(KEY_THEME, s.theme.name)
            putString(KEY_BACKDROP, s.backdrop.name)
            putInt(KEY_BATCH, s.notificationBatchHours)
            s.gestures.forEach { (g, a) -> putString(GESTURE + g.name, a.encode()) }
        }
    }

    /** True exactly once per [flag]: for one-off migrations when the defaults change. */
    fun consumeOnce(flag: String): Boolean {
        val key = "once:$flag"
        if (sp.getBoolean(key, false)) return false
        sp.edit { putBoolean(key, true) }
        return true
    }

    /** True the first time the app list loads after install. */
    fun consumeFirstRun(): Boolean {
        if (sp.getBoolean(KEY_SEEDED, false)) return false
        sp.edit { putBoolean(KEY_SEEDED, true) }
        return true
    }

    fun opensToday(packageName: String): Int = sp.getInt(openKey(packageName), 0)

    fun recordOpen(packageName: String) {
        sp.edit { putInt(openKey(packageName), opensToday(packageName) + 1) }
    }

    /** Drops open counters from previous days. */
    fun pruneOldCounters() {
        val todayPrefix = "$OPENS${LocalDate.now()}:"
        val stale = sp.all.keys.filter { it.startsWith(OPENS) && !it.startsWith(todayPrefix) }
        if (stale.isNotEmpty()) sp.edit { stale.forEach(::remove) }
    }

    private fun openKey(packageName: String) = "$OPENS${LocalDate.now()}:$packageName"

    companion object {
        const val FILE = "unscroll"
        const val KEY_DISTRACTING = "distracting"
        const val KEY_BATCH = "notification_batch_hours"

        private const val SEP = "\n"
        private const val OPENS = "opens:"
        private const val LABEL = "label:"
        private const val GESTURE = "gesture:"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_PAUSE = "pause_seconds"
        private const val KEY_SCREEN_TIME = "show_screen_time"
        private const val KEY_KEYBOARD = "open_keyboard"
        private const val KEY_MAGNIFICATION = "magnification"
        private const val KEY_RAIL_LEFT = "rail_on_left"
        private const val KEY_ICONS = "icon_style"
        private const val KEY_THEME = "theme"
        private const val KEY_BACKDROP = "backdrop"
        private const val KEY_SEEDED = "seeded"

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
