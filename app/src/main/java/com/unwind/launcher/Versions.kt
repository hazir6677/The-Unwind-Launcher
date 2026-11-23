package com.unwind.launcher

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/** Where release notes (and, in the GitHub version, new APKs) are published. */
const val UPDATE_REPO = "hazir6677/The-Unwind-Launcher"

/** How often the launcher looks for a new version on its own. */
const val UPDATE_EVERY_MS = 6 * 60 * 60 * 1000L

/**
 * What the rest of the app needs from an updater. There are two builds of Unwind and each has its own
 * UpdateController: the GitHub one downloads and installs updates, the F-Droid one only checks F-Droid.
 */
interface AppUpdater {
    /** Installed version, read from this app's own package info. Never comes from the network. */
    val current: String
    val checkedAt: Long
    /** True while a newer version is known. Drives the little dot and the banner. */
    val hasUpdate: Boolean
    /** The newer version's number while one is known, otherwise null. */
    val pendingVersion: String?
    fun check(manual: Boolean)
    fun onResume() {}
}

// ---------- Versions ----------

fun currentVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

private fun parseVersion(v: String): List<Int> =
    v.trim().trimStart('v', 'V').split('.', '-', '_').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

/** "v2.5" vs "2.4": true when [remote] is a higher version than [local]. */
fun isNewer(remote: String, local: String): Boolean {
    val a = parseVersion(remote)
    val b = parseVersion(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

// ---------- Comparing versions ----------

/** How the version on F-Droid compares with the installed one. */
enum class Relation { Newer, Same, Ahead }

fun relationOf(remote: String, local: String): Relation = when {
    isNewer(remote, local) -> Relation.Newer
    isNewer(local, remote) -> Relation.Ahead
    else -> Relation.Same
}

/** Turns a GitHub release's markdown body into plain lines for the TV (also used by What's New). */
fun tidyNotes(body: String): String =
    body.replace("\r", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("[*`>_]"), "").replace(Regex("\n{3,}"), "\n\n").trim()

// ---------- Release notes (GitHub, read-only) ----------

internal fun getJson(url: String): JSONObject? {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    c.setRequestProperty("Accept", "application/vnd.github+json")
    c.setRequestProperty("User-Agent", "UnwindLauncher")
    try {
        when (c.responseCode) {
            200 -> {}
            404 -> return null
            403, 429 -> throw IllegalStateException("GitHub asked us to slow down. Try again in a little while.")
            else -> throw IllegalStateException("GitHub answered with error ${c.responseCode}.")
        }
        return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally {
        c.disconnect()
    }
}

/** The release published under the tag of the installed version (v2.4 or 2.4), or null if that build was never published. */
fun fetchReleaseNotesFor(version: String): String? {
    for (tag in listOf("v$version", version)) {
        val j = runCatching { getJson("https://api.github.com/repos/$UPDATE_REPO/releases/tags/$tag") }.getOrNull() ?: continue
        val notes = tidyNotes(j.optString("body"))
        if (notes.isNotBlank()) return notes
    }
    return null
}

/** The TV's address on your Wi-Fi or cable, like 192.168.1.23. Handy for the PC guide. */
fun localIpv4(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val lp = cm.getLinkProperties(cm.activeNetwork)
    lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}.getOrNull()

