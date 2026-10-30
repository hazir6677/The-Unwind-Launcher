package com.unwind.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Look & feel ----------
private val Lime = Color(0xFFC5E1A5)
private val Forest = Color(0xFF1E3322)
private const val APP_NAME = "Unwind"

// Apps pinned to Home the first time it runs (only the ones actually installed are shown).
// Edit this list to change the defaults. Reorder/add/remove later with the "Add apps" tile.
private val DEFAULT_FAVS = listOf(
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "in.startv.hotstar",
    "in.startv.hotstar.dplus.tv",
    "com.jio.media.ondemand",
    "com.apple.atve.androidtv.appletv",
    "com.spotify.tv.android",
)

enum class Screen { Home, Apps, Edit }

class AppEntry(val pkg: String, val label: String, val intent: Intent, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

// ---------- App data ----------
fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val out = LinkedHashMap<String, AppEntry>()
    // Leanback first, then the regular phone launcher category, so sideloaded phone apps
    // (like Google Photos) show up too.
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || out.containsKey(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val icon = ri.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
            out[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), launch, icon)
        }
    }
    return out.values.sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppEntry) {
    try {
        ctx.startActivity(app.intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun openSearch(ctx: Context) {
    val tries = listOf(
        Intent("android.search.action.GLOBAL_SEARCH"),
        Intent(Intent.ACTION_ASSIST),
        Intent(Intent.ACTION_VOICE_COMMAND),
    )
    for (i in tries) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
    Toast.makeText(ctx, "No search app found", Toast.LENGTH_SHORT).show()
}

fun openSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open settings", Toast.LENGTH_SHORT).show()
    }
}

// ---------- Root ----------
@Composable
fun LauncherApp(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var saved by remember {
        mutableStateOf(prefs.getString("favs", null)?.split(",")?.filter { it.isNotBlank() })
    }
    var screen by remember { mutableStateOf(Screen.Home) }

    LaunchedEffect(tick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }

    val installed = apps.map { it.pkg }.toSet()
    val favPkgs: List<String> = saved ?: DEFAULT_FAVS.filter { it in installed }
    val favApps = favPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    fun toggleFav(pkg: String) {
        val next = if (pkg in favPkgs) favPkgs - pkg else favPkgs + pkg
        saved = next
        prefs.edit().putString("favs", next.joinToString(",")).apply()
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Wallpaper()
        // Soft scrims so text stays readable on any wallpaper
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xAA000000), 0.65f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xAA000000))
            )
        )

        when (screen) {
            Screen.Home -> HomeContent(
                favApps = favApps,
                onLaunch = { launchApp(ctx, it) },
                onAdd = { screen = Screen.Edit },
            )
            Screen.Apps -> AppGrid(apps, false, favPkgs.toSet()) { launchApp(ctx, it) }
            Screen.Edit -> AppGrid(apps, true, favPkgs.toSet()) { toggleFav(it.pkg) }
        }

        Text(
            APP_NAME,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 33.dp, top = 28.dp),
        )
        TopPill(ctx, Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 24.dp))
        Rail(
            selected = if (screen == Screen.Home) 0 else 2,
            onClick = { i ->
                when (i) {
                    0 -> screen = Screen.Home
                    1 -> openSearch(ctx)
                    2 -> screen = Screen.Apps
                    3 -> openSettings(ctx)
                }
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp, top = 24.dp),
        )
    }
}

@Composable
fun Wallpaper() {
    val ctx = LocalContext.current
    // Drop your own image at app/src/main/res/drawable-nodpi/wallpaper.jpg to change it.
    val id = remember { ctx.resources.getIdentifier("wallpaper", "drawable", ctx.packageName) }
    if (id != 0) {
        Image(
            painterResource(id),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFFD9A760), Color(0xFF8FA27A), Color(0xFF2F4A35), Color(0xFF101C12))
                )
            )
        )
    }
}

// ---------- Glass ----------
fun Modifier.glass(shape: Shape, focused: Boolean = false): Modifier {
    val fill = Brush.verticalGradient(
        listOf(
            Color(0xFFB5D99C).copy(alpha = if (focused) 0.50f else 0.26f),
            Color(0xFF2B4A2E).copy(alpha = if (focused) 0.70f else 0.50f),
        )
    )
    val edge = if (focused) {
        Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.7f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f)))
    }
    return this
        .clip(shape)
        .background(fill)
        .border(if (focused) 2.dp else 1.dp, edge, shape)
}

// ---------- Home ----------
@Composable
fun HomeContent(favApps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onAdd: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val copy = when (hour) {
        in 5..11 -> Triple("Good morning", "Rise and", "shine")
        in 12..16 -> Triple("Good afternoon", "Take a", "breather")
        in 17..20 -> Triple("Good evening", "Time to", "unwind")
        else -> Triple("Good night", "Time to", "unwind")
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(favApps.size) {
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
            color = Lime,
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
                ) { AppIcon(app.icon) }
            }
            item {
                Tile(
                    "Add apps",
                    if (favApps.isEmpty()) Modifier.focusRequester(first) else Modifier,
                    onClick = onAdd,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

// ---------- All apps / edit favorites ----------
@Composable
fun AppGrid(apps: List<AppEntry>, editing: Boolean, favs: Set<String>, onClick: (AppEntry) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.size) {
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
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 40.dp, top = 8.dp),
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                val isFav = editing && app.pkg in favs
                Tile(
                    if (isFav) "✓ ${app.label}" else app.label,
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onClick(app) },
                    highlight = isFav,
                ) { AppIcon(app.icon) }
            }
        }
    }
}

@Composable
fun AppIcon(bmp: ImageBitmap) {
    Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(46.dp), contentScale = ContentScale.Fit)
}

@Composable
fun Tile(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    highlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "tile")
    Column(
        modifier
            .width(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).glass(CircleShape, focused),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (highlight) Lime else Color.White.copy(alpha = if (focused) 1f else 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Rail & top pill ----------
@Composable
fun Rail(selected: Int, onClick: (Int) -> Unit, modifier: Modifier) {
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Apps, Icons.Rounded.Settings)
    Column(
        modifier.width(71.dp).glass(RoundedCornerShape(36.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icons.forEachIndexed { i, icon ->
            FocusButton(icon, 54.dp, RoundedCornerShape(22.dp), selected == i) { onClick(i) }
        }
    }
}

@Composable
fun TopPill(ctx: Context, modifier: Modifier) {
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var time by remember { mutableStateOf(fmt.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = fmt.format(Date())
            delay(15_000)
        }
    }
    Row(
        modifier.height(46.dp).glass(RoundedCornerShape(23.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FocusButton(Icons.Rounded.Search, 34.dp, CircleShape) { openSearch(ctx) }
        FocusButton(Icons.Rounded.Settings, 34.dp, CircleShape) { openSettings(ctx) }
        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(time, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun FocusButton(icon: ImageVector, size: Dp, shape: Shape, selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "btn")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    selected -> Lime
                    focused -> Color.White.copy(alpha = 0.28f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Forest else Color.White,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
