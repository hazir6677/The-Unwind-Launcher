# Unwind Launcher

A custom home screen for Android TV. Frosted glass panels sit on top of your wallpaper, and the icons, glass, weather chip and highlights all take their colors from whatever picture you pick.

[](https://github.com/hazir6677/The-Unwind-Launcher/actions/workflows/build.yml)

&lt;p align=&quot;center&quot;&gt;
  &lt;img src=&quot;Tinted homescreen with weather.jpg&quot; alt=&quot;Unwind home screen with tinted glass, pinned apps and live weather chip&quot; width=&quot;100%&quot;&gt;
&lt;/p&gt;

I run it on a 55-inch Xiaomi TV, so that's where most of the testing happened.

## What's new in v2.2

- **Live weather and 5-day forecast:** A small weather chip sits right under the page title in the top-left corner with custom-drawn sky icons tinted to your wallpaper. Press OK on it to see current conditions, feels-like temperature, humidity, wind speed and a 5-day outlook (with rain probability) powered by [Open-Meteo](https://open-meteo.com/). It detects your city automatically from the TV's location or IP address, or lets you search for any city, town or village and switch between Celsius (`°C, km/h`) and Fahrenheit (`°F, mph`).
- **Wallpaper color themes (`Soft`, `Vivid`, `Mixed`):** Pulls three distinct palettes from the same wallpaper, similar to Material You on Pixel. Pick **Soft** for lighter pastel shades, **Vivid** for brighter punchier accents, or **Mixed** for a balanced middle ground.
- **App folders in the app drawer:** Switch the All apps page from the A–Z grid to **Folders** (`Video`, `Music`, `Games`, `Social`, `News`, `Photos`, `Productivity` and `Other`), sorted automatically like the iOS App Library. Hold OK on any app inside a folder to move it to another folder or reset it to automatic sorting.
- **Home layouts and adjustable glass dock:** Keep your pinned apps in one **Endless row** that scrolls sideways, or switch to **Stacked rows** that fill the screen width and wrap below. You can also place a glass dock bar behind the first row in your active glass style and set its height (`Default`, `Lower` or `Lowest`).
- **Editable greetings for every part of the day:** Rewrite the three-line greeting on Home (`Small line`, `Headline` and `Highlighted line`) separately for Morning, Afternoon, Evening and Night using the built-in editor and live preview.
- **Unified shape language and sliding rail highlight:** The left dock, top-right pill, home dock, search box and weather chip now take their outer corners from your chosen icon shape (`Circle`, `Squircle`, `Rounded` or `Hexagon`), and the active page highlight in the left dock slides smoothly with a spring motion.
- **System keyboard search and smarter icon tinting:** App search and weather city lookup now use Android TV's built-in on-screen keyboard (`Gboard` or system IME) without trapping D-pad focus. Themed icon extraction also keeps tonal shading on multicolored logos and reaches inside inner tile backgrounds.
- **Welcome screen and smoother release builds:** A short animated intro runs the first time Unwind opens (and can be replayed from Preferences). GitHub Actions now builds a minified `app-release.apk` alongside `app-debug.apk`, with `ProfileInstaller` pre-compiling Compose code on first run to remove start-up stutter.

## Screenshots

### v2.2 highlights

&lt;p align=&quot;center&quot;&gt;
  &lt;img src=&quot;Tinted homescreen with weather.jpg&quot; alt=&quot;Tinted home screen with weather chip and evening greeting&quot; width=&quot;100%&quot;&gt;
&lt;/p&gt;
&lt;p align=&quot;center&quot;&gt;&lt;em&gt;Tinted home screen with the live weather chip, custom evening greeting and themed icons&lt;/em&gt;&lt;/p&gt;

&lt;table&gt;
  &lt;tr&gt;
    &lt;td&gt;&lt;img src=&quot;App folders in the app drawer.jpg&quot; alt=&quot;App folders in the app drawer&quot;&gt;&lt;/td&gt;
    &lt;td&gt;&lt;img src=&quot;Launcher prefernces page with color theme styles.jpg&quot; alt=&quot;Preferences page with wallpaper and color theme styles&quot;&gt;&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td align=&quot;center&quot;&gt;App folders in the app drawer (press OK to open, hold OK on an app to move it)&lt;/td&gt;
    &lt;td align=&quot;center&quot;&gt;Preferences page with Daily wallpaper, Soft / Vivid / Mixed color themes and glass styles&lt;/td&gt;
  &lt;/tr&gt;
&lt;/table&gt;

### Glass styles across wallpapers

Same home screen, two wallpapers, two glass styles. The colors change on their own when the wallpaper does.

&lt;table&gt;
  &lt;tr&gt;
    &lt;td&gt;&lt;img src=&quot;shot_home_liquid_green.png&quot; alt=&quot;Liquid Glass, green wallpaper&quot;&gt;&lt;/td&gt;
    &lt;td&gt;&lt;img src=&quot;shot_home_normal_green.png&quot; alt=&quot;Normal glass, green wallpaper&quot;&gt;&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td align=&quot;center&quot;&gt;Liquid Glass, Misty Valley wallpaper&lt;/td&gt;
    &lt;td align=&quot;center&quot;&gt;Normal glass, Misty Valley wallpaper&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td&gt;&lt;img src=&quot;shot_home_liquid_amber.png&quot; alt=&quot;Liquid Glass, amber wallpaper&quot;&gt;&lt;/td&gt;
    &lt;td&gt;&lt;img src=&quot;shot_preferences.png&quot; alt=&quot;Preferences screen overview&quot;&gt;&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td align=&quot;center&quot;&gt;Liquid Glass, amber sunrise wallpaper&lt;/td&gt;
    &lt;td align=&quot;center&quot;&gt;Preferences overview&lt;/td&gt;
  &lt;/tr&gt;
&lt;/table&gt;

## What you get

There are three glass styles: **Liquid Glass**, **Normal** and **Frost Glass**. Normal is a tinted, softly blurred panel. Frost Glass is a heavier, milkier blur. Liquid Glass is the fancy one. It bends the wallpaper at the edges the way a real piece of glass would, adds a bit of edge light and a rainbow fringe, and swells with a springy motion when you focus on it.

For wallpapers you can use one of the six that come with it (`Misty Valley`, `Emerald Lake`, `Golden Hour`, `Morning Fog`, `Dusk`, `Night Canopy`), the Bing daily wallpaper, or your own picture from the TV or a USB drive. From there, you can switch the palette between **Soft**, **Vivid** and **Mixed** so the tint and accent are as subtle or punchy as you want.

App icons get recolored to match the wallpaper. The logo is pulled off its background at high resolution and tinted. Multicolored foreground logos keep their lighter and darker facets so they don't flatten into a plain blob, and if an icon can't be separated cleanly, it shows up as a two-tone chip so it still fits in. You can switch to **Original** if you'd rather have the full-color icons. Icon shapes are **Circle**, **Squircle**, **Rounded** and **Hexagon**, and all the bars and panels reshape their ends to match.

The top-left corner always tells you where you are (`Home`, `Search`, `Apps`, `Favorites` or `Preferences`), with the live weather chip tucked right underneath.

Day to day:

- **Home** has your pinned apps plus an **Add apps** tile at the end for choosing them.
- **All apps** includes sideloaded phone apps like Google Photos, which most TV launchers hide. Switch between a single A–Z grid or automatic **Folders** (`Video`, `Music`, `Games`, `Social`, `News`, `Photos`, `Productivity`, `Other`), and hold OK on an app inside a folder to move it somewhere else.
- **Search** uses your TV's native on-screen keyboard to filter installed apps as you type, without locking up remote navigation.
- **Weather** sits under the page title and opens a 5-day forecast popup with automatic location detection, manual city search and Celsius/Fahrenheit switching.
- **Top bar** has a search shortcut, Android system settings, a live list of nearby Wi-Fi networks (with signal strength and 2.4 / 5 / 6 GHz band), TV inputs (HDMI, tuner, AV), the clock and your signed-in Google account.
- **D-pad navigation** moves cleanly between the left dock, the weather chip, the top bar and your apps in every direction.
- **Home layout** is your call: one endless row that scrolls sideways, or stacked rows that fill the screen width and wrap below. Either way you can turn on a glass dock behind the first row and set how low it sits on the screen.
- **Greetings** on Home (the `"Good evening / Time to unwind"` bit) can be rewritten for morning, afternoon, evening and night from Preferences.
- **Welcome screen** plays a short animated intro the first time Unwind opens, and you can replay it any time from the bottom of Preferences.

## Getting the APK

Download the latest APK from the [Releases page](https://github.com/hazir6677/The-Unwind-Launcher/releases) (or grab the build artifact from [GitHub Actions](https://github.com/hazir6677/The-Unwind-Launcher/actions/workflows/build.yml)) and sideload it onto your TV.

Both `app-release.apk` and `app-debug.apk` are built automatically. Install `app-release.apk` when available—it's minified and noticeably smoother on TV hardware.

The APK is signed with a fixed key that lives in this repo's GitHub Secrets (never in the code), so each new build installs right over the old one and keeps your settings. It's a personal sideload key, so it won't go on the Play Store as is.

## Making it your Home screen

Press the **Home** button on your remote. If the TV asks which Home app to use, pick **Unwind** and choose **Always**.

If it doesn't ask, open **Preferences** in Unwind, scroll to the bottom and tap **Open Home settings**, then pick Unwind from there.

To go back, pick your TV's original Home app in the same place, or just uninstall Unwind.

Some TVs lock the Home button to their built-in screen. Xiaomi does this on some models. If yours does, Unwind still works as a regular app, and you can force it with ADB:

1. On the TV, go to **Settings > Device Preferences > About** and press **Build** seven times to unlock **Developer options**. Then turn on **Network debugging** (or **USB debugging**).
2. On a computer with ADB, connect: `adb connect <TV-IP-address>`
3. Run: `adb shell cmd package set-home-activity com.unwind.launcher/.MainActivity`

To undo it, uninstall Unwind: `adb uninstall com.unwind.launcher`

## Finding your way around

The dock on the left has four buttons: house for **Home**, magnifier for **Search**, grid for **All apps**, and sliders for **Preferences**. A spring-animated highlight slides to whichever screen you're on.

Right above the dock sits the page title and the **Weather chip**. Press OK on the weather chip to open the 5-day forecast, switch between `°C` and `°F`, or change your city.

The pill in the top right has the search shortcut, a gear for Android system settings, **Wi-Fi** (the connected network is highlighted), an **HDMI** icon for switching TV inputs, the clock, and an avatar showing which Google account is signed in.

## Preferences

- **Wallpaper:** `Daily wallpaper` (Bing image of the day), `My photo`, or one of the six built-in nature wallpapers
- **Color theme:** `Soft` (lighter, pastel shades), `Vivid` (brighter, punchier shades), or `Mixed` (a balance of both)
- **Glass style:** `Liquid Glass`, `Normal`, `Frost Glass`
- **Icon shape:** `Circle`, `Squircle`, `Rounded`, `Hexagon`
- **Icon style:** `Themed` (tinted to your wallpaper) or `Original` (full-color app icons)
- **Home layout:** `Endless row` (scrolls sideways) or `Stacked rows` (wraps into rows below)
- **Dock:** `Dock on` or `Dock off` (places a glass bar behind the first row)
- **Dock height:** `Default`, `Lower`, `Lowest`
- **All apps page:** `All apps` (A to Z grid) or `Folders` (sorted by app type; hold OK on an app to move it)
- **App names:** `Names on` or `Names off` on Home
- **Greetings:** Customize the 3-line greeting for `Morning` (5 AM–12 PM), `Afternoon` (12 PM–5 PM), `Evening` (5 PM–9 PM) and `Night` (9 PM–5 AM)
- **Make Unwind your Home screen:** Quick setup steps and a shortcut to **Open Home settings**
- **Welcome screen:** **Replay welcome screen** button

For your own picture, choose **My photo**, then pick from the list of pictures on the TV or USB drive (or tap **Browse files...**). The picture gets copied into the launcher and scaled to TV resolution (handling EXIF rotation automatically), so it stays even if you unplug the USB drive or delete the original. Tap **My photo** again any time to pick a different one.

Daily wallpaper and Weather both need internet. They check when the launcher opens and refresh every half hour while it's up.

## Permissions

- **Internet:** to download the Bing daily wallpaper and fetch weather forecasts from [Open-Meteo](https://open-meteo.com/) (plus city search via Open-Meteo and [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org/), and IP fallback location when GPS/network location isn't granted).
- **Location and Wi-Fi state:** Android won't let an app list nearby Wi-Fi networks without location access. If granted, Unwind also uses the TV's last known location for local weather. Nothing is stored externally or tracked.
- **Pictures and storage:** only to show your pictures when you pick a custom wallpaper. It asks the first time you open the picture picker.
- **Accounts and contacts:** only to show which Google account is signed in and its profile avatar in the top bar.

## How it works

**Glass.** Each wallpaper is blurred ahead of time at three strengths (`soft`, `heavy` and `clear`) and cropped to the screen's aspect ratio so the backdrop lines up with the wallpaper behind it. Normal and Frost just draw the matching blurred slice behind a tint. Liquid Glass stacks six thin lens bands (`LENS_Z` from `0.84x` at the outer rim to `1.06x` in the center). The outer rim pulls in wallpaper from outside the shape, which is what the edge of a real piece of glass does. Each band further in does a little less of that, and the center magnifies slightly. Everything is drawn as anti-aliased paths, so edges stay smooth as tiles scale.

**Icons.** Modern Android icons come in layers, and Unwind checks the Android 13+ monochrome layer or adaptive foreground layer first. If an adaptive foreground is a flat silhouette whose original artwork is multicolored, Unwind maps its color luminance into tonal opacity so the tinted glyph keeps its internal detail. Older icons are separated from their background by border color and luminance—even looking inside an inner tile if the logo sits inside a box—and shrunk in halves (`scaleDown`) so edges stay crisp on a big screen.

**Colors.** The wallpaper is sampled on a 48×27 grid weighted by saturation and brightness to find its dominant hue. From that base hue, Unwind builds the **Mixed**, **Soft** and **Vivid** palettes and adjusts the dark background scrims based on the wallpaper's overall luminance so text always stays readable.

**Weather.** Forecasts come from [Open-Meteo](https://open-meteo.com/), which needs no API key or account. All weather icons (sun, moon, clouds, fog, rain, snow, lightning) are drawn directly on a Compose `Canvas` using the wallpaper's accent color, so no external icon font is needed.

**Apps.** Queries both `CATEGORY_LEANBACK_LAUNCHER` and standard `CATEGORY_LAUNCHER` activities, which is why sideloaded phone apps show up alongside native TV apps. Folder view checks `ApplicationInfo.category` first and falls back to package-name matching for TV apps that don't declare a category.

## Tech

[Kotlin](https://kotlinlang.org/) 1.9.24 and [Jetpack Compose](https://developer.android.com/compose) (BOM `2024.06.00`), Material 3 with extended icons, AndroidX `ProfileInstaller` `1.3.1`, Android Gradle Plugin `8.5.2`, Gradle `8.7`, JDK 17. `minSdk 24`, `targetSdk 34`. Zero `androidx.tv` dependency—all D-pad focus routing and spring animations are built directly on plain Compose primitives.

## Known problems

- Some TVs, including some Xiaomi models, lock the Home button to their own home screen. Unwind still works as an app there, or you can set it via the ADB command above.
- A few unusual icons can't be separated from their background, so they show as two-tone chips.
- Switching inputs goes through the TV's own Live TV / input framework (`TvInputManager`), so how well it works depends on the brand.
- Sideloaded phone apps are built for touch, and some are hard to use with a remote.

## Maybe later

- Reordering pinned apps on Home
- Custom folder creation and renaming
- More glass and icon styles
- Top bar layout options

## License

MIT. See `LICENSE`.
