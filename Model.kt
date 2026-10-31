package com.unwind.launcher

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin

enum class GlassStyle(val label: String, val blurb: String) {
    Liquid("Liquid Glass", "Clear, bright edges"),
    Normal("Normal", "Tinted, soft blur"),
    Frost("Frost Glass", "Heavy blur, milky"),
}

enum class IconShape(val label: String) {
    Circle("Circle"), Squircle("Squircle"), Rounded("Rounded"), Hexagon("Hexagon"),
}

enum class IconStyle(val label: String, val blurb: String) {
    Themed("Themed", "Tinted to your wallpaper"),
    Original("Original", "Full-color app icons"),
}

enum class Screen { Home, Apps, Edit, Search, Settings }
enum class Popup { None, Wifi, Inputs, Account }

private val HexShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(cx, cy)
    for (i in 0 until 6) {
        val a = Math.toRadians((60 * i - 90).toDouble())
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun IconShape.toShape(): Shape = when (this) {
    IconShape.Circle -> CircleShape
    IconShape.Squircle -> RoundedCornerShape(percent = 34)
    IconShape.Rounded -> RoundedCornerShape(percent = 20)
    IconShape.Hexagon -> HexShape
}

/** A wallpaper plus the colors the UI borrows from it. */
class Wp(val key: String, val name: String, val accent: Color, val tint: Color, val dark: Color)

val WALLPAPERS = listOf(
    Wp("wp_misty", "Misty Valley", Color(0xFFC5E1A5), Color(0xFF3E6B3C), Color(0xFF1E3322)),
    Wp("wp_emerald", "Emerald Lake", Color(0xFFCDEBC0), Color(0xFF3F6B4A), Color(0xFF1C3324)),
    Wp("wp_golden", "Golden Hour", Color(0xFFFFE2A8), Color(0xFF7A5524), Color(0xFF3A2710)),
    Wp("wp_fog", "Morning Fog", Color(0xFFF3F9EE), Color(0xFF55695E), Color(0xFF26342C)),
    Wp("wp_dusk", "Dusk", Color(0xFFE7D5FA), Color(0xFF5A4A7A), Color(0xFF2A2040)),
    Wp("wp_night", "Night Canopy", Color(0xFF9FE3D0), Color(0xFF1D4A47), Color(0xFF0B2220)),
)

data class LauncherSettings(
    val wallpaper: Int = 0,
    val shape: IconShape = IconShape.Circle,
    val showNames: Boolean = true,
    val iconStyle: IconStyle = IconStyle.Themed,
    val glass: GlassStyle = GlassStyle.Normal,
)

private inline fun <reified T : Enum<T>> parseEnum(s: String?, def: T): T =
    enumValues<T>().firstOrNull { it.name == s } ?: def

fun loadSettings(ctx: Context): LauncherSettings {
    val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    val d = LauncherSettings()
    return LauncherSettings(
        wallpaper = p.getInt("wallpaper", d.wallpaper),
        shape = parseEnum(p.getString("shape", null), d.shape),
        showNames = p.getBoolean("showNames", d.showNames),
        iconStyle = parseEnum(p.getString("iconStyle", null), d.iconStyle),
        glass = parseEnum(p.getString("glass", null), d.glass),
    )
}

fun saveSettings(ctx: Context, s: LauncherSettings) {
    ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
        .putInt("wallpaper", s.wallpaper)
        .putString("shape", s.shape.name)
        .putBoolean("showNames", s.showNames)
        .putString("iconStyle", s.iconStyle.name)
        .putString("glass", s.glass.name)
        .apply()
}

/** Everything the glass/theme code needs, provided once at the root. */
class UiCtx(
    val settings: LauncherSettings,
    val wp: Wp,
    val soft: ImageBitmap?,
    val heavy: ImageBitmap?,
    val size: IntSize,
)

val LocalUi = compositionLocalOf<UiCtx> { error("UiCtx not provided") }
