package com.unwind.launcher

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val APP_NAME = "Unwind"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
fun WallpaperLayer(wp: Wp) {
    val ctx = LocalContext.current
    Crossfade(targetState = wp.key, animationSpec = tween(700), label = "wallpaper") { key ->
        val id = remember(key) { ctx.resources.getIdentifier(key, "drawable", ctx.packageName) }
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
}

@Composable
fun LauncherApp(tick: Int) {
    val ctx = LocalContext.current
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
    var wifi by remember { mutableStateOf<WifiState?>(null) }
    var acct by remember { mutableStateOf<AcctState?>(null) }
    var inputs by remember { mutableStateOf(emptyList<InputItem>()) }

    val wp = WALLPAPERS[settings.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)]
    val locOk = remember(permTick) { hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) }
    val accOk = remember(permTick) { hasPerm(ctx, Manifest.permission.GET_ACCOUNTS) }
    val popupFocus = remember { FocusRequester() }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val accountLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }

    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(wp.key) { backdrops = withContext(Dispatchers.Default) { makeBackdrops(ctx, wp.key) } }
    LaunchedEffect(permTick) { acct = withContext(Dispatchers.IO) { readAccount(ctx) } }
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
            Popup.Account -> {
                acct = withContext(Dispatchers.IO) { readAccount(ctx) }
            }
            Popup.None -> {}
        }
    }
    LaunchedEffect(popup, wifi?.nets?.size, inputs.size, acct?.emails?.size, locOk, accOk) {
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

    fun updateSettings(s: LauncherSettings) {
        settings = s
        saveSettings(ctx, s)
    }

    fun requestLocation() {
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun requestAccounts() {
        accountLauncher.launch(arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.READ_CONTACTS))
    }

    fun openPopup(p: Popup) {
        popup = p
        if (p == Popup.Wifi && !locOk) requestLocation()
        if (p == Popup.Account && !accOk) requestAccounts()
    }

    fun closePopup() {
        popup = Popup.None
        focusKick++
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    BackHandler(enabled = popup != Popup.None) { closePopup() }

    val ui = UiCtx(settings, wp, backdrops?.soft, backdrops?.heavy, rootSize)
    val railSel = when (screen) {
        Screen.Home -> 0
        Screen.Search -> 1
        Screen.Apps, Screen.Edit -> 2
        Screen.Settings -> 3
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
        CompositionLocalProvider(LocalUi provides ui) {
            // While a popup is open, nothing behind it can take focus.
            Box(Modifier.fillMaxSize().focusProperties { canFocus = popup == Popup.None }) {
                WallpaperLayer(wp)
                // Soft scrims keep text readable on any wallpaper
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to Color(0xB0000000), 0.62f to Color.Transparent)
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xA0000000))
                    )
                )

                when (screen) {
                    Screen.Home -> HomeContent(
                        favApps = favApps,
                        focusKick = focusKick,
                        onLaunch = { launchApp(ctx, it) },
                        onAdd = { screen = Screen.Edit },
                    )
                    Screen.Apps -> AppGrid(apps, false, favPkgs.toSet(), focusKick) { launchApp(ctx, it) }
                    Screen.Edit -> AppGrid(apps, true, favPkgs.toSet(), focusKick) { toggleFav(it.pkg) }
                    Screen.Search -> SearchScreen(apps, focusKick) { launchApp(ctx, it) }
                    Screen.Settings -> SettingsScreen(settings, focusKick) { updateSettings(it) }
                }

                Text(
                    APP_NAME,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 33.dp, top = 28.dp),
                )
                TopPill(
                    acct = acct,
                    onSearch = { screen = Screen.Search },
                    onSystem = { openSystemSettings(ctx) },
                    onWifi = { openPopup(Popup.Wifi) },
                    onInputs = { openPopup(Popup.Inputs) },
                    onAccount = { openPopup(Popup.Account) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 24.dp),
                )
                Rail(
                    selected = railSel,
                    onClick = { i ->
                        when (i) {
                            0 -> screen = Screen.Home
                            1 -> screen = Screen.Search
                            2 -> screen = Screen.Apps
                            3 -> screen = Screen.Settings
                            else -> {}
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp, top = 24.dp),
                )
            }

            if (popup != Popup.None) {
                PopupLayer(popup, onDismiss = { closePopup() }) {
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
                        Popup.Account -> AccountPopup(
                            acct = acct,
                            accOk = accOk,
                            focus = popupFocus,
                            onGrant = { requestAccounts() },
                            onManage = { openAccountSettings(ctx) },
                        )
                        Popup.None -> {}
                    }
                }
            }
        }
    }
}
