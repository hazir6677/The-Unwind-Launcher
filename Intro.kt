package com.unwind.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------
//  How the intro stays smooth on a TV
//
//  * ONE clock (0..1 over TIMELINE_MS) drives everything. Each piece reads it only inside a
//    graphicsLayer / draw block, so nothing recomposes while it plays: it is all draw-phase work.
//  * Text fades with ModulateAlpha, which fades the glyphs directly instead of rendering every
//    block into its own off-screen buffer.
//  * The glass card is painted into its own cached layer once, and then only moved, scaled and
//    faded as a finished texture. Re-running the glass shaders every frame was the stutter.
//  * The dark scrim is a plain color alpha, not a full-screen layer.
// ---------------------------------------------------------------------------------------

private const val TIMELINE_MS = 2600f

/** Fast start, very long soft landing. The "iOS" feel. */
private val OutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Same, with a small overshoot, for the leaf. */
private val OutBack = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)

private typealias Clock = Animatable<Float, AnimationVector1D>

/** Progress (0..1, eased) of the part of the timeline that runs from [startMs] for [durMs]. */
private fun seg(p: Float, startMs: Float, durMs: Float, easing: Easing = OutExpo): Float =
    easing.transform(((p * TIMELINE_MS - startMs) / durMs).coerceIn(0f, 1f))

/** Fades in and rises a little, starting at [startMs]. Costs nothing but a layer property change per frame. */
private fun Modifier.enter(clock: Clock, startMs: Float, durMs: Float = 700f, rise: Float = 16f): Modifier =
    graphicsLayer {
        val t = seg(clock.value, startMs, durMs)
        alpha = t
        translationY = (1f - t) * rise * density
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/** The leaf mark, drawn as a vector so it can take its color from the wallpaper. It pops in, glows, and draws its vein. */
@Composable
private fun LeafMark(clock: Clock) {
    val accent = LocalUi.current.wp.accent
    val drift by rememberInfiniteTransition(label = "leaf").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "leafDrift",
    )
    Box(
        Modifier
            .size(78.dp, 90.dp)
            .graphicsLayer {
                val t = seg(clock.value, 150f, 950f, OutBack)
                val s = 0.55f + 0.45f * t
                scaleX = s
                scaleY = s
                rotationZ = (1f - t) * -14f
                alpha = seg(clock.value, 150f, 450f, LinearEasing)
                translationY = (drift - 0.5f) * 8.dp.toPx()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val leaf = Path().apply {
                    moveTo(w * 0.12f, h * 0.95f)
                    cubicTo(w * 0.02f, h * 0.45f, w * 0.40f, h * 0.05f, w * 0.98f, h * 0.02f)
                    cubicTo(w * 1.00f, h * 0.55f, w * 0.62f, h * 0.98f, w * 0.12f, h * 0.95f)
                    close()
                }
                val vein = Path().apply {
                    moveTo(w * 0.15f, h * 0.92f)
                    cubicTo(w * 0.30f, h * 0.55f, w * 0.55f, h * 0.30f, w * 0.86f, h * 0.14f)
                }
                val measure = PathMeasure().also { it.setPath(vein, false) }
                val veinLen = measure.length
                val part = Path()
                val fill = Brush.linearGradient(
                    listOf(lerp(accent, Color.White, 0.30f), accent),
                    start = Offset(w, 0f),
                    end = Offset(0f, h),
                )
                val glow = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w / 2f, h / 2f),
                    radius = w,
                )
                val veinStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    drawCircle(glow, radius = w, center = Offset(w / 2f, h / 2f), alpha = seg(clock.value, 300f, 900f))
                    drawPath(leaf, fill)
                    part.reset()
                    measure.getSegment(0f, veinLen * seg(clock.value, 550f, 800f), part, true)
                    drawPath(part, Color(0x55102010), style = veinStroke)
                }
            },
    )
}

