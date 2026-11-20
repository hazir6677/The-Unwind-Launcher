package com.unwind.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/*
 * Home widgets.
 *
 * They are deliberately bare: no panel, no card, no background. Just thin white type and shapes with a soft
 * shadow, drawn straight on the wallpaper the way widgets look on an iPhone lock screen, only larger so they
 * read from the couch. The scrims behind Home already keep white text readable on bright pictures.
 *
 * Each widget is drawn at a natural size and scaled down (never up) to fit whatever height Home has left above
 * the app row, so they adapt to any TV and to the Stacked layout.
 */

private val Lift = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 14f))

private const val CAL_H = 236f
private const val CLOCK_DIGITAL_H = 150f
private const val CLOCK_ANALOG_H = 206f
private const val WEATHER_H = 190f
private const val DATE_H = 180f

/** Natural height of a widget (in dp) before it is shrunk to fit. */
private fun naturalHeight(w: HomeWidget, face: ClockFace): Float = when (w) {
    HomeWidget.Calendar -> CAL_H
    HomeWidget.Clock -> if (face == ClockFace.Analog) CLOCK_ANALOG_H else CLOCK_DIGITAL_H
    HomeWidget.Weather -> WEATHER_H
    HomeWidget.Date -> DATE_H
    else -> 120f
}

/** The widget slot on Home. Greetings keeps its own sizing; the others scale to the space they are given. */
@Composable
fun HomeWidgetView(widget: HomeWidget, greeting: Greeting, weather: WeatherData?) {
    val ui = LocalUi.current
    when (widget) {
        HomeWidget.Greetings -> GreetingText(greeting)
        HomeWidget.None -> {}
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val s = (maxHeight.value / naturalHeight(widget, ui.settings.clockFace)).coerceIn(0.5f, 1f)
            when (widget) {
                HomeWidget.Calendar -> CalendarWidget(s)
                HomeWidget.Clock -> ClockWidget(ui.settings.clockFace, s)
                HomeWidget.Weather -> WeatherWidget(weather, s)
                HomeWidget.Date -> DateWidget(s)
                else -> {}
            }
        }
    }
}

// ---------- Ticking ----------

/** Wall-clock time that refreshes every [everyMs]. */
@Composable
private fun rememberNow(everyMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everyMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(everyMs)
        }
    }
    return now
}

// ---------- Month calendar ----------

