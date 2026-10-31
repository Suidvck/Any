package com.suidvck.any.features.miniapp

import android.content.Context

/**
 * Global capabilities for HTML apps written by the AI.
 *
 * Mini-apps share the main WebView with the browser, so these values are applied only while a
 * mini-app is on screen. Normal websites keep JavaScript and storage; a generated app gets
 * exactly what the user allowed on the "Mini-app permissions" sheet.
 */
object MiniAppPermissions {

    /** Values live next to the AI settings so backups keep them with the rest of the prefs. */
    private const val PREFS = "settings"
    private const val PREFIX = "miniapp_perm_"

    const val JAVASCRIPT = "javascript"
    const val STORAGE = "storage"
    const val NETWORK = "network"
    const val DOWNLOADS = "downloads"
    const val MEDIA = "media"
    const val LOCATION = "location"

    const val TOGGLE_COUNT = 6

    data class State(
        val javaScript: Boolean = true,
        val storage: Boolean = true,
        val network: Boolean = true,
        val downloads: Boolean = true,
        val media: Boolean = false,
        val location: Boolean = false
    ) {
        val enabledCount: Int
            get() = listOf(javaScript, storage, network, downloads, media, location)
                .count { it }
    }

    fun load(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            javaScript = prefs.getBoolean(PREFIX + JAVASCRIPT, true),
            storage = prefs.getBoolean(PREFIX + STORAGE, true),
            network = prefs.getBoolean(PREFIX + NETWORK, true),
            downloads = prefs.getBoolean(PREFIX + DOWNLOADS, true),
            media = prefs.getBoolean(PREFIX + MEDIA, false),
            location = prefs.getBoolean(PREFIX + LOCATION, false)
        )
    }

    fun isJavaScriptEnabled(context: Context): Boolean = read(context, JAVASCRIPT, true)

    fun isStorageEnabled(context: Context): Boolean = read(context, STORAGE, true)

    fun isNetworkEnabled(context: Context): Boolean = read(context, NETWORK, true)

    fun isDownloadsEnabled(context: Context): Boolean = read(context, DOWNLOADS, true)

    fun isMediaEnabled(context: Context): Boolean = read(context, MEDIA, false)

    fun isLocationEnabled(context: Context): Boolean = read(context, LOCATION, false)

    fun set(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREFIX + key, enabled)
            .apply()
    }

    /** Restores the shipped defaults (used by QA and by a future "reset" action). */
    fun reset(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        listOf(JAVASCRIPT, STORAGE, NETWORK, DOWNLOADS, MEDIA, LOCATION)
            .forEach { editor.remove(PREFIX + it) }
        editor.apply()
    }

    private fun read(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREFIX + key, default)

    /** True for the synthetic origin a saved mini-app is loaded from. */
    fun isMiniAppHost(host: String?): Boolean {
        val value = host?.lowercase().orEmpty()
        return value == "anyapp.local" || value.endsWith(".anyapp.local")
    }
}
