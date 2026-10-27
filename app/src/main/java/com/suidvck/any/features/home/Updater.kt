package com.suidvck.any.features.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.R
import com.suidvck.any.ui.AnyChrome
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks the latest GitHub release of the app. Network work happens on a
 * background thread and everything user-facing is posted back to the main thread.
 */
object Updater {

    private const val API_URL = "https://api.github.com/repos/Suidvck/Any/releases/latest"
    private const val PREFS = "settings"
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val USER_AGENT = "AnyApp/1.0"
    private const val BODY_EXCERPT = 500

    private val ui = Handler(Looper.getMainLooper())
    private val checking = AtomicBoolean(false)

    private data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val url: String
    )

    /**
     * @param silent true for the launch check: throttled to once per 24h, no
     *               result toast when up to date, no toast on network errors.
     */
    fun check(activity: Activity, silent: Boolean = false) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (silent) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (last > 0L && System.currentTimeMillis() - last < DAY_MS) return
        }
        if (!checking.compareAndSet(false, true)) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

        if (!silent) toast(activity, activity.getString(R.string.home_update_checking))

        Thread {
            var release: Release? = null
            var failure: Exception? = null
            try {
                release = fetchLatest()
            } catch (e: Exception) {
                failure = e
            }
            ui.post { deliver(activity, release, failure, silent) }
        }.apply {
            name = "any-update-check"
            isDaemon = true
        }.start()
    }

    private fun deliver(
        activity: Activity,
        release: Release?,
        failure: Exception?,
        silent: Boolean
    ) {
        checking.set(false)
        if (activity.isFinishing || activity.isDestroyed) return

        if (failure != null || release == null) {
            if (!silent) toast(activity, activity.getString(R.string.home_update_failed))
            return
        }

        val current = versionName(activity)
        if (!isNewer(release.tag, current)) {
            if (!silent) toast(activity, activity.getString(R.string.home_update_latest))
            return
        }
        showUpdate(activity, release)
    }

    private fun showUpdate(activity: Activity, release: Release) {
        val body = release.body.trim()
        val excerpt = if (body.length > BODY_EXCERPT) {
            body.take(BODY_EXCERPT).trimEnd() + "…"
        } else {
            body
        }
        val message = buildString {
            if (release.name.isNotBlank() && release.name != release.tag) {
                append(release.name).append("\n\n")
            }
            if (excerpt.isNotBlank()) append(excerpt)
        }.trim()

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.home_update_title, release.tag))
            .setPositiveButton(R.string.home_update_open) { _, _ ->
                openRelease(activity, release.url)
            }
            .setNegativeButton(R.string.home_update_later, null)
        if (message.isNotEmpty()) builder.setMessage(message)
        val dialog = builder.create()
        dialog.show()
        AnyChrome.dialog(dialog)
    }

    private fun openRelease(activity: Activity, url: String) {
        if (url.isBlank()) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast(activity, activity.getString(R.string.home_update_no_browser))
        } catch (e: Exception) {
            toast(activity, activity.getString(R.string.home_update_no_browser))
        }
    }

    private fun fetchLatest(): Release {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("GitHub HTTP $code")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            val tag = json.optString("tag_name").trim()
                .ifBlank { json.optString("name").trim() }
            if (tag.isBlank()) throw IOException("Release has no tag")
            return Release(
                tag = tag,
                name = json.optString("name").trim(),
                body = json.optString("body").orEmpty(),
                url = json.optString("html_url").trim()
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val newer = versionNumbers(latest)
        val installed = versionNumbers(current)
        val size = maxOf(newer.size, installed.size)
        for (index in 0 until size) {
            val a = newer.getOrElse(index) { 0 }
            val b = installed.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionNumbers(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }

    private fun versionName(activity: Activity): String {
        @Suppress("DEPRECATION")
        val info = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        }.getOrNull()
        return info?.versionName?.trim().orEmpty().ifBlank { "0" }
    }

    private fun toast(activity: Activity, text: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        } else {
            ui.post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
