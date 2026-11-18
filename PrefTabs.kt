package com.unwind.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The tabs along the top of Preferences. */
enum class PrefTab(val label: String, val w: Dp) {
    Look("Look", 96.dp),
    Feel("Sound & motion", 170.dp),
    Backup("Backup", 110.dp),
    Updates("Updates", 124.dp),
    Home("Home setup", 140.dp),
}

@Composable
fun PrefTabBar(tab: PrefTab, onTab: (PrefTab) -> Unit, nav: Nav, updateBadge: Boolean) {
    val ui = LocalUi.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrefTab.values().forEachIndexed { i, t ->
            // the open tab is where focus lands when Preferences opens or you come back from the rail
            val base = if (t == tab) Modifier.focusRequester(nav.content) else Modifier
            OptionCard(
                selected = t == tab,
                w = t.w,
                h = 44.dp,
                modifier = base.dpad(left = if (i == 0) nav.rail else null, up = nav.pill),
                onClick = { onTab(t) },
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(t.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (t == PrefTab.Updates && updateBadge) {
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ui.wp.accent))
                    }
                }
            }
        }
    }
}

/** A glass card you can land on with the D-pad, so pages that are only reading still scroll. */
@Composable
fun ReadCard(modifier: Modifier = Modifier, width: Dp = 700.dp, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.012f else 1f, animationSpec = LiquidSpring, label = "read")
    GlassBox(
        modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) Sfx.move() }
            .focusable(),
        RoundedCornerShape(18.dp),
        focused,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), content = content)
    }
}

@Composable
private fun StepCard(n: Int, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    ReadCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(ui.wp.accent), contentAlignment = Alignment.Center) {
                Text("$n", color = ui.wp.dark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = inkFg(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = inkFg(Color.White.copy(alpha = 0.92f)), fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(6.dp))
}

/** A command to type on the computer. Big monospace text so it is easy to read from the couch. */
@Composable
private fun Command(text: String) {
    val ui = LocalUi.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text, color = ui.wp.accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
    }
}

// ---------- Sound & motion ----------

