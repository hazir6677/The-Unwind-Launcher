package com.unwind.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One springy motion curve used everywhere, so the whole UI moves like one liquid. */
val LiquidSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 280f)

// ---------- Bitmap helpers ----------

fun decodeRes(ctx: Context, name: String, sample: Int): Bitmap? {
    val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
    if (id == 0) return null
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeResource(ctx.resources, id, o)
}

/** Shrinks a bitmap by halves first, which avoids the jagged/shimmering look of a single big downscale. */
fun scaleDown(src: Bitmap, w: Int, h: Int): Bitmap {
    var cur = src
    while (cur.width / 2 >= w && cur.height / 2 >= h) {
        cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    }
    return if (cur.width == w && cur.height == h) cur else Bitmap.createScaledBitmap(cur, w, h, true)
}

/** Pre-blurred copies of the wallpaper, already cropped to the screen's shape so glass lines up with it. */
class Backdrops(val soft: Bitmap, val heavy: Bitmap, val liquid: LiquidData?)

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val n = 2 * r + 1
    for (l in 0 until lines) for (i in 0 until len) {
        var sr = 0
        var sg = 0
        var sb = 0
        for (k in -r..r) {
            val j = (i + k).coerceIn(0, len - 1)
            val p = if (horizontal) src[l * w + j] else src[j * w + l]
            sr += (p shr 16) and 255
            sg += (p shr 8) and 255
            sb += p and 255
        }
        val o = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
        if (horizontal) dst[l * w + i] = o else dst[i * w + l] = o
    }
}

private fun cropToAspect(src: Bitmap, aspect: Float): Bitmap {
    val sa = src.width.toFloat() / src.height
    return when {
        abs(sa - aspect) < 0.01f -> src
        sa > aspect -> {
            val nw = (src.height * aspect).roundToInt().coerceIn(1, src.width)
            Bitmap.createBitmap(src, (src.width - nw) / 2, 0, nw, src.height)
        }
        else -> {
            val nh = (src.width / aspect).roundToInt().coerceIn(1, src.height)
            Bitmap.createBitmap(src, 0, (src.height - nh) / 2, src.width, nh)
        }
    }
}

/**
 * [scrimA] and [screenWidthPx] let the Liquid backdrop match what is really behind the glass: the wallpaper with the
 * launcher's own scrims on top, softened by a small blur (see LG.BLUR_PX).
 */
