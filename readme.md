# Unwind Launcher

A custom home screen for Android TV. Frosted glass panels sit on top of your wallpaper, and the icons, glass and highlights all take their colors from whatever picture you pick.

[](https://github.com/hazir6677/The-Unwind-Launcher/actions/workflows/build.yml)

&lt;p align=&quot;center&quot;&gt;
  &lt;img src=&quot;Tinted%20homescreen%20with%20weather.jpg&quot; alt=&quot;Unwind home screen with tinted glass and weather&quot; width=&quot;100%&quot;&gt;
&lt;/p&gt;

I run it on a 55-inch Xiaomi TV, so that's where most of the testing happened.

## What's new in v2.2

- Weather chip under the page title with a 5-day forecast, automatic location or city search, and Celsius/Fahrenheit switching.
- Three color themes pulled from your wallpaper: Soft, Vivid and Mixed.
- App folders in the app drawer, sorted by app type (hold OK on an app to move it to another folder).
- Endless row or stacked rows on Home, with an optional glass dock and height settings.
- Custom greetings for morning, afternoon, evening and night, plus a first-run welcome screen.
- Smooth release builds (`app-release.apk`), system keyboard search, and bars that match your chosen icon shape.

## Screenshots

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
    &lt;td&gt;&lt;img src=&quot;Tinted%20homescreen%20with%20weather.jpg&quot; alt=&quot;Normal glass with weather chip&quot;&gt;&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td align=&quot;center&quot;&gt;Liquid Glass, my own photo&lt;/td&gt;
    &lt;td align=&quot;center&quot;&gt;Normal glass with the weather chip&lt;/td&gt;
  &lt;/tr&gt;
&lt;/table&gt;

App folders in the app drawer, and the Preferences page where you pick the wallpaper, color theme and glass style:

&lt;table&gt;
  &lt;tr&gt;
    &lt;td&gt;&lt;img src=&quot;App%20folders%20in%20the%20app%20drawer.jpg&quot; alt=&quot;App folders in the app drawer&quot;&gt;&lt;/td&gt;
    &lt;td&gt;&lt;img src=&quot;Launcher%20prefernces%20page%20with%20color%20theme%20styles.jpg&quot; alt=&quot;Unwind Preferences page with color themes&quot;&gt;&lt;/td&gt;
  &lt;/tr&gt;
  &lt;tr&gt;
    &lt;td align=&quot;center&quot;&gt;App folders in the app drawer&lt;/td&gt;
    &lt;td align=&quot;center&quot;&gt;Preferences: wallpaper, color theme and glass style&lt;/td&gt;
  &lt;/tr&gt;
&lt;/table&gt;

## What you get

There are three glass styles: Liquid Glass, Normal and Frost Glass. Normal is a tinted, softly blurred panel. Frost Glass is a heavier, milkier blur. Liquid Glass is the fancy one. It bends the wallpaper at the edges the way a real piece of glass would, adds a bit of edge light and a rainbow fringe, and swells with a springy motion when you focus on it.

For wallpapers you can use one of the six that come with it, the daily wallpaper, or your own picture from the TV or a USB drive. Each wallpaper gives you three color themes: Soft (lighter, pastel shades), Vivid (brighter, punchier shades) and Mixed (a balance of both).

App icons get recolored to match the wallpaper. The logo is pulled off its background at high resolution and tinted. If an icon can't be separated cleanly, it shows up as a two-tone chip so it still fits in. You can switch to Original if you'd rather have the full-color icons. Icon shapes are Circle, Squircle, Rounded and Hexagon, and the bars and panels take their corners from the shape you pick. You can also hide the app names on Home.

The top-left corner always tells you where you are (Home, Search, Apps, Favorites or Preferences), with a small weather chip right below it.

Day to day:

- Home has a row of pinned apps, plus an Add apps tile for choosing them.
- The All apps page includes sideloaded phone apps like Google Photos, which most TV launchers hide. In Preferences you can switch it to folders (Video, Music, Games and so on, sorted by each app's category). Hold OK on an app inside a folder to move it somewhere else.
- Search uses the TV's on-screen keyboard to filter your installed apps as you type.
- The weather chip opens current conditions and a 5-day outlook from [Open-Meteo](https://open-meteo.com/). It finds your city automatically or lets you search for one, and switches between Celsius and Fahrenheit.
- The top bar has system settings, a list of every nearby Wi-Fi network (with signal strength and band), TV inputs, the clock, and your Google account.
- The D-pad moves between the dock, the weather chip, the top bar and your apps, in both directions.
- Home layout is your call: one endless row that scrolls sideways, or rows that fill the screen width and then wrap below. Either way you can turn on a glass dock behind the first row, in whichever glass style you picked, and set how low the dock sits on the screen.
- The greeting on Home (the "Time to unwind" bit) can be rewritten for morning, afternoon, evening and night from Preferences.
- A short welcome screen shows the first time Unwind opens. You can replay it from the bottom of Preferences.

## Getting the APK

Download the latest `app-release.apk` (or `app-debug.apk`) from the [Releases page](https://github.com/hazir6677/The-Unwind-Launcher/releases) and sideload it onto your TV. The release build is much smoother than the debug build, so that's the one to install.

The APK is signed with a fixed key that lives in this repo's GitHub Secrets (never in the code), so each new build installs over the old one and keeps your settings. It's a personal sideload key, so it won't go on the Play Store as is.

## Making it your Home screen

Press the Home button on your remote. If the TV asks which Home app to use, pick Unwind and choose Always.

If it doesn't ask, open Preferences in Unwind, scroll to the bottom and tap Open Home settings, then pick Unwind from there.

To go back, pick your TV's original Home app in the same place, or just uninstall Unwind.

Some TVs lock the Home button to their built-in screen. Xiaomi does this on some models. If yours does, Unwind still works as a regular app, and you can force it with ADB:

1. On the TV, go to Settings > Device Preferences > About and press Build seven times to unlock Developer options. Then turn on Network debugging (or USB debugging).
2. On a computer with ADB, connect: `adb connect <TV-IP-address>`
3. Run: `adb shell cmd package set-home-activity com.unwind.launcher/.MainActivity`

To undo it, uninstall Unwind: `adb uninstall com.unwind.launcher`

## Finding your way around

The dock on the left has four buttons: house for Home, magnifier for search, grid for all apps, sliders for Preferences.

Above the dock sits the page title and the weather chip. Press OK on the weather chip for the 5-day forecast, city search and temperature units.

The pill in the top right has the same search shortcut, a gear for Android system settings, Wi-Fi (the connected network is highlighted), an HDMI icon for switching inputs, the clock, and an avatar showing which Google account is signed in.

## Preferences

- Wallpaper: Daily wallpaper, My photo, or one of the six stock wallpapers
- Color theme: Soft, Vivid, Mixed
- Glass style: Liquid Glass, Normal, Frost Glass
- Icon shape: Circle, Squircle, Rounded, Hexagon
- Icon style: Themed or Original
- Home layout: Endless row or Stacked rows
- Dock: on or off, with Default, Lower or Lowest height
- All apps page: All apps (A to Z) or Folders
- App names: on or off
- Greetings: edit the lines shown on Home for Morning, Afternoon, Evening and Night
- Home screen: a short guide and a shortcut to the Home settings
- Welcome screen: replay the first-run intro

For your own picture, choose My photo, then pick from the list of pictures on the TV or USB drive (or tap Browse files). The picture gets copied into the launcher, so it stays even if you delete the original. Tap My photo again any time to choose a different one.

Daily wallpaper and weather both need internet. They check when the launcher opens and every half hour after that.

## Permissions

- Internet: to download the daily wallpaper and get the weather from [Open-Meteo](https://open-meteo.com/).
- Location and Wi-Fi state: Android won't let an app list nearby Wi-Fi networks without location access. If allowed, location is also used for local weather. Nothing is stored or sent anywhere else.
- Pictures and storage: only to show your pictures when you pick a wallpaper. It asks the first time you open the picker.
- Accounts and contacts: only to show which Google account is signed in.

## How it works

**Glass.** Each wallpaper is blurred ahead of time at three strengths and cut to the shape of whatever's being drawn. Normal and Frost just draw the matching blurred slice behind a tint. Liquid Glass stacks six thin lens bands. The outer rim pulls in wallpaper from outside the shape, which is what the edge of a real glass does. Each band further in does a little less of that, and the center magnifies slightly. It's all anti-aliased paths, so edges stay smooth as things scale.

**Icons.** Modern Android icons come in layers, and the foreground layer is what gets used (keeping lighter and darker tones on multicolored logos so they don't turn into a flat blob). Older icons are analyzed by color, including logos sitting on a tile inside the icon. The logo is shrunk in halves down to the size it's shown at, which avoids jagged edges on a big screen.

**Colors.** The wallpaper is sampled for its dominant hue, and the Soft, Vivid and Mixed palettes come from that. Bright pictures get a stronger dark overlay so the text stays readable.

**Weather.** Forecasts come from [Open-Meteo](https://open-meteo.com/) with no API key needed, and the sky icons are drawn directly on canvas in the wallpaper's accent color.

**Apps.** It lists both TV apps and regular phone apps, which is why sideloaded phone apps show up in the grid.

## Tech

Kotlin 1.9 and Jetpack Compose (BOM 2024.06), Material 3 with extended icons, ProfileInstaller 1.3, Android Gradle Plugin 8.5, Gradle 8.7, JDK 17. minSdk 24, targetSdk 34. No `androidx.tv` dependency.

## Known problems

- Some TVs, including some Xiaomi models, lock the Home button to their own home screen. Unwind still works as an app there.
- A few unusual icons can't be separated from their background, so they show as two-tone chips.
- Switching inputs goes through the TV's own Live TV app, so how well it works depends on the brand.
- Sideloaded phone apps are built for touch, and some are hard to use with a remote.

## Maybe later

- Reordering pinned apps
- More glass and icon styles
- Top bar layout options

## License

MIT. See `LICENSE`.