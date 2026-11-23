package com.unwind.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Pinned to Home the first time it runs (only apps that are installed show up).
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

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pressing Home while Unwind is already open should bring you back to the Home page.
        addOnNewIntentListener { intent ->
            if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick++
        }
        setContent { LauncherApp(resumeTick, homeTick) }
    }

    override fun onResume() {
        super.onResume()
        Motion.paused = false
        resumeTick++
    }

    override fun onPause() {
        super.onPause()
        Motion.paused = true
    }
}

@Composable
fun WallpaperLayer(wall: WallData?) {
    Crossfade(targetState = wall, animationSpec = tween(700), label = "wallpaper") { w ->
        if (w != null) {
            Image(
                w.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFD9A760), Color(0xFF8FA27A), Color(0xFF2F4A35), Color(0xFF101C12)),
                    ),
                ),
            )
        }
    }
}

@Composable
fun LauncherApp(tick: Int, homeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var saved by remember {
        mutableStateOf(prefs.getString("favs", null)?.split(",")?.filter { it.isNotBlank() })
    }
    var settings by remember { mutableStateOf(loadSettings(ctx)) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var popup by remember { mutableStateOf(Popup.None) }
    var focusKick by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var backdrops by remember { mutableStateOf<Backdrops?>(null) }
    var wall by remember { mutableStateOf<WallData?>(null) }
    var wallVersion by remember { mutableIntStateOf(0) }
    var wallStatus by remember { mutableStateOf("") }
    var gallery by remember { mutableStateOf<List<GalleryItem>?>(null) }
    var wifi by remember { mutableStateOf<WifiState?>(null) }
    var inputs by remember { mutableStateOf(emptyList<InputItem>()) }
    // The welcome screen shows the very first time Unwind opens (and again from Preferences)
    var showIntro by remember { mutableStateOf(!prefs.getBoolean("introSeen", false)) }
    var greetPart by remember { mutableStateOf(DayPart.Morning) }
    var folderMap by remember { mutableStateOf(loadFolderMap(prefs)) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var moveApp by remember { mutableStateOf<AppEntry?>(null) }
    val moveFocus = remember { FocusRequester() }
    // Long-press menu on a home app, and the app currently being carried along the home row
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var dockMove by remember { mutableStateOf<String?>(null) }
    val menuFocus = remember { FocusRequester() }
    // 0 = Home as normal, 1 = zoomed and faded out while an app opens
    val launchP = remember { Animatable(0f) }
    var prefTab by remember { mutableStateOf(PrefTab.Look) }
    // Preferences remembers which card had focus, so coming back from a sub-screen lands on it again
    val prefRows = remember { PrefRows() }
    var prefRestore by remember { mutableStateOf(false) }
    // What's New pop-up: the notes for the installed version only
    var notesOpen by remember { mutableStateOf(false) }
    var wasNotesOpen by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<NotesUi?>(null) }
    val updater = remember { UpdateController(ctx.applicationContext, scope, prefs) }
    var backups by remember { mutableStateOf(Backup.list(ctx)) }
    var backupStatus by remember { mutableStateOf("") }
    var bannerVer by remember { mutableStateOf<String?>(null) }
    var bannerSeen by remember { mutableStateOf("") }

    val baseWp = wall?.wp ?: WALLPAPERS[settings.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)]
    val wp = remember(baseWp, settings.colorTheme) { baseWp.withTheme(settings.colorTheme) }
    val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val locOk = remember(permTick) { hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) }
    val storageOk = remember(permTick) { hasPerm(ctx, storagePerm) }
    val isHome = remember(tick, homeTick) { isDefaultHome(ctx) }
    val popupFocus = remember { FocusRequester() }
    val chipReq = remember { FocusRequester() }

    // Weather: where the TV is (or the city you picked), refreshed every half hour
    var wPrefs by remember { mutableStateOf(loadWeatherPrefs(prefs)) }
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var weatherErr by remember { mutableStateOf<String?>(null) }
    var weatherTick by remember { mutableIntStateOf(0) }

    // Focus targets for moving around with the D-pad
    val railReqs = remember { List(3) { FocusRequester() } }
    val pillReq = remember { FocusRequester() }
    val contentReq = remember { FocusRequester() }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val storageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    fun updateSettings(s: LauncherSettings) {
        settings = s
        saveSettings(ctx, s)
    }

    fun applyPicked(uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveCustomWallpaper(ctx, uri) }
            if (ok) {
                updateSettings(settings.copy(wallSource = WallSource.Custom))
                wallVersion++
                prefRestore = true
                screen = Screen.Settings
            } else {
                Toast.makeText(ctx, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val docLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) applyPicked(uri)
        }

    // Motion and sound follow the Preferences switches everywhere at once
    SideEffect {
        Motion.bouncy = settings.bouncy
        Sfx.pack = settings.soundPack
        Sfx.volume = settings.soundVolume.gain
    }
    LaunchedEffect(Unit) { Sfx.init(ctx.applicationContext) }

    // Updates: check shortly after opening and then every few hours (the GitHub version also carries on installing after the permission screen)
    LaunchedEffect(tick) { updater.onResume() }
    LaunchedEffect(settings.autoUpdate) {
        if (!settings.autoUpdate) return@LaunchedEffect
        delay(4000)
        while (true) {
            if (System.currentTimeMillis() - updater.checkedAt >= UPDATE_EVERY_MS) updater.check(manual = false)
            delay(60 * 60 * 1000L)
        }
    }
    val pendingVer = updater.pendingVersion
    LaunchedEffect(pendingVer) {
        if (pendingVer != null && pendingVer != bannerSeen) {
            bannerSeen = pendingVer
            bannerVer = pendingVer
            delay(9000)
            bannerVer = null
        }
    }

    // Saving and restoring the whole setup
    fun finishRestore(r: Result<Int>) {
        if (r.isSuccess) {
            Toast.makeText(ctx, "Setup restored. Restarting Unwind...", Toast.LENGTH_SHORT).show()
            (ctx as? Activity)?.recreate()
        } else {
            backupStatus = "Couldn't restore: " + (r.exceptionOrNull()?.message ?: "that file didn't work.")
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { Backup.write(ctx, it) } }
                }
                backupStatus = if (r.isSuccess) "Setup saved." else "Couldn't save: " + (r.exceptionOrNull()?.message ?: "unknown problem")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)!!.use { Backup.read(ctx, it) } }
                }
                finishRestore(r)
            }
        }
    }
    fun saveBackup() {
        try {
            saveLauncher.launch(Backup.suggestedName())
        } catch (e: Exception) {
            // no file browser on this TV: keep it in Unwind's own folder instead
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Backup.saveToAppDir(ctx) } }
                backups = Backup.list(ctx)
                backupStatus = r.fold(
                    { "This TV has no file browser, so the setup was saved inside Unwind: ${it.name}. It shows below." },
                    { "Couldn't save: " + (it.message ?: "unknown problem") },
                )
            }
        }
    }
    fun restoreBackup() {
        try {
            restoreLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            backupStatus = "This TV has no file browser. Use a setup from the list below, if you have one."
        }
    }
    fun restoreFile(f: java.io.File) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { f.inputStream().use { Backup.read(ctx, it) } } }
            finishRestore(r)
        }
    }

    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    // Back from an app: ease Home in from the zoomed, faded state
    LaunchedEffect(tick) {
        if (launchP.value > 0f) {
            if (Motion.bouncy) launchP.animateTo(0f, spring(0.62f, 220f)) else launchP.animateTo(0f, tween(240))
        }
    }
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            screen = Screen.Home
            popup = Popup.None
            menuApp = null
            dockMove = null
            focusKick++
        }
    }

    // Load whichever wallpaper is selected (stock, Bing daily, or your own picture)
    LaunchedEffect(settings.wallSource, settings.wallpaper, wallVersion) {
        val loaded = withContext(Dispatchers.IO) { loadWallpaper(ctx, settings) }
        if (loaded != null) wall = loaded
    }
    // Glass needs blurred copies of the wallpaper, cut to the screen's shape
    LaunchedEffect(wall, rootSize) {
        val w = wall
        if (w != null && rootSize.width > 0 && rootSize.height > 0) {
            val aspect = rootSize.width.toFloat() / rootSize.height
            // same scrim strength the launcher draws over the wallpaper, so the glass sees what is really behind it
            val sa = (0.45f + 0.35f * w.wp.luma).coerceIn(0.45f, 0.85f)
            backdrops = withContext(Dispatchers.Default) { makeBackdrops(w.bitmap, aspect, sa) }
        }
    }
    // Bing image of the day: check when opened and then every half hour while the launcher is up
    LaunchedEffect(settings.wallSource, tick) {
        if (settings.wallSource == WallSource.Bing) {
            if (wallStatus.isBlank()) wallStatus = "Checking for today's wallpaper..."
            while (true) {
                val r = withContext(Dispatchers.IO) { refreshBing(ctx) }
                wallStatus = when {
                    !r.ok -> r.message
                    r.message.isBlank() -> "Today's wallpaper"
                    else -> "Today: ${r.message}"
                }
                if (r.changed) wallVersion++
                delay(if (r.ok) 30 * 60 * 1000L else 2 * 60 * 1000L)
            }
        } else {
            wallStatus = ""
        }
    }
    LaunchedEffect(screen, storageOk) {
        if (screen == Screen.Gallery && storageOk) {
            gallery = null
            gallery = withContext(Dispatchers.IO) { queryImages(ctx) }
        }
    }
    LaunchedEffect(wPrefs, locOk, weatherTick) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { loadWeather(ctx, wPrefs) } }
            r.onSuccess { weather = it; weatherErr = null }
            r.onFailure { weatherErr = it.message ?: "Couldn't get the weather." }
            delay(if (r.isSuccess) 30 * 60 * 1000L else 60 * 1000L)
        }
    }
    LaunchedEffect(popup, permTick) {
        when (popup) {
            Popup.Wifi -> {
                while (true) {
                    wifi = withContext(Dispatchers.IO) { readWifi(ctx) }
                    delay(3000)
                }
            }
            Popup.Inputs -> {
                inputs = withContext(Dispatchers.IO) { readInputs(ctx) }
            }
            Popup.Weather -> {
                val w = weather
                if (w == null || System.currentTimeMillis() - w.at > 10 * 60 * 1000L) weatherTick++
            }
            Popup.None -> {}
        }
    }
    LaunchedEffect(popup, wifi?.nets?.size, inputs.size, locOk) {
        if (popup != Popup.None) {
            delay(150)
            runCatching { popupFocus.requestFocus() }
        }
    }

    val installed = apps.map { it.pkg }.toSet()
    val favPkgs: List<String> = saved ?: DEFAULT_FAVS.filter { it in installed }
    val favApps = favPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    fun toggleFav(pkg: String) {
        val next = if (pkg in favPkgs) favPkgs - pkg else favPkgs + pkg
        saved = next
        prefs.edit().putString("favs", next.joinToString(",")).apply()
    }

    /** Puts [pkg] where [target] is (before it when moving left, after it when moving right) and saves the order. */
    fun reorderFav(pkg: String, target: String, right: Boolean) {
        val cur = favPkgs.toMutableList()
        if (pkg !in cur || target !in cur || pkg == target) return
        cur.remove(pkg)
        val ti = cur.indexOf(target)
        cur.add(if (right) ti + 1 else ti, pkg)
        saved = cur
        prefs.edit().putString("favs", cur.joinToString(",")).apply()
    }

    fun openMenu(app: AppEntry) {
        menuApp = app
    }

    fun closeMenu() {
        menuApp = null
        focusKick++
    }

    /**
     * Opening an app: Home zooms in slightly and fades, then the app starts. Coming back, Home eases in
     * again (see the effect on [tick] above). If the app never takes over the screen, Home just fades back.
     */
    fun openApp(app: AppEntry) {
        if (!settings.launchAnim || launchP.isRunning) {
            launchApp(ctx, app)
            return
        }
        scope.launch {
            launchP.animateTo(1f, tween(170))
            launchApp(ctx, app)
            delay(900)
            if (!Motion.paused) launchP.animateTo(0f, tween(220))
        }
    }

    fun requestLocation() {
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun openGallery() {
        screen = Screen.Gallery
        if (!storageOk) storageLauncher.launch(storagePerm)
    }

    fun browseFiles() {
        try {
            docLauncher.launch(arrayOf("image/*"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "This TV has no file browser. Use the picture list instead.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPopup(p: Popup) {
        popup = p
        if (p == Popup.Wifi && !locOk) requestLocation()
    }

    fun closePopup() {
        popup = Popup.None
        focusKick++
    }

    fun openWhatsNew() {
        notes = null
        notesOpen = true
        scope.launch { notes = loadNotes(ctx.applicationContext, updater.current) }
    }
    // After the pop-up closes, the highlight goes back to the What's New button
    LaunchedEffect(notesOpen) {
        if (notesOpen) {
            wasNotesOpen = true
        } else if (wasNotesOpen) {
            wasNotesOpen = false
            delay(150)
            runCatching { prefRows.at(prefRows.lastRow, prefRows.lastCol)?.requestFocus() }
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        if (screen == Screen.Gallery || screen == Screen.Greeting) {
            prefRestore = true
            screen = Screen.Settings
        } else {
            screen = Screen.Home
        }
    }
    // Inside a folder, Back goes to the folder list first
    BackHandler(enabled = screen == Screen.Apps && openFolder != null && settings.appsView == AppsView.Folders) {
        openFolder = null
        focusKick++
    }
    BackHandler(enabled = moveApp != null) { moveApp = null }
    // When Unwind is the Home app, Back on the Home page does nothing (it shouldn't close the launcher)
    BackHandler(enabled = isHome && screen == Screen.Home && popup == Popup.None) { }
    BackHandler(enabled = popup != Popup.None) { closePopup() }
    // Newest handlers win: Back first cancels a move in progress, then closes the long-press menu
    BackHandler(enabled = menuApp != null) { closeMenu() }
    BackHandler(enabled = dockMove != null) { dockMove = null }

    LaunchedEffect(screen) {
        if (screen != Screen.Apps) openFolder = null
        if (screen != Screen.Home) dockMove = null
    }

    fun moveTo(app: AppEntry, folder: String?) {
        val next = if (folder == null || folder == app.folder) folderMap - app.pkg else folderMap + (app.pkg to folder)
        folderMap = next
        saveFolderMap(prefs, next)
        moveApp = null
    }

    // One UiCtx per real change. A new object on every recomposition would make every panel and card in the app redraw.
    val ui = remember(settings, wp, backdrops, rootSize) { UiCtx(settings, wp, backdrops, rootSize) }
    val railSel = when (screen) {
        Screen.Home -> 0
        Screen.Apps, Screen.Edit -> 1
        Screen.Settings, Screen.Gallery, Screen.Greeting -> 2
    }
    val nav = Nav(contentReq, pillReq, railReqs[railSel])
    val title = when (screen) {
        Screen.Home -> "Home"
        Screen.Apps -> "Apps"
        Screen.Edit -> "Favorites"
        Screen.Settings, Screen.Gallery, Screen.Greeting -> "Preferences"
    }
    val scrimA = (0.45f + 0.35f * wp.luma).coerceIn(0.45f, 0.85f)

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
        CompositionLocalProvider(LocalUi provides ui) {
            // While a popup is open, nothing behind it can take focus.
            Box(Modifier.fillMaxSize().focusProperties { canFocus = popup == Popup.None && !showIntro && moveApp == null && menuApp == null && !notesOpen }.graphicsLayer {
                // opening an app: Home zooms in a little and fades; reads the value while drawing, so nothing recomposes
                val p = launchP.value
                alpha = (1f - p).coerceIn(0f, 1f)
                scaleX = 1f + 0.07f * p
                scaleY = 1f + 0.07f * p
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }) {
                WallpaperLayer(wall)
                // Soft scrims keep text readable on any wallpaper (stronger on bright pictures)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = scrimA), 0.62f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = scrimA * 0.9f)),
                    ),
                )
                // Top scrim: the top bar, page hints and the first row of cards sit over bright sky on some wallpapers
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = scrimA * 0.6f), 0.38f to Color.Transparent),
                    ),
                )

                Box(Modifier.fillMaxSize().bounceIn(screen)) {
                when (screen) {
                    Screen.Home -> HomeContent(
                        favApps = favApps,
                        weather = weather,
                        focusKick = focusKick,
                        nav = nav,
                        movingPkg = dockMove,
                        onLaunch = { openApp(it) },
                        onAdd = { screen = Screen.Edit },
                        onMenu = { openMenu(it) },
                        onMoveTo = { pkg, target, right -> reorderFav(pkg, target, right) },
                        onMoveDone = { dockMove = null },
                    )
                    Screen.Apps -> if (settings.appsView == AppsView.Folders) {
                        AppFolders(
                            apps = apps,
                            folderMap = folderMap,
                            openFolder = openFolder,
                            focusKick = focusKick,
                            nav = nav,
                            onOpenFolder = { openFolder = it },
                            onLaunch = { openApp(it) },
                            onMove = { moveApp = it },
                        )
                    } else {
                        AppGrid(apps, false, favPkgs.toSet(), focusKick, nav) { openApp(it) }
                    }
                    Screen.Edit -> AppGrid(apps, true, favPkgs.toSet(), focusKick, nav) { toggleFav(it.pkg) }
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        focusKick = focusKick,
                        nav = nav,
                        wallVersion = wallVersion,
                        wallStatus = wallStatus,
                        rows = prefRows,
                        restore = prefRestore,
                        onWhatsNew = { openWhatsNew() },
                        onChange = { updateSettings(it) },
                        onPickCustom = {
                            if (customFile(ctx).exists() && settings.wallSource != WallSource.Custom) {
                                updateSettings(settings.copy(wallSource = WallSource.Custom))
                            } else {
                                openGallery()
                            }
                        },
                        onOpenHomeSettings = { openHomeSettings(ctx) },
                        onEditGreeting = { greetPart = it; screen = Screen.Greeting },
                        onReplayIntro = { showIntro = true },
                        tab = prefTab,
                        onTab = { prefTab = it },
                        updater = updater,
                        backups = backups,
                        backupStatus = backupStatus,
                        onSaveBackup = { saveBackup() },
                        onRestoreBackup = { restoreBackup() },
                        onRestoreFile = { restoreFile(it) },
                    )
                    Screen.Greeting -> GreetingEditor(
                        part = greetPart,
                        initial = settings.greetings[greetPart] ?: DEFAULT_GREETINGS.getValue(greetPart),
                        nav = nav,
                        onSave = { g ->
                            updateSettings(settings.copy(greetings = settings.greetings + (greetPart to g)))
                            prefRestore = true
                            screen = Screen.Settings
                        },
                        onCancel = { prefRestore = true; screen = Screen.Settings },
                    )
                    Screen.Gallery -> GalleryScreen(
                        items = gallery,
                        hasPerm = storageOk,
                        nav = nav,
                        onGrant = { storageLauncher.launch(storagePerm) },
                        onBrowse = { browseFiles() },
                        onPick = { applyPicked(it.uri) },
                    )
                }
                }

                // Header: always says which screen you're on
                Crossfade(
                    targetState = title,
                    animationSpec = tween(220),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 33.dp, top = 26.dp),
                    label = "header",
                ) { t ->
                    Text(
                        t,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.3.sp,
                        style = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 12f)),
                    )
                }
                // Small weather chip right under the page title
                WeatherChip(
                    data = weather,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 33.dp, top = 64.dp)
                        .focusRequester(chipReq)
                        .dpad(right = pillReq, down = railReqs[railSel]),
                    onClick = { openPopup(Popup.Weather) },
                )
                TopPill(
                    nav = nav,
                    onSystem = { openSystemSettings(ctx) },
                    onWifi = { openPopup(Popup.Wifi) },
                    onInputs = { openPopup(Popup.Inputs) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 24.dp),
                    // the big clock widget is the clock now, so the small one steps aside and the bar closes up
                    showClock = settings.homeWidget != HomeWidget.Clock,
                )
                Rail(
                    selected = railSel,
                    onClick = { i ->
                        when (i) {
                            0 -> screen = Screen.Home
                            1 -> screen = Screen.Apps
                            2 -> { prefRestore = false; screen = Screen.Settings }
                            else -> {}
                        }
                    },
                    reqs = railReqs,
                    toContent = contentReq,
                    chip = chipReq,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp, top = 24.dp),
                    badge = if (updater.hasUpdate) 2 else -1,
                )
            }

            AnimatedVisibility(
                visible = bannerVer != null && popup == Popup.None && !showIntro,
                enter = slideInVertically(spring(0.5f, 300f)) { -it * 2 } + fadeIn(),
                exit = slideOutVertically { -it * 2 } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            ) {
                UpdateBanner(bannerVer ?: bannerSeen)
            }

            if (popup != Popup.None) {
                PopupLayer(popup, fromStart = popup == Popup.Weather, onDismiss = { closePopup() }) {
                    when (popup) {
                        Popup.Wifi -> WifiPopup(
                            state = wifi,
                            locOk = locOk,
                            focus = popupFocus,
                            onGrant = { requestLocation() },
                            onSettings = { openWifiSettings(ctx) },
                        )
                        Popup.Inputs -> InputsPopup(
                            items = inputs,
                            focus = popupFocus,
                            onPick = { switchInput(ctx, it); closePopup() },
                            onSettings = { openSystemSettings(ctx) },
                        )
                        Popup.Weather -> WeatherPopup(
                            data = weather,
                            error = weatherErr,
                            prefs = wPrefs,
                            locOk = locOk,
                            focus = popupFocus,
                            onPrefs = { wPrefs = it; saveWeatherPrefs(prefs, it) },
                            onGrantLocation = { requestLocation() },
                            onRefresh = { weatherTick++ },
                        )
                        Popup.None -> {}
                    }
                }
            }

            menuApp?.let { a ->
                AppMenuDialog(
                    app = a,
                    isSystem = remember(a.pkg) { isSystemApp(ctx, a.pkg) },
                    focus = menuFocus,
                    onInfo = { closeMenu(); openAppInfo(ctx, a.pkg) },
                    onMove = { menuApp = null; dockMove = a.pkg },
                    onRemove = { closeMenu(); if (a.pkg in favPkgs) toggleFav(a.pkg) },
                    onUninstall = { closeMenu(); uninstallApp(ctx, a.pkg) },
                    onBlockedUninstall = {
                        Toast.makeText(ctx, "${a.label} is built into the TV, so Android can't uninstall it", Toast.LENGTH_LONG).show()
                    },
                    onDismiss = { closeMenu() },
                )
            }

            moveApp?.let { a ->
                MoveAppDialog(
                    app = a,
                    currentFolder = folderMap[a.pkg] ?: a.folder,
                    isManual = folderMap.containsKey(a.pkg),
                    focus = moveFocus,
                    onPick = { moveTo(a, it) },
                    onAuto = { moveTo(a, null) },
                    onDismiss = { moveApp = null },
                )
            }

            if (notesOpen) {
                WhatsNewPopup(version = updater.current, notes = notes, onClose = { notesOpen = false })
            }

            // The intro animates itself in and out (see Intro.kt), so no wrapper fade here
            if (showIntro) {
                IntroScreen(
                    onDone = {
                        prefs.edit().putBoolean("introSeen", true).apply()
                        showIntro = false
                        focusKick++
                    },
                )
            }
        }
    }
}
