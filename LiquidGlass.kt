package com.unwind.launcher

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Liquid Glass, Compose side. The math lives in LiquidCore.kt (pure Kotlin, tested on a JVM); this file:
 *  - measures where each glass element is,
 *  - decides its foreground color from the wallpaper behind it (per element, with hysteresis),
 *  - bakes the refracted glass on a background thread and caches it,
 *  - cross-fades between bakes and foreground colors,
 *  - hands the foreground color to whatever is drawn on the glass (see inkFg / inkAccent).
 */

// ---------- What content on glass should use ----------

/** The adaptive foreground for one glass element. Null (no provider) means "use the original colors". */
@Immutable
class GlassInk(val fg: Color, val accent: Color, val shadow: Float)

val LocalGlassInk = compositionLocalOf<GlassInk?> { null }

/** A black overlay that dialogs draw between the wallpaper and their glass (0..1). */
val LocalGlassDim = compositionLocalOf { 0f }

/** Text or icons under this alpha would drop below the contrast target, so on adaptive glass it is raised to this. */
private const val INK_MIN_ALPHA = 0.88f

/** Use for white text/icons on glass. On Liquid glass it flips between white and near-black as needed. */
@Composable
fun inkFg(default: Color = Color.White): Color {
    val ink = LocalGlassInk.current ?: return default
    return ink.fg.copy(alpha = maxOf(default.alpha, INK_MIN_ALPHA))
}

/** Use for accent-colored text/icons on glass: keeps the hue, shifts lightness until it is readable. */
@Composable
fun inkAccent(default: Color): Color {
    val ink = LocalGlassInk.current ?: return default
    return ink.accent.copy(alpha = maxOf(default.alpha, INK_MIN_ALPHA))
}

// ---------- Per-element state ----------

@Stable
class GlassNode {
    var pos by mutableStateOf(IntOffset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var choice by mutableStateOf<FgChoice?>(null)
}

private class Baked(val image: ImageBitmap, val pad: Int)

@Stable
private class LiquidLook {
    var cur by mutableStateOf<Baked?>(null)
    var prev by mutableStateOf<Baked?>(null)
    val fade = Animatable(1f)
    var prevLight: Boolean? = null
}

private object LiquidCache {
    val lru = object : LruCache<String, Baked>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Baked): Int = value.image.width * value.image.height * 4
    }
}