@Composable
fun FeelTab(settings: LauncherSettings, nav: Nav, onChange: (LauncherSettings) -> Unit) {
    val first = Modifier.dpad(left = nav.rail, up = nav.content)
    val upOnly = Modifier.dpad(up = nav.content)

    SectionTitle("Move sounds", "A tiny nature sound each time the highlight moves. Press OK on one to hear it.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SoundPack.values().forEachIndexed { i, p ->
            OptionCard(
                selected = settings.soundPack == p,
                w = 150.dp,
                h = 68.dp,
                modifier = if (i == 0) first else upOnly,
                onClick = {
                    Sfx.play(p, settings.soundVolume.gain)
                    onChange(settings.copy(soundPack = p))
                },
            ) {
                Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)) {
                    Text(p.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(p.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    SectionTitle("Sound volume", "Keep it as a whisper, or let it be heard")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SoundVolume.values().forEachIndexed { i, v ->
            OptionCard(
                selected = settings.soundVolume == v,
                w = 150.dp,
                h = 56.dp,
                modifier = if (i == 0) Modifier.dpad(left = nav.rail) else Modifier,
                onClick = {
                    Sfx.play(if (settings.soundPack == SoundPack.Off) SoundPack.Droplet else settings.soundPack, v.gain)
                    onChange(settings.copy(soundVolume = v))
                },
            ) {
                Text(v.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    SectionTitle("Bouncy motion", "Icons, cards and pages spring and settle as you move. Turn off for plain, smooth movement.")
    ToggleCard(if (settings.bouncy) "Bouncy motion on" else "Bouncy motion off", settings.bouncy, Modifier.dpad(left = nav.rail)) {
        onChange(settings.copy(bouncy = !settings.bouncy))
    }
}

// ---------- Backup ----------

private fun fileSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.US, "%.1f MB", bytes / 1048576.0) else "${maxOf(1L, bytes / 1024)} KB"

@Composable
fun BackupTab(
    nav: Nav,
    backups: List<File>,
    status: String,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onRestoreFile: (File) -> Unit,
) {
    val first = Modifier.dpad(left = nav.rail, up = nav.content)
    val toRail = Modifier.dpad(left = nav.rail)
    var armed by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }

    SectionTitle(
        "Save your setup",
        "Home apps and their order, folders, theme, glass, shapes, wallpaper, greetings, weather, sound and motion go into one file",
    )
    ActionCard("Save setup to a file", first, 300.dp, onSave)
    Spacer(Modifier.height(24.dp))

    SectionTitle("Restore a setup", "Puts everything back as it was saved. Unwind restarts when it's done.")
    ActionCard("Restore from a file", toRail, 300.dp, onRestore)
    if (status.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(status, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.width(560.dp))
    }

    if (backups.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionTitle("Saved on this TV", "Press OK twice on one to restore it")
        backups.forEach { f ->
            val isArmed = armed == f.name
            OptionCard(
                selected = isArmed,
                w = 460.dp,
                h = 60.dp,
                modifier = toRail,
                onClick = { if (isArmed) { armed = null; onRestoreFile(f) } else armed = f.name },
            ) {
                Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp)) {
                    Text(f.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (isArmed) "Press OK again to restore this setup" else "${fmt.format(Date(f.lastModified()))}  ·  ${fileSize(f.length())}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "Tip: save a copy to a USB stick or a cloud folder if you can. Setups kept inside Unwind's own folder are removed if you uninstall it.",
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.width(560.dp),
    )
}

// ---------- Updates ----------

@Composable
private fun VersionBlock(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val ui = LocalUi.current
    Column(modifier) {
        Text(label.uppercase(), color = inkAccent(ui.wp.accent), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        Text(value, color = if (accent) inkAccent(ui.wp.accent) else inkFg(), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UpdatesTab(settings: LauncherSettings, nav: Nav, up: UpdateController, onChange: (LauncherSettings) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val s = up.ui
    val rel = s.release
    val canInstall = remember(s.needsPermission, s.phase, s.installing) { canInstallPackages(ctx) }
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val primary = remember { FocusRequester() }

    // When the main button changes meaning (check > download > install), keep the highlight on it
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(s.phase) {
        if (firstRun[0]) { firstRun[0] = false; return@LaunchedEffect }
        delay(120)
        runCatching { primary.requestFocus() }
    }

    SectionTitle("Updates", "Unwind looks for new versions on GitHub and can install them for you")
    GlassBox(Modifier.width(680.dp), RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.unwind_icon),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(ui.settings.shape.toShape()),
                )
                Spacer(Modifier.width(20.dp))
                VersionBlock("Installed", "v${s.current}")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("\u2192", color = inkFg(Color.White.copy(alpha = 0.7f)), fontSize = 26.sp)
                }
                when {
                    rel != null && (s.phase != UpPhase.UpToDate) -> VersionBlock("Next update", "v${rel.version}", accent = true)
                    s.phase == UpPhase.Checking -> VersionBlock("Next update", "Checking")
                    s.phase == UpPhase.UpToDate -> VersionBlock("Latest", "v${s.current}")
                    else -> VersionBlock("Next update", "\u2014")
                }
            }
            Spacer(Modifier.height(14.dp))

            val line = when {
                s.phase == UpPhase.Checking -> "Checking GitHub..."
                s.phase == UpPhase.UpToDate -> "You're on the latest version."
                s.phase == UpPhase.Available && rel != null ->
                    "A new version is ready to download" + if (rel.size > 0) " (${fileSize(rel.size)})." else "."
                s.phase == UpPhase.Downloading -> "Downloading... ${(up.progress * 100).toInt()}%"
                s.phase == UpPhase.Ready -> if (s.installing) "Handing it to Android..." else "Downloaded and ready to install."
                s.phase == UpPhase.Failed -> s.error
                else -> "Press the button to look for updates."
            }
            Text(
                line,
                color = if (s.phase == UpPhase.Failed) inkAccent(Color(0xFFFFB4A8)) else inkFg(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )

            if (s.phase == UpPhase.Downloading || (s.phase == UpPhase.Ready && s.installing)) {
                Spacer(Modifier.height(10.dp))
                val frac by animateFloatAsState(up.progress, animationSpec = spring(1f, 200f), label = "dl")
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(ui.wp.accent))
                }
            }

            if (rel != null && rel.notes.isNotBlank() && s.phase != UpPhase.UpToDate) {
                Spacer(Modifier.height(12.dp))
                Text("What's new", color = inkAccent(ui.wp.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(rel.notes, color = inkFg(Color.White.copy(alpha = 0.9f)), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }

            if (s.phase == UpPhase.Ready && !canInstall) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android needs your OK before an app can install updates. Press the button, switch on \"Allow from this source\" for Unwind, then press Back. The update installs on its own.",
                    color = inkFg(Color.White.copy(alpha = 0.85f)),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            if (s.checkedAt > 0L) {
                Spacer(Modifier.height(10.dp))
                Text("Last checked ${fmt.format(Date(s.checkedAt))}", color = inkFg(Color.White.copy(alpha = 0.7f)), fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    val busy = s.phase == UpPhase.Checking || s.phase == UpPhase.Downloading || s.installing
    if (!busy) {
        val label: String
        val action: () -> Unit
        when {
            s.phase == UpPhase.Available -> { label = "Download update"; action = up::download }
            s.phase == UpPhase.Ready -> { label = if (canInstall) "Install update" else "Allow installs and update"; action = up::install }
            s.phase == UpPhase.Failed && rel != null && s.file == null -> { label = "Try the download again"; action = up::download }
            else -> { label = "Check for updates"; action = { up.check(true) } }
        }
        ActionCard(label, Modifier.focusRequester(primary).dpad(left = nav.rail, up = nav.content), 280.dp, action)
        Spacer(Modifier.height(18.dp))
    }

    ToggleCard(
        if (settings.autoUpdate) "Check automatically: on" else "Check automatically: off",
        settings.autoUpdate,
        Modifier.dpad(left = nav.rail, up = if (busy) nav.content else null),
    ) { onChange(settings.copy(autoUpdate = !settings.autoUpdate)) }
    Spacer(Modifier.height(8.dp))
    Text(
        "Unwind checks every few hours while it's open and tells you when something new is out.",
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 12.sp,
        modifier = Modifier.width(560.dp),
    )
}

// ---------- Home setup (and the PC guide) ----------

private fun openDeveloperOptions(ctx: Context) {
    val tries = listOf(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (i in tries) {
        if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
    Toast.makeText(ctx, "Open your TV's Settings > Device Preferences > About.", Toast.LENGTH_LONG).show()
}

@Composable
fun HomeSetupTab(nav: Nav, onOpenHomeSettings: () -> Unit) {
    val ctx = LocalContext.current
    val ip = remember { localIpv4(ctx) }
    val addr = ip ?: "192.168.x.x"
    val first = Modifier.dpad(left = nav.rail, up = nav.content)
    val toRail = Modifier.dpad(left = nav.rail)
    val gap = Modifier.height(12.dp)

    SectionTitle("Make Unwind your Home screen", "So the Home button on your remote opens Unwind")
    ReadCard(first) {
        GuideStep(1, "Press the Home button on your remote.")
        GuideStep(2, "If the TV asks which Home app to use, pick Unwind, then choose Always.")
        GuideStep(3, "No question? Tap the button below and pick Unwind there.")
        Spacer(Modifier.height(6.dp))
        Text(
            "To switch back: pick your TV's original Home app in the same place, or uninstall Unwind.",
            color = inkFg(Color.White.copy(alpha = 0.72f)),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
    Spacer(Modifier.height(12.dp))
    ActionCard("Open Home settings", toRail, 240.dp, onOpenHomeSettings)
    Spacer(Modifier.height(28.dp))

    SectionTitle("Didn't work? Set it from a computer", "About 10 minutes. Nothing gets deleted and every step can be undone.")
    ReadCard(toRail) {
        Body("Some TVs ignore the Home picker and keep opening their own screen. A computer can tell the TV, in plain typed commands, to use Unwind instead.")
        Body("You need: a Windows, Mac or Linux computer on the same Wi-Fi (or network) as the TV. No cables.")
        Body("Use the Up and Down buttons to go through the steps.")
    }
    Spacer(gap)

    StepCard(1, "Turn on Developer options (on the TV)", toRail) {
        Body("Open Settings > Device Preferences > About. Move to \"Build\" (on some TVs \"Android TV OS build\") and press OK seven times quickly. A message says you are now a developer.")
        Body("This button tries to jump straight to the right place:")
    }
    Spacer(Modifier.height(8.dp))
    ActionCard("Open developer settings", toRail, 260.dp) { openDeveloperOptions(ctx) }
    Spacer(gap)

    StepCard(2, "Switch on debugging (on the TV)", toRail) {
        Body("Go back one step to Settings > Device Preferences > Developer options.")
        Body("Turn on \"USB debugging\" (the name is odd, but it also covers Wi-Fi). On newer TVs (Android 11 and up) you'll see \"Wireless debugging\" instead. Turn that on.")
    }
    Spacer(gap)

    StepCard(3, "Find your TV's address", toRail) {
        Body("Your computer finds the TV by its address, which is a number like 192.168.1.23. Right now this TV's address is:")
        Command(ip ?: "Not connected. Join Wi-Fi first.")
        Body("You can also find it in Settings > Network & Internet > your network > IP address.")
        Body("Newer TVs with Wireless debugging: open Developer options > Wireless debugging. It shows \"IP address & Port\" (like 192.168.1.23:38211). Choosing \"Pair device with pairing code\" shows a 6-digit code and a second port number. Keep that screen handy.")
    }
    Spacer(gap)

    StepCard(4, "Get ADB on your computer", toRail) {
        Body("ADB is a small, free tool from Google that lets a computer talk to the TV. On the computer, search for \"Android platform tools\", open the page on developer.android.com, download the one for your computer and unzip it.")
        Body("Then open a terminal inside that folder:")
        Body("Windows: open the folder, click the address bar at the top, type  cmd  and press Enter.")
        Body("Mac or Linux: right-click the folder and choose \"Open in Terminal\". Type  ./adb  wherever the steps below say  adb.")
    }
    Spacer(gap)

    StepCard(5, "Connect the computer to the TV", toRail) {
        Body("Most TVs: type this and press Enter.")
        Command("adb connect $addr:5555")
        Body("Newer TVs using Wireless debugging: first pair, using the pairing address and 6-digit code shown on the TV:")
        Command("adb pair $addr:<pairing port>")
        Body("Then connect, using the port shown on the main Wireless debugging screen:")
        Command("adb connect $addr:<port>")
        Body("A box appears on the TV asking \"Allow debugging?\". Choose \"Always allow\" and OK. Then check:")
        Command("adb devices")
        Body("Your TV should be listed with the word \"device\" beside it. \"unauthorized\" means the box on the TV still needs an OK.")
    }
    Spacer(gap)

    StepCard(6, "Tell the TV to use Unwind", toRail) {
        Body("Type this exactly, then press the Home button on the remote.")
        Command("adb shell cmd package set-home-activity com.unwind.launcher/.MainActivity")
        Body("Unwind should open. That's it for most TVs.")
    }
    Spacer(gap)

    StepCard(7, "Still opening the old Home screen?", toRail) {
        Body("Find out which app the TV currently treats as Home:")
        Command("adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        Body("The last line looks like  com.something.launcher/.SomeName . The part before the slash is the app's name. Switch that app off (it is not deleted):")
        Command("adb shell pm disable-user --user 0 <that name>")
        Body("Then press Home again. To bring the old Home screen back whenever you like:")
        Command("adb shell pm enable <that name>")
        Body("Only do this after step 6 has worked at least once, and never switch off Unwind itself. Common names are com.google.android.tvlauncher and com.google.android.apps.tv.launcherx.")
    }
    Spacer(gap)

    StepCard(8, "Finish", toRail) {
        Body("Disconnect when you're done:")
        Command("adb disconnect")
        Body("You can switch debugging off again in Developer options. Unwind stays your Home screen.")
    }
    Spacer(gap)

    StepCard(9, "If something goes wrong", toRail) {
        Body("\"Can't connect\": the computer and TV must be on the same Wi-Fi, the TV must be awake, and the address may have changed after a restart (check step 3 again).")
        Body("\"adb is not recognized\": the terminal is not open inside the platform-tools folder (step 4).")
        Body("Home button does nothing at all: on the TV there's no Home app left. Run the \"pm enable\" command from step 7.")
    }
}

// ---------- The "update available" banner ----------

/** Slides in from the top for a few seconds when a new version is found. It never takes focus. */
@Composable
fun UpdateBanner(version: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    GlassBox(modifier, ui.settings.shape.toBarShape(), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.unwind_icon),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(ui.settings.shape.toShape()),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Update available: Unwind v$version", color = inkFg(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Open Preferences, then Updates", color = inkFg(Color.White.copy(alpha = 0.8f)), fontSize = 11.sp)
            }
        }
    }
}