@Composable
private fun CalendarWidget(s: Float) {
    val ui = LocalUi.current
    val now = rememberNow(60_000L)
    // Rebuilt only when the calendar day changes, not every minute
    val dayId = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    val info = remember(dayId) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val fdow = cal.firstDayOfWeek
        val lead = (first.get(Calendar.DAY_OF_WEEK) - fdow + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sym = DateFormatSymbols.getInstance().shortWeekdays
        CalInfo(
            title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time),
            today = cal.get(Calendar.DAY_OF_MONTH),
            lead = lead,
            days = days,
            weekdays = List(7) { c -> sym[((fdow - 1 + c) % 7) + 1].take(1).uppercase() },
        )
    }
    val cell = (32f * s).dp
    val shape = ui.settings.shape.toShape()
    val weeks = (info.lead + info.days + 6) / 7

    Column {
        Text(
            info.title,
            color = ui.wp.accent,
            fontSize = (17f * s).sp,
            fontWeight = FontWeight.Medium,
            style = Lift,
        )
        Spacer(Modifier.height((8f * s).dp))
        Row {
            for (c in 0..6) {
                Box(Modifier.size(cell, (22f * s).dp), contentAlignment = Alignment.Center) {
                    Text(
                        info.weekdays[c],
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = (12f * s).sp,
                        fontWeight = FontWeight.Medium,
                        style = Lift,
                    )
                }
            }
        }
        for (r in 0 until weeks) {
            Row {
                for (c in 0..6) {
                    val n = r * 7 + c - info.lead + 1
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (n in 1..info.days) {
                            val isToday = n == info.today
                            if (isToday) {
                                // a see-through marker in the chosen icon shape
                                Box(
                                    Modifier
                                        .size(cell * 0.92f)
                                        .background(ui.wp.accent.copy(alpha = 0.40f), shape)
                                        .border(1.5.dp, ui.wp.accent.copy(alpha = 0.9f), shape),
                                )
                            }
                            Text(
                                n.toString(),
                                color = Color.White.copy(alpha = if (isToday) 1f else 0.9f),
                                fontSize = (14f * s).sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                style = Lift,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class CalInfo(val title: String, val today: Int, val lead: Int, val days: Int, val weekdays: List<String>)

// ---------- Clock ----------

@Composable
private fun ClockWidget(face: ClockFace, s: Float) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val analog = face == ClockFace.Analog
    val now = rememberNow(if (analog) 1_000L else 10_000L)
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(ctx) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    if (!analog) {
        val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()) }
        val ampmFmt = remember { SimpleDateFormat("a", Locale.getDefault()) }
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    timeFmt.format(Date(now)),
                    color = Color.White,
                    fontSize = (96f * s).sp,
                    lineHeight = (100f * s).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-2f * s).sp,
                    style = Lift,
                )
                if (!is24) {
                    Spacer(Modifier.width((10f * s).dp))
                    Text(
                        ampmFmt.format(Date(now)).uppercase(),
                        color = ui.wp.accent,
                        fontSize = (22f * s).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = (18f * s).dp),
                        style = Lift,
                    )
                }
            }
            Text(dateFmt.format(Date(now)), color = Color.White.copy(alpha = 0.9f), fontSize = (17f * s).sp, style = Lift)
        }
    } else {
        val accent = ui.wp.accent
        val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
        val h = cal.get(Calendar.HOUR) // 0..11
        val m = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        Column {
            Canvas(Modifier.size((180f * s).dp)) {
                val c = center
                val r = size.minDimension / 2f
                val hair = Color.White
                drawCircle(hair.copy(alpha = 0.5f), radius = r - 1.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                // hour ticks, longer at 12, 3, 6 and 9
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val long = i % 3 == 0
                    val outer = r * 0.88f
                    val inner = outer - r * (if (long) 0.13f else 0.07f)
                    drawLine(
                        hair.copy(alpha = if (long) 0.95f else 0.6f),
                        Offset(c.x + outer * sin(a).toFloat(), c.y - outer * cos(a).toFloat()),
                        Offset(c.x + inner * sin(a).toFloat(), c.y - inner * cos(a).toFloat()),
                        strokeWidth = (if (long) 2.5.dp else 1.5.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                fun hand(deg: Float, len: Float, tail: Float, w: Float, color: Color) {
                    val a = Math.toRadians(deg.toDouble())
                    val dx = sin(a).toFloat()
                    val dy = -cos(a).toFloat()
                    drawLine(
                        color,
                        Offset(c.x - dx * r * tail, c.y - dy * r * tail),
                        Offset(c.x + dx * r * len, c.y + dy * r * len),
                        strokeWidth = w,
                        cap = StrokeCap.Round,
                    )
                }
                hand((h + m / 60f + sec / 3600f) * 30f, 0.50f, 0.0f, 4.5.dp.toPx(), hair)
                hand((m + sec / 60f) * 6f, 0.76f, 0.0f, 3.dp.toPx(), hair)
                hand(sec * 6f, 0.84f, 0.16f, 1.5.dp.toPx(), accent)
                drawCircle(accent, radius = 5.dp.toPx(), center = c)
            }
            Spacer(Modifier.height((8f * s).dp))
            Text(dateFmt.format(Date(now)), color = Color.White.copy(alpha = 0.9f), fontSize = (15f * s).sp, style = Lift)
        }
    }
}

// ---------- Weather ----------

@Composable
private fun WeatherWidget(data: WeatherData?, s: Float) {
    val ui = LocalUi.current
    if (data == null) {
        Text("Getting the weather...", color = Color.White.copy(alpha = 0.85f), fontSize = (17f * s).sp, style = Lift)
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.now.code, data.now.isDay, (76f * s).dp)
            Spacer(Modifier.width((14f * s).dp))
            Column {
                Text(
                    "${data.now.temp}\u00B0",
                    color = Color.White,
                    fontSize = (74f * s).sp,
                    lineHeight = (76f * s).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    style = Lift,
                )
                Text(
                    data.city.label() + "  \u00B7  feels like ${data.now.feels}\u00B0",
                    color = ui.wp.accent,
                    fontSize = (15f * s).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Lift,
                )
            }
        }
        Spacer(Modifier.height((14f * s).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((22f * s).dp)) {
            for (d in data.days.take(4)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.label, color = Color.White.copy(alpha = 0.78f), fontSize = (12f * s).sp, style = Lift)
                    WeatherGlyph(d.code, true, (28f * s).dp)
                    Text("${d.hi}\u00B0", color = Color.White, fontSize = (14f * s).sp, fontWeight = FontWeight.Medium, style = Lift)
                    Text("${d.lo}\u00B0", color = Color.White.copy(alpha = 0.6f), fontSize = (12f * s).sp, style = Lift)
                }
            }
        }
    }
}

