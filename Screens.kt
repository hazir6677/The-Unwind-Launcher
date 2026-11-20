package com.unwind.launcher

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------- Home ----------

@Composable
private fun HomeTile(
    app: AppEntry?,
    modifier: Modifier,
    lifted: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onMenu: (AppEntry) -> Unit,
    onFocus: () -> Unit,
) {
    val ui = LocalUi.current
    if (app != null) {
        // Holding OK on an app opens its menu (App info, Move, Remove, Uninstall)
        Tile(
            app.label,
            modifier,
            onClick = { onLaunch(app) },
            onFocus = onFocus,
            onLongPress = { onMenu(app) },
            lifted = lifted,
        ) { d -> AppGlyph(app, d) }
    } else {
        Tile("Add apps", modifier, onClick = onAdd, onFocus = onFocus) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(34.dp))
        }
    }
}

/** A line above the row while an app is being carried, so the controls are never a mystery. */
@Composable
private fun MoveHint(label: String, stacked: Boolean) {
    val ui = LocalUi.current
    Text(
        "Moving $label  \u00B7  " + (if (stacked) "arrows move it" else "Left and Right move it") + "  \u00B7  OK places it  \u00B7  Back cancels",
        color = ui.wp.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        style = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 10f)),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    favApps: List<AppEntry>,
    weather: WeatherData?,
    focusKick: Int,
    nav: Nav,
    movingPkg: String?,
    onLaunch: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onMenu: (AppEntry) -> Unit,
    onMoveTo: (pkg: String, target: String, right: Boolean) -> Unit,
    onMoveDone: () -> Unit,
) {
    val ui = LocalUi.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val part = dayPartFor(hour)
    val greeting = ui.settings.greetings[part] ?: DEFAULT_GREETINGS.getValue(part)

    // Each tile can be focused by app, so the highlight can go back to the same app after the menu closes
    val reqs = remember { HashMap<String, FocusRequester>() }
    var lastPkg by remember { mutableStateOf<String?>(null) }
    fun reqFor(i: Int, pkg: String): FocusRequester = if (i == 0) nav.content else reqs.getOrPut(pkg) { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(favApps.size, focusKick, movingPkg) {
        delay(200)
        val want = movingPkg ?: lastPkg
        val i = if (want == null) -1 else favApps.indexOfFirst { it.pkg == want }
        runCatching { (if (i < 0) nav.content else reqFor(i, want!!)).requestFocus() }
    }
    // While carrying an app along a long row, keep it on screen
    val order = favApps.joinToString(",") { it.pkg }
    LaunchedEffect(movingPkg, order) {
        if (movingPkg == null) return@LaunchedEffect
        val idx = favApps.indexOfFirst { it.pkg == movingPkg }
        if (idx < 0) return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val cut = info == null || info.offset < 0 || info.offset + info.size > listState.layoutInfo.viewportEndOffset
        if (cut) runCatching { listState.animateScrollToItem((idx - 1).coerceAtLeast(0)) }
    }

    val stacked = ui.settings.homeLayout == HomeLayout.Stacked
    val dock = ui.settings.dock
    val widget = ui.settings.homeWidget
    // The dock takes the icon shape: pill for circles, squarer for squircles/rounded, hexagonal for hexagons
    val dockShape = ui.settings.shape.toBarShape()
    val dockPad = if (ui.settings.shape == IconShape.Hexagon) 30.dp else 18.dp
    // the "Add apps" tile is the last item in the flow
    val items: List<AppEntry?> = favApps + null
    val movingLabel = favApps.firstOrNull { it.pkg == movingPkg }?.label

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // How many tiles fit across, worked out from the screen so it adapts to any TV
        val tileW = 86.dp
        val gap = 16.dp
        val usable = maxWidth - 163.dp - 48.dp - (if (dock) dockPad * 2 else 0.dp)
        val cols = maxOf(1, ((usable + gap) / (tileW + gap)).toInt())
        val rows = items.chunked(cols)
        val tall = stacked && rows.size > 1
        val topPad by animateDpAsState(if (tall) 104.dp else 140.dp, label = "homeTop")
        // Dock height setting: how far the row sits above the bottom edge
        val lift = 78 - ui.settings.dockPos.bottom
        val bottomPad by animateDpAsState(
            if (tall) maxOf(8, 28 - lift / 2).dp else ui.settings.dockPos.bottom.dp,
            label = "homeBottom",
        )

        // While an app is picked up, the arrows carry it and OK puts it down. Everything else is held back.
        val carry = Modifier.onPreviewKeyEvent { e ->
            val mv = movingPkg ?: return@onPreviewKeyEvent false
            val k = e.key
            val relevant = k == Key.DirectionLeft || k == Key.DirectionRight || k == Key.DirectionUp || k == Key.DirectionDown ||
                k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter
            if (!relevant) return@onPreviewKeyEvent false
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
            val i = favApps.indexOfFirst { it.pkg == mv }
            fun step(d: Int) {
                if (i < 0 || d == 0) return
                val t = (i + d).coerceIn(0, favApps.lastIndex)
                if (t != i) {
                    onMoveTo(mv, favApps[t].pkg, t > i)
                    Sfx.move()
                }
            }
            when (k) {
                Key.DirectionLeft -> step(-1)
                Key.DirectionRight -> step(1)
                Key.DirectionUp -> if (stacked) step(-cols)
                Key.DirectionDown -> if (stacked) step(cols)
                else -> if (e.nativeKeyEvent.repeatCount == 0) onMoveDone()
            }
            true
        }

        Column(Modifier.fillMaxSize().then(carry).padding(start = 163.dp, top = topPad, bottom = bottomPad)) {
            if (!stacked) {
                // Everything above the row is the widget slot; it takes whatever height is left
                Box(Modifier.weight(1f).fillMaxWidth()) { HomeWidgetView(widget, greeting, weather) }
                if (movingLabel != null) {
                    MoveHint(movingLabel, false)
                    Spacer(Modifier.height(6.dp))
                }
                // Endless row. With the dock on, the glass bar stays put and the apps slide across it.
                OptGlass(
                    dock,
                    if (dock) Modifier.padding(end = 24.dp).offset(x = -dockPad) else Modifier,
                    dockShape,
                ) {
                    CompositionLocalProvider(LocalOnDock provides dock) {
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        // vertical padding leaves room for the focused tile to swell without being clipped
                        contentPadding = PaddingValues(
                            start = if (dock) dockPad else 0.dp,
                            top = 18.dp,
                            bottom = 18.dp,
                            end = if (dock) dockPad else 48.dp,
                        ),
                    ) {
                        itemsIndexed(items, key = { _, a -> a?.pkg ?: "__add" }) { i, app ->
                            HomeTile(
                                app,
                                (if (app != null) Modifier.focusRequester(reqFor(i, app.pkg)) else if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                                    .animateItemPlacement(spring(dampingRatio = 0.7f, stiffness = 400f))
                                    .dpad(left = if (i == 0) nav.rail else null, up = nav.pill),
                                lifted = app != null && app.pkg == movingPkg,
                                onLaunch = onLaunch,
                                onAdd = onAdd,
                                onMenu = onMenu,
                                onFocus = { if (app != null) lastPkg = app.pkg },
                            )
                        }
                    }
                    }
                }
            } else {
                // Stacked rows: the first row fills the width, then apps continue in rows below it.
                // The whole block starts a little to the left so the dock bar and the focus swell have room.
                if (widget == HomeWidget.Greetings) {
                    HomeWidgetView(widget, greeting, weather)
                } else if (widget != HomeWidget.None) {
                    // other widgets shrink to this fixed slot so the rows keep their room
                    Box(Modifier.height(150.dp).fillMaxWidth()) { HomeWidgetView(widget, greeting, weather) }
                }
                if (movingLabel != null) MoveHint(movingLabel, true)
                val side = if (dock) dockPad else 14.dp
                Box(
                    Modifier.weight(1f).fillMaxWidth().offset(x = -side),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 18.dp)) {
                        rows.forEachIndexed { ri, row ->
                            if (ri > 0) Spacer(Modifier.height(14.dp))
                            OptGlass(ri == 0 && dock, Modifier, dockShape) {
                            Row(
                                if (ri == 0 && dock) {
                                    Modifier.padding(horizontal = dockPad, vertical = 14.dp)
                                } else {
                                    Modifier.padding(start = side)
                                },
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                CompositionLocalProvider(LocalOnDock provides (ri == 0 && dock)) {
                                row.forEachIndexed { ci, app ->
                                    val idx = ri * cols + ci
                                    HomeTile(
                                        app,
                                        (if (app != null) Modifier.focusRequester(reqFor(idx, app.pkg)) else if (idx == 0) Modifier.focusRequester(nav.content) else Modifier)
                                            .dpad(left = if (ci == 0) nav.rail else null, up = if (ri == 0) nav.pill else null),
                                        lifted = app != null && app.pkg == movingPkg,
                                        onLaunch = onLaunch,
                                        onAdd = onAdd,
                                        onMenu = onMenu,
                                        onFocus = { if (app != null) lastPkg = app.pkg },
                                    )
                                }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- All apps / pick favorites ----------

@Composable
fun AppGrid(
    apps: List<AppEntry>,
    editing: Boolean,
    favs: Set<String>,
    focusKick: Int,
    nav: Nav,
    onClick: (AppEntry) -> Unit,
) {
    val columns = 7
    LaunchedEffect(apps.size, focusKick) {
        delay(200)
        if (apps.isNotEmpty()) runCatching { nav.content.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = 163.dp, top = 84.dp, end = 40.dp)) {
        Text(
            if (editing) "Press OK on an app to add it to Home or remove it." else "Press OK to open an app.",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                val isFav = editing && app.pkg in favs
                Tile(
                    if (isFav) "\u2713 ${app.label}" else app.label,
                    (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                        .dpad(
                            left = if (i % columns == 0) nav.rail else null,
                            up = if (i < columns) nav.pill else null,
                        ),
                    onClick = { onClick(app) },
                    highlight = isFav,
                    forceName = true,
                ) { d -> AppGlyph(app, d) }
            }
        }
    }
}

// ---------- In-launcher app search ----------

@Composable
fun KeyCap(label: String, w: Dp, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, animationSpec = GlassSpring, label = "key")
    GlassBox(
        modifier
            .size(w, 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) Sfx.move() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        RoundedCornerShape(10.dp),
        focused,
        blur = false,
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused || active) inkAccent(ui.wp.accent) else inkFg(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SearchScreen(apps: List<AppEntry>, focusKick: Int, nav: Nav, onLaunch: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(focusKick) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    Column(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp)) {
        // Android's own keyboard: press OK on the box to type, Search/Done or Back to come out
        SystemTextField(
            value = query,
            onValueChange = { query = it },
            hint = "Search your apps. Press OK to type.",
            modifier = Modifier.width(520.dp).focusRequester(nav.content).dpad(left = nav.rail, up = nav.pill),
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cols = maxOf(1, ((maxWidth + 12.dp) / (86.dp + 12.dp)).toInt())
            if (results.isEmpty()) {
                Text("No apps match \"${query.trim()}\"", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(results, key = { _, a -> a.pkg }) { i, app ->
                        Tile(
                            app.label,
                            Modifier.dpad(left = if (i % cols == 0) nav.rail else null),
                            onClick = { onLaunch(app) },
                            forceName = true,
                        ) { d -> AppGlyph(app, d) }
                    }
                }
            }
        }
    }
}

// ---------- Shared bits for the settings pages ----------

@Composable
fun OptionCard(
    selected: Boolean,
    w: Dp,
    h: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, animationSpec = GlassSpring, label = "option")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(w, h)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            // a dark, wallpaper-tinted body so cards never vanish on bright wallpapers
            .background(ui.wp.dark.copy(alpha = if (selected || focused) 0.58f else 0.46f))
            .border(
                if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                if (focused) Color.White else if (selected) ui.wp.accent else Color.White.copy(alpha = 0.25f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) Sfx.move() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, sub: String) {
    val ui = LocalUi.current
    val lift = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2f), 8f))
    Text(title, color = ui.wp.accent, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = lift)
    Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, style = lift)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun ActionCard(label: String, modifier: Modifier = Modifier, w: Dp = 240.dp, onClick: () -> Unit) {
    OptionCard(selected = false, w = w, h = 52.dp, modifier = modifier, onClick = onClick) {
        GlassBox(Modifier.fillMaxSize(), RoundedCornerShape(16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = inkFg(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WallCard(
    label: String,
    thumb: ImageBitmap?,
    placeholder: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    Column {
        OptionCard(selected = selected, w = 150.dp, h = 84.dp, modifier = modifier, onClick = onClick) {
            if (thumb != null) {
                Image(thumb, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ui.wp.tint, ui.wp.dark))),
                    contentAlignment = Alignment.Center,
                ) { Text(placeholder, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ui.wp.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = ui.wp.dark, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}

@Composable
fun GuideStep(n: Int, text: String) {
    val ui = LocalUi.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(ui.wp.accent),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = ui.wp.dark, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = inkFg(), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
fun ToggleCard(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    OptionCard(selected = false, w = 260.dp, h = 56.dp, modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier
                    .size(48.dp, 26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) ui.wp.accent else Color.White.copy(alpha = 0.28f)),
            ) {
                if (ui.settings.glass == GlassStyle.Clear && ui.backdrops?.clear != null) {
                    // In Clear the thumb is a small see-through capsule; its middle stays clear so the track shows through
                    GlassBox(
                        Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(2.dp).size(32.dp, 22.dp),
                        RoundedCornerShape(11.dp),
                        strict = false,
                        overlayOnly = true,
                    ) {}
                } else {
                    Box(
                        Modifier
                            .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (on) ui.wp.dark else Color.White),
                    )
                }
            }
        }
    }
}

/** A tiny drawing of the layout: one row fading off to the side, or rows wrapping below. */
@Composable
private fun LayoutGlyph(stacked: Boolean) {
    val ui = LocalUi.current
    val c = ui.wp.accent
    Canvas(Modifier.size(52.dp, 28.dp)) {
        val r = 5.dp.toPx()
        val g = 4.dp.toPx()
        if (stacked) {
            for (row in 0..1) for (col in 0..3) {
                drawCircle(c.copy(alpha = if (row == 0) 1f else 0.6f), r, Offset(r + col * (2 * r + g), r + row * (2 * r + g)))
            }
        } else {
            for (col in 0..3) {
                drawCircle(c.copy(alpha = if (col == 3) 0.35f else 1f), r, Offset(r + col * (2 * r + g), size.height / 2f))
            }
        }
    }
}

// ---------- Preferences ----------

/** Wallpaper thumbnails kept for the life of the app (they only change when a wallpaper is replaced). */
private object WallThumbs {
    var stock: List<ImageBitmap?>? = null
    var custom: ImageBitmap? = null
    var bing: ImageBitmap? = null
    var version: Int = -1
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    focusKick: Int,
    nav: Nav,
    wallVersion: Int,
    wallStatus: String,
    rows: PrefRows,
    restore: Boolean,
    onWhatsNew: () -> Unit,
    onChange: (LauncherSettings) -> Unit,
    onPickCustom: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onEditGreeting: (DayPart) -> Unit,
    onReplayIntro: () -> Unit,
    tab: PrefTab,
    onTab: (PrefTab) -> Unit,
    updater: UpdateController,
    backups: List<java.io.File>,
    backupStatus: String,
    onSaveBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRestoreFile: (java.io.File) -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    // Thumbnails are decoded once and kept, so reopening Preferences doesn't decode six large pictures again
    var thumbs by remember { mutableStateOf<List<ImageBitmap?>>(WallThumbs.stock ?: emptyList()) }
    var customThumb by remember { mutableStateOf(WallThumbs.custom) }
    var bingThumb by remember { mutableStateOf(WallThumbs.bing) }
    LaunchedEffect(Unit) {
        if (WallThumbs.stock == null) {
            WallThumbs.stock = withContext(Dispatchers.IO) { WALLPAPERS.map { decodeRes(ctx, it.key, 4)?.asImageBitmap() } }
        }
        thumbs = WallThumbs.stock ?: emptyList()
    }
    LaunchedEffect(wallVersion) {
        if (WallThumbs.version != wallVersion) {
            val pair = withContext(Dispatchers.IO) {
                Pair(
                    decodeFileScaled(customFile(ctx), 320, 180)?.asImageBitmap(),
                    decodeFileScaled(bingFile(ctx), 320, 180)?.asImageBitmap(),
                )
            }
            WallThumbs.custom = pair.first
            WallThumbs.bing = pair.second
            WallThumbs.version = wallVersion
        }
        customThumb = WallThumbs.custom
        bingThumb = WallThumbs.bing
    }
    // Opening Preferences lands on the open tab. Coming back from a sub-screen (greeting editor, picture list)
    // puts the highlight back on the card you came from.
    LaunchedEffect(focusKick) {
        delay(200)
        val back = if (restore && rows.lastRow >= 0) rows.at(rows.lastRow, rows.lastCol) else null
        runCatching { (back ?: nav.content).requestFocus() }
    }

    val isBing = settings.wallSource == WallSource.Bing
    val isCustom = settings.wallSource == WallSource.Custom
    val isStock = settings.wallSource == WallSource.Stock
    Column(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp)) {
        PrefTabBar(tab, onTab, nav, updater.hasUpdate, rows)
        Spacer(Modifier.height(16.dp))
        key(tab) {
        Column(Modifier.fillMaxSize().bounceIn(tab, 12.dp).verticalScroll(rememberScrollState()).padding(bottom = 60.dp)) {
        when (tab) {
        PrefTab.Widgets -> WidgetsTab(settings, nav, rows, onEditGreeting, onChange)
        PrefTab.Feel -> FeelTab(settings, nav, rows, onChange)
        PrefTab.Backup -> BackupTab(nav, rows, backups, backupStatus, onSaveBackup, onRestoreBackup, onRestoreFile)
        PrefTab.Updates -> UpdatesTab(settings, nav, rows, updater, onWhatsNew, onChange)
        PrefTab.Home -> HomeSetupTab(nav, rows, onOpenHomeSettings)
        PrefTab.Look -> {
        // Wallpaper
        SectionTitle("Wallpaper", "Icons and panels pick up its colors, whatever picture you choose")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WallCard(
                "Daily wallpaper", bingThumb, "Daily", isBing,
                Modifier.prefItem(rows, nav, 0, 0),
            ) { onChange(settings.copy(wallSource = WallSource.Bing)) }
            WallCard(
                "My photo", customThumb, "+ Choose", isCustom,
                Modifier.prefItem(rows, nav, 0, 1),
                onPickCustom,
            )
            WALLPAPERS.forEachIndexed { i, w ->
                val sel = isStock && settings.wallpaper == i
                WallCard(
                    w.name, thumbs.getOrNull(i), "", sel,
                    Modifier.prefItem(rows, nav, 0, 2 + i),
                ) { onChange(settings.copy(wallSource = WallSource.Stock, wallpaper = i)) }
            }
        }
        if (isBing && wallStatus.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(wallStatus, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (isCustom) {
            Spacer(Modifier.height(8.dp))
            Text("Select \"My photo\" again to pick a different picture.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))

        // Color theme
        SectionTitle("Color theme", "Three palettes pulled from your wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ColorTheme.values().forEachIndexed { ci, t ->
                val preview = remember(ui.wp.baseTint, ui.wp.key, t) { ui.wp.withTheme(t) }
                OptionCard(
                    selected = settings.colorTheme == t,
                    w = 200.dp,
                    h = 72.dp,
                    modifier = Modifier.prefItem(rows, nav, 1, ci),
                    onClick = { onChange(settings.copy(colorTheme = t)) },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ThemeSwatch(preview, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(t.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Tint style
        SectionTitle("Tint style", "How panels, the dock and tiles look")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassStyle.values().forEachIndexed { gi, g ->
                OptionCard(
                    selected = settings.glass == g,
                    w = 160.dp,
                    h = 72.dp,
                    modifier = Modifier.prefItem(rows, nav, 2, gi),
                    onClick = { onChange(settings.copy(glass = g)) },
                ) {
                    GlassBox(Modifier.fillMaxSize(), RoundedCornerShape(16.dp), style = g, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.label, color = inkFg(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(g.blurb, color = inkFg(Color.White.copy(alpha = 0.8f)), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Icon shape
        SectionTitle("Icon shape", "Applies to the home row and all apps")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconShape.values().forEachIndexed { si, s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OptionCard(
                        selected = settings.shape == s,
                        w = 84.dp,
                        h = 76.dp,
                        modifier = Modifier.prefItem(rows, nav, 3, si),
                        onClick = { onChange(settings.copy(shape = s)) },
                    ) {
                        GlassBox(Modifier.align(Alignment.Center).size(46.dp), s.toShape(), strict = false, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = inkAccent(ui.wp.accent), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Icon style
        SectionTitle("Icon style", "Themed icons are recolored to match the wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconStyle.values().forEachIndexed { ii, st ->
                OptionCard(
                    selected = settings.iconStyle == st,
                    w = 200.dp,
                    h = 64.dp,
                    modifier = Modifier.prefItem(rows, nav, 4, ii),
                    onClick = { onChange(settings.copy(iconStyle = st)) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                        Text(st.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(st.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Home layout
        SectionTitle("Home layout", "How your pinned apps sit on Home")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeLayout.values().forEachIndexed { li, l ->
                OptionCard(
                    selected = settings.homeLayout == l,
                    w = 260.dp,
                    h = 72.dp,
                    modifier = Modifier.prefItem(rows, nav, 5, li),
                    onClick = { onChange(settings.copy(homeLayout = l)) },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        LayoutGlyph(l == HomeLayout.Stacked)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(l.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(l.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Dock
        SectionTitle("Dock", "Put the first row on a bar, in your chosen tint style")
        ToggleCard(if (settings.dock) "Dock on" else "Dock off", settings.dock, Modifier.prefItem(rows, nav, 6)) {
            onChange(settings.copy(dock = !settings.dock))
        }
        Spacer(Modifier.height(24.dp))

        // Dock height
        SectionTitle("Dock height", "How close to the bottom of the screen the home row sits")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DockPos.values().forEachIndexed { di, d ->
                OptionCard(
                    selected = settings.dockPos == d,
                    w = 140.dp,
                    h = 56.dp,
                    modifier = Modifier.prefItem(rows, nav, 7, di),
                    onClick = { onChange(settings.copy(dockPos = d)) },
                ) {
                    Text(
                        d.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // All apps view
        SectionTitle("All apps page", "List every app, or sort them into folders by type (hold OK on an app to move it)")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppsView.values().forEachIndexed { ai, v ->
                OptionCard(
                    selected = settings.appsView == v,
                    w = 260.dp,
                    h = 64.dp,
                    modifier = Modifier.prefItem(rows, nav, 8, ai),
                    onClick = { onChange(settings.copy(appsView = v)) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 10.dp)) {
                        Text(v.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(v.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // App names
        SectionTitle("App names", "Show the name under each icon on Home")
        ToggleCard(if (settings.showNames) "Names on" else "Names off", settings.showNames, Modifier.prefItem(rows, nav, 9)) {
            onChange(settings.copy(showNames = !settings.showNames))
        }
        Spacer(Modifier.height(24.dp))

        // Welcome screen
        SectionTitle("Welcome screen", "See the intro that shows the first time Unwind opens")
        ActionCard("Replay welcome screen", Modifier.prefItem(rows, nav, 10), 240.dp, onReplayIntro)
        }
        }
        }
        }
    }
}

// ---------- Choosing a picture from the TV ----------

@Composable
private fun GalleryThumb(item: GalleryItem, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { decodeThumb(ctx, item.uri, 480)?.asImageBitmap() }
    }
    OptionCard(selected = false, w = 160.dp, h = 90.dp, modifier = modifier, onClick = onClick) {
        val t = thumb
        if (t != null) {
            Image(t, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun GalleryScreen(
    items: List<GalleryItem>?,
    hasPerm: Boolean,
    nav: Nav,
    onGrant: () -> Unit,
    onBrowse: () -> Unit,
    onPick: (GalleryItem) -> Unit,
) {
    LaunchedEffect(items?.size, hasPerm) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }
    val hasGrid = hasPerm && !items.isNullOrEmpty()
    Column(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp)) {
        Text("Choose a wallpaper", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Pick any picture stored on this TV or on a USB drive.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        when {
            !hasPerm -> {
                Text("Allow Unwind to see the pictures on this TV.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                ActionCard("Allow access", Modifier.focusRequester(nav.content).dpad(left = nav.rail), 240.dp, onGrant)
            }
            items == null -> Text("Loading pictures...", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            items.isEmpty() -> {
                Text(
                    "No pictures found. Copy a JPG or PNG to this TV (or plug in a USB drive), then come back.",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
        if (hasPerm && items != null) {
            Spacer(Modifier.height(10.dp))
            ActionCard(
                "Browse files...",
                (if (!hasGrid) Modifier.focusRequester(nav.content) else Modifier).dpad(left = nav.rail),
                200.dp,
                onBrowse,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (hasGrid && items != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            ) {
                itemsIndexed(items, key = { _, g -> g.uri.toString() }) { i, item ->
                    GalleryThumb(
                        item,
                        if (i == 0) Modifier.focusRequester(nav.content) else Modifier,
                    ) { onPick(item) }
                }
            }
        }
    }
}


/** A round swatch split into three diagonal bands (tint, accent, dark), like the Pixel wallpaper & style picker. */
@Composable
fun ThemeSwatch(wp: Wp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val circle = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
        clipPath(circle) {
            drawRect(wp.accent)
            // top-right band
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.45f, 0f); lineTo(w, 0f); lineTo(w, h * 0.55f); close()
                },
                androidx.compose.ui.graphics.lerp(wp.accent, Color.White, 0.35f),
            )
            // bottom-left band
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h * 0.45f); lineTo(w * 0.55f, h); lineTo(0f, h); close()
                },
                wp.tint,
            )
        }
    }
}
