package com.unwind.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================
//  Weather comes from Open-Meteo (open-meteo.com): free, no account or API key, fast, and
//  it can answer with several different forecast models. City search uses its geocoder.
//  OpenWeather and AccuWeather both need a personal API key, which is why they aren't used.
// =====================================================================================

class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    /** "Kochi, Kerala" */
    fun label(): String = if (region.isBlank() || region == name) name else "$name, $region"

    /** "Kochi, Kerala, India" */
    fun full(): String = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/** Which forecast model Open-Meteo should use. */
enum class WeatherModel(val label: String, val blurb: String, val param: String?) {
    Best("Best match", "Open-Meteo picks the best model for your area", null),
    Ecmwf("ECMWF", "The European model, strong on 3 to 7 day outlooks", "ecmwf_ifs025"),
    Gfs("NOAA GFS", "The US global model", "gfs_seamless"),
    Icon("DWD ICON", "The German global model", "icon_seamless"),
}

class WeatherPrefs(
    val auto: Boolean = true,
    val city: City? = null,
    val model: WeatherModel = WeatherModel.Best,
    val fahrenheit: Boolean = false,
) {
    fun copy(
        auto: Boolean = this.auto,
        city: City? = this.city,
        model: WeatherModel = this.model,
        fahrenheit: Boolean = this.fahrenheit,
    ) = WeatherPrefs(auto, city, model, fahrenheit)
}

class WeatherNow(val temp: Int, val feels: Int, val code: Int, val isDay: Boolean, val humidity: Int, val wind: Int)
class WeatherDay(val label: String, val code: Int, val hi: Int, val lo: Int, val rain: Int)

class WeatherData(
    val city: City,
    /** How the place was found: "TV location", "Internet address" or "Picked by you" */
    val how: String,
    val model: WeatherModel,
    val now: WeatherNow,
    val days: List<WeatherDay>,
    val fahrenheit: Boolean,
    val note: String,
    val at: Long,
)

private const val SEP = "\u001F"

fun loadWeatherPrefs(p: SharedPreferences): WeatherPrefs {
    val city = p.getString("w_city", null)?.split(SEP)?.let { f ->
        if (f.size == 5) {
            val lat = f[3].toDoubleOrNull()
            val lon = f[4].toDoubleOrNull()
            if (lat != null && lon != null) City(f[0], f[1], f[2], lat, lon) else null
        } else {
            null
        }
    }
    val model = WeatherModel.values().firstOrNull { it.name == p.getString("w_model", null) } ?: WeatherModel.Best
    return WeatherPrefs(
        auto = p.getBoolean("w_auto", true) || city == null,
        city = city,
        model = model,
        fahrenheit = p.getBoolean("w_f", false),
    )
}

fun saveWeatherPrefs(p: SharedPreferences, w: WeatherPrefs) {
    p.edit()
        .putBoolean("w_auto", w.auto)
        .putString("w_model", w.model.name)
        .putBoolean("w_f", w.fahrenheit)
        .also { e ->
            val c = w.city
            if (c == null) e.remove("w_city")
            else e.putString("w_city", listOf(c.name, c.region, c.country, c.lat.toString(), c.lon.toString()).joinToString(SEP))
        }
        .apply()
}

// ---------- Network ----------

private fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 10000
    c.setRequestProperty("User-Agent", "UnwindLauncher/2.3 (Android TV)")
    c.setRequestProperty("Accept", "application/json")
    try {
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

private class Located(val city: City, val how: String)

@SuppressLint("MissingPermission")
private fun lastKnownLocation(ctx: Context): Pair<Double, Double>? {
    val ok = hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || hasPerm(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!ok) return null
    return try {
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (best == null) null else best.latitude to best.longitude
    } catch (e: Exception) {
        null
    }
}

private fun nameFor(lat: Double, lon: Double): City? {
    val j = JSONObject(
        httpGet("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"),
    )
    val name = j.optString("city").ifBlank { j.optString("locality") }.ifBlank { j.optString("principalSubdivision") }
    if (name.isBlank()) return null
    return City(name, j.optString("principalSubdivision"), j.optString("countryName"), lat, lon)
}

private fun cityFromIp(): City? {
    runCatching {
        val j = JSONObject(httpGet("https://ipwho.is/"))
        if (j.optBoolean("success", true) && j.has("latitude")) {
            val name = j.optString("city").ifBlank { j.optString("region") }.ifBlank { j.optString("country") }
            return City(name, j.optString("region"), j.optString("country"), j.getDouble("latitude"), j.getDouble("longitude"))
        }
    }
    runCatching {
        val j = JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json"))
        val name = j.optString("city").ifBlank { j.optString("region") }.ifBlank { j.optString("country") }
        return City(name, j.optString("region"), j.optString("country"), j.getString("latitude").toDouble(), j.getString("longitude").toDouble())
    }
    return null
}

/** Works out where the TV is: its own location if Android already allows that, otherwise the internet address. */
private fun locateTv(ctx: Context): Located? {
    lastKnownLocation(ctx)?.let { (lat, lon) ->
        val c = runCatching { nameFor(lat, lon) }.getOrNull()
        if (c != null) return Located(c, "TV location")
    }
    val ip = cityFromIp() ?: return null
    return Located(ip, "Internet address")
}

fun searchCities(query: String): List<City> {
    val q = URLEncoder.encode(query.trim(), "UTF-8")
    val j = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json"))
    val arr = j.optJSONArray("results") ?: return emptyList()
    val out = ArrayList<City>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out += City(o.optString("name"), o.optString("admin1"), o.optString("country"), o.getDouble("latitude"), o.getDouble("longitude"))
    }
    return out
}

