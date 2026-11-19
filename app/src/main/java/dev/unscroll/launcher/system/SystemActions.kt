package dev.unscroll.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Starts the first intent the device can handle. */
fun startFirst(context: Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) {
            // try the next one
        }
    }
    return false
}

object SystemActions {
    fun expandNotifications(context: Context) = statusBar(context, "expandNotificationsPanel")

    fun expandQuickSettings(context: Context) = statusBar(context, "expandSettingsPanel")

    // StatusBarManager has no public API for this, but every minimalist launcher relies on it
    // and it is permitted with EXPAND_STATUS_BAR.
    @SuppressLint("WrongConstant")
    private fun statusBar(context: Context, method: String) {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun openDefaultLauncherSettings(context: Context) = startFirst(
        context,
        listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS)),
    )
}
