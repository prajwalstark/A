package dev.unscroll.launcher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    /** Every launchable app across all profiles, alphabetically. Call off the main thread. */
    fun loadApps(): List<AppInfo> {
        val me = Process.myUserHandle()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .mapNotNull { info ->
                    val component = info.componentName
                    if (component.packageName == appContext.packageName) return@mapNotNull null
                    AppInfo(
                        label = info.label.toString().trim(),
                        packageName = component.packageName,
                        component = component,
                        user = user,
                        key = "${component.flattenToShortString()}#$serial",
                        isWorkProfile = user != me,
                        isSystemApp = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
        }.sortedWith(compareBy(collator) { it.label })
    }

    /**
     * The app's icon rendered at [sizePx]. Call off the main thread.
     *
     * With [themed], returns only the icon's one-colour glyph (Android 13+ themed icons) as an
     * alpha mask for the UI to tint, or null if the app doesn't provide one.
     */
    fun loadIcon(app: AppInfo, sizePx: Int, themed: Boolean = false): Bitmap? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
        val info = launcherApps.resolveActivity(intent, app.user) ?: return null
        if (!themed) return info.getBadgedIcon(0)?.toBitmap(sizePx, sizePx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val glyph = (info.getIcon(0) as? AdaptiveIconDrawable)?.monochrome ?: return null
        // Adaptive icon layers are 108dp with the visible part in the middle 72dp:
        // draw them 1.5x larger so the visible part fills the bitmap.
        val extra = sizePx / 4
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            glyph.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
            glyph.draw(Canvas(bitmap))
        }
    }.getOrNull()

    fun launch(app: AppInfo) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstall(app: AppInfo) {
        @Suppress("DEPRECATION") // ACTION_DELETE is still the simplest way to show the system dialog.
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun openAlarms() = startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun startSafely(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /**
     * Package names of the default phone, messaging, camera and browser apps, used to give
     * a fresh install a sensible home screen instead of an empty one.
     */
    fun essentialPackages(): List<String> {
        val pm = appContext.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        )
        return intents.mapNotNull { intent ->
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" } // "android" = the chooser, i.e. no default set
        }.distinct()
    }

    /** Calls [onChange] on the main thread whenever apps are installed, removed or updated. */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }
}
