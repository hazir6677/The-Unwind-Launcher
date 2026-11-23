package com.unwind.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

private const val K_LAST_CHECK = "updLastCheck"

// THE GITHUB VERSION. It checks GitHub releases, downloads the new APK and hands it to Android to install.
// (The F-Droid version lives in src/fdroid and only checks F-Droid.)

class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val published: String,
)

enum class UpPhase { Idle, Checking, UpToDate, Available, Downloading, Ready, Failed }

class UpdateUi(
    val phase: UpPhase = UpPhase.Idle,
    /** Installed version, read from this app's own package info on the device. Never comes from GitHub. */
    val current: String = "",
    /** A newer release that can be downloaded (null when you are up to date). */
    val release: ReleaseInfo? = null,
    /** The newest published release on GitHub, whatever its relation to the installed version (null until a check succeeds). */
    val latest: ReleaseInfo? = null,
    val file: File? = null,
    val error: String = "",
    val checkedAt: Long = 0L,
    val needsPermission: Boolean = false,
    val installing: Boolean = false,
) {
    fun copy(
        phase: UpPhase = this.phase,
        release: ReleaseInfo? = this.release,
        latest: ReleaseInfo? = this.latest,
        file: File? = this.file,
        error: String = this.error,
        checkedAt: Long = this.checkedAt,
        needsPermission: Boolean = this.needsPermission,
        installing: Boolean = this.installing,
    ) = UpdateUi(phase, current, release, latest, file, error, checkedAt, needsPermission, installing)
}

// ---------- Talking to GitHub ----------

private fun releaseFromJson(j: JSONObject): ReleaseInfo? {
    val tag = j.optString("tag_name")
    if (tag.isBlank()) return null
    // the download is optional here: the version number is shown even before the build has attached its APK
    var apk: JSONObject? = null
    val assets = j.optJSONArray("assets")
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name").lowercase()
            if (!name.endsWith(".apk")) continue
            if (apk == null || (apk.optString("name").lowercase().contains("debug") && !name.contains("debug"))) apk = a
        }
    }
    return ReleaseInfo(
        version = tag.trimStart('v', 'V'),
        notes = tidyNotes(j.optString("body")),
        apkUrl = apk?.optString("browser_download_url").orEmpty(),
        size = apk?.optLong("size", 0L) ?: 0L,
        published = j.optString("published_at"),
    )
}

/** The latest published release (GitHub's own "latest" on the Releases page), or null if there is none yet. */
private fun fetchLatestRelease(): ReleaseInfo? =
    getJson("https://api.github.com/repos/$UPDATE_REPO/releases/latest")?.let(::releaseFromJson)

private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates")

private fun looksRight(ctx: Context, f: File): Boolean {
    val info = runCatching { ctx.packageManager.getPackageArchiveInfo(f.path, 0) }.getOrNull()
    return info != null && info.packageName == ctx.packageName
}

/** An already-downloaded copy of this release, if one is waiting in the cache. */
private fun cachedApk(ctx: Context, r: ReleaseInfo): File? {
    val f = File(updatesDir(ctx), "Unwind-${r.version}.apk")
    return if (f.exists() && (r.size <= 0 || f.length() == r.size) && looksRight(ctx, f)) f else null
}

