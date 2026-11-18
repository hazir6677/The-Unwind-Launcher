package com.unwind.launcher

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Liquid Glass core. Plain Kotlin on purpose (no Android classes) so the math can be run and
 * tested on a normal JVM. LiquidGlass.kt wraps this for Compose.
 *
 * Everything you are likely to tune is in [LG] below.
 */

/** Tunable constants. Distances are in px at the screen's native size unless noted. */
object LG {
    // ---- Refraction ----
    /** Refractive index of the glass. Apple's look sits around 1.5. */
    const val REFRACTIVE_INDEX = 1.5f
    /** Width of the curved rim as a fraction of the element's shortest side (spec: 10-20%). */
    const val BEZEL_FRACTION = 0.16f
    const val BEZEL_MIN_PX = 5f
    const val BEZEL_MAX_PX = 44f
    /** Slab thickness under the curved rim, as a multiple of the bezel width. Higher = stronger bend. */
    const val THICKNESS = 1.15f
    /** Overall multiplier on the displacement. 1 = physical. */
    const val REFRACTION_STRENGTH = 1.0f
    /** +1: rim shows content from further inside (physical for a convex dome). -1: pulls in outside content. */
    const val REFRACT_DIRECTION = 1f
    /** How much the flat middle magnifies (0.02 = 2%). Spec: "maybe very slightly". */
    const val CENTER_MAGNIFY = 0.025f

    // ---- Blur ----
    /** Blur baked into the backdrop, in screen px (spec: 2-8). Used by makeLiquidBackdrop. */
    const val BLUR_PX = 4

    // ---- Dispersion ----
    /** Extra red/blue shift at the rim in px. R and B end up about 2x this apart (spec: 1-3px total). */
    const val DISPERSION_PX = 1.0f

    // ---- Specular ----
    const val RIM_WIDTH_PX = 1.4f
    /** Peak rim brightness (0..1) before the dark/light-background adaptation. */
    const val RIM_INTENSITY = 0.85f
    const val GLOW_INTENSITY = 0.20f
    const val INNER_SHADOW_INTENSITY = 0.16f
    /** Light comes from the top-left. */
    const val LIGHT_X = -0.7071f
    const val LIGHT_Y = -0.7071f

    // ---- Color ----
    const val SATURATION = 1.45f      // spec: 120-180%
    const val BRIGHTNESS_LIFT = 0.04f
    const val TINT_WHITE = 0.08f      // spec: 5-12%
    /** Faint dark tint that fades in over bright backgrounds so the glass stays legible. */
    const val TINT_DARK_MAX = 0.10f

    // ---- Shadows ----
    const val SHADOW_OFFSET_Y = 8f
    const val SHADOW_BLUR = 32f
    const val SHADOW_ALPHA = 0.22f
    const val CONTACT_BLUR = 4f
    const val CONTACT_OFFSET_Y = 1.5f
    const val CONTACT_ALPHA = 0.20f

    // ---- Legibility ----
    const val TEXT_CONTRAST = 4.5f
    const val ICON_CONTRAST = 3.0f
    /** The solver aims this far above the minimum, to absorb the rim highlight and refraction it cannot predict. */
    const val SOLVER_MARGIN = 0.8f
    /** The other color must be at least this much better (ratio) before the foreground flips. */
    const val HYSTERESIS = 1.12f
    /** Spread (p90-p10 of luminance) above which the backdrop is calmed, and how fast that ramps up. */
    const val CALM_START = 0.10f
    const val CALM_RANGE = 0.40f
    const val CALM_MAX = 0.92f
    /** How much the bright (for white text) or dark (for dark text) patches count versus the average. 1 = worst case only. */
    const val WORST_WEIGHT = 0.9f
    const val MAX_SCRIM = 0.30f       // spec asks for 10-25% typically; hard ceiling here
    const val COLOR_FADE_MS = 260
    /** Wallpaper map resolution (width in px). Spec: about 64. */
    const val SAMPLE_W = 64
    val FG_LIGHT = 0xFFFFFFFF.toInt()
    val FG_DARK = 0xFF141416.toInt()

    // ---- Motion ----
    const val PRESS_SCALE = 1.06f
}

// ============================================================================================
// Color math
// ============================================================================================

