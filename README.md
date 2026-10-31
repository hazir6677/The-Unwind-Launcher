# Unwind Launcher

A calm, glassy home screen for Android TV. Frosted-glass rail, a time-aware greeting, and a row of round app tiles over a soft sunset wallpaper. Built with Kotlin and Jetpack Compose.

## Features

- **Glass UI:** translucent rail, top bar, and round app tiles with a soft focus glow
- **Time-aware greeting:** "Good morning / afternoon / evening" with a matching headline
- **Pinned apps row:** your favorites on the home screen, with an **Add apps** tile at the end
- **All apps grid:** lists every installed app, including sideloaded phone apps like Google Photos that stock launchers hide
- **D-pad friendly:** everything is reachable with a normal TV remote, with focus scaling and highlights
- **Live clock:** shown in the top bar
- **Search and Settings shortcuts:** open the TV's own search and settings
- **Custom wallpaper:** drop in your own image and rebuild

## Get the APK

You don't need Android Studio. GitHub builds the app for you.

1. Open the **Actions** tab of this repo.
2. Open the latest run of **Build APK**. A green check means it worked.
3. Scroll to **Artifacts** at the bottom and download `unwind-launcher-apk`.
4. Unzip it to get `app-debug.apk`.

Pushing any change to the repo triggers a new build. You can also start one with **Run workflow**.

## Install on your TV

1. Get `app-debug.apk` onto the TV (USB drive, a file-transfer app, or a cloud drive and the TV's browser).
2. Allow installs from unknown sources for whichever app you use to open the file.
3. Open the APK and install it.
4. Launch **Unwind** from your TV's app list or from a third-party launcher.

## Using it

- **Home:** OK on a tile opens the app.
- **Add apps tile:** opens a picker. Press OK on an app to add it to Home or remove it (a ✓ marks pinned apps).
- **Rail buttons:** Home, Search, All apps, Settings.
- **Back:** returns to Home from any screen.

## Customize

| What | Where |
| --- | --- |
| Wallpaper | Replace `wallpaper.jpg` (keep the file name) |
| App name shown top-left | `APP_NAME` at the top of `MainActivity.kt` |
| Default pinned apps | `DEFAULT_FAVS` in `MainActivity.kt` (package names) |
| Colors | `Lime` and `Forest` at the top of `MainActivity.kt` |

If you delete `wallpaper.jpg`, the launcher falls back to a built-in gradient.

## Repo layout

All files sit in the repo root so they can be uploaded from a phone or tablet. The build workflow (`.github/workflows/build.yml`) moves them into a standard Android project structure before building:

| File | Becomes |
| --- | --- |
| `MainActivity.kt` | `app/src/main/java/com/unwind/launcher/MainActivity.kt` |
| `AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` |
| `app-build.gradle.kts` | `app/build.gradle.kts` |
| `strings.xml`, `themes.xml` | `app/src/main/res/values/` |
| `ic_banner.xml` | `app/src/main/res/drawable/` |
| `wallpaper.jpg` | `app/src/main/res/drawable-nodpi/` |

`settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties` stay in the root.

## Tech

- Kotlin 1.9, Jetpack Compose (BOM 2024.06), Material 3 and extended icons
- Android Gradle Plugin 8.5, Gradle 8.7, JDK 17
- minSdk 24, targetSdk 34
- Plain Compose focus handling, no `androidx.tv` dependency

## Limitations

- **Home button:** this runs as an app you open, not a replacement home screen. Android TV 10 generally doesn't let third-party launchers take over the Home button.
- **Glass effect:** it's simulated with translucent gradients. Real background blur needs Android 12 or newer.
- **Phone apps:** sideloaded phone apps are built for touch, so some may be hard to use with a remote.
- **Build type:** the workflow produces a debug APK, which is signed with a debug key and is fine for sideloading.