private fun forecastUrl(c: City, model: WeatherModel, f: Boolean): String {
    val sb = StringBuilder("https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}")
    sb.append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m")
    sb.append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
    sb.append("&timezone=auto&forecast_days=5")
    if (f) sb.append("&temperature_unit=fahrenheit&wind_speed_unit=mph")
    model.param?.let { sb.append("&models=").append(it) }
    return sb.toString()
}

private fun parseForecast(json: String): Pair<WeatherNow, List<WeatherDay>> {
    val j = JSONObject(json)
    val cur = j.getJSONObject("current")
    if (cur.isNull("temperature_2m")) throw IOException("No data from this model")
    val t = cur.getDouble("temperature_2m")
    val now = WeatherNow(
        temp = t.roundToInt(),
        feels = cur.optDouble("apparent_temperature", t).roundToInt(),
        code = cur.optInt("weather_code", 0),
        isDay = cur.optInt("is_day", 1) == 1,
        humidity = cur.optInt("relative_humidity_2m", 0),
        wind = cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
    )
    val d = j.getJSONObject("daily")
    val times = d.getJSONArray("time")
    val codes = d.getJSONArray("weather_code")
    val hi = d.getJSONArray("temperature_2m_max")
    val lo = d.getJSONArray("temperature_2m_min")
    val rain = d.optJSONArray("precipitation_probability_max")
    val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val show = SimpleDateFormat("EEE", Locale.getDefault())
    val days = ArrayList<WeatherDay>()
    for (i in 0 until times.length()) {
        val label = if (i == 0) {
            "Today"
        } else {
            runCatching { show.format(parse.parse(times.getString(i))!!) }.getOrDefault(times.getString(i))
        }
        days += WeatherDay(
            label = label,
            code = codes.optInt(i, 0),
            hi = hi.optDouble(i, 0.0).roundToInt(),
            lo = lo.optDouble(i, 0.0).roundToInt(),
            rain = rain?.optInt(i, 0) ?: 0,
        )
    }
    return now to days
}

/** Blocking: call from a background thread. Throws if nothing can be shown. */
fun loadWeather(ctx: Context, p: WeatherPrefs): WeatherData {
    val how: String
    val city: City
    val picked = p.city
    if (p.auto || picked == null) {
        val loc = locateTv(ctx) ?: throw IOException("Couldn't work out where this TV is. Pick a city instead.")
        city = loc.city
        how = loc.how
    } else {
        city = picked
        how = "Picked by you"
    }
    fun attempt(m: WeatherModel) = runCatching { parseForecast(httpGet(forecastUrl(city, m, p.fahrenheit))) }.getOrNull()
    var used = p.model
    var note = ""
    var res = attempt(p.model)
    if (res == null && p.model != WeatherModel.Best) {
        res = attempt(WeatherModel.Best)
        used = WeatherModel.Best
        note = "${p.model.label} had no data here, so this is Best match."
    }
    if (res == null) throw IOException("Couldn't reach the weather service. Check the internet connection.")
    return WeatherData(city, how, used, res.first, res.second, p.fahrenheit, note, System.currentTimeMillis())
}

fun describeWeather(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80 -> "Light showers"
    81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Cloudy"
}

// ---------- Weather glyphs (drawn, so they take the wallpaper's accent and need no icon font) ----------

private enum class Sky { Clear, Partly, Cloud, Fog, Drizzle, Rain, Snow, Storm }

private fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.Clear
    2 -> Sky.Partly
    3 -> Sky.Cloud
    45, 48 -> Sky.Fog
    in 51..57 -> Sky.Drizzle
    in 61..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloud
}

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color, rays: Boolean = true) {
    drawCircle(color, r, c)
    if (!rays) return
    val sw = r * 0.34f
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            color,
            Offset(c.x + ca * r * 1.5f, c.y + sa * r * 1.5f),
            Offset(c.x + ca * r * 1.95f, c.y + sa * r * 1.95f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(c: Offset, r: Float, color: Color) {
    val a = Path().apply { addOval(Rect(c, r)) }
    val b = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, a, b), color)
}

private fun DrawScope.drawCloud(left: Float, top: Float, w: Float, color: Color) {
    val h = w * 0.55f
    drawCircle(color, h * 0.42f, Offset(left + w * 0.28f, top + h * 0.62f))
    drawCircle(color, h * 0.55f, Offset(left + w * 0.52f, top + h * 0.45f))
    drawCircle(color, h * 0.40f, Offset(left + w * 0.76f, top + h * 0.64f))
    drawRoundRect(color, Offset(left + w * 0.14f, top + h * 0.55f), Size(w * 0.74f, h * 0.46f), CornerRadius(h * 0.23f))
}