private fun srgbToLinear(c8: Int): Float {
    val c = c8 / 255f
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

private val LIN = FloatArray(256) { srgbToLinear(it) }

/** WCAG relative luminance of an ARGB color (alpha ignored). */
fun relLuminance(argb: Int): Float =
    0.2126f * LIN[(argb shr 16) and 255] + 0.7152f * LIN[(argb shr 8) and 255] + 0.0722f * LIN[argb and 255]

fun contrastRatio(l1: Float, l2: Float): Float {
    val hi = max(l1, l2)
    val lo = min(l1, l2)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Dark adaptation for a pixel whose gamma-space luma is [lg]. Shared by the baker and the legibility sampler. */
fun darkTintFor(lg: Float): Float = LG.TINT_DARK_MAX * smooth(0.35f, 0.9f, lg * lg)

private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3f - 2f * t)
}

// ============================================================================================
// Shape: signed distance of a rounded rectangle
// ============================================================================================

/** Rounded-rect shape inside a [w]x[h] box, with corner radius [r] (px). Capsule: r = min(w,h)/2. */
class LgShape(val w: Float, val h: Float, r: Float) {
    val r: Float = r.coerceIn(0f, min(w, h) / 2f)
    private val hw = w / 2f
    private val hh = h / 2f

    /** Distance outside the edge (negative inside). Writes the outward normal into [out] (2 floats). */
    fun sdf(x: Float, y: Float, out: FloatArray?): Float {
        val px = x - hw
        val py = y - hh
        val qx = abs(px) - (hw - r)
        val qy = abs(py) - (hh - r)
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val len = sqrt(ox * ox + oy * oy)
        val d = len + min(max(qx, qy), 0f) - r
        if (out != null) {
            var nx: Float
            var ny: Float
            if (len > 1e-4f) {
                nx = ox / len; ny = oy / len
            } else if (qx > qy) {
                nx = 1f; ny = 0f
            } else {
                nx = 0f; ny = 1f
            }
            if (px < 0f) nx = -nx
            if (py < 0f) ny = -ny
            out[0] = nx
            out[1] = ny
        }
        return d
    }
}

// ============================================================================================
// Optics: convex squircle bezel + Snell's law
// ============================================================================================

/**
 * Precomputed rim profile. Index i covers depth s = i/(N-1) across the bezel (0 = the very edge, 1 = where the
 * glass goes flat). Each entry is how far (in bezel-widths) the sampled background is shifted, along the
 * inward normal.
 *
 * Surface: convex squircle  h(s) = (1 - (1-s)^4)^(1/4)  (vertical wall at the edge, flat at s = 1).
 * A ray comes straight down, meets the surface tilted by theta_i = atan(slope), bends to theta_t by
 * Snell (sin theta_i = n sin theta_t) and then travels the glass thickness beneath that point.
 * Lateral shift = depth * tan(theta_i - theta_t).
 */
class BezelProfile(val n: Float = LG.REFRACTIVE_INDEX, val thickness: Float = LG.THICKNESS) {
    companion object { const val N = 256 }

    val shift = FloatArray(N)

    init {
        for (i in 0 until N) {
            val s = i / (N - 1f)
            val a = 1f - s
            val a4 = a * a * a * a
            val hgt = (1f - a4).coerceAtLeast(0f).pow(0.25f)
            // slope dh/ds (in bezel-widths per bezel-width); infinite at s = 0, so cap the angle
            val slope = if (s < 1e-3f) 1e3f else a * a * a / (1f - a4).coerceAtLeast(1e-6f).pow(0.75f)
            val thetaI = atan(slope)
            val sinT = (kotlin.math.sin(thetaI) / n).coerceIn(-1f, 1f)
            val thetaT = asin(sinT)
            val depth = thickness * 0.5f + hgt * thickness * 0.5f
            shift[i] = depth * tan(thetaI - thetaT)
        }
    }

    fun at(s: Float): Float {
        if (s >= 1f) return 0f
        val f = s.coerceAtLeast(0f) * (N - 1)
        val i = f.toInt().coerceAtMost(N - 2)
        val t = f - i
        return shift[i] * (1f - t) + shift[i + 1] * t
    }
}

// ============================================================================================
// The baker
// ============================================================================================

/** The pre-blurred wallpaper-with-scrims that the glass looks through. [w]x[h] covers the whole screen. */
class LgBackdrop(val w: Int, val h: Int, val argb: IntArray)

/**
 * Where the glass is on screen and how it should respond to its surroundings.
 * [x],[y]: top-left of the element in screen px. [lumaMean]: 0..1 relative luminance of what's behind it.
 */