// ---------- Date ----------

@Composable
private fun DateWidget(s: Float) {
    val ui = LocalUi.current
    val now = rememberNow(60_000L)
    val day = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val weekday = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val month = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val d = Date(now)
    Column {
        Text(
            weekday.format(d).uppercase(),
            color = ui.wp.accent,
            fontSize = (16f * s).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (3f * s).sp,
            style = Lift,
        )
        Text(
            day.format(d),
            color = Color.White,
            fontSize = (118f * s).sp,
            lineHeight = (120f * s).sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-3f * s).sp,
            style = Lift,
        )
        Text(month.format(d), color = Color.White.copy(alpha = 0.9f), fontSize = (20f * s).sp, style = Lift)
    }
}

// ---------- Preferences tab ----------

@Composable
fun WidgetsTab(
    settings: LauncherSettings,
    nav: Nav,
    rows: PrefRows,
    onEditGreeting: (DayPart) -> Unit,
    onChange: (LauncherSettings) -> Unit,
) {
    val ui = LocalUi.current
    SectionTitle("Home widget", "What sits above your apps. Every widget is see-through, so your wallpaper stays in view.")
    HomeWidget.values().toList().chunked(3).forEachIndexed { ri, group ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            group.forEachIndexed { ci, w ->
                OptionCard(
                    selected = settings.homeWidget == w,
                    w = 232.dp,
                    h = 78.dp,
                    modifier = Modifier.prefItem(rows, nav, ri, ci),
                    onClick = { onChange(settings.copy(homeWidget = w)) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp)) {
                        Text(w.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            w.blurb,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    when (settings.homeWidget) {
        HomeWidget.Clock -> {
            Spacer(Modifier.height(10.dp))
            SectionTitle("Clock style", "Digital or analog. The small clock in the top bar hides while the clock widget is on.")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ClockFace.values().forEachIndexed { i, f ->
                    OptionCard(
                        selected = settings.clockFace == f,
                        w = 150.dp,
                        h = 56.dp,
                        modifier = Modifier.prefItem(rows, nav, 2, i),
                        onClick = { onChange(settings.copy(clockFace = f)) },
                    ) {
                        Text(f.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        HomeWidget.Greetings -> {
            Spacer(Modifier.height(10.dp))
            SectionTitle("Greetings", "Change the words on Home for each part of the day")
            val nowPart = dayPartFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DayPart.values().forEachIndexed { gi, dp ->
                    val g = settings.greetings[dp] ?: DEFAULT_GREETINGS.getValue(dp)
                    OptionCard(
                        selected = dp == nowPart,
                        w = 180.dp,
                        h = 84.dp,
                        modifier = Modifier.prefItem(rows, nav, 2, gi),
                        onClick = { onEditGreeting(dp) },
                    ) {
                        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)) {
                            Text(
                                if (dp == nowPart) "${dp.label} \u00B7 now" else dp.label,
                                color = ui.wp.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${g.line1} ${g.line2}".trim().ifEmpty { g.top },
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(dp.hours, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        else -> {}
    }
}