@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val accent = LocalUi.current.wp.accent
    val cloud = Color.White.copy(alpha = 0.94f)
    val drop = lerp(accent, Color.White, 0.25f)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (skyOf(code)) {
            Sky.Clear -> if (isDay) drawSun(Offset(s * 0.5f, s * 0.5f), s * 0.2f, accent)
            else drawMoon(Offset(s * 0.5f, s * 0.5f), s * 0.3f, accent)
            Sky.Partly -> {
                if (isDay) drawSun(Offset(s * 0.66f, s * 0.34f), s * 0.15f, accent)
                else drawMoon(Offset(s * 0.66f, s * 0.34f), s * 0.2f, accent)
                drawCloud(s * 0.04f, s * 0.30f, s * 0.9f, cloud)
            }
            Sky.Cloud -> drawCloud(s * 0.05f, s * 0.22f, s * 0.9f, cloud)
            Sky.Fog -> {
                drawCloud(s * 0.08f, s * 0.12f, s * 0.84f, cloud.copy(alpha = 0.85f))
                for (i in 0 until 3) {
                    val y = s * (0.66f + i * 0.12f)
                    drawLine(cloud, Offset(s * (0.18f + 0.04f * i), y), Offset(s * (0.82f - 0.04f * i), y), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            Sky.Drizzle, Sky.Rain -> {
                drawCloud(s * 0.05f, s * 0.10f, s * 0.9f, cloud)
                val n = if (skyOf(code) == Sky.Rain) 3 else 2
                for (i in 0 until n) {
                    val x = s * (0.30f + i * (if (n == 3) 0.2f else 0.28f))
                    drawLine(drop, Offset(x, s * 0.68f), Offset(x - s * 0.06f, s * 0.88f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                }
            }
            Sky.Snow -> {
                drawCloud(s * 0.05f, s * 0.10f, s * 0.9f, cloud)
                for (i in 0 until 3) {
                    drawCircle(drop, s * 0.055f, Offset(s * (0.30f + i * 0.2f), s * (0.74f + (i % 2) * 0.1f)))
                }
            }
            Sky.Storm -> {
                drawCloud(s * 0.05f, s * 0.08f, s * 0.9f, cloud)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f)
                    lineTo(s * 0.40f, s * 0.80f)
                    lineTo(s * 0.50f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.97f)
                    lineTo(s * 0.64f, s * 0.72f)
                    lineTo(s * 0.53f, s * 0.72f)
                    close()
                }
                drawPath(bolt, accent)
            }
        }
    }
}

// ---------- The small chip under the page title ----------

@Composable
fun WeatherChip(data: WeatherData?, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, animationSpec = LiquidSpring, label = "chip")
    val shape = ui.settings.shape.toBarShape()
    Row(
        modifier
            .height(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(shape, focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherGlyph(data?.now?.code ?: 2, data?.now?.isDay ?: true, 22.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            if (data != null) "${data.now.temp}\u00B0" else "--",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------- The popup ----------

@Composable
private fun DayColumn(d: WeatherDay, modifier: Modifier) {
    val ui = LocalUi.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.label, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        WeatherGlyph(d.code, true, 30.dp)
        Spacer(Modifier.height(4.dp))
        Text("${d.hi}\u00B0", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("${d.lo}\u00B0", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(
            if (d.rain >= 20) "${d.rain}%" else " ",
            color = ui.wp.accent,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Current weather, a 5 day outlook, and the choices: which city, which forecast model, which unit.
 * Changing city uses the TV's own on-screen keyboard.
 */
@Composable
fun WeatherPopup(
    data: WeatherData?,
    error: String?,
    prefs: WeatherPrefs,
    locOk: Boolean,
    focus: FocusRequester,
    onPrefs: (WeatherPrefs) -> Unit,
    onGrantLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ui = LocalUi.current
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<City>()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    BackHandler(enabled = picking) { picking = false }
    LaunchedEffect(picking) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(query) {
        searchFailed = false
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        val r = withContext(Dispatchers.IO) { runCatching { searchCities(query) } }
        results = r.getOrDefault(emptyList())
        searchFailed = r.isFailure
        searching = false
    }

    PopupPanel(if (picking) "Choose a city" else "Weather", 400.dp) {
        if (!picking) {
            if (data != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(data.now.code, data.now.isDay, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${data.now.temp}\u00B0", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Medium)
                        Text(describeWeather(data.now.code), color = ui.wp.accent, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(data.city.full(), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val unit = if (data.fahrenheit) "mph" else "km/h"
                Text(
                    "Feels like ${data.now.feels}\u00B0  \u00B7  Humidity ${data.now.humidity}%  \u00B7  Wind ${data.now.wind} $unit",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                if (data.note.isNotBlank()) {
                    Text(data.note, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    data.days.forEach { d -> DayColumn(d, Modifier.weight(1f)) }
                }
            } else {
                Text(
                    error ?: "Getting the weather...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            PopupRow(
                "Change city",
                subtitle = if (prefs.auto) "Now: detected automatically" + (data?.let { " (${it.how.lowercase()})" } ?: "") else "Now: ${data?.city?.label() ?: prefs.city?.label() ?: ""}",
                icon = Icons.Rounded.LocationOn,
                modifier = Modifier.focusRequester(focus),
                onClick = { picking = true },
            )
            Spacer(Modifier.height(4.dp))
            PopupRow(
                "Weather source",
                subtitle = "${prefs.model.label}: ${prefs.model.blurb}",
                icon = Icons.Rounded.Cloud,
                onClick = {
                    val all = WeatherModel.values()
                    onPrefs(prefs.copy(model = all[(prefs.model.ordinal + 1) % all.size]))
                },
            )
            Spacer(Modifier.height(4.dp))
            PopupRow(
                "Units",
                subtitle = if (prefs.fahrenheit) "Fahrenheit (\u00B0F, mph)" else "Celsius (\u00B0C, km/h)",
                icon = Icons.Rounded.Tune,
                onClick = { onPrefs(prefs.copy(fahrenheit = !prefs.fahrenheit)) },
            )
            if (prefs.auto && !locOk) {
                Spacer(Modifier.height(4.dp))
                PopupRow(
                    "Use the TV's location",
                    subtitle = "More precise than the internet address. Needs permission.",
                    icon = Icons.Rounded.LocationOn,
                    onClick = onGrantLocation,
                )
            }
            Spacer(Modifier.height(4.dp))
            PopupRow("Refresh", icon = Icons.Rounded.Refresh, onClick = onRefresh)
        } else {
            SystemTextField(
                value = query,
                onValueChange = { query = it },
                hint = "Type a city. Press OK to start.",
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            val q = query.trim()
            when {
                searching -> Text("Searching...", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                searchFailed -> Text("Couldn't search. Check the internet connection.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                q.length >= 2 && results.isEmpty() -> Text("No cities found for \"$q\".", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
            if (results.isNotEmpty()) {
                Column(
                    Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.forEach { c ->
                        PopupRow(
                            c.name,
                            subtitle = listOf(c.region, c.country).filter { it.isNotBlank() }.joinToString(", "),
                            icon = Icons.Rounded.LocationOn,
                            onClick = {
                                onPrefs(prefs.copy(auto = false, city = c))
                                query = ""
                                picking = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            PopupRow(
                "Detect automatically",
                subtitle = "Use where this TV is",
                icon = Icons.Rounded.LocationOn,
                highlighted = prefs.auto,
                onClick = {
                    onPrefs(prefs.copy(auto = true))
                    query = ""
                    picking = false
                },
            )
            Spacer(Modifier.height(4.dp))
            PopupRow("Back to weather", icon = Icons.Rounded.Close, onClick = { picking = false })
        }
    }
}