/** The Indian flag, drawn by hand so it looks the same on every TV (no emoji font needed). */
@Composable
private fun IndiaFlag() {
    Canvas(Modifier.size(30.dp, 20.dp).clip(RoundedCornerShape(3.dp))) {
        val band = size.height / 3f
        drawRect(Color(0xFFFF9933), Offset(0f, 0f), Size(size.width, band))
        drawRect(Color.White, Offset(0f, band), Size(size.width, band))
        drawRect(Color(0xFF138808), Offset(0f, band * 2f), Size(size.width, band))
        val c = center
        val r = band * 0.42f
        val navy = Color(0xFF000080)
        drawCircle(navy, r, c, style = Stroke(width = 0.8.dp.toPx()))
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0)
            drawLine(
                navy,
                c,
                Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                strokeWidth = 0.4.dp.toPx(),
            )
        }
    }
}

@Composable
private fun IntroBlock(label: String, body: String) {
    val ui = LocalUi.current
    Text(label.uppercase(), color = ui.wp.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
    Spacer(Modifier.height(4.dp))
    Text(body, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 19.sp)
}

/** The welcome screen shown the first time Unwind opens. OK on "Get started" (or Back) closes it. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val ui = LocalUi.current
    val focus = remember { FocusRequester() }
    val clock = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val scrim = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.78f))) }
    val shape = RoundedCornerShape(36.dp)

    LaunchedEffect(Unit) { clock.animateTo(1f, tween(TIMELINE_MS.toInt(), easing = LinearEasing)) }
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(leaving) {
        if (leaving) {
            exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            onDone()
        }
    }
    val leave = { if (!leaving) leaving = true }
    BackHandler(enabled = true) { leave() }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrim, alpha = seg(clock.value, 0f, 450f, LinearEasing) * (1f - exit.value)) }
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(760.dp)
                .graphicsLayer {
                    val t = seg(clock.value, 0f, 1000f)
                    val x = 1f - exit.value
                    val s = (0.92f + 0.08f * t) * (0.97f + 0.03f * x)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - t) * 28.dp.toPx() + exit.value * 10.dp.toPx()
                    alpha = x
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithContent {
                    drawContent()
                    // One soft light sweep across the glass as the card settles
                    val t = seg(clock.value, 350f, 1100f, FastOutSlowInEasing)
                    if (t > 0f && t < 1f) {
                        val c = -0.25f + 1.5f * t
                        val sheen = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                (c - 0.14f).coerceIn(0f, 1f) to Color.Transparent,
                                c.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.16f),
                                (c + 0.14f).coerceIn(0f, 1f) to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height * 0.55f),
                        )
                        drawRoundRect(sheen, cornerRadius = CornerRadius(36.dp.toPx()))
                    }
                },
        ) {
            // The glass is its own cached layer: painted once, then only faded as a texture
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = seg(clock.value, 0f, 600f, LinearEasing)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .glass(shape),
            )
            Column(Modifier.padding(horizontal = 40.dp, vertical = 30.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Left: the mark, the name and a one-line promise
                    Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LeafMark(clock)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "unwind",
                            modifier = Modifier.enter(clock, 550f, 800f, 14f),
                            color = Color(0xFFF7F5E8),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            "A calmer home screen for your TV",
                            modifier = Modifier.enter(clock, 800f, 700f, 10f),
                            color = ui.wp.accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(150.dp)
                            .graphicsLayer {
                                scaleY = seg(clock.value, 700f, 800f)
                                alpha = seg(clock.value, 700f, 400f, LinearEasing)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .background(Color.White.copy(alpha = 0.22f)),
                    )
                    Spacer(Modifier.width(24.dp))
                    // Right: what it is, who it's for
                    Column(Modifier.weight(1f)) {
                        Column(Modifier.enter(clock, 950f, 750f)) {
                            IntroBlock(
                                "What it is",
                                "Your wallpaper, your apps and a little glass. No rows of recommendations, no ads, nothing shouting for your attention. Just the things you actually open.",
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.enter(clock, 1200f, 750f)) {
                            IntroBlock(
                                "Who it's for",
                                "Anyone who turns the TV on to relax, not to scroll. If you open the same few apps every night, say YouTube and Netflix, they sit right here, one click away.",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .enter(clock, 1450f, 600f, 0f)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).enter(clock, 1550f, 700f, 12f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Built with Love, and some coding here and there", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("by Hazir from", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            IndiaFlag()
                        }
                    }
                    Box(Modifier.enter(clock, 1800f, 650f, 12f)) {
                        ActionCard("Get started", Modifier.focusRequester(focus), 160.dp) { leave() }
                    }
                }
            }
        }
    }
}
