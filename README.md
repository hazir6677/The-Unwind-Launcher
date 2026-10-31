<div align="center">

# Unwind Launcher

**A calm, glassy home screen for Android TV.**

Frosted-glass dock, themed app icons, real nature wallpapers, and a launcher settings page that lets you make it yours.

[![Build APK](https://github.com/hazir6677/Unwind-Launcher/actions/workflows/build.yml/badge.svg)](https://github.com/hazir6677/Unwind-Launcher/actions/workflows/build.yml)
![Android TV](https://img.shields.io/badge/Android%20TV-10%2B-3ddc84)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff)

</div>

<!-- To add a screenshot: upload a file named screenshot.png to the repo root, then remove the comment markers on the next line.
![Unwind Launcher](screenshot.png)
-->

## Features

**Look and feel**
- **Three glass styles:** Liquid Glass, Normal, and Frost Glass. Every panel blurs the wallpaper behind it.
- **Six wallpapers:** Misty Valley, Emerald Lake, Golden Hour, Morning Fog, Dusk, and Night Canopy. Icons, highlights, and glass pick up each wallpaper's colors.
- **Themed icons:** app logos are separated from their backgrounds and recolored to match the wallpaper. Switch to Original for full-color icons.
- **Icon shapes:** Circle, Squircle, Rounded, or Hexagon. App names can be hidden.
- **Time-aware greeting:** the headline changes through the day.

**Everyday use**
- **Pinned apps row:** your favorites on Home, with an Add apps tile to choose them.
- **All apps grid:** includes sideloaded phone apps like Google Photos that stock launchers hide.
- **App search:** an on-screen keyboard that filters your installed apps as you type.
- **Top bar:** system settings, Wi-Fi networks (connected one highlighted), TV inputs (HDMI, USB, tuner), clock, and your Google account.
- **Remote friendly:** everything is reachable with a normal D-pad remote.

## Quick start

GitHub builds the app for you, so you don't need Android Studio.

1. Open the **Actions** tab of this repo.
2. Open the latest run of **Build APK**. A green check means it worked.
3. Scroll to **Artifacts** and download `unwind-launcher-apk`.
4. Unzip it to get `app-debug.apk`.
5. Sideload it on your TV (USB drive, a file-transfer app, or a cloud drive), allow installs from unknown sources, and install.
6. Open **Unwind** from your TV's app list or from another launcher.

Every push starts a new build. You can also start one by hand with **Run workflow**.

## Using it

| Where | What it does |
| --- | --- |
| Dock: house | Home screen |
| Dock: magnifier | In-launcher app search |
| Dock: grid | All installed apps |
| Dock: sliders | Launcher settings |
| Top bar: gear | Android system settings |
| Top bar: Wi-Fi | Nearby networks, connected one highlighted |
| Top bar: HDMI icon | Switch TV inputs |
| Top bar: avatar | Signed-in Google account |
| Add apps tile | Choose which apps are pinned to Home |

## Launcher settings

Open it from the sliders icon in the dock.

| Setting | Options |
| --- | --- |
| Wallpaper | 6 stock wallpapers |
| Glass style | Liquid Glass, Normal, Frost Glass |
| Icon shape | Circle, Squircle, Rounded, Hexagon |
| Icon style | Themed, Original |
| App names | On or off |

Your choices are saved on the TV and applied instantly.

## Permissions

You're asked the first time you open the Wi-Fi or account popups.

| Permission | Why |
| --- | --- |
| Location | Android requires it to list nearby Wi-Fi networks. Nothing is stored or sent anywhere. |
| Accounts and contacts | Only to show which Google account is signed in. Most TVs have no profile photo, so the avatar shows your initial. |

## Customize in code

| What | Where |
| --- | --- |
| Launcher name (top-left) | `APP_NAME` in `MainActivity.kt` |
| Default pinned apps | `DEFAULT_FAVS` in `MainActivity.kt` |
| Wallpaper list and colors | `WALLPAPERS` in `Model.kt` |
| Glass looks | `glass()` in `Glass.kt` |
| Your own wallpaper | Replace one of the `wp_*.jpg` files (1920x1080 works best) |

## How it works

- **Glass:** each wallpaper is pre-blurred at two strengths. Glass elements draw the matching slice of the blurred image behind a tinted overlay, so the blur lines up with the screen.
- **Themed icons:** the launcher estimates each icon's background color, keeps the pixels that differ from it, crops to the logo, and tints that shape with the wallpaper's accent. If it can't find a clean logo, it uses the original icon.
- **Apps:** it lists both TV apps and regular phone apps, so sideloaded phone apps appear in the grid.

## Project layout

All files sit in the repo root so they can be uploaded from a phone or tablet. The workflow in `.github/workflows/build.yml` moves them into a standard Android project before building.

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | Activity, screen routing, popups, state |
| `Screens.kt` | Home, All apps, Search, Launcher settings |
| `Components.kt` | Tiles, dock, top bar, popups |
| `Glass.kt` | Glass styles and wallpaper blur |
| `AppData.kt` | Installed apps and icon glyph extraction |
| `SystemInfo.kt` | Wi-Fi, TV inputs, accounts |
| `Model.kt` | Settings, wallpapers, shapes |
| `AndroidManifest.xml` | Permissions and launcher entry |
| `wp_*.jpg` | Stock wallpapers |
| `app-build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | Gradle setup |

## Tech

Kotlin 1.9, Jetpack Compose (BOM 2024.06), Material 3 with extended icons, Android Gradle Plugin 8.5, Gradle 8.7, JDK 17. minSdk 24, targetSdk 34. No `androidx.tv` dependency.

## Limitations

- **Home button:** Unwind runs as an app you open. Android TV 10 generally doesn't let third-party launchers replace Home.
- **Icon theming:** a few unusual icons can't be separated from their background and keep their original look.
- **Switching inputs:** works through the TV's own Live TV app, so it varies by brand.
- **Phone apps:** sideloaded phone apps are built for touch, so some can be hard to use with a remote.
- **Debug build:** the APK is signed with a debug key, which is fine for sideloading.

## Ideas for later

- Use your own photo as a wallpaper
- More glass and icon styles
- Reorder pinned apps

## License

No license has been added yet. Add a `LICENSE` file before sharing the project publicly.