private fun LgBake.toImage(): ImageBitmap {
    // the baker produces premultiplied pixels; Bitmap.setPixels wants straight alpha
    val out = IntArray(argb.size)
    for (i in argb.indices) {
        val p = argb[i]
        val a = p ushr 24
        out[i] = if (a == 0 || a == 255) {
            p
        } else {
            val r = (((p shr 16) and 255) * 255 / a).coerceAtMost(255)
            val g = (((p shr 8) and 255) * 255 / a).coerceAtMost(255)
            val b = ((p and 255) * 255 / a).coerceAtMost(255)
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, width, 0, 0, width, height)
    return bmp.asImageBitmap()
}

private fun cornerRadiusPx(shape: Shape, size: Size, dir: LayoutDirection, d: Density): Float =
    when (val o = shape.createOutline(size, dir, d)) {
        is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
        is Outline.Rectangle -> 0f
        is Outline.Generic -> min(size.width, size.height) * 0.25f // e.g. the hexagon: approximated, then clipped to its real path
    }

/**
 * Watches one glass element: where it is, what is behind it, which foreground it needs, and bakes the glass.
 * Re-runs when the wallpaper, the element's position or size, or the density changes. While an element is moving
 * (scrolling) the previous bake keeps drawing and the new one is made once it settles.
 */
@Composable
private fun rememberLiquidLook(node: GlassNode, shape: Shape, enabled: Boolean, strict: Boolean, overlayOnly: Boolean): LiquidLook {
    val ui = LocalUi.current
    val dens = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val dim = LocalGlassDim.current
    val look = remember { LiquidLook() }
    val data = ui.backdrops?.liquid
    val screen = ui.size
    LaunchedEffect(enabled, data, screen, strict, overlayOnly, dens.density, dim) {
        val dd = data
        if (!enabled || dd == null || screen.width <= 0 || screen.height <= 0) return@LaunchedEffect
        // Position and size are read through a snapshot flow, not in composition, so scrolling does not recompose.
        snapshotFlow { Pair(node.pos, node.size) }.collectLatest { (pos, sz) ->
            if (sz.width < 4 || sz.height < 4) return@collectLatest
            // while the element is moving the previous bake keeps drawing; this waits until it settles
            if (look.cur != null) delay(90)
            val d = dens.density
            val w = sz.width
            val h = sz.height
            val sizeF = Size(w.toFloat(), h.toFloat())
            val generic = shape.createOutline(sizeF, dir, dens) is Outline.Generic
            val radius = cornerRadiusPx(shape, sizeF, dir, dens)
            val bezel = (min(w, h) * LG.BEZEL_FRACTION).coerceIn(LG.BEZEL_MIN_PX * d, LG.BEZEL_MAX_PX * d)
            val x = pos.x.toFloat()
            val y = pos.y.toFloat()
            // Judge the region this glass actually covers, rim included, as the glass will show it
            val st = analyzeRegion(dd.luma, screen.width, screen.height, x, y, x + w, y + h, analysisGrow(bezel, radius), dim)
            val target = (if (strict) LG.TEXT_CONTRAST else LG.ICON_CONTRAST) + LG.SOLVER_MARGIN
            val ch = chooseForeground(st, target, look.prevLight)
            look.prevLight = ch.usesLight
            node.choice = ch

            val key = "${System.identityHashCode(dd)}|$w|$h|${radius.toInt()}|${x.toInt()}|${y.toInt()}|" +
                "${(ch.scrim * 100).toInt()}|${(ch.calm * 10).toInt()}|${ch.scrimIsDark}|${(st.mean * 24).toInt()}|" +
                "$overlayOnly|${(dim * 100).toInt()}|${(d * 100).toInt()}|$generic"
            val baked = LiquidCache.lru.get(key) ?: withContext(Dispatchers.Default) {
                val p = LgParams(
                    w = w, h = h, radius = radius, x = x, y = y,
                    screenW = screen.width, screenH = screen.height,
                    lumaMean = st.mean, density = d,
                    opaqueInterior = !overlayOnly, shadow = !generic,
                    calm = ch.calm, meanR = st.meanR, meanG = st.meanG, meanB = st.meanB,
                    scrim = ch.scrim, scrimDark = ch.scrimIsDark, dim = dim,
                )
                val bk = bakeLiquid(dd.backdrop, p)
                Baked(bk.toImage(), bk.pad)
            }.also { LiquidCache.lru.put(key, it) }

            if (look.cur !== baked) {
                val had = look.cur != null
                look.prev = look.cur
                look.cur = baked
                look.fade.snapTo(if (had) 0f else 1f)
                if (had) look.fade.animateTo(1f, tween(LG.COLOR_FADE_MS))
            }
        }
    }
    return look
}

// ---------- The modifier ----------

/**
 * Liquid Glass surface. Draws the baked refraction/shading, plus the live parts: on focus the rim brightens and the
 * focus ring swells in with the shared spring. Callers handle the scale-up (they already spring-scale on focus).
 * [overlayOnly]: draw just the edge effects and leave the middle clear (for small controls sitting on other UI).
 */
@Composable
internal fun Modifier.liquidGlass(
    shape: Shape,
    focused: Boolean,
    idle: Boolean,
    node: GlassNode,
    strict: Boolean,
    overlayOnly: Boolean,
): Modifier {
    val look = rememberLiquidLook(node, shape, idle || focused, strict, overlayOnly)
    val lift = animateFloatAsState(if (focused) 1f else 0f, animationSpec = LiquidSpring, label = "liquidLift")
    return this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            node.pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            node.size = it.size
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = outline.asPath()
            val generic = outline is Outline.Generic
            val ring = insetPath(shape, size, 1.25.dp.toPx(), layoutDirection, this)
            val rimW = 1.5.dp.toPx()
            val focusW = 2.5.dp.toPx()
            onDrawBehind {
                val p = lift.value.coerceIn(0f, 1.25f)
                if (!idle && p < 0.01f) return@onDrawBehind
                val cur = look.cur
                if (cur == null) {
                    // first bake is on its way
                    drawPath(path, Color.White.copy(alpha = 0.10f))
                } else {
                    val prev = look.prev
                    val f = look.fade.value
                    if (generic) {
                        clipPath(path) {
                            if (prev != null && f < 1f) drawImage(prev.image, topLeft = Offset(-prev.pad.toFloat(), -prev.pad.toFloat()))
                            drawImage(cur.image, topLeft = Offset(-cur.pad.toFloat(), -cur.pad.toFloat()), alpha = f)
                        }
                    } else {
                        if (prev != null && f < 1f) drawImage(prev.image, topLeft = Offset(-prev.pad.toFloat(), -prev.pad.toFloat()))
                        drawImage(cur.image, topLeft = Offset(-cur.pad.toFloat(), -cur.pad.toFloat()), alpha = f)
                    }
                }
                if (p > 0.02f) {
                    val a = min(1f, p)
                    // highlights brighten (more on the lit corners), then the focus ring
                    drawPath(
                        ring,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.95f * a),
                            0.5f to Color.White.copy(alpha = 0.20f * a),
                            1f to Color.White.copy(alpha = 0.75f * a),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        style = Stroke(width = rimW),
                    )
                    drawPath(ring, Color.White.copy(alpha = a), style = Stroke(width = focusW))
                }
            }
        }
}

