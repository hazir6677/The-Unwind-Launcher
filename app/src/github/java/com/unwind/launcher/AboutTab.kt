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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// THE GITHUB VERSION of the About tab: checks GitHub, downloads the update and installs it.

// ---------- About (and the GitHub updater) ----------

@Composable
private fun VersionBlock(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val ui = LocalUi.current
    Column(modifier) {
        Text(label.uppercase(), color = inkAccent(ui.wp.accent), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        Text(value, color = if (accent) inkAccent(ui.wp.accent) else inkFg(), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AboutTab(
    settings: LauncherSettings,
    nav: Nav,
    rows: PrefRows,
    up: UpdateController,
    onWhatsNew: () -> Unit,
    onChange: (LauncherSettings) -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val s = up.ui
    val rel = s.release
    val canInstall = remember(s.needsPermission, s.phase, s.installing) { canInstallPackages(ctx) }
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val latest = s.latest
    val relation = latest?.let { relationOf(it.version, s.current) }

    // When the main button changes meaning (check > download > install), keep the highlight on it
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(s.phase) {
        if (firstRun[0]) { firstRun[0] = false; return@LaunchedEffect }
        delay(120)
        runCatching { rows.at(0, 0)?.requestFocus() }
    }

    SectionTitle("About", "Unwind looks for new versions on GitHub and can install them for you")
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
                // The right-hand number is always the newest release published on GitHub. It never borrows the installed version.
                when {
                    rel != null && (s.phase != UpPhase.UpToDate) -> VersionBlock("Next update", "v${rel.version}", accent = true)
                    s.phase == UpPhase.Checking && latest == null -> VersionBlock("Latest", "Checking")
                    latest != null -> VersionBlock("Latest", "v${latest.version}", accent = relation == Relation.Newer)
                    else -> VersionBlock("Latest", "\u2014")
                }
            }
            Spacer(Modifier.height(14.dp))

            val line = when {
                s.phase == UpPhase.Checking -> "Checking GitHub..."
                s.phase == UpPhase.UpToDate -> when (relation) {
                    Relation.Ahead -> "You're running v${s.current}, which is newer than the latest published release (v${latest?.version}). This looks like a test build."
                    Relation.Newer -> "v${latest?.version} is published on GitHub, but its download isn't attached yet. Try again in a few minutes."
                    else -> "You're on the latest version."
                }
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
                Text("In the new version", color = inkAccent(ui.wp.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        var col = 0
        if (!busy) {
            val label: String
            val action: () -> Unit
            when {
                s.phase == UpPhase.Available -> { label = "Download update"; action = up::download }
                s.phase == UpPhase.Ready -> { label = if (canInstall) "Install update" else "Allow installs and update"; action = up::install }
                s.phase == UpPhase.Failed && rel != null && s.file == null -> { label = "Try the download again"; action = up::download }
                else -> { label = "Check for updates"; action = { up.check(true) } }
            }
            ActionCard(label, Modifier.prefItem(rows, nav, 0, col++), 280.dp, action)
        }
        // small and quiet next to the main button: what changed in the version installed on this TV
        OptionCard(
            selected = false,
            w = 150.dp,
            h = 40.dp,
            modifier = Modifier.prefItem(rows, nav, 0, col++),
            onClick = onWhatsNew,
        ) {
            Text("What's New", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
    }
    Spacer(Modifier.height(18.dp))

    ToggleCard(
        if (settings.autoUpdate) "Check automatically: on" else "Check automatically: off",
        settings.autoUpdate,
        Modifier.prefItem(rows, nav, 1),
    ) { onChange(settings.copy(autoUpdate = !settings.autoUpdate)) }
    Spacer(Modifier.height(8.dp))
    Text(
        "Unwind checks every few hours while it's open and tells you when something new is out.",
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 12.sp,
        modifier = Modifier.width(560.dp),
    )
    Spacer(Modifier.height(18.dp))
    Text(
        "Unwind Launcher is free software under the MIT license.\nSource code: github.com/hazir6677/The-Unwind-Launcher",
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.width(560.dp),
    )
}

