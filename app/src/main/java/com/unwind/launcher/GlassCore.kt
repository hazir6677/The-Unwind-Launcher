package com.unwind.launcher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Glass core: the colour math behind the "Clear" tint style. Plain Kotlin on purpose (no Android
 * classes) so it can be run and tested on a normal JVM. ClearGlass.kt wraps it for Compose.
 *
 * Nothing in here touches pixels per panel any more. The old version baked a refraction image for
 * every panel on the CPU (millions of pixel operations per panel), which is what made panes and
 * Preferences slow. Now the work is limited to:
 *   - one small blurred copy of the wallpaper (built once per wallpaper), and
 *   - a few arithmetic checks per panel that decide light or dark text.
 * Edge light, depth and translucency are drawn by the GPU as ordinary gradients and paths.
 *
 * Everything you are likely to tune is in [LG] below.
 */

/** Tunable constants. Distances are dp unless noted. */
object LG {
    // ---- Look (drawn on the GPU) ----
    /** Width of the lens band along the edge, as a fraction of the shortest side, and its limits in dp. */
    const val EDGE_FRACTION = 0.14f
    const val EDGE_MIN_DP = 4f
    const val EDGE_MAX_DP = 22f
    /** How much the wallpaper is pulled in at the very edge (0.93 = 7%). Two steps fake the curve of a lens. */
    const val EDGE_ZOOM_OUTER = 0.93f
    const val EDGE_ZOOM_INNER = 0.965f
    const val RIM_WIDTH_DP = 1.2f
    const val SHEEN_ALPHA = 0.16f
    const val SHADOW_ALPHA = 0.14f

    // ---- Color ----
    const val TINT_WHITE = 0.08f
    /** Faint dark tint that fades in over bright backgrounds so the panel stays legible. */
    const val TINT_DARK_MAX = 0.10f

    // ---- Legibility ----
    const val TEXT_CONTRAST = 4.5f
    const val ICON_CONTRAST = 3.0f
    const val SOLVER_MARGIN = 0.8f
    const val HYSTERESIS = 1.12f
    const val CALM_MAX = 0.92f
    const val WORST_WEIGHT = 0.9f
    const val MAX_SCRIM = 0.30f
    const val COLOR_FADE_MS = 200
    /** Wallpaper map resolution (width in px). */
    const val SAMPLE_W = 64
    val FG_LIGHT = 0xFFFFFFFF.toInt()
    val FG_DARK = 0xFF141416.toInt()
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

/** What a panel looks like to the eye for a given backdrop pixel: the wallpaper under a faint white tint and dark adaptation. */
fun seenThroughGlass(argb: Int): Int {
    var r = ((argb shr 16) and 255) / 255f
    var g = ((argb shr 8) and 255) / 255f
    var b = (argb and 255) / 255f
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

/** The small wallpaper copy the Clear style looks through (scrims applied, lightly blurred) and the ~64px luminance map. */
class ClearData(val w: Int, val h: Int, val blurred: IntArray, val luma: LumaMap)

/**
 * [px] is the wallpaper already cropped to the screen's aspect and scaled to [w]x[h] (about 480 wide).
 * One pass over a few hundred thousand pixels at most, done once per wallpaper on a background thread.
 */
fun buildClearData(px: IntArray, w: Int, h: Int, scrimA: Float): ClearData {
    val scrimmed = applyLauncherScrims(px, w, h, scrimA)
    val blurred = blurArgb(scrimmed, w, h, 1, 2)
    val lw = LG.SAMPLE_W
    val lh = max(8, (lw.toFloat() * h / w).toInt())
    return ClearData(w, h, blurred, LumaMap(lw, lh, boxDownArgb(scrimmed, w, h, lw, lh)))
}