private fun downloadApk(ctx: Context, r: ReleaseInfo, onProgress: (Float) -> Unit): File {
    val dir = updatesDir(ctx).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "Unwind-${r.version}.apk")
    val part = File(dir, out.name + ".part")
    val c = URL(r.apkUrl).openConnection() as HttpURLConnection
    c.connectTimeout = 15_000
    c.readTimeout = 30_000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "UnwindLauncher")
    try {
        if (c.responseCode != 200) throw IllegalStateException("The download failed (error ${c.responseCode}).")
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else r.size
        c.inputStream.use { ins ->
            part.outputStream().use { outs ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastTick = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    outs.write(buf, 0, n)
                    done += n
                    val now = SystemClock.uptimeMillis()
                    if (total > 0 && now - lastTick > 80) {
                        lastTick = now
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    } finally {
        c.disconnect()
    }
    if (!part.renameTo(out)) throw IOException("Couldn't save the download.")
    if (!looksRight(ctx, out)) {
        out.delete()
        throw IllegalStateException("That file isn't an Unwind update.")
    }
    onProgress(1f)
    return out
}

// ---------- Installing ----------

/** On Android 8+ every app needs its own "install unknown apps" switch. Older Androids have one global switch. */
fun canInstallPackages(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) ctx.packageManager.canRequestPackageInstalls() else true

fun openUnknownSourcesSettings(ctx: Context) {
    val tries = listOf(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (i in tries) {
        val ok = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
    Toast.makeText(ctx, "Open Settings > Security and allow Unwind to install unknown apps.", Toast.LENGTH_LONG).show()
}

private const val SESSION_NAME = "unwind-update"

private fun installApk(ctx: Context, apk: File) {
    val installer = ctx.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(ctx.packageName)
    if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    val id = installer.createSession(params)
    installer.openSession(id).use { s ->
        apk.inputStream().use { ins ->
            s.openWrite(SESSION_NAME, 0, apk.length()).use { outs ->
                ins.copyTo(outs)
                s.fsync(outs)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
        s.commit(pi.intentSender)
    }
}

/** Android reports back here. Its job: show the system "Install?" screen, or tell you if something failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm != null) runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {}
            else -> {
                val why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"
                Toast.makeText(ctx, "The update didn't install: $why", Toast.LENGTH_LONG).show()
            }
        }
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
    var progress by mutableFloatStateOf(0f)
        private set

    override val current: String get() = ui.current
    override val checkedAt: Long get() = ui.checkedAt
    override val pendingVersion: String? get() = ui.release?.version?.takeIf { ui.phase == UpPhase.Available || ui.phase == UpPhase.Ready }

    /** True while a newer version is known (downloaded or not). */
    override val hasUpdate: Boolean get() = ui.phase == UpPhase.Available || ui.phase == UpPhase.Ready ||
        (ui.phase == UpPhase.Downloading) || (ui.phase == UpPhase.Failed && ui.release != null)

    /** [manual] = the person pressed the button, so show progress and errors. Automatic checks stay quiet. */
    override fun check(manual: Boolean) {
        if (ui.phase == UpPhase.Checking || ui.phase == UpPhase.Downloading || ui.installing) return
        if (manual) ui = ui.copy(phase = UpPhase.Checking, error = "")
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fetchLatestRelease() } }
            val now = System.currentTimeMillis()
            r.onSuccess { rel ->
                prefs.edit().putLong(K_LAST_CHECK, now).apply()
                // the installed version (ui.current) and the published one (rel) come from different places and are compared here
                if (rel != null && rel.apkUrl.isNotBlank() && isNewer(rel.version, ui.current)) {
                    val f = withContext(Dispatchers.IO) { cachedApk(ctx, rel) }
                    progress = if (f != null) 1f else 0f
                    ui = ui.copy(
                        phase = if (f != null) UpPhase.Ready else UpPhase.Available,
                        release = rel, latest = rel, file = f, checkedAt = now, error = "",
                    )
                } else {
                    ui = ui.copy(phase = UpPhase.UpToDate, release = null, latest = rel, file = null, checkedAt = now, error = "")
                }
            }
            r.onFailure { e ->
                if (manual) ui = ui.copy(phase = UpPhase.Failed, error = friendly(e))
                else if (ui.phase == UpPhase.Checking) ui = ui.copy(phase = UpPhase.Idle)
            }
        }
    }

    fun download() {
        val rel = ui.release ?: return
        if (ui.phase == UpPhase.Downloading) return
        progress = 0f
        ui = ui.copy(phase = UpPhase.Downloading, error = "")
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { downloadApk(ctx, rel) { progress = it } } }
            r.onSuccess { ui = ui.copy(phase = UpPhase.Ready, file = it, error = "") }
            r.onFailure { ui = ui.copy(phase = UpPhase.Failed, file = null, error = friendly(it)) }
        }
    }

    /** Hands the downloaded file to Android. If the permission isn't on yet, sends you to switch it on first. */
    fun install() {
        val f = ui.file ?: return
        if (!canInstallPackages(ctx)) {
            ui = ui.copy(needsPermission = true)
            openUnknownSourcesSettings(ctx)
            return
        }
        ui = ui.copy(needsPermission = false, installing = true)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { installApk(ctx, f) } }
            r.onFailure { ui = ui.copy(phase = UpPhase.Failed, error = friendly(it)) }
            delay(4000)
            ui = ui.copy(installing = false)
        }
    }

    /** Called when Unwind comes back to the front, for example after you switched the permission on. */
    override fun onResume() {
        if (ui.needsPermission && canInstallPackages(ctx)) install()
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "Couldn't reach GitHub. Check the TV's internet and try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}
