package com.unwind.launcher

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The "Clear" tint style, Compose side. The math lives in GlassCore.kt; this file:
 *  - measures where each panel is,
 *  - decides its foreground colour from the wallpaper behind it (per panel, with hysteresis),
 *  - draws the panel with plain GPU paths and gradients (no per-panel image is ever built),
 *  - hands the foreground colour to whatever is drawn on the panel (see inkFg / inkAccent).
 *
 * Why it is fast: the old renderer computed a refracted image for every panel on the CPU. This one
 * draws the same ingredients (wallpaper behind, lens-like edge, thin rim, soft depth) with a handful
 * of draw calls that the GPU handles in well under a millisecond, and it has nothing to cache or
 * warm up, so panes and popups appear on the first frame.
 */

// ---------- What content on a panel should use ----------

/** The adaptive foreground for one panel. Null (no provider) means "use the original colors". */
@Immutable
class GlassInk(val fg: Color, val accent: Color, val shadow: Float)

val LocalGlassInk = compositionLocalOf<GlassInk?> { null }

/** A black overlay that dialogs draw between the wallpaper and their panel (0..1). */
val LocalGlassDim = compositionLocalOf { 0f }

/** Text or icons under this alpha would drop below the contrast target, so on adaptive panels it is raised to this. */
private const val INK_MIN_ALPHA = 0.88f

/** Use for white text/icons on a panel. In the Clear style it flips between white and near-black as needed. */
@Composable
fun inkFg(default: Color = Color.White): Color {
    val ink = LocalGlassInk.current ?: return default
    return ink.fg.copy(alpha = maxOf(default.alpha, INK_MIN_ALPHA))
}

/** Use for accent-colored text/icons on a panel: keeps the hue, shifts lightness until it is readable. */
@Composable
fun inkAccent(default: Color): Color {
    val ink = LocalGlassInk.current ?: return default
    return ink.accent.copy(alpha = maxOf(default.alpha, INK_MIN_ALPHA))
}

// ---------- Per-panel state ----------

