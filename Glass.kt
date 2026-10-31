package com.unwind.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

fun decodeRes(ctx: Context, name: String, sample: Int): Bitmap? {
    val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
    if (id == 0) return null
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeResource(ctx.resources, id, o)
}

class Backdrops(val soft: ImageBitmap, val heavy: ImageBitmap)

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

/** Pre-blurred copies of the wallpaper. Glass elements draw these so they look like real frosted glass. */
fun makeBackdrops(ctx: Context, name: String): Backdrops? {
    val src = decodeRes(ctx, name, 8) ?: return null
    fun blurred(div: Int, r: Int, passes: Int): ImageBitmap {
        val w = max(24, src.width / div)
        val h = max(14, src.height / div)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val a = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            blurPass(a, b, w, h, r, true)
            blurPass(b, a, w, h, r, false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out.asImageBitmap()
    }
    return Backdrops(blurred(2, 2, 2), blurred(6, 2, 3))
}

/**
 * Draws a glass surface in one of three styles. The wallpaper behind it is drawn blurred and
 * aligned to the screen, so it reads as real background blur.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    focused: Boolean = false,
    style: GlassStyle? = null,
    blur: Boolean = true,
): Modifier {
    val ui = LocalUi.current
    val st = style ?: ui.settings.glass
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    val tint = ui.wp.tint
    val backdrop = if (st == GlassStyle.Frost) ui.heavy else ui.soft
    val screen = ui.size

    val edge: Brush = if (focused) {
        SolidColor(Color.White)
    } else when (st) {
        GlassStyle.Liquid -> Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.45f to Color.White.copy(alpha = 0.10f),
            1f to Color.White.copy(alpha = 0.65f),
        )
        GlassStyle.Normal -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
        )
        GlassStyle.Frost -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.15f))
        )
    }
    val edgeWidth = if (focused) 2.5.dp else if (st == GlassStyle.Liquid) 1.5.dp else 1.dp

    return this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
        .clip(shape)
        .drawBehind {
            if (blur && backdrop != null && screen.width > 0) {
                drawImage(
                    backdrop,
                    dstOffset = IntOffset(-pos.x, -pos.y),
                    dstSize = screen,
                    filterQuality = FilterQuality.Medium,
                )
            }
            when (st) {
                GlassStyle.Normal -> {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                tint.copy(alpha = if (focused) 0.55f else 0.34f),
                                tint.copy(alpha = if (focused) 0.75f else 0.55f),
                            )
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.12f),
                            0.4f to Color.Transparent,
                        )
                    )
                }
                GlassStyle.Liquid -> {
                    drawRect(Color.White.copy(alpha = if (focused) 0.20f else 0.08f))
                    drawRect(tint.copy(alpha = 0.12f))
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                            center = Offset(size.width * 0.25f, 0f),
                            radius = max(size.width, size.height) * 0.85f,
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.7f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.14f),
                        )
                    )
                }
                GlassStyle.Frost -> {
                    drawRect(Color.White.copy(alpha = if (focused) 0.34f else 0.22f))
                    drawRect(tint.copy(alpha = 0.22f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.35f to Color.Transparent,
                        )
                    )
                }
            }
        }
        .border(edgeWidth, edge, shape)
}
