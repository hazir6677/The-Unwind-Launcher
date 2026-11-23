package com.unwind.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Saves the whole launcher setup into one `.unwind` file (a zip inside) and puts it back later.
 *
 * It holds every saved preference (theme, glass, shapes, home row apps in order, folders, greetings,
 * weather city, sound and motion choices) and the wallpaper pictures. Because it copies all
 * preferences generically, anything added in later versions is included without touching this file.
 */
object Backup {
    private const val FORMAT = 1
    private const val MAX_FILE = 40L * 1024 * 1024
    private const val MAX_PREFS = 2L * 1024 * 1024

    /** Things that describe "right now" rather than your setup, so they are neither saved nor restored. */
    private val SKIP = setOf("bingChecked", "bingId", "bingTitle", "updLastCheck")
    private val KEY_OK = Regex("^[A-Za-z0-9_]{1,64}$")
    private val WALLPAPER_FILES = listOf("custom_wallpaper.jpg", "bing_wallpaper.jpg")

    fun suggestedName(): String =
        "Unwind-setup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".unwind"

    /** Where setups are kept when the TV has no file browser. */
    fun appDir(ctx: Context): File = (ctx.getExternalFilesDir("Backups") ?: File(ctx.filesDir, "Backups")).apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        appDir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".unwind") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun saveToAppDir(ctx: Context): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val f = File(appDir(ctx), "Unwind-setup-$stamp.unwind")
        f.outputStream().use { write(ctx, it) }
        return f
    }

    fun write(ctx: Context, out: OutputStream) {
        val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            if (k in SKIP || !KEY_OK.matches(k) || v == null) continue
            val o = JSONObject()
            when (v) {
                is String -> o.put("t", "s").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is Set<*> -> o.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            p.put(k, o)
        }
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("app", ctx.packageName)
            .put("appVersion", currentVersion(ctx))
            .put("savedAt", System.currentTimeMillis())
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("prefs.json")); z.write(p.toString().toByteArray()); z.closeEntry()
            for (name in WALLPAPER_FILES) {
                val f = File(ctx.filesDir, name)
                if (!f.exists() || f.length() > MAX_FILE) continue
                z.putNextEntry(ZipEntry("files/$name"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Reads and checks the whole file first and only then changes anything, so a bad file can't half-apply. */
    fun read(ctx: Context, input: InputStream): Int {
        var manifest: JSONObject? = null
        var prefsJson: JSONObject? = null
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val limit = if (e.name.startsWith("files/")) MAX_FILE else MAX_PREFS
                val bytes = readLimited(z, limit)
                when {
                    e.name == "manifest.json" -> manifest = JSONObject(String(bytes))
                    e.name == "prefs.json" -> prefsJson = JSONObject(String(bytes))
                    e.name.startsWith("files/") -> files[e.name.removePrefix("files/")] = bytes
                }
                z.closeEntry()
            }
        }
        val m = manifest ?: throw IllegalStateException("That isn't an Unwind setup file.")
        val p = prefsJson ?: throw IllegalStateException("That isn't an Unwind setup file.")
        if (m.optInt("format", 0) !in 1..FORMAT) throw IllegalStateException("That setup file is from a newer version of Unwind. Update first.")

        val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val ed = prefs.edit()
        for (k in prefs.all.keys) if (k !in SKIP) ed.remove(k)
        var count = 0
        for (k in p.keys()) {
            if (k in SKIP || !KEY_OK.matches(k)) continue
            val o = p.getJSONObject(k)
            when (o.getString("t")) {
                "s" -> ed.putString(k, o.getString("v"))
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "ss" -> {
                    val a = o.getJSONArray("v")
                    ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
                else -> continue
            }
            count++
        }
        ed.commit()
        for ((name, bytes) in files) {
            if (name !in WALLPAPER_FILES) continue
            val tmp = File(ctx.filesDir, "$name.tmp")
            tmp.writeBytes(bytes)
            val dest = File(ctx.filesDir, name)
            dest.delete()
            tmp.renameTo(dest)
        }
        return count
    }

    private fun readLimited(z: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = z.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw IllegalStateException("That file is too big to be an Unwind setup.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
