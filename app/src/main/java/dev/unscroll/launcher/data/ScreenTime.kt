package dev.unscroll.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

/** Today's foreground time per app, computed from raw usage events. */
object ScreenTime {
    // Raw event type values, so we don't trip over API-level gated constants on minSdk 26.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val ACTIVITY_STOPPED = 23
    private const val DEVICE_SHUTDOWN = 26

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Opens the "Usage access" settings page, on our own entry when the device supports it. */
    fun requestAccess(context: Context) {
        val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
        val generic = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        for (intent in listOf(specific, generic)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // try the next one
            }
        }
    }

    /** Milliseconds in the foreground since local midnight, by package. Call off the main thread. */
    fun todayByPackage(context: Context): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(start, now) ?: return emptyMap()

        val totals = HashMap<String, Long>()
        val resumedClasses = HashMap<String, MutableSet<String>>()
        val foregroundSince = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            foregroundSince.remove(pkg)?.let { since -> totals.merge(pkg, (at - since).coerceAtLeast(0), Long::plus) }
            resumedClasses.remove(pkg)
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val cls = event.className.orEmpty()
            val time = event.timeStamp
            when (event.eventType) {
                ACTIVITY_RESUMED -> {
                    val classes = resumedClasses.getOrPut(pkg) { mutableSetOf() }
                    if (classes.isEmpty()) foregroundSince[pkg] = time
                    classes += cls
                }
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> {
                    val classes = resumedClasses[pkg]
                    if (classes != null) {
                        classes -= cls
                        if (classes.isEmpty()) close(pkg, time)
                    }
                }
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> resumedClasses.keys.toList().forEach { close(it, time) }
            }
        }
        resumedClasses.keys.toList().forEach { close(it, now) }
        totals.remove(context.packageName)
        return totals
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> "${minutes}m"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
