package com.unwind.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
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
fun HomeContent(favApps: List<AppEntry>, focusKick: Int, onLaunch: (AppEntry) -> Unit, onAdd: () -> Unit) {
    val ui = LocalUi.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val copy = when (hour) {
        in 5..11 -> Triple("Good morning", "Rise and", "shine")
        in 12..16 -> Triple("Good afternoon", "Take a", "breather")
        in 17..20 -> Triple("Good evening", "Time to", "unwind")
        else -> Triple("Good night", "Time to", "unwind")
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(favApps.size, focusKick) {
        delay(200)
        runCatching { first.requestFocus() }
    }
    val shadow = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 14f))

    Column(Modifier.fillMaxSize().padding(start = 163.dp, top = 140.dp, bottom = 96.dp)) {
        Text(copy.first, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, style = shadow)
        Spacer(Modifier.height(6.dp))
        Text(
            copy.second,
            color = Color.White,
            fontSize = 54.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-1).sp,
            style = shadow,
        )
        Text(
            copy.third,
            color = ui.wp.accent,
            fontSize = 54.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-1).sp,
            style = shadow,
        )
        Spacer(Modifier.weight(1f))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp),
        ) {
            itemsIndexed(favApps, key = { _, a -> a.pkg }) { i, app ->
                Tile(
                    app.label,
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onLaunch(app) },
                ) { d -> AppGlyph(app, d) }
            }
            item {
                Tile(
                    "Add apps",
                    if (favApps.isEmpty()) Modifier.focusRequester(first) else Modifier,
                    onClick = onAdd,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

// ---------- All apps / pick favorites ----------

@Composable
fun AppGrid(apps: List<AppEntry>, editing: Boolean, favs: Set<String>, focusKick: Int, onClick: (AppEntry) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.size, focusKick) {
        delay(200)
        if (apps.isNotEmpty()) runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = 163.dp, top = 96.dp, end = 40.dp)) {
        Text(
            if (editing) "Choose your favorites" else "All apps",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (editing) "Press OK on an app to add it to Home or remove it." else "Press OK to open an app.",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                val isFav = editing && app.pkg in favs
                Tile(
                    if (isFav) "\u2713 ${app.label}" else app.label,
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onClick(app) },
                    highlight = isFav,
                ) { d -> AppGlyph(app, d) }
            }
        }
    }
}

// ---------- In-launcher app search ----------

@Composable
private fun Key(label: String, w: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "key")
    Box(
        modifier
            .size(w, 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(10.dp), focused, blur = false)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) ui.wp.accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SearchScreen(apps: List<AppEntry>, focusKick: Int, onLaunch: (AppEntry) -> Unit) {
    val ui = LocalUi.current
    var query by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKick) {
        delay(200)
        runCatching { first.requestFocus() }
    }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    Row(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp, bottom = 24.dp)) {
        Column(Modifier.width(392.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (query.isEmpty()) "Search your apps" else query,
                    color = Color.White.copy(alpha = if (query.isEmpty()) 0.65f else 1f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            val rows = listOf("abcdefghi", "jklmnopqr", "stuvwxyz0", "123456789")
            rows.forEachIndexed { ri, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { ci, ch ->
                        Key(
                            ch.uppercase(),
                            38.dp,
                            if (ri == 0 && ci == 0) Modifier.focusRequester(first) else Modifier,
                        ) { query += ch }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("Space", 126.dp, Modifier) { query += " " }
                Key("Delete", 126.dp, Modifier) { query = query.dropLast(1) }
                Key("Clear", 126.dp, Modifier) { query = "" }
            }
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxSize()) {
            if (results.isEmpty()) {
                Text("No apps match \"${query.trim()}\"", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(results, key = { _, a -> a.pkg }) { _, app ->
                        Tile(app.label, Modifier, onClick = { onLaunch(app) }) { d -> AppGlyph(app, d) }
                    }
                }
            }
        }
    }
}

// ---------- Launcher settings ----------

@Composable
private fun OptionCard(
    selected: Boolean,
    w: Dp,
    h: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "option")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(w, h)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .border(
                if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                if (focused) Color.White else if (selected) ui.wp.accent else Color.White.copy(alpha = 0.25f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
private fun SectionTitle(title: String, sub: String) {
    val ui = LocalUi.current
    Text(title, color = ui.wp.accent, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SettingsScreen(settings: LauncherSettings, focusKick: Int, onChange: (LauncherSettings) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var thumbs by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(Unit) {
        thumbs = withContext(Dispatchers.IO) { WALLPAPERS.map { decodeRes(ctx, it.key, 4)?.asImageBitmap() } }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKick, thumbs.size) {
        delay(200)
        runCatching { first.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 150.dp, top = 84.dp, end = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 60.dp),
    ) {
        Text("Launcher settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(18.dp))

        // Wallpaper
        SectionTitle("Wallpaper", "Icons and glass pick up its colors")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WALLPAPERS.forEachIndexed { i, w ->
                Column {
                    OptionCard(
                        selected = settings.wallpaper == i,
                        w = 150.dp,
                        h = 84.dp,
                        modifier = if (settings.wallpaper == i) Modifier.focusRequester(first) else Modifier,
                        onClick = { onChange(settings.copy(wallpaper = i)) },
                    ) {
                        val t = thumbs.getOrNull(i)
                        if (t != null) Image(t, contentDescription = w.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (settings.wallpaper == i) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ui.wp.accent),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = ui.wp.dark, modifier = Modifier.size(15.dp)) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(w.name, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Glass style
        SectionTitle("Glass style", "How panels, the dock and tiles look")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassStyle.values().forEach { g ->
                OptionCard(
                    selected = settings.glass == g,
                    w = 160.dp,
                    h = 72.dp,
                    onClick = { onChange(settings.copy(glass = g)) },
                ) {
                    Box(Modifier.fillMaxSize().glass(RoundedCornerShape(16.dp), style = g), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(g.blurb, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Icon shape
        SectionTitle("Icon shape", "Applies to the home row and all apps")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconShape.values().forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OptionCard(
                        selected = settings.shape == s,
                        w = 84.dp,
                        h = 76.dp,
                        onClick = { onChange(settings.copy(shape = s)) },
                    ) {
                        Box(Modifier.align(Alignment.Center).size(46.dp).glass(s.toShape()), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(24.dp))
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
            IconStyle.values().forEach { st ->
                OptionCard(
                    selected = settings.iconStyle == st,
                    w = 200.dp,
                    h = 64.dp,
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

        // App names
        SectionTitle("App names", "Show the name under each icon on Home")
        OptionCard(
            selected = false,
            w = 260.dp,
            h = 56.dp,
            onClick = { onChange(settings.copy(showNames = !settings.showNames)) },
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (settings.showNames) "Names on" else "Names off", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier
                        .size(48.dp, 26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (settings.showNames) ui.wp.accent else Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
                            .align(if (settings.showNames) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (settings.showNames) ui.wp.dark else Color.White),
                    )
                }
            }
        }
    }
}
