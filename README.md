# Unwind Launcher

A calm, glassy home screen for Android TV, built with Kotlin and Jetpack Compose. Frosted-glass dock, a time-aware greeting, themed app icons, and real nature wallpapers.

## Features

- **Three glass styles:** Liquid Glass, Normal, and Frost Glass. Panels blur the wallpaper behind them.
- **Six wallpapers:** pick one in Launcher settings. Icons, highlights, and glass pick up its colors.
- **Themed icons:** app logos are recolored to match the wallpaper, or switch to Original full-color icons.
- **Icon shapes:** Circle, Squircle, Rounded, or Hexagon. App names can be turned off.
- **Pinned apps row:** your favorites on Home, with an Add apps tile to choose them.
- **All apps grid:** includes sideloaded phone apps (like Google Photos) that stock launchers hide.
- **App search:** an on-screen keyboard that filters your installed apps.
- **Top bar:** search, system settings, Wi-Fi networks (connected one highlighted), TV inputs (HDMI, USB, tuner), clock, and account.
- **D-pad friendly:** everything works with a normal TV remote.

## Get the APK

GitHub builds the app for you, so you don't need Android Studio.

1. Open the **Actions** tab of this repo.
2. Open the latest run of **Build APK**. A green check means it worked.
3. Scroll to **Artifacts** and download `unwind-launcher-apk`.
4. Unzip it to get `app-debug.apk`, then sideload it on your TV.

Every push triggers a new build. You can also start one with **Run workflow**.

## Using it

| Where | What it does |
| --- | --- |
| Dock: Home / Search / Apps | Home screen, in-app search, all apps |
| Dock: sliders icon | Launcher settings (wallpaper, glass, icon shape and style, app names) |
| Top bar: gear | Android system settings |
| Top bar: Wi-Fi | Nearby networks, connected one highlighted |
| Top bar: HDMI icon | Switch TV inputs |
| Top bar: avatar | Signed-in Google account |
| Add apps tile | Choose which apps are pinned to Home |

## Permissions

The first time you open the Wi-Fi or account popups, Android asks for permission:

- **Location:** Android requires it to list nearby Wi-Fi networks. Nothing is stored or sent anywhere.
- **Accounts and contacts:** used only to show which Google account is signed in. A TV usually has no profile photo, so the avatar shows your initial.

## Customize in code

| What | Where |
| --- | --- |
| Launcher name (top-left) | `APP_NAME` in `MainActivity.kt` |
| Default pinned apps | `DEFAULT_FAVS` in `MainActivity.kt` |
| Wallpaper list and their colors | `WALLPAPERS` in `Model.kt` |
| Your own wallpaper | Replace one of the `wp_*.jpg` files (1920x1080 works best) |

## Repo layout

All files sit in the repo root so they can be uploaded from a phone or tablet. The workflow (`.github/workflows/build.yml`) moves them into a standard Android project before building: `*.kt` files go to `app/src/main/java/com/unwind/launcher/`, `*.jpg` wallpapers to `res/drawable-nodpi/`, and the manifest, strings, theme, and banner to their usual spots.

## Tech

Kotlin 1.9, Compose BOM 2024.06, Material 3 and extended icons, Android Gradle Plugin 8.5, Gradle 8.7, JDK 17. minSdk 24, targetSdk 34.

## Limitations

- **Home button:** this runs as an app you open. Android TV 10 generally doesn't let third-party launchers take over Home.
- **Icon theming:** logos are separated from their backgrounds automatically. A few unusual icons fall back to their original look.
- **Switching inputs:** works through the TV's own Live TV app, so behavior varies by brand.
- **Phone apps:** sideloaded phone apps are built for touch, so some can be hard to use with a remote.
