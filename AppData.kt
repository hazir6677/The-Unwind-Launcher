package com.unwind.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AppEntry(
    val pkg: String,
    val label: String,
    val intent: Intent,
    val icon: ImageBitmap,
    val mask: ImageBitmap?, // white glyph with alpha, cropped tight; null when we couldn't isolate one
    val bg: Color,          // the icon's own background color (Transparent if it has none)
)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val out = LinkedHashMap<String, AppEntry>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || out.containsKey(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val bmp = ri.loadIcon(pm).toBitmap(N, N)
            val g = extractGlyph(bmp)
            out[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), launch, bmp.asImageBitmap(), g.mask, g.bg)
        }
    }
    return out.values.sortedBy { it.label.lowercase() }
}

fun tryStart(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
    Toast.makeText(ctx, "Couldn't open that on this TV", Toast.LENGTH_SHORT).show()
}

fun launchApp(ctx: Context, app: AppEntry) {
    try {
        ctx.startActivity(app.intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun openSystemSettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_SETTINGS))
fun openWifiSettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
fun openAccountSettings(ctx: Context) =
    tryStart(ctx, Intent(Settings.ACTION_SYNC_SETTINGS), Intent(Settings.ACTION_SETTINGS))

// ---------- Turning an app icon into a tintable glyph ----------

private const val N = 128

private class Glyph(val mask: ImageBitmap?, val bg: Color)

private fun dist(a: Int, b: Int): Float {
    val dr = ((a shr 16) and 255) - ((b shr 16) and 255)
    val dg = ((a shr 8) and 255) - ((b shr 8) and 255)
    val db = (a and 255) - (b and 255)
    return sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.67f
}

private fun lum(c: Int): Float =
    (0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)) / 255f

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Separates the logo from the icon's background so it can be re-tinted. Works on solid backgrounds
 * (white YouTube/Netflix tiles, blue Prime tile), gradients with a light logo, and transparent icons.
 * If it can't find a clean glyph it returns mask = null and the UI falls back to the original icon.
 */
private fun extractGlyph(bmp: Bitmap): Glyph {
    val px = IntArray(N * N)
    bmp.getPixels(px, 0, N, 0, 0, N, N)
    val border = ArrayList<Int>()
    var transparent = 0
    var total = 0
    for (y in 0 until N) for (x in 0 until N) {
        if (x < 5 || y < 5 || x >= N - 5 || y >= N - 5) {
            total++
            val p = px[y * N + x]
            if ((p ushr 24) < 40) transparent++ else border.add(p)
        }
    }
    val ink = FloatArray(N * N)
    var bgColor = Color.Transparent
    if (transparent > total / 2 || border.isEmpty()) {
        for (i in ink.indices) ink[i] = (px[i] ushr 24) / 255f
    } else {
        fun median(shift: Int): Int = border.map { (it shr shift) and 255 }.sorted()[border.size / 2]
        val bg = (0xFF shl 24) or (median(16) shl 16) or (median(8) shl 8) or median(0)
        bgColor = Color(bg)
        val spread = border.map { dist(it, bg) }.average().toFloat()
        val lb = lum(bg)
        for (i in ink.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            ink[i] = when {
                a < 0.16f -> 0f
                spread < 0.09f -> smooth(0.10f, 0.28f, dist(p, bg)) * a
                lb < 0.6f -> smooth(0.18f, 0.40f, lum(p) - lb) * a
                else -> smooth(0.18f, 0.40f, lb - lum(p)) * a
            }
        }
    }
    var minX = N
    var minY = N
    var maxX = -1
    var maxY = -1
    var count = 0
    for (y in 0 until N) for (x in 0 until N) {
        if (ink[y * N + x] > 0.35f) {
            count++
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
    }
    val coverage = count / (N * N).toFloat()
    if (count == 0 || coverage > 0.6f || coverage < 0.01f) return Glyph(null, bgColor)

    minX = max(0, minX - 2); minY = max(0, minY - 2)
    maxX = min(N - 1, maxX + 2); maxY = min(N - 1, maxY + 2)
    val w = maxX - minX + 1
    val h = maxY - minY + 1
    val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val a = (ink[(y + minY) * N + (x + minX)] * 255f).toInt().coerceIn(0, 255)
        out[y * w + x] = (a shl 24) or 0xFFFFFF
    }
    val mb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    mb.setPixels(out, 0, w, 0, 0, w, h)
    return Glyph(mb.asImageBitmap(), bgColor)
}
