package com.unwind.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The little nature sounds for moving around. There are no audio files in the app: each sound is
 * generated the first time Unwind runs (a few milliseconds of math), saved as a tiny WAV in the
 * cache and played through SoundPool, which is built for short, instant sounds. Every play is
 * nudged a few percent up or down in pitch so it never sounds like a machine repeating itself.
 */
object Sfx {
    private const val RATE = 44100
    private const val MIN_GAP_MS = 70L

    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<SoundPack, Int>()
    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>()
    private val rnd = Random()
    private var lastAt = 0L

    @Volatile var pack: SoundPack = SoundPack.Droplet
    @Volatile var volume: Float = SoundVolume.Medium.gain

    fun init(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        pool = sp
        val dir = File(ctx.cacheDir, "sfx")
        Thread {
            dir.mkdirs()
            for (p in SoundPack.values()) {
                if (p == SoundPack.Off) continue
                val f = File(dir, "${p.name.lowercase()}_v1.wav")
                if (!f.exists()) runCatching { f.writeBytes(wav(synth(p))) }
                if (f.exists()) ids[p] = sp.load(f.path, 1)
            }
        }.start()
    }

    /** Plays when the highlight lands on something. Quiet gaps are kept so fast scrolling stays gentle. */
    fun move() {
        if (pack == SoundPack.Off) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MS) return
        lastAt = now
        play(pack, volume)
    }

    /** For the picker: plays one sound even if the saved choice is something else. */
    fun play(p: SoundPack, vol: Float) {
        if (p == SoundPack.Off) return
        val sp = pool ?: return
        val id = ids[p] ?: return
        if (id !in loaded) return
        val rate = 0.93f + rnd.nextFloat() * 0.16f
        sp.play(id, vol, vol, 1, 0, rate)
    }

    // ---------- Sound design ----------

    private fun synth(p: SoundPack): ShortArray {
        val secs = when (p) {
            SoundPack.Droplet -> 0.30
            SoundPack.Chime -> 0.55
            SoundPack.Bamboo -> 0.16
            SoundPack.Bird -> 0.20
            SoundPack.Pebble -> 0.13
            SoundPack.Off -> 0.01
        }
        val n = (RATE * secs).toInt()
        val f = FloatArray(n)
        val tau = 2.0 * PI
        val noise = Random(7)
        when (p) {
            SoundPack.Droplet -> {
                // A bubble: the pitch climbs fast while the note fades, then two faint echoes like a pond
                var ph = 0.0
                for (i in 0 until n) {
                    val t = i / RATE.toDouble()
                    val freq = 520.0 + 980.0 * (1.0 - exp(-t / 0.022))
                    ph += tau * freq / RATE
                    val env = (1.0 - exp(-t / 0.002)) * exp(-t / 0.05)
                    f[i] = (sin(ph) * env + 0.18 * sin(ph * 2.01) * env * env).toFloat()
                }
                echo(f, (0.052 * RATE).toInt(), 0.22f)
                echo(f, (0.097 * RATE).toInt(), 0.10f)
            }
            SoundPack.Chime -> {
                // Glass-like partials that are not whole multiples, each dying at its own speed
                val base = 1568.0
                val ratio = doubleArrayOf(1.0, 2.76, 5.40, 8.93)
                val amp = doubleArrayOf(1.0, 0.45, 0.20, 0.08)
                val decay = doubleArrayOf(0.16, 0.09, 0.05, 0.03)
                for (i in 0 until n) {
                    val t = i / RATE.toDouble()
                    var v = 0.0
                    for (k in ratio.indices) v += amp[k] * sin(tau * base * ratio[k] * t) * exp(-t / decay[k])
                    f[i] = (v * (1.0 - exp(-t / 0.001))).toFloat()
                }
            }
            SoundPack.Bamboo -> {
                // A hollow tube: a short low note, a brighter overtone and a tiny wooden click
                for (i in 0 until n) {
                    val t = i / RATE.toDouble()
                    val tone = sin(tau * 660.0 * t) * exp(-t / 0.028) + 0.5 * sin(tau * 1330.0 * t) * exp(-t / 0.015)
                    val click = (noise.nextFloat() * 2f - 1f) * exp(-t / 0.002) * 0.6
                    f[i] = (tone + click).toFloat()
                }
            }
            SoundPack.Bird -> {
                // Two quick rising notes
                fun note(start: Double, len: Double, f0: Double, f1: Double) {
                    val s = (start * RATE).toInt()
                    val m = (len * RATE).toInt()
                    var ph = 0.0
                    for (j in 0 until m) {
                        if (s + j >= n) break
                        val u = j / m.toDouble()
                        ph += tau * (f0 + (f1 - f0) * u * u) / RATE
                        val env = sin(PI * u).pow(2.0)
                        f[s + j] += (sin(ph) * env * 0.8 + 0.12 * sin(ph * 2.0) * env).toFloat()
                    }
                }
                note(0.0, 0.060, 2300.0, 3500.0)
                note(0.075, 0.050, 2700.0, 3900.0)
            }
            SoundPack.Pebble -> {
                // A soft low "tock" that drops in pitch, with a little grit
                var ph = 0.0
                for (i in 0 until n) {
                    val t = i / RATE.toDouble()
                    ph += tau * (190.0 + 260.0 * exp(-t / 0.012)) / RATE
                    val env = (1.0 - exp(-t / 0.0015)) * exp(-t / 0.022)
                    f[i] = (sin(ph) * env + (noise.nextFloat() * 2f - 1f) * exp(-t / 0.0025) * 0.35).toFloat()
                }
            }
            SoundPack.Off -> {}
        }
        // Normalize, then fade the last few milliseconds so it never ends on a click
        var peak = 0f
        for (v in f) peak = maxOf(peak, abs(v))
        val scale = if (peak > 0f) 0.85f / peak else 1f
        val fade = (0.008 * RATE).toInt().coerceAtMost(n)
        return ShortArray(n) { i ->
            val tail = if (i >= n - fade) (n - i) / fade.toFloat() else 1f
            (f[i] * scale * tail * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun echo(f: FloatArray, delay: Int, gain: Float) {
        for (i in delay until f.size) f[i] += f[i - delay] * gain
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val bytes = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + bytes); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(RATE); bb.putInt(RATE * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(bytes)
        for (s in pcm) bb.putShort(s)
        return bb.array()
    }
}

/** Add to anything focusable that does not already call [Sfx.move] itself. */
fun Modifier.moveSound(): Modifier = this.onFocusChanged { if (it.isFocused) Sfx.move() }
