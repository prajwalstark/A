# Unscroll

A calm, minimalist Android launcher that makes your phone a tool again instead of a slot machine.

<p>
  <img src="docs/home.png" width="250" alt="Home screen in the Sunset theme: gradient clock, date, screen time and five apps as text">
  <img src="docs/app-list.png" width="250" alt="App list magnifying around the finger like the macOS Dock, with Maps highlighted">
  <img src="docs/pause.png" width="250" alt="Mindful pause before opening Instagram">
</p>

## What it does

- **A quiet home screen.** A clock, today's screen time, and up to seven apps as plain text. No icons (unless you want them), badges, widgets, or feeds.
- **One-hand friendly.** Your apps sit in the lower half of the screen. In the app list, the search box is at the bottom, and the best match lands right above it.
- **A Dock-style app list that never scrolls.** Every app fits on screen, packed toward your thumb. Slide a finger over the list and the apps near it magnify like the macOS Dock and pop out toward the center of the screen in an arc, with a haptic tick as you pass each one. Set the strength with the Magnification slider in Settings.
  - Lift your finger to open the highlighted app, hold still to open its menu, or slide off the list to cancel.
  - With a long app list, the resting view becomes a compact stack of bars with A–Z markers, and magnification makes the area under your finger readable.
  - Tap the search box to type instead. Press Enter to open the top match. In Settings you can have the keyboard open automatically.
- **Mindful pause.** Opening an app you've marked as distracting starts a short breathing countdown (5–30 s) that shows how often you've opened it today and how long you've spent in it. "Not now" is the big button.
- **Adaptive notifications.** Pinned apps get a small dot when they have notifications. Distracting apps never do, and you can have their notifications held back and delivered together every hour or every 3 hours. Calls, alarms and reminders always come through.
- **Two themes.**
  - **Sunset** (default): a neon dusk palette with hot pink to orange gradients, deep violet nights and aqua highlights. It uses the bold condensed Anton and Barlow fonts in a game-menu style.
  - **Classic**: quiet, Pixel-like minimalism with Material You accent colors taken from your wallpaper (Android 12+).
  - Either theme can show your wallpaper clear or dimmed, or hide it behind a solid background.
- **Customizable.** Icons can be off, grayscale or in color. You can rename apps and hide apps. Five gestures (swipe up, down, left and right, and double-tap) can each open the app list, notifications or quick settings, lock the screen, or open any app.

Social and video apps such as Instagram, TikTok, YouTube, X, Reddit and Snapchat are marked as distracting on first run if you have them installed. Long-press any app to change that.

## Controls

| On the home screen | Default |
| --- | --- |
| Swipe up | App list |
| Swipe down | Notifications |
| Double tap | Lock screen |
| Swipe left or right | Nothing (you can set these) |
| Long press on empty space | Unscroll settings |
| Tap the clock | Alarms |
| Long press an app | Pin, reorder, distracting, rename, hide, info, uninstall |

In the app list, slide over the apps and lift to open one. Tap empty space or press Back to close it.

## Install on your phone

**Option A: Android Studio**

1. Open this folder in Android Studio and let it sync.
2. Pick the `release` build variant (Build > Select Build Variant) so it runs smoothly. Debug Compose builds are noticeably janky.
3. Plug in your phone with USB debugging on and press Run.

**Option B: command line** (JDK 17+ and the Android SDK installed)

```bash
./gradlew assembleRelease
```

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with your local debug key so you can sideload it without setting up a keystore.

**Then make it your home screen.** Open Unscroll, long-press empty space, and tap *Make Unscroll your home screen*. You can also go to Android Settings > Apps > Default apps > Home app.

## Optional permissions

Unscroll works without any of these. Each one switches on a feature, and you're asked from Settings only when you turn that feature on.

| Permission | Enables |
| --- | --- |
| Usage access | Screen time on the home screen and in the pause screen |
| Notification access | Notification dots and batching for distracting apps |
| Accessibility ("Unscroll lock gesture") | Locking the screen with a gesture (Android 9+). It reads nothing on screen. |

Everything stays on your phone. Unscroll has no internet permission.

## Limits

A launcher only controls what you open *from it*. Opening a distracting app from a notification, from Recents or from a share sheet skips the pause. That's a deliberate trade-off to avoid needing invasive permissions.

## Project layout

```
app/src/main/java/dev/unscroll/launcher/
├── MainActivity.kt          the HOME activity
├── LauncherViewModel.kt     app list, settings, pause flow, gestures
├── data/                    apps, preferences, screen-time calculation
├── system/                  notification listener, lock service, status bar helpers
└── ui/                      Compose screens: home, app list, pause, long-press sheet, settings
```

Kotlin, Jetpack Compose, Material 3. Min Android 8.0 (API 26), target API 35.

## Credits

The Sunset theme uses [Anton](https://fonts.google.com/specimen/Anton) and [Barlow](https://fonts.google.com/specimen/Barlow) / [Barlow Condensed](https://fonts.google.com/specimen/Barlow+Condensed). Both are licensed under the SIL Open Font License 1.1, and the license texts are in `third_party/fonts/`.