@Stable
class GlassNode {
    var pos by mutableStateOf(IntOffset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var choice by mutableStateOf<FgChoice?>(null)
    /** Faint dark adaptation (0..0.1) and the average backdrop colour, used by the cheap legibility layers. */
    var darkTint by mutableStateOf(0f)
    var avgColor by mutableStateOf(Color.Gray)
}

private class Memo { var prevLight: Boolean? = null }

private fun cornerRadiusPx(shape: Shape, size: Size, dir: LayoutDirection, d: Density): Float =
    when (val o = shape.createOutline(size, dir, d)) {
        is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
        is Outline.Rectangle -> 0f
        is Outline.Generic -> min(size.width, size.height) * 0.25f
    }

private fun linToSrgb(v: Float): Float = v.coerceIn(0f, 1f).pow(1f / 2.2f)

/**
 * Watches one panel: where it is, what is behind it, which foreground it needs. A few hundred
 * multiplications, no images. While the panel is moving (scrolling) it waits until it settles.
 */
@Composable
private fun rememberGlassChoice(node: GlassNode, shape: Shape, enabled: Boolean, strict: Boolean) {
    val ui = LocalUi.current
    val dens = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val dim = LocalGlassDim.current
    val memo = remember { Memo() }
    val data = ui.backdrops?.clear
    val screen = ui.size
    LaunchedEffect(enabled, data, screen, strict, dens.density, dim) {
        val dd = data
        if (!enabled || dd == null || screen.width <= 0 || screen.height <= 0) return@LaunchedEffect
        snapshotFlow { Pair(node.pos, node.size) }.collectLatest { (pos, sz) ->
            if (sz.width < 4 || sz.height < 4) return@collectLatest
            if (node.choice != null) delay(90)
            val d = dens.density
            val w = sz.width
            val h = sz.height
            val sizeF = Size(w.toFloat(), h.toFloat())
            val radius = cornerRadiusPx(shape, sizeF, dir, dens)
            val edge = (min(w, h) * LG.EDGE_FRACTION).coerceIn(LG.EDGE_MIN_DP * d, LG.EDGE_MAX_DP * d)
            val x = pos.x.toFloat()
            val y = pos.y.toFloat()
            val st = analyzeRegion(dd.luma, screen.width, screen.height, x, y, x + w, y + h, analysisGrow(edge, radius), dim)
            val target = (if (strict) LG.TEXT_CONTRAST else LG.ICON_CONTRAST) + LG.SOLVER_MARGIN
            val ch = chooseForeground(st, target, memo.prevLight)
            memo.prevLight = ch.usesLight
            node.darkTint = darkTintFor(linToSrgb(st.mean))
            node.avgColor = Color(linToSrgb(st.meanR), linToSrgb(st.meanG), linToSrgb(st.meanB))
            node.choice = ch
        }
    }
}

// ---------- Drawing ----------

private fun backdropShader(bmp: android.graphics.Bitmap, sx: Float, sy: Float, z: Float, cx: Float, cy: Float, ox: Float, oy: Float): ShaderBrush {
    val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val m = Matrix()
    m.setScale(z * sx, z * sy)
    m.postTranslate((1f - z) * cx - ox, (1f - z) * cy - oy)
    shader.setLocalMatrix(m)
    return ShaderBrush(shader)
}

/**
 * The Clear panel: the wallpaper behind it, a faint tint, a soft sheen and a hint of
 * depth below. On focus the rim brightens and the focus ring swells in with the shared spring.
 * [overlayOnly]: draw nothing but the focus ring (small controls on other UI).
 */
@Composable
internal fun Modifier.clearGlass(
    shape: Shape,
    focused: Boolean,
    idle: Boolean,
    node: GlassNode,
    strict: Boolean,
    overlayOnly: Boolean,
): Modifier {
    rememberGlassChoice(node, shape, idle || focused, strict)
    val lift = animateFloatAsState(if (focused) 1f else 0f, animationSpec = GlassSpring, label = "clearLift")
    val ui = LocalUi.current
    val onDock = LocalOnDock.current
    val bd = ui.backdrops?.clear
    val screen = ui.size
    val dockBody = androidx.compose.ui.graphics.lerp(ui.wp.dark, ui.wp.tint, 0.35f).copy(alpha = 0.58f)
    return this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            val np = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            // only write when something changed, so idle panels cost nothing
            if (node.pos != np) node.pos = np
            if (node.size != it.size) node.size = it.size
        }
        .drawWithCache {
            val w = size.width
            val h = size.height
            val dir = layoutDirection
            val path = shape.createOutline(size, dir, this).asPath()
            val ring = insetPath(shape, size, 1.25.dp.toPx(), dir, this)
            val rimStroke = Stroke(width = LG.RIM_WIDTH_DP.dp.toPx())
            val focusStroke = Stroke(width = 2.5.dp.toPx())
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = LG.SHEEN_ALPHA),
                0.45f to Color.Transparent,
                startY = 0f,
                endY = h,
            )
            val shadowOff1 = 2.dp.toPx()
            val shadowOff2 = 5.dp.toPx()
            val shadowA = LG.SHADOW_ALPHA

            onDrawBehind {
                val p = lift.value.coerceIn(0f, 1.25f)
                if (!idle && p < 0.01f) return@onDrawBehind
                val a = min(1f, p)
                if (bd == null || screen.width <= 0 || screen.height <= 0) {
                    // only seen for a moment before the wallpaper copy is ready
                    drawPath(path, Color.White.copy(alpha = 0.10f + 0.06f * a))
                } else {
                    val ox = node.pos.x.toFloat()
                    val oy = node.pos.y.toFloat()
                    val cx = ox + w / 2f
                    val cy = oy + h / 2f
                    val sx = screen.width.toFloat() / bd.bitmap.width
                    val sy = screen.height.toFloat() / bd.bitmap.height
                    if (!overlayOnly) {
                        // two soft layers of depth just below the edge
                        translateAndFill(path, shadowOff2, Color.Black.copy(alpha = shadowA * 0.45f))
                        translateAndFill(path, shadowOff1, Color.Black.copy(alpha = shadowA * 0.75f))
                        // the wallpaper behind
                        drawPath(path, backdropShader(bd.bitmap, sx, sy, 1f, cx, cy, ox, oy))
                        // legibility layers: dark adaptation, calming a busy backdrop, then a light or dark scrim
                        val ch = node.choice
                        if (node.darkTint > 0.005f) drawPath(path, Color.Black.copy(alpha = node.darkTint))
                        drawPath(path, Color.White.copy(alpha = LG.TINT_WHITE))
                        if (ch != null) {
                            if (ch.calm > 0.02f) drawPath(path, node.avgColor.copy(alpha = ch.calm * 0.6f))
                            if (ch.scrim > 0.005f) {
                                drawPath(path, (if (ch.scrimIsDark) Color.Black else Color.White).copy(alpha = ch.scrim))
                            }
                        }
                        drawPath(path, sheen)
                    }
                    // no outline and no edge band: the panel simply ends where the glass ends
                    // on the dock, icons get a darker, more solid body so they separate from the bar
                    if (onDock) drawPath(path, dockBody)
                }
                if (p > 0.02f) {
                    drawPath(
                        ring,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.95f * a),
                            0.5f to Color.White.copy(alpha = 0.20f * a),
                            1f to Color.White.copy(alpha = 0.75f * a),
                            start = Offset.Zero,
                            end = Offset(w, h),
                        ),
                        style = rimStroke,
                    )
                    drawPath(ring, Color.White.copy(alpha = a), style = focusStroke)
                }
            }
        }
}

private fun DrawScope.translateAndFill(path: Path, dy: Float, color: Color) {
    translate(0f, dy) { drawPath(path, color) }
}

// ---------- The wrapper that also hands content its foreground ----------

/**
 * A panel that also tells what is drawn on it which color to use. Same look as `Modifier.glass`; use this
 * when the content has text or monochrome icons (read the color with [inkFg] / [inkAccent]).
 * In Tinted and Frosted it simply draws the original panel and the content keeps its original colors.
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
    val clear = (style ?: ui.settings.glass) == GlassStyle.Clear && ui.backdrops?.clear != null
    val onDock = LocalOnDock.current
    Box(modifier.glass(shape, focused, style, blur, idle, node, strict, overlayOnly), contentAlignment) {
        if (clear) {
            val ch = node.choice
            val fade = tween<Color>(LG.COLOR_FADE_MS)
            val fg by animateColorAsState(Color(if (onDock) LG.FG_LIGHT else (ch?.fg ?: LG.FG_LIGHT)), fade, label = "inkFg")
            val accentTarget = if (ch != null) {
                Color(adaptAccent(ui.wp.accent.toArgb(), ch.bgL, if (strict) LG.TEXT_CONTRAST else LG.ICON_CONTRAST))
            } else {
                ui.wp.accent
            }
            val accent by animateColorAsState(accentTarget, fade, label = "inkAccent")
            val shadow by animateFloatAsState(ch?.shadow ?: 0f, tween(LG.COLOR_FADE_MS), label = "inkShadow")
            val ink = remember(fg, accent, shadow) { GlassInk(fg, accent, shadow) }
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

/** A panel only when [on]; otherwise a plain Box (used where the dock bar is optional). */
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
