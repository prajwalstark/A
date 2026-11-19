package dev.unscroll.launcher.data

import android.content.ComponentName
import android.os.UserHandle

/** One launchable activity, in one user profile (personal or work). */
data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Stable id used to store favorites / hidden state. */
    val key: String,
    val isWorkProfile: Boolean,
    val isSystemApp: Boolean,
)
