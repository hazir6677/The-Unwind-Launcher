package com.unwind.launcher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Where the notes come from ----------
//
// One source of truth: CHANGELOG.md in the repo. The build copies it into the app (assets/changelog.md),
// and the same file is used for the GitHub release text, so the notes are written once.
//  1. The notes for the INSTALLED version come from that bundled file (works offline, always matches this build).
//  2. If this build has no section there, the release published under the same tag on GitHub is used.
//  3. Otherwise a short "no notes" message.
// The newest release on GitHub is never shown here: this pop-up is about what you have installed.

enum class NoteKind { Heading, Bullet, Text, Gap }

class NoteLine(val kind: NoteKind, val text: String)

class NotesUi(val version: String, val lines: List<NoteLine>, val source: String)

private fun sameVersion(a: String, b: String) = !isNewer(a, b) && !isNewer(b, a)

/** The `## vX.Y` section of the bundled changelog for [version], or null. */
private fun bundledSection(ctx: Context, version: String): String? {
    val text = runCatching { ctx.assets.open("changelog.md").bufferedReader().use { it.readText() } }.getOrNull() ?: return null
    val out = StringBuilder()
    var inside = false
    for (line in text.replace("\r", "").split("\n")) {
        if (line.startsWith("## ")) {
            if (inside) break
            inside = sameVersion(line.removePrefix("## ").trim(), version)
            continue
        }
        if (inside) out.append(line).append('\n')
    }
    return out.toString().trim().ifBlank { null }
}

/** Plain text or light markdown into lines the pop-up can lay out. */
fun parseNotes(md: String): List<NoteLine> {
    val lines = ArrayList<NoteLine>()
    for (raw in md.replace("\r", "").split("\n")) {
        val t = raw.trim()
        when {
            t.isEmpty() -> if (lines.isNotEmpty() && lines.last().kind != NoteKind.Gap) lines.add(NoteLine(NoteKind.Gap, ""))
            t.startsWith("#") -> {
                if (lines.isNotEmpty() && lines.last().kind != NoteKind.Gap) lines.add(NoteLine(NoteKind.Gap, ""))
                lines.add(NoteLine(NoteKind.Heading, t.trimStart('#').trim()))
            }
            t.startsWith("- ") || t.startsWith("* ") -> lines.add(NoteLine(NoteKind.Bullet, t.drop(2).replace(Regex("[*`_]"), "").trim()))
            else -> lines.add(NoteLine(NoteKind.Text, t.replace(Regex("[*`_]"), "")))
        }
    }
    while (lines.isNotEmpty() && lines.last().kind == NoteKind.Gap) lines.removeAt(lines.lastIndex)
    return lines
}

/** Loads the notes for the installed [version]. Runs off the UI thread. */
suspend fun loadNotes(ctx: Context, version: String): NotesUi = withContext(Dispatchers.IO) {
    val bundled = bundledSection(ctx, version)
    if (bundled != null) return@withContext NotesUi(version, parseNotes(bundled), "bundled")
    val remote = runCatching { fetchReleaseNotesFor(version) }.getOrNull()
    if (remote != null) return@withContext NotesUi(version, parseNotes(remote), "github")
    NotesUi(version, listOf(NoteLine(NoteKind.Text, "No release notes were published for v$version.")), "none")
}

// ---------- The pop-up ----------

/** A readable, remote-friendly pop-up. Down/Up scroll the notes, Down at the end reaches Close, Back dismisses it. */
@Composable
fun WhatsNewPopup(version: String, notes: NotesUi?, onClose: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val body = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    var bodyFocused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 110.dp.toPx() }
    val shape = RoundedCornerShape(26.dp)

    BackHandler(enabled = true) { onClose() }
    LaunchedEffect(notes != null) {
        delay(150)
        runCatching { (if (notes != null) body else close).requestFocus() }
    }

    CompositionLocalProvider(LocalGlassDim provides 0.55f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.pointerInput(Unit) { detectTapGestures { } }.bounceIn(Unit, 14.dp)) {
                GlassBox(Modifier.width(640.dp), shape) {
                    Column(Modifier.padding(horizontal = 26.dp, vertical = 22.dp)) {
                        Text("What's new", color = inkFg(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "In the version installed on this TV: v$version",
                            color = inkAccent(ui.wp.accent),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    if (bodyFocused) 2.dp else 1.dp,
                                    if (bodyFocused) Color.White else Color.White.copy(alpha = 0.18f),
                                    RoundedCornerShape(14.dp),
                                )
                                .focusRequester(body)
                                .onFocusChanged { bodyFocused = it.isFocused }
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionDown -> {
                                            if (scroll.value < scroll.maxValue) scope.launch { scroll.animateScrollBy(step) }
                                            else runCatching { close.requestFocus() }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (scroll.value > 0) scope.launch { scroll.animateScrollBy(-step) }
                                            true
                                        }
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                }
                                .focusable(),
                        ) {
                            Column(
                                Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (notes == null) {
                                    Text("Loading...", color = inkFg(Color.White.copy(alpha = 0.8f)), fontSize = 15.sp)
                                } else {
                                    notes.lines.forEach { l ->
                                        when (l.kind) {
                                            NoteKind.Heading -> Text(l.text, color = inkAccent(ui.wp.accent), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            NoteKind.Bullet -> Row {
                                                Text("\u2022", color = inkAccent(ui.wp.accent), fontSize = 15.sp, modifier = Modifier.width(18.dp))
                                                Text(l.text, color = inkFg(), fontSize = 15.sp, lineHeight = 21.sp)
                                            }
                                            NoteKind.Text -> Text(l.text, color = inkFg(), fontSize = 15.sp, lineHeight = 21.sp)
                                            NoteKind.Gap -> Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ActionCard("Close", Modifier.focusRequester(close).dpad(up = body), 150.dp, onClose)
                        }
                        Text(
                            "Use Up and Down to read, Back to close",
                            color = inkFg(Color.White.copy(alpha = 0.7f)),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
