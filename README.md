# Unwind Launcher

### A calmer home for your Android TV.

Unwind turns your TV home screen into a wallpaper-driven experience. Frosted glass, adaptive colours, thoughtfully styled icons and smooth animations come together in a clean interface designed for the big screen.

Pick a wallpaper. Unwind takes care of the rest.

Built and primarily tested on a 55-inch Xiaomi TV.

## ✨ Highlights

- **Three glass styles:** Liquid Glass, Normal and Frost Glass, each with its own look and feel.
- **Wallpaper-aware theming:** Colours, accents and app icons adapt to your wallpaper. Choose Soft, Vivid or Mixed palettes.
- **Live weather:** Current conditions and a five-day forecast, with automatic location detection and manual city search.
- **Smarter app drawer:** Browse apps alphabetically or use automatically sorted folders, including apps most TV launchers hide.
- **Flexible home layouts:** Choose a single scrolling row or stacked rows, with an optional adjustable glass dock.
- **Custom greetings:** Personalise your Home screen greeting for morning, afternoon, evening and night.
- **TV-friendly navigation:** D-pad focus, spring animations and native on-screen keyboard support.

## Screenshots

### Glass styles across wallpapers

The same interface, adapting to different wallpapers and glass styles.

| Liquid Glass | Normal Glass |
|---|---|
| <img src="shot_home_liquid_green.png" alt="Liquid Glass with green wallpaper"> | <img src="shot_home_normal_green.png" alt="Normal Glass with green wallpaper"> |
| Misty Valley | Misty Valley |

| Liquid Glass | Preferences |
|---|---|
| <img src="shot_home_liquid_amber.png" alt="Liquid Glass with amber wallpaper"> | <img src="shot_preferences.png" alt="Preferences overview"> |
| Golden Hour | Settings overview |

## The experience

### Glass that feels like glass

Choose between three visual styles:

- **Liquid Glass:** Distorts the wallpaper around its edges, with subtle lighting, colour fringing and springy focus animations.
- **Normal:** A softly blurred, tinted glass panel.
- **Frost Glass:** A heavier, milkier blur.

### Colours that follow your wallpaper

Use one of six built-in nature wallpapers, the Bing daily wallpaper or your own photo. Unwind extracts a colour palette and builds three variations:

- **Soft** for lighter, pastel tones.
- **Vivid** for brighter accents.
- **Mixed** for a balance between the two.

App icons can be tinted to match the palette or left in their original colours. Choose from Circle, Squircle, Rounded and Hexagon shapes.

### Your apps, your way

The Home screen holds your pinned apps, while the app drawer offers two ways to browse:

- **All apps:** A familiar alphabetical grid.
- **Folders:** Automatic categories for Video, Music, Games, Social, News, Photos, Productivity and Other.

Sideloaded phone apps appear alongside Android TV apps. Hold OK on an app in a folder to move it or restore automatic sorting.

### Made for the remote

Navigate between the left dock, weather, top bar and app grid using the D-pad. Search uses your TV's native on-screen keyboard, so typing doesn't interrupt remote navigation.

The top bar also gives you quick access to Android settings, nearby Wi-Fi networks and signal strength, TV inputs, the clock and your signed-in Google account.

## Download and install

1. Open the [Releases page](../../releases) and download the latest APK.
2. Sideload it onto your Android TV.
3. Install `app-release.apk` when available. It is minified and includes release optimisations.
   
### Make Unwind your default Home screen

Press the Home button on your remote. When prompted, select Unwind and choose **Always**.

If your TV doesn't show the prompt, open Preferences in Unwind and select **Open Home settings**.

Some TVs, including certain Xiaomi models, restrict the Home button to their built-in launcher. You can still use Unwind as a regular app, or set it as Home through ADB:

1. On your TV, open **Settings → Device Preferences → About** and press Build seven times to enable Developer options.
2. Enable Network debugging or USB debugging.
3. Connect from a computer:

```bash
adb connect <TV-IP-address>
```

4. Set Unwind as the Home activity:

```bash
adb shell cmd package set-home-activity com.unwind.launcher/.MainActivity
```

To revert, uninstall Unwind:

```bash
adb uninstall com.unwind.launcher
```

The APK is signed with a dedicated key stored in GitHub Secrets, not in the source code. This allows new builds to install over existing versions while retaining settings. It is a personal sideload key and is not suitable for Play Store distribution.

## Preferences

Make the launcher yours:

| Setting | Options |
|---|---|
| Wallpaper | Bing daily, My photo, six built-in wallpapers |
| Colour palette | Soft, Vivid, Mixed |
| Glass style | Liquid Glass, Normal, Frost Glass |
| Icon shape | Circle, Squircle, Rounded, Hexagon |
| Icon colours | Themed or Original |
| Home layout | Endless row or Stacked rows |
| Glass dock | On or Off, with adjustable height |
| App drawer | Alphabetical grid or Folders |
| App names | Show or hide on Home |
| Greetings | Custom text for each part of the day |

Custom wallpapers are copied into the launcher and scaled to your TV's resolution, so they remain available even after the original file or USB drive is removed.

## Permissions and privacy

Unwind requests only the permissions needed for its features:

- **Internet:** Bing wallpapers, Open-Meteo weather forecasts and city lookup.
- **Location and Wi-Fi state:** Local weather detection and nearby Wi-Fi scanning. Android requires location access to list nearby networks.
- **Pictures and storage:** Access to images when selecting a custom wallpaper.
- **Accounts and contacts:** Displaying your signed-in Google account and avatar.

Weather can use your TV's last known location or an IP-based location fallback. Location and personal data are not stored externally or tracked by Unwind.

Daily wallpapers and weather require an internet connection. They refresh when the launcher opens and every 30 minutes while it is running.

## Under the hood

Unwind is built with Kotlin and Jetpack Compose, with custom rendering and navigation designed specifically for Android TV.

A few things that make it work:

- **Glass rendering:** Pre-blurred wallpaper layers power Normal and Frost Glass. Liquid Glass uses six lens bands to simulate edge distortion and subtle central magnification.
- **Adaptive icons:** Extracts monochrome or adaptive icon layers where available, preserving tonal detail in multicoloured logos. Older icons use background and luminance analysis.
- **Dynamic colours:** Samples the wallpaper on a 48 × 27 grid, weighting saturation and brightness to generate its colour palettes.
- **Weather graphics:** Custom-drawn Compose Canvas icons, powered by Open-Meteo. No API key or account required.
- **App discovery:** Queries both Android TV and standard Android launcher activities, making sideloaded phone apps discoverable too.

### Tech stack

Kotlin 1.9.24 · Jetpack Compose · Material 3 · AndroidX ProfileInstaller 1.3.1 · Gradle 8.7 · Android Gradle Plugin 8.5.2 · JDK 17

`minSdk 24` · `targetSdk 34`

No `androidx.tv` dependency. D-pad focus handling and spring animations are implemented directly with Compose primitives.

## Known limitations

- Some TV manufacturers restrict replacing their built-in Home screen. ADB can help work around this.
- A few unusual app icons cannot be cleanly separated from their backgrounds and may appear as two-tone tiles.
- Input switching depends on the TV manufacturer's `TvInputManager` implementation.
- Some sideloaded phone apps are designed for touch and may be awkward to navigate with a remote.

## On the roadmap

- Reordering pinned apps.
- Creating and renaming custom folders.
- More glass and icon styles.
- Additional top bar layouts.

## License

Released under the [MIT License](LICENSE).