fun makeBackdrops(src: Bitmap, aspect: Float, scrimA: Float, screenWidthPx: Int): Backdrops {
    val cropped = cropToAspect(src, aspect)
    fun blurred(w: Int, r: Int, passes: Int): Bitmap {
        val h = max(14, (w / aspect).roundToInt())
        val small = scaleDown(cropped, w, h)
        val a = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            blurPass(a, b, w, h, r, true)
            blurPass(b, a, w, h, r, false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }
    // Liquid: about 960 wide (half of a 1080p screen) so the rim stays sharp, then scrims and a light blur
    val lw = 960
    val lh = max(14, (lw / aspect).roundToInt())
    val lsmall = scaleDown(cropped, lw, lh)
    val lpx = IntArray(lw * lh)
    lsmall.getPixels(lpx, 0, lw, 0, 0, lw, lh)
    for (i in lpx.indices) lpx[i] = lpx[i] or (0xFF shl 24)
    return Backdrops(
        soft = blurred(160, 2, 2),
        heavy = blurred(56, 2, 3),
        liquid = buildLiquidData(lpx, lw, lh, scrimA, screenWidthPx),
    )
}

// ---------- Path helpers ----------

internal fun Outline.asPath(): Path {
    val o = this
    return Path().apply {
        when (o) {
            is Outline.Rectangle -> addRect(o.rect)
            is Outline.Rounded -> addRoundRect(o.roundRect)
            is Outline.Generic -> addPath(o.path)
        }
    }
}

internal fun insetPath(shape: Shape, size: Size, inset: Float, dir: LayoutDirection, d: Density): Path {
    val s = Size((size.width - 2f * inset).coerceAtLeast(2f), (size.height - 2f * inset).coerceAtLeast(2f))
    val p = shape.createOutline(s, dir, d).asPath()
    p.translate(Offset(inset, inset))
    return p
}

/**
 * A brush that paints the wallpaper aligned to the screen, but zoomed by [z] around the middle of
 * the element. z below 1 pulls in content from outside the element (what glass edges do),
 * z above 1 magnifies it.
 */
private fun backdropBrush(
    bmp: Bitmap, sx: Float, sy: Float, z: Float,
    cx: Float, cy: Float, ox: Float, oy: Float,
): ShaderBrush {
    val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val m = Matrix()
    m.setScale(z * sx, z * sy)
    m.postTranslate((1f - z) * cx - ox, (1f - z) * cy - oy)
    shader.setLocalMatrix(m)
    return ShaderBrush(shader)
}

// ---------- The glass itself ----------

/**
 * Draws a glass surface in one of three styles.
 *
 * Liquid: real refraction, baked per element (see LiquidCore.kt / LiquidGlass.kt).
 * Normal / Frost: the wallpaper blurred behind a tint (unchanged).
 *
 * Everything is drawn as anti-aliased paths, so edges stay smooth even while scaling.
 * [idle] = false means: only draw while focused (used for small buttons).
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    focused: Boolean = false,
    style: GlassStyle? = null,
    blur: Boolean = true,
    idle: Boolean = true,
    node: GlassNode? = null,
    strict: Boolean = true,
    overlayOnly: Boolean = false,
): Modifier {
    val ui = LocalUi.current
    val st = style ?: ui.settings.glass
    // Liquid has its own renderer (LiquidGlass.kt); Normal and Frost are untouched below.
    return if (st == GlassStyle.Liquid && ui.backdrops?.liquid != null) {
        liquidGlass(shape, focused, idle, node ?: remember { GlassNode() }, strict, overlayOnly)
    } else {
        classicGlass(shape, focused, style, blur, idle)
    }
}

@Composable
private fun Modifier.classicGlass(
    shape: Shape,
    focused: Boolean,
    style: GlassStyle?,
    blur: Boolean,
    idle: Boolean,
): Modifier {
    val ui = LocalUi.current
    val st = style ?: ui.settings.glass
    val lift = animateFloatAsState(if (focused) 1f else 0f, animationSpec = LiquidSpring, label = "glassLift")
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    val tint = ui.wp.tint
    val screen = ui.size
    val backdrops = ui.backdrops

    return this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
        .drawWithCache {
            val w = size.width
            val h = size.height
            val dir = layoutDirection
            val path = shape.createOutline(size, dir, this).asPath()
            val ring = insetPath(shape, size, 1.25.dp.toPx(), dir, this)
            val hair = 1.dp.toPx()
            val focusW = 2.5.dp.toPx()

            onDrawBehind {
                val p = lift.value.coerceIn(0f, 1.25f)
                if (!idle && p < 0.01f) return@onDrawBehind
                val ox = pos.x.toFloat()
                val oy = pos.y.toFloat()
                val cx = ox + w / 2f
                val cy = oy + h / 2f
                val bmp = when (st) {
                    GlassStyle.Liquid -> null
                    GlassStyle.Normal -> backdrops?.soft
                    GlassStyle.Frost -> backdrops?.heavy
                }
                val bm = if (blur && screen.width > 0 && screen.height > 0) bmp else null
                val sx = if (bm != null) screen.width.toFloat() / bm.width else 1f
                val sy = if (bm != null) screen.height.toFloat() / bm.height else 1f

                when (st) {
                    GlassStyle.Liquid -> {
                        // Only seen for a moment before the wallpaper data is ready; liquidGlass() does the real work.
                        drawPath(path, Color.White.copy(alpha = 0.10f + 0.06f * min(p, 1f)))
                    }
                    GlassStyle.Normal -> {
                        if (bm != null) drawPath(path, backdropBrush(bm, sx, sy, 1f, cx, cy, ox, oy))
                        drawPath(
                            path,
                            Brush.verticalGradient(
                                listOf(
                                    tint.copy(alpha = 0.34f + 0.21f * min(p, 1f)),
                                    tint.copy(alpha = 0.55f + 0.20f * min(p, 1f)),
                                ),
                            ),
                        )
                        drawPath(
                            path,
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.12f),
                                0.4f to Color.Transparent,
                            ),
                        )
                        drawPath(
                            ring,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))),
                            style = Stroke(width = hair),
                        )
                    }
                    GlassStyle.Frost -> {
                        if (bm != null) drawPath(path, backdropBrush(bm, sx, sy, 1f, cx, cy, ox, oy))
                        drawPath(path, Color.White.copy(alpha = 0.22f + 0.12f * min(p, 1f)))
                        drawPath(path, tint.copy(alpha = 0.22f))
                        drawPath(
                            path,
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.16f),
                                0.35f to Color.Transparent,
                            ),
                        )
                        drawPath(
                            ring,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.15f))),
                            style = Stroke(width = hair),
                        )
                    }
                }
                if (p > 0.02f) {
                    drawPath(ring, Color.White.copy(alpha = min(1f, p)), style = Stroke(width = focusW))
                }
            }
        }
}

/**
 * A light, see-through focus highlight for small buttons (top bar, dock, list rows):
 * a soft glow, glossy top light and a bright rim that swells in with a spring.
 */
@Composable
fun Modifier.liquidFocus(shape: Shape, focused: Boolean): Modifier {
    val lift = animateFloatAsState(if (focused) 1f else 0f, animationSpec = LiquidSpring, label = "focusLift")
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val path = shape.createOutline(size, layoutDirection, this).asPath()
        val ring = insetPath(shape, size, 1.25.dp.toPx(), layoutDirection, this)
        val rimW = 1.5.dp.toPx()
        val focusW = 2.dp.toPx()
        onDrawBehind {
            val p = lift.value.coerceIn(0f, 1.25f)
            if (p < 0.01f) return@onDrawBehind
            val a = min(1f, p)
            drawPath(path, Color.White.copy(alpha = 0.22f * a))
            drawPath(
                path,
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.40f * a), Color.Transparent),
                    center = Offset(w * 0.3f, 0f),
                    radius = max(w, h) * 0.9f,
                ),
            )
            drawPath(
                ring,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.95f * a),
                    0.5f to Color.White.copy(alpha = 0.25f * a),
                    1f to Color.White.copy(alpha = 0.75f * a),
                ),
                style = Stroke(width = rimW),
            )
            drawPath(ring, Color.White.copy(alpha = a), style = Stroke(width = focusW))
        }
    }
}
