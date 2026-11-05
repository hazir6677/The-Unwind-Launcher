<div align="center">

# Unwind Launcher

**A calm, glassy home screen for Android TV.**

Liquid-glass panels, themed app icons, a daily Bing wallpaper (or your own photo), and a settings page that lets you make it yours.

[![Build APK](https://github.com/hazir6677/The-Unwind-Launcher/actions/workflows/build.yml/badge.svg)](https://github.com/hazir6677/The-Unwind-Launcher/actions/workflows/build.yml)
![Android TV](https://img.shields.io/badge/Android%20TV-10%2B-3ddc84)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff)

</div>

<!-- To add a screenshot: upload a file named screenshot.png to the repo root, then remove the comment markers on the next line.
Crop out your account email and Wi-Fi name first.
![Unwind Launcher](screenshot.png)
-->

## Features

**Look and feel**
- **Three glass styles:** Liquid Glass, Normal, and Frost Glass. Liquid Glass bends the wallpaper at the edges like real glass (refraction), adds edge light and a rainbow fringe, and swells with a springy motion when you focus it.
- **Wallpapers:** six stock wallpapers, **Bing image of the day** (changes daily), or **your own picture** from the TV or a USB drive. Icons, glass, and highlights are tinted from whatever picture is on screen.
- **Themed icons:** logos are separated from their backgrounds at high resolution and recolored to match the wallpaper. Anything that can't be separated is shown as a two-tone chip, so every icon still matches. Switch to Original for full-color icons.
- **Icon shapes:** Circle, Squircle, Rounded, or Hexagon. App names can be hidden on Home.
- **Smooth motion:** one spring curve drives focus, the dock highlight slides between pages, and the wallpaper cross-fades.
- **Page header:** the top-left always says where you are: Home, Search, Apps, Favorites, or Preferences.

**Everyday use**
- **Pinned apps row:** your favorites on Home, with an Add apps tile to choose them.
- **All apps grid:** includes sideloaded phone apps like Google Photos that stock launchers hide.
- **App search:** an on-screen keyboard that filters your installed apps as you type.
- **Top bar:** system settings, **all nearby Wi-Fi networks** (with signal and band), TV inputs (HDMI in order, no duplicates), clock, and your Google account.
- **Remote friendly:** the D-pad always takes you back and forth between the dock, the top bar, and your apps.
- **Home screen replacement:** can be set as the app the Home button opens (see below).

## Make it your Home screen

1. Press the **Home** button on your remote.
2. If the TV asks which Home app to use, pick **Unwind**, then choose **Always**.
3. No question? Open **Preferences** in Unwind, scroll to the bottom, and tap **Open Home settings**. Pick Unwind there.

To go back, pick your TV's original Home app in the same place, or uninstall Unwind.

Some TVs lock the Home button to their built-in home screen. If yours does, Unwind still works as an app, and a one-time ADB command can make it the default (see below).

<details>
<summary>ADB method (computer needed, once)</summary>

1. On the TV: Settings, Device Preferences, About, press **Build** 7 times to unlock Developer options. Then turn on **Network debugging** (or USB debugging).
2. On a computer with ADB installed, connect: `adb connect <TV-IP-address>`
3. Run: `adb shell cmd package set-home-activity com.unwind.launcher/.MainActivity`

To undo it, uninstall Unwind (`adb uninstall com.unwind.launcher`).

</details>

## Using it

| Where | What it does |
| --- | --- |
| Dock: house | Home screen |
| Dock: magnifier | In-launcher app search |
| Dock: grid | All installed apps |
| Dock: sliders | Preferences |
| Top bar: magnifier | App search |
| Top bar: gear | Android system settings |
| Top bar: Wi-Fi | Nearby networks, connected one highlighted |
| Top bar: HDMI icon | Switch TV inputs |
| Top bar: avatar | Signed-in Google account |
| Add apps tile | Choose which apps are pinned to Home |

## Preferences

| Setting | Options |
| --- | --- |
| Wallpaper | Bing daily, My photo, or 6 stock wallpapers |
| Glass style | Liquid Glass, Normal, Frost Glass |
| Icon shape | Circle, Squircle, Rounded, Hexagon |
| Icon style | Themed, Original |
| App names | On or off |
| Home screen | Short guide and a shortcut to Home settings |

**Your own picture:** choose **My photo**, then pick from the list of pictures on the TV or USB drive (or tap **Browse files...**). It is copied into the launcher, so it stays even if the original is removed.

**Bing daily:** needs internet. It checks when the launcher opens and again every half hour, and only downloads when there's a new picture.

## Permissions

| Permission | Why |
| --- | --- |
| Internet | Downloads the Bing picture of the day. |
| Location + Wi-Fi state | Android requires location to list nearby Wi-Fi networks. Nothing is stored or sent anywhere. |
| Pictures / storage | Only to show your pictures so you can pick a wallpaper. Asked the first time you open the picker. |
| Accounts and contacts | Only to show which Google account is signed in. |

## Customize in code

| What | Where |
| --- | --- |
| Default pinned apps | `DEFAULT_FAVS` in `MainActivity.kt` |
| Stock wallpaper list and colors | `WALLPAPERS` in `Model.kt` |
| Glass looks and refraction strength | `glass()` in `Glass.kt` (the `backdropBrush` zoom values) |
| Motion feel | `LiquidSpring` in `Glass.kt` |
| Icon extraction | `AppData.kt` |
| Stock wallpapers | Replace one of the `wp_*.jpg` files (1920x1080 works best) |

## How it works

- **Glass:** each wallpaper is pre-blurred at three strengths and cut to the screen's shape. Normal and Frost draw the matching blurred slice behind a tint. Liquid draws six thin nested lens bands: the outer rim pulls in wallpaper from outside the shape (what a glass edge does), each band inward does less of that, and the center magnifies slightly. Everything is anti-aliased paths, so edges stay smooth while scaling.
- **Themed icons:** modern icons are split into layers, and the foreground layer is exactly the logo. Older icons are analyzed by color, including logos sitting on a tile inside the icon. Glyphs are shrunk in halves to the exact size they're shown at, which avoids jagged edges on big screens.
- **Wallpaper colors:** the picture is sampled for its dominant hue, and the accent, glass tint, and dark shade are derived from it. Bright pictures get a stronger scrim so text stays readable.
- **Apps:** it lists both TV apps and regular phone apps, so sideloaded phone apps appear in the grid.

## Project layout

All files sit in the repo root so they can be uploaded from a phone or tablet. The workflow in `.github/workflows/build.yml` moves them into a standard Android project before building.

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | Activity, screen routing, popups, state, Home button handling |
| `Screens.kt` | Home, All apps, Search, Preferences, wallpaper picker |
| `Components.kt` | Tiles, dock, top bar, popups, D-pad wiring |
| `Glass.kt` | Liquid / Normal / Frost glass and wallpaper blur |
| `Wallpapers.kt` | Wallpaper loading, Bing daily, TV-storage picker, color extraction |
| `AppData.kt` | Installed apps and icon glyph extraction |
| `SystemInfo.kt` | Wi-Fi, TV inputs, accounts, default-Home check |
| `Model.kt` | Settings, wallpapers, shapes |
| `AndroidManifest.xml` | Permissions, launcher and Home entries |
| `unwind_icon.png`, `unwind_banner.png` | App icon and TV banner |
| `wp_*.jpg` | Stock wallpapers |
| `app-build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `proguard-rules.pro` | Gradle setup |

## Tech

Kotlin 1.9, Jetpack Compose (BOM 2024.06), Material 3 with extended icons, Android Gradle Plugin 8.5, Gradle 8.7, JDK 17. minSdk 24, targetSdk 34. No `androidx.tv` dependency.

## Limitations

- **Home button:** some TVs (including some Xiaomi models) lock the Home button to their own home screen. Unwind still works as an app there.
- **Icon theming:** a few unusual icons can't be separated from their background and are shown as two-tone chips.
- **Switching inputs:** works through the TV's own Live TV app, so it varies by brand.
- **Phone apps:** sideloaded phone apps are built for touch, so some can be hard to use with a remote.
- **Signing:** the APKs are signed with a debug key, which is fine for sideloading.

## Ideas for later

- Reorder pinned apps
- More glass and icon styles
- Dock and top-bar layout options

## License

MIT. See `LICENSE`.