// ---------- The wrapper that also hands content its foreground ----------

/**
 * A glass surface that also tells what is drawn on it which color to use. Same look as `Modifier.glass`; use this
 * when the content has text or monochrome icons (read the color with [inkFg] / [inkAccent]).
 * In Normal and Frost it simply draws the original glass and the content keeps its original colors.
 *
 * [strict] = true: text (4.5:1). false: icons and glyphs only (3:1).
 */
@Composable
fun GlassBox(
    modifier: Modifier,
    shape: Shape,
    focused: Boolean = false,
    style: GlassStyle? = null,
    blur: Boolean = true,
    idle: Boolean = true,
    strict: Boolean = true,
    overlayOnly: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    val node = remember { GlassNode() }
    val liquid = (style ?: ui.settings.glass) == GlassStyle.Liquid && ui.backdrops?.liquid != null
    Box(modifier.glass(shape, focused, style, blur, idle, node, strict, overlayOnly), contentAlignment) {
        if (liquid) {
            val ch = node.choice
            val fade = tween<Color>(LG.COLOR_FADE_MS)
            val fg by animateColorAsState(Color(ch?.fg ?: LG.FG_LIGHT), fade, label = "inkFg")
            val accentTarget = if (ch != null) {
                Color(adaptAccent(ui.wp.accent.toArgb(), ch.bgL, if (strict) LG.TEXT_CONTRAST else LG.ICON_CONTRAST))
            } else {
                ui.wp.accent
            }
            val accent by animateColorAsState(accentTarget, fade, label = "inkAccent")
            val shadow by animateFloatAsState(ch?.shadow ?: 0f, tween(LG.COLOR_FADE_MS), label = "inkShadow")
            val ink = remember(fg, accent, shadow) { GlassInk(fg, accent, shadow) }
            // a very soft shadow in the opposite color separates letters from a busy refracted background
            val textStyle = if (shadow > 0.01f) {
                val c = if (ch?.usesLight == false) Color.White else Color.Black
                TextStyle(shadow = Shadow(color = c.copy(alpha = shadow), offset = Offset(0f, 1.5f), blurRadius = 6f))
            } else {
                TextStyle.Default
            }
            CompositionLocalProvider(LocalGlassInk provides ink) {
                ProvideTextStyle(textStyle) { content() }
            }
        } else {
            content()
        }
    }
}

/** A glass surface only when [on]; otherwise a plain Box (used where the dock bar is optional). */
@Composable
fun OptGlass(
    on: Boolean,
    modifier: Modifier,
    shape: Shape,
    strict: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (on) GlassBox(modifier, shape, strict = strict, content = content) else Box(modifier, content = content)
}
