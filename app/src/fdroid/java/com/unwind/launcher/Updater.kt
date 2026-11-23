package com.unwind.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// THE F-DROID VERSION. It never downloads or installs anything. It asks F-Droid whether a newer version
// exists and opens Unwind's F-Droid page. (The GitHub version lives in src/github.)

/** Where Unwind lives on F-Droid. */
const val FDROID_ID = "com.unwind.launcher"
const val FDROID_PAGE = "https://f-droid.org/packages/$FDROID_ID/"
private const val FDROID_API = "https://f-droid.org/api/v1/packages/$FDROID_ID"

private const val K_LAST_CHECK = "updLastCheck"

enum class UpPhase { Idle, Checking, UpToDate, Available, NotListed, Failed }

data class UpdateUi(
    val phase: UpPhase = UpPhase.Idle,
    /** Installed version, read from this app's own package info on the device. Never comes from the network. */
    val current: String = "",
    /** The newest version F-Droid offers (null until a check succeeds, or while Unwind isn't listed yet). */
    val latest: String? = null,
    val error: String = "",
    val checkedAt: Long = 0L,
)

// ---------- Asking F-Droid ----------

private class FdroidInfo(val version: String, val code: Long)

/**
 * Reads F-Droid's public per-app API. Returns null when Unwind isn't on F-Droid (yet).
 * The answer lists published versions plus a "suggested" one, which is the one F-Droid installs.
 */
private fun fetchFdroid(): FdroidInfo? {
    val c = URL(FDROID_API).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    c.setRequestProperty("Accept", "application/json")
    c.setRequestProperty("User-Agent", "UnwindLauncher")
    try {
        when (c.responseCode) {
            200 -> {}
            404 -> return null
            else -> throw IllegalStateException("F-Droid answered with error ${c.responseCode}.")
        }
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val list = j.optJSONArray("packages") ?: return null
        val suggested = j.optLong("suggestedVersionCode", -1L)
        var best: FdroidInfo? = null
        var pick: FdroidInfo? = null
        for (i in 0 until list.length()) {
            val p = list.getJSONObject(i)
            val v = FdroidInfo(p.optString("versionName"), p.optLong("versionCode"))
            if (v.version.isBlank()) continue
            if (best == null || v.code > best.code) best = v
            if (v.code == suggested) pick = v
        }
        return pick ?: best
    } finally {
        c.disconnect()
    }
}

// ---------- The part the screens talk to ----------

class UpdateController(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
) : AppUpdater {
    var ui by mutableStateOf(UpdateUi(current = currentVersion(ctx), checkedAt = prefs.getLong(K_LAST_CHECK, 0L)))
        private set

    override val current: String get() = ui.current
    override val checkedAt: Long get() = ui.checkedAt
    override val pendingVersion: String? get() = ui.latest?.takeIf { ui.phase == UpPhase.Available }

    /** True while F-Droid offers a newer version. */
    override val hasUpdate: Boolean get() = ui.phase == UpPhase.Available

    /** [manual] = the person pressed the button, so show progress and errors. Automatic checks stay quiet. */
    override fun check(manual: Boolean) {
        if (ui.phase == UpPhase.Checking) return
        if (manual) ui = ui.copy(phase = UpPhase.Checking, error = "")
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fetchFdroid() } }
            val now = System.currentTimeMillis()
            r.onSuccess { info ->
                prefs.edit().putLong(K_LAST_CHECK, now).apply()
                // the installed version (ui.current) and the published one (info) come from different places and are compared here
                ui = when {
                    info == null -> ui.copy(phase = UpPhase.NotListed, latest = null, checkedAt = now, error = "")
                    isNewer(info.version, ui.current) -> ui.copy(phase = UpPhase.Available, latest = info.version, checkedAt = now, error = "")
                    else -> ui.copy(phase = UpPhase.UpToDate, latest = info.version, checkedAt = now, error = "")
                }
            }
            r.onFailure { e ->
                if (manual) ui = ui.copy(phase = UpPhase.Failed, error = friendly(e))
                else if (ui.phase == UpPhase.Checking) ui = ui.copy(phase = UpPhase.Idle)
            }
        }
    }

    /** Opens Unwind's F-Droid page. The F-Droid app picks the link up if it's installed, otherwise a browser does. */
    fun openPage() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isFailure) {
            Toast.makeText(ctx, "No browser or F-Droid on this TV. Open $FDROID_PAGE on another device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "Couldn't reach F-Droid. Check the TV's internet and try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}
