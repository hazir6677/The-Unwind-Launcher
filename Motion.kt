package com.unwind.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One switch for all the bounce in the UI. Every focus scale, glow and highlight slide already goes
 * through [focusSpring], and screens and popups come in with [bounceIn], so turning this off in
 * Preferences calms everything at once and the two never drift apart.
 */
object Motion {
    @Volatile var bouncy: Boolean = true

    /** True while the launcher is in the background (set from the activity). The launch animation uses it to tell "an app opened" from "the app failed to open". */
    @Volatile var paused: Boolean = false

    // Lower damping = more wobble. 0.40 overshoots by about a quarter of the move, then settles.
    const val BOUNCE_DAMPING = 0.40f
    const val BOUNCE_STIFFNESS = 300f
}

/** The spring behind every focus animation. Bouncy when enabled, smooth and settled when not. */
fun focusSpring(): SpringSpec<Float> =
    if (Motion.bouncy) spring(Motion.BOUNCE_DAMPING, Motion.BOUNCE_STIFFNESS) else spring(1f, 500f)

/**
 * Pops a screen, tab or popup in: it rises a few dp, grows from just under full size and overshoots
 * a little before it settles. [key] restarts the animation (use the screen or tab that changed).
 */
@Composable
fun Modifier.bounceIn(key: Any, rise: Dp = 18.dp): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        p.snapTo(0f)
        if (Motion.bouncy) p.animateTo(1f, spring(0.55f, 170f)) else p.animateTo(1f, tween(200))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = (v * 1.6f).coerceIn(0f, 1f)
        val s = 0.97f + 0.03f * v
        scaleX = s
        scaleY = s
        translationY = (1f - v) * rise.toPx()
        // fade the glyphs and shapes directly; the default would render the whole screen into an extra buffer every frame
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