class LgParams(
    val w: Int,
    val h: Int,
    val radius: Float,
    val x: Float,
    val y: Float,
    val screenW: Int,
    val screenH: Int,
    val lumaMean: Float,
    val density: Float = 1f,
    /** false = draw only edge effects (rim, glow, dispersion fringe, shadow) and leave the middle clear. */
    val opaqueInterior: Boolean = true,
    val shadow: Boolean = true,
    /** 0..1: how much a busy background is calmed toward its own average inside the glass (legibility). */
    val calm: Float = 0f,
    /** Average backdrop color of this element's region in linear light (0..1), the target of [calm]. */
    val meanR: Float = 0.5f,
    val meanG: Float = 0.5f,
    val meanB: Float = 0.5f,
    /** Adaptive scrim (0..1 opacity) mixed into what the glass sees, black if [scrimDark] else white. */
    val scrim: Float = 0f,
    val scrimDark: Boolean = true,
    /** Black overlay already drawn between the wallpaper and this glass (a dialog dim), 0..1. */
    val dim: Float = 0f,
)

/** Result bitmap: premultiplied ARGB, with [pad] px of room around the element for shadows. */
class LgBake(val width: Int, val height: Int, val pad: Int, val argb: IntArray)

private val profile = BezelProfile()

private fun shadowPad(minSide: Float, density: Float): Int {
    val k = min(1f, minSide / (200f * density))
    return (LG.SHADOW_BLUR * density * (0.35f + 0.65f * k) + LG.SHADOW_OFFSET_Y * density).toInt() + 2
}

class LgSampler(private val bd: LgBackdrop, screenW: Int, screenH: Int) {
    private val kx = bd.w.toFloat() / screenW
    private val ky = bd.h.toFloat() / screenH

