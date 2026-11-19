package dev.unscroll.launcher.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dev.unscroll.launcher.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * Adaptive notifications:
 *  - tracks which apps have unread notifications, for the dots on the home screen;
 *  - when batching is on, holds back notifications from apps marked as distracting
 *    (snoozes them) and lets them all through together at the start of each window.
 */
class NotificationsService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        _counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        maybeHoldBack(sbn)
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        _counts.value = active
            .filter { it.isClearable && !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupingBy { it.packageName }
            .eachCount()
    }

    private fun maybeHoldBack(sbn: StatusBarNotification) {
        val sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val hours = sp.getInt(Prefs.KEY_BATCH, 0)
        if (hours <= 0) return
        if (sbn.packageName !in sp.getStringSet(Prefs.KEY_DISTRACTING, null).orEmpty()) return

        val n = sbn.notification
        if (!sbn.isClearable || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (n.category in NEVER_HOLD) return

        val window = hours * HOUR
        val sinceStart = LocalTime.now().toSecondOfDay() * 1000L % window
        // Snoozed notifications come back right at the start of a window: let them through.
        if (sinceStart < GRACE) return
        runCatching { snoozeNotification(sbn.key, window - sinceStart) }
    }

    companion object {
        private const val HOUR = 60 * 60 * 1000L
        private const val GRACE = 90 * 1000L
        private val NEVER_HOLD = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            "navigation", // Notification.CATEGORY_NAVIGATION (API 28)
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_REMINDER,
        )

        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        /** Unread notification count by package name. Empty without notification access. */
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
        val connected = MutableStateFlow(false)

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun requestAccess(context: Context) {
            val component = ComponentName(context, NotificationsService::class.java)
            val intents = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
                    )
                }
                add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            startFirst(context, intents)
        }
    }
}