    /** Reads one color channel (0 = R, 1 = G, 2 = B) with bilinear filtering at a screen-space point. */
    fun channel(sx: Float, sy: Float, c: Int): Float {
        val fx = (sx * kx - 0.5f).coerceIn(0f, bd.w - 1.001f)
        val fy = (sy * ky - 0.5f).coerceIn(0f, bd.h - 1.001f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val tx = fx - x0
        val ty = fy - y0
        val sh = 16 - 8 * c
        val i = y0 * bd.w + x0
        val a = (bd.argb[i] shr sh) and 255
        val b = (bd.argb[i + 1] shr sh) and 255
        val cc = (bd.argb[i + bd.w] shr sh) and 255
        val d = (bd.argb[i + bd.w + 1] shr sh) and 255
        val top = a + (b - a) * tx
        val bot = cc + (d - cc) * tx
        return (top + (bot - top) * ty) / 255f
    }
}

/**
 * Bakes one Liquid Glass element. This is the heart of the effect:
 * 1. For each pixel inside the shape, find its depth into the rim and the inward normal.
 * 2. Displace where we read the wallpaper from by the Snell's-law shift (zero in the middle, growing toward the
 *    edge, and strong enough at the very border to flip). Red, green and blue use slightly different shifts.
 * 3. Boost saturation, add a faint tint, adapt to bright backgrounds.
 * 4. Add the specular rim (brightest top-left / bottom-right), inner glow, inner shadow.
 * 5. Add drop shadow and contact shadow outside the shape.
 */
fun bakeLiquid(bd: LgBackdrop, p: LgParams): LgBake {
    val d = p.density
    val minSide = min(p.w, p.h).toFloat()
    val pad = if (p.shadow) shadowPad(minSide, d) else 2
    val bw = p.w + 2 * pad
    val bh = p.h + 2 * pad
    val out = IntArray(bw * bh)
    val shape = LgShape(p.w.toFloat(), p.h.toFloat(), p.radius)
    val sampler = LgSampler(bd, p.screenW, p.screenH)

    val bezel = (minSide * LG.BEZEL_FRACTION).coerceIn(LG.BEZEL_MIN_PX * d, LG.BEZEL_MAX_PX * d)
    val maxShift = bezel * profile.shift.max() // px, before strength
    val strength = LG.REFRACTION_STRENGTH * LG.REFRACT_DIRECTION
    val disp = LG.DISPERSION_PX * d
    val cx = p.w / 2f
    val cy = p.h / 2f

    // Adapt highlights and shadows to the background: bright rims on dark, subtle on light.
    val dark = 1f - smooth(0.08f, 0.55f, p.lumaMean)       // 1 on dark backgrounds
    val rimK = LG.RIM_INTENSITY * (0.55f + 0.45f * dark)
    val glowK = LG.GLOW_INTENSITY * (0.6f + 0.4f * dark)
    val innerShK = LG.INNER_SHADOW_INTENSITY * (1.35f - 0.7f * dark)
    val dropK = LG.SHADOW_ALPHA * (1.25f - 0.65f * dark)
    val contactK = LG.CONTACT_ALPHA * (1.25f - 0.65f * dark)

    val n = FloatArray(2)
    val rimW = LG.RIM_WIDTH_PX * d
    // the glow hugs the rim (it must not wash out text near it), so its width is fixed, not tied to the bezel
    val glowW = min(6f * d, bezel * 0.9f)
    val shadowBlur = LG.SHADOW_BLUR * d * (0.35f + 0.65f * min(1f, minSide / (200f * d)))
    val contactBlur = LG.CONTACT_BLUR * d
    val offY = LG.SHADOW_OFFSET_Y * d * (0.4f + 0.6f * min(1f, minSide / (200f * d)))
    val cOffY = LG.CONTACT_OFFSET_Y * d

    for (j in 0 until bh) {
        val ly = j - pad + 0.5f
        for (i in 0 until bw) {
            val lx = i - pad + 0.5f
            val sd = shape.sdf(lx, ly, n)
            var r = 0f; var g = 0f; var b = 0f; var a = 0f

            // ---- shadows (outside, and also faintly under the glass so thin glass still has weight) ----
            if (p.shadow) {
                val sdD = shape.sdf(lx, ly - offY, null)
                val big = dropK * (1f - smooth(-shadowBlur * 0.35f, shadowBlur * 0.65f, sdD))
                val sdC = shape.sdf(lx, ly - cOffY, null)
                val small = contactK * (1f - smooth(-contactBlur * 0.5f, contactBlur, sdC))
                val sh = 1f - (1f - big) * (1f - small)
                a = sh
            }

            val cov = clamp01(0.5f - sd) // anti-aliased coverage of the glass body
            if (cov > 0f) {
                val u = (-sd).coerceAtLeast(0f)            // depth into the glass, px
                val s = u / bezel                           // 0 at edge .. 1 at the end of the bezel
                val shift = profile.at(s) * bezel * strength // px, along the inward normal (-n)
                // inward direction is -n (n points outward)
                val inX = -n[0]
                val inY = -n[1]
                // dispersion scales with how bent the ray is
                val bend = if (maxShift > 0f) clamp01(abs(shift) / (maxShift * LG.REFRACTION_STRENGTH)) else 0f
                val dj = disp * bend * (if (strength < 0f) -1f else 1f)
                // flat middle: slight magnification toward the center
                val mx = (lx - cx) * -LG.CENTER_MAGNIFY
                val my = (ly - cy) * -LG.CENTER_MAGNIFY
                val sxBase = p.x + lx + mx
                val syBase = p.y + ly + my
                var cr = sampler.channel(sxBase + inX * (shift + dj), syBase + inY * (shift + dj), 0)
                var cg = sampler.channel(sxBase + inX * shift, syBase + inY * shift, 1)
                var cb = sampler.channel(sxBase + inX * (shift - dj), syBase + inY * (shift - dj), 2)

                if (p.dim > 0f) { val k = 1f - p.dim; cr *= k; cg *= k; cb *= k }

                // ---- busy backgrounds get calmed toward their average so text stays readable ----
                if (p.calm > 0f) {
                    // blend in linear light (the region mean is a linear-light average), then back to gamma
                    cr = (cr.pow(2.2f) * (1f - p.calm) + p.meanR * p.calm).pow(1f / 2.2f)
                    cg = (cg.pow(2.2f) * (1f - p.calm) + p.meanG * p.calm).pow(1f / 2.2f)
                    cb = (cb.pow(2.2f) * (1f - p.calm) + p.meanB * p.calm).pow(1f / 2.2f)
                }

                // ---- color: saturate, lift, tint ----
                val lum = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                val sat = LG.SATURATION
                cr = lum + (cr - lum) * sat
                cg = lum + (cg - lum) * sat
                cb = lum + (cb - lum) * sat
                cr += LG.BRIGHTNESS_LIFT; cg += LG.BRIGHTNESS_LIFT; cb += LG.BRIGHTNESS_LIFT
                // faint dark adaptation, per pixel, only where the picture is bright
                val lg = clamp01(0.2126f * cr + 0.7152f * cg + 0.0722f * cb)
                val darkTint = darkTintFor(lg)
                cr = cr * (1f - darkTint); cg = cg * (1f - darkTint); cb = cb * (1f - darkTint)
                val tw = LG.TINT_WHITE
                cr = cr * (1f - tw) + tw; cg = cg * (1f - tw) + tw; cb = cb * (1f - tw) + tw

                // saturation can push channels past 1; clamp first or a scrim would just eat the overshoot
                cr = clamp01(cr); cg = clamp01(cg); cb = clamp01(cb)

                // ---- adaptive scrim, applied after the color path (same order the legibility solver assumes) ----
                if (p.scrim > 0f) {
                    val k = 1f - p.scrim
                    if (p.scrimDark) { cr *= k; cg *= k; cb *= k }
                    else { cr = cr * k + p.scrim; cg = cg * k + p.scrim; cb = cb * k + p.scrim }
                }

                // ---- light: depends on which way the edge faces ----
                val nl = n[0] * LG.LIGHT_X + n[1] * LG.LIGHT_Y   // +1 facing the light, -1 facing away
                val spec = max(abs(nl).pow(1.6f) * (if (nl > 0f) 1f else 0.72f), 0.10f) // 2 lobes, weak sides
                // thin rim: strongest right at the edge, gone after rimW
                val rim = (1f - smooth(rimW * 0.35f, rimW * 1.25f, u)) * rimK * spec
                // soft inner glow just inside the rim, mostly on the lit side
                val glowSide = 0.35f + 0.65f * clamp01(nl * 0.5f + 0.5f)
                val glow = exp(-u / glowW * 2.4f) * glowK * glowSide
                // subtle inner shadow on the side facing away from the light, a bit further in
                val away = clamp01(-nl)
                val ish = (u - rimW * 2f) / (glowW * 0.8f)
                val innerSh = away * exp(-(ish * ish)) * innerShK

                var wr = cr; var wg = cg; var wb = cb
                wr = wr * (1f - innerSh)
                wg = wg * (1f - innerSh)
                wb = wb * (1f - innerSh)
                val add = glow + rim
                wr = wr + (1f - wr) * add
                wg = wg + (1f - wg) * add
                wb = wb + (1f - wb) * add

                if (p.opaqueInterior) {
                    // glass body over the shadow
                    val ga = cov
                    r = clamp01(wr) * ga; g = clamp01(wg) * ga; b = clamp01(wb) * ga
                    a = ga + a * (1f - ga)
                    // shadow is dark, so tint what shows through the partial edge
                } else {
                    // edge effects only: premultiplied white highlight + dark inner shadow + color fringe
                    val fill = 0.14f * cov                       // a faint white body so the control still reads as glass
                    val hiA = clamp01(rim + glow * 0.7f) * cov
                    val hi = hiA + fill * (1f - hiA)
                    val shA = innerSh * 0.9f * cov
                    val fr = clamp01((cr - cg) * 3f) * bend * 0.25f * cov
                    val fb = clamp01((cb - cg) * 3f) * bend * 0.25f * cov
                    r = hi + fr
                    g = hi
                    b = hi + fb
                    val ca = clamp01(hi + shA + max(fr, fb))
                    a = ca + a * (1f - ca)
                }
            }
            val ai = (clamp01(a) * 255f + 0.5f).toInt()
            val ri = (clamp01(r) * 255f + 0.5f).toInt().coerceAtMost(ai)
            val gi = (clamp01(g) * 255f + 0.5f).toInt().coerceAtMost(ai)
            val bi = (clamp01(b) * 255f + 0.5f).toInt().coerceAtMost(ai)
            out[j * bw + i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
    return LgBake(bw, bh, pad, out)
}

// ============================================================================================
// Backdrop construction (wallpaper + scrims, small blur)
// ============================================================================================

/** Box blur, [passes] times in each direction. Three passes of a small box is close to a Gaussian. */
fun blurArgb(src: IntArray, w: Int, h: Int, radius: Int, passes: Int): IntArray {
    var a = src.copyOf()
    var b = IntArray(src.size)
    if (radius <= 0) return a
    val n = 2 * radius + 1
    repeat(passes) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (k in -radius..radius) {
                    val p = a[y * w + (x + k).coerceIn(0, w - 1)]
                    sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
                }
                b[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (k in -radius..radius) {
                    val p = b[(y + k).coerceIn(0, h - 1) * w + x]
                    sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
                }
                a[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
    }
    return a
}

/**
 * Applies the launcher's two black scrims (a left-to-right one and a bottom one) to wallpaper pixels, exactly as
 * MainActivity draws them. Without this the glass would show a brighter picture than the UI around it.
 */
fun applyLauncherScrims(px: IntArray, w: Int, h: Int, scrimA: Float): IntArray {
    val out = IntArray(px.size)
    for (y in 0 until h) {
        val fy = (y + 0.5f) / h
        val vA = scrimA * 0.9f * clamp01((fy - 0.55f) / 0.45f)
        for (x in 0 until w) {
            val fx = (x + 0.5f) / w
            val hA = scrimA * (1f - clamp01(fx / 0.62f))
            val p = px[y * w + x]
            val keep = (1f - hA) * (1f - vA) // two black layers over each other
            val r = (((p shr 16) and 255) * keep).toInt()
            val g = (((p shr 8) and 255) * keep).toInt()
            val b = ((p and 255) * keep).toInt()
            out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return out
}

// ============================================================================================
// Legibility
// ============================================================================================

/** What the glass looks like to the eye for a given backdrop pixel (same color path as bakeLiquid). */
fun seenThroughGlass(argb: Int): Int {
    var r = ((argb shr 16) and 255) / 255f
    var g = ((argb shr 8) and 255) / 255f
    var b = (argb and 255) / 255f
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    r = lum + (r - lum) * LG.SATURATION
    g = lum + (g - lum) * LG.SATURATION
    b = lum + (b - lum) * LG.SATURATION
    r += LG.BRIGHTNESS_LIFT; g += LG.BRIGHTNESS_LIFT; b += LG.BRIGHTNESS_LIFT
    val dt = darkTintFor(clamp01(0.2126f * r + 0.7152f * g + 0.0722f * b))
    r *= (1f - dt); g *= (1f - dt); b *= (1f - dt)
    val tw = LG.TINT_WHITE
    r = r * (1f - tw) + tw; g = g * (1f - tw) + tw; b = b * (1f - tw) + tw
    return (0xFF shl 24) or ((clamp01(r) * 255 + 0.5f).toInt() shl 16) or ((clamp01(g) * 255 + 0.5f).toInt() shl 8) or
        (clamp01(b) * 255 + 0.5f).toInt()
}

/** Region growth to pass to [analyzeRegion]: the bezel (rim area counts) minus the box corners a rounded shape cuts off. */
fun analysisGrow(bezel: Float, radius: Float): Float = bezel - 0.29f * radius

/** A small (about 64px wide) copy of what is behind the glass, used for luminance sampling. */
class LumaMap(val w: Int, val h: Int, val argb: IntArray)

/** Luminance statistics of a screen region, as seen through the glass. */
class RegionStats(
    val mean: Float,
    val p10: Float,
    val p90: Float,
    /** p90 - p10: how patchy the region is. */
    val spread: Float,
    /** Average raw backdrop color in LINEAR light (0..1), before the glass color path. */
    val meanR: Float = 0.5f,
    val meanG: Float = 0.5f,
    val meanB: Float = 0.5f,
)

/**
 * Measures the region [x0,y0,x1,y1] (screen px). The region is grown by the bezel so the distorted rim
 * is included, since refraction pulls background in from there. Pixels are run through the glass color path first.
 */
fun analyzeRegion(map: LumaMap, screenW: Int, screenH: Int, x0: Float, y0: Float, x1: Float, y1: Float, grow: Float, dim: Float = 0f): RegionStats {
    // [grow] may be negative (round shapes: the box corners are not part of the glass)
    val kx = map.w.toFloat() / screenW
    val ky = map.h.toFloat() / screenH
    val ax = (((x0 - grow) * kx).toInt()).coerceIn(0, map.w - 1)
    val ay = (((y0 - grow) * ky).toInt()).coerceIn(0, map.h - 1)
    val bx = (((x1 + grow) * kx).toInt()).coerceIn(ax, map.w - 1)
    val by = (((y1 + grow) * ky).toInt()).coerceIn(ay, map.h - 1)
    val ls = FloatArray((bx - ax + 1) * (by - ay + 1))
    var k = 0
    var sum = 0f
    var sr = 0f; var sg = 0f; var sb = 0f
    for (y in ay..by) for (x in ax..bx) {
        var px = map.argb[y * map.w + x]
        if (dim > 0f) {
            val k = 1f - dim
            px = (0xFF shl 24) or ((((px shr 16) and 255) * k).toInt() shl 16) or ((((px shr 8) and 255) * k).toInt() shl 8) or ((px and 255) * k).toInt()
        }
        val l = relLuminance(seenThroughGlass(px))
        ls[k++] = l
        sum += l
        sr += LIN[(px shr 16) and 255]; sg += LIN[(px shr 8) and 255]; sb += LIN[px and 255]
    }
    ls.sort()
    val cnt = ls.size
    val p10 = ls[((cnt - 1) * 0.10f).toInt()]
    val p90 = ls[((cnt - 1) * 0.90f).toInt()]
    return RegionStats(sum / cnt, p10, p90, p90 - p10, sr / cnt, sg / cnt, sb / cnt)
}

/** The decision for one glass element. */
class FgChoice(
    val fg: Int,
    /** 0..1, black or white scrim opacity to add behind the glass (0 = none). */
    val scrim: Float,
    val scrimIsDark: Boolean,
    /** 0..1: how much a busy backdrop is calmed toward its average (last resort, only when a scrim is not enough). */
    val calm: Float,
    /** Text-shadow alpha (0 = none); the shadow is the opposite color of [fg]. */
    val shadow: Float,
    /** Predicted worst-case contrast of [fg] against the region, after scrim and calm. */
    val contrast: Float,
    val usesLight: Boolean,
    /** Luminance of the background right behind the text (worst case for [fg], after scrim and calm). */
    val bgL: Float = 0.5f,
)

/** Luminance after a black or white scrim. Scrims blend in sRGB (gamma) space, so convert there and back. */
private fun lumAfterScrim(l: Float, scrim: Float, dark: Boolean): Float {
    val c = l.coerceAtLeast(0f).pow(1f / 2.2f)
    val c2 = if (dark) c * (1f - scrim) else c * (1f - scrim) + scrim
    return c2.pow(2.2f)
}

/** Contrast of white against a background of luminance [l]. */
fun contrastWhite(l: Float) = contrastRatio(1f, l)
fun contrastDark(l: Float) = contrastRatio(relLuminance(LG.FG_DARK), l)

/**
 * Picks light or dark foreground and the least help it needs.
 *
 * 1. Compare the WCAG contrast of white and near-black (not a 50% threshold), judging each by its worst case
 *    (white by the bright 90th percentile, dark by the dark 10th), blended with the mean.
 * 2. If the winner is below [target], search for the cheapest combination of scrim (preferred) and calm
 *    (last resort, flattens a patchy backdrop) that reaches it.
 * 3. [prevLight] (if known) adds hysteresis: the color only flips if the other is clearly better.
 */
fun chooseForeground(s: RegionStats, target: Float, prevLight: Boolean?): FgChoice {
    val wt = LG.WORST_WEIGHT
    fun lightL(calm: Float) = max(s.mean, s.mean + wt * (s.p90 - s.mean) * (1f - calm))
    fun darkL(calm: Float) = min(s.mean, s.mean + wt * (s.p10 - s.mean) * (1f - calm))
    fun cLight(sc: Float, calm: Float) = contrastWhite(lumAfterScrim(lightL(calm), sc, true))
    fun cDark(sc: Float, calm: Float) = contrastDark(lumAfterScrim(darkL(calm), sc, false))

    class Plan(val scrim: Float, val calm: Float, val contrast: Float, val ok: Boolean, val bgL: Float) {
        val cost get() = scrim + 0.5f * calm
    }
    fun plan(light: Boolean): Plan {
        var best: Plan? = null
        var calm = 0f
        while (calm <= LG.CALM_MAX + 1e-4f) {
            var sc = 0f
            while (sc <= LG.MAX_SCRIM + 1e-4f) {
                val c = if (light) cLight(sc, calm) else cDark(sc, calm)
                if (c >= target) {
                    val p = Plan(sc, calm, c, true, if (light) lumAfterScrim(lightL(calm), sc, true) else lumAfterScrim(darkL(calm), sc, false))
                    if (best == null || p.cost < best.cost - 1e-4f) best = p
                    break // more scrim at this calm only costs more
                }
                sc += 0.01f
            }
            calm += 0.1f
        }
        return best ?: Plan(
            LG.MAX_SCRIM, LG.CALM_MAX,
            if (light) cLight(LG.MAX_SCRIM, LG.CALM_MAX) else cDark(LG.MAX_SCRIM, LG.CALM_MAX), false,
            if (light) lumAfterScrim(lightL(LG.CALM_MAX), LG.MAX_SCRIM, true) else lumAfterScrim(darkL(LG.CALM_MAX), LG.MAX_SCRIM, false),
        )
    }
    val pl = plan(true)
    val pd = plan(false)
    val w0 = cLight(0f, 0f)
    val d0 = cDark(0f, 0f)
    var useLight = when (prevLight) {
        true -> !(d0 > w0 * LG.HYSTERESIS)
        false -> w0 > d0 * LG.HYSTERESIS
        null -> w0 >= d0
    }
    // if the contrast winner needs a lot of help and the other needs clearly less, go with the cheaper one
    val chosen0 = if (useLight) pl else pd
    val other0 = if (useLight) pd else pl
    if (chosen0.cost > 0.12f && other0.ok && other0.cost < chosen0.cost - 0.06f) useLight = !useLight
    val pick = if (useLight) pl else pd
    val busy = s.spread > 0.18f
    return FgChoice(
        fg = if (useLight) LG.FG_LIGHT else LG.FG_DARK,
        scrim = pick.scrim,
        scrimIsDark = useLight,
        calm = pick.calm,
        shadow = if (busy || pick.contrast < target + 0.8f) 0.24f else 0f,
        contrast = pick.contrast,
        usesLight = useLight,
        bgL = pick.bgL,
    )
}

/**
 * Nudges a hue-bearing color ([argb], usually the wallpaper accent) lighter or darker until it reaches [target]
 * contrast on a background of luminance [bgL]. Keeps the hue so accent text stays "accent", but legible.
 */
fun adaptAccent(argb: Int, bgL: Float, target: Float): Int {
    var r = ((argb shr 16) and 255) / 255f
    var g = ((argb shr 8) and 255) / 255f
    var b = (argb and 255) / 255f
    val towardWhite = contrastWhite(bgL) >= contrastDark(bgL)
    for (i in 0..20) {
        val l = relLuminance(
            (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt(),
        )
        if (contrastRatio(l, bgL) >= target) break
        val t = 0.1f
        if (towardWhite) { r += (1f - r) * t; g += (1f - g) * t; b += (1f - b) * t }
        else { r *= (1f - t); g *= (1f - t); b *= (1f - t) }
    }
    return (0xFF shl 24) or ((clamp01(r) * 255 + 0.5f).toInt() shl 16) or ((clamp01(g) * 255 + 0.5f).toInt() shl 8) or
        (clamp01(b) * 255 + 0.5f).toInt()
}

// ============================================================================================
// Building the backdrop + luminance map from a wallpaper
// ============================================================================================

/** Average-downscale an ARGB image to [ow]x[oh]. */
fun boxDownArgb(src: IntArray, w: Int, h: Int, ow: Int, oh: Int): IntArray {
    val o = IntArray(ow * oh)
    for (y in 0 until oh) for (x in 0 until ow) {
        val x0 = x * w / ow; val x1 = max(x0 + 1, (x + 1) * w / ow)
        val y0 = y * h / oh; val y1 = max(y0 + 1, (y + 1) * h / oh)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (yy in y0 until y1) for (xx in x0 until x1) {
            val p = src[yy * w + xx]
            r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
        }
        o[y * ow + x] = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
    return o
}

class LiquidData(val backdrop: LgBackdrop, val luma: LumaMap)

/**
 * [px] is the wallpaper already cropped to the screen's aspect and scaled to [w]x[h] (about 960 wide).
 * Result: the backdrop the glass looks through (launcher scrims applied, small blur) and the ~64px luminance map.
 * [screenWidthPx] sets the blur: LG.BLUR_PX is in screen pixels.
 */
fun buildLiquidData(px: IntArray, w: Int, h: Int, scrimA: Float, screenWidthPx: Int): LiquidData {
    val scrimmed = applyLauncherScrims(px, w, h, scrimA)
    // blur sigma in backdrop px = BLUR_PX * (backdrop width / screen width); two passes of a box give sigma ~ r
    val sigma = LG.BLUR_PX * w.toFloat() / max(1, screenWidthPx)
    val r = sigma.toInt().coerceIn(1, 4)
    val blurred = blurArgb(scrimmed, w, h, r, 2)
    val lw = LG.SAMPLE_W
    val lh = max(8, (lw.toFloat() * h / w).toInt())
    return LiquidData(LgBackdrop(w, h, blurred), LumaMap(lw, lh, boxDownArgb(scrimmed, w, h, lw, lh)))
}
