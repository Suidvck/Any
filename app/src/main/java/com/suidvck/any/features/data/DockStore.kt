package com.suidvck.any.features.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class DockItem(
    val label: String,
    val url: String,
    val icon: String
)

/**
 * Home dock with exactly three slots. Missing/invalid slots are padded from the defaults
 * (Instagram, Discord, TikTok), matching the home mock-up.
 */
class DockStore(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val file = JsonFile(File(appContext.filesDir, "dock.json"))
    private val lock = Any()

    private var loaded = false
    private var stored: MutableList<DockItem> = defaults().toMutableList()

    fun items(): List<DockItem> = synchronized(lock) {
        ensureLoaded()
        coerce(stored).toList()
    }

    fun setItems(items: List<DockItem>) {
        synchronized(lock) {
            ensureLoaded()
            stored = coerce(items).toMutableList()
            persistLocked()
        }
    }

    fun reset() {
        synchronized(lock) {
            ensureLoaded()
            stored = defaults().toMutableList()
            persistLocked()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val text = when (val read = file.readJson()) {
            JsonFile.Read.Missing -> return
            JsonFile.Read.Corrupt -> {
                val preserved = file.quarantine()
                Log.w("DockStore", "corrupt dock quarantined at ${preserved?.name ?: "?"}")
                return
            }
            is JsonFile.Read.Ok -> read.text
        }
        val root = try {
            JSONObject(text)
        } catch (_: Throwable) {
            return
        }
        val array = root.optJSONArray("items") ?: return
        val parsed = ArrayList<DockItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (url.isEmpty()) continue
            val label = obj.optString("label").trim().ifEmpty { hostLabel(url) }
            val icon = obj.optString("icon").trim().lowercase(Locale.ROOT)
                .takeIf { it in ICON_KEYS }
                ?: "letter"
            parsed.add(DockItem(label, url, icon))
        }
        stored = coerce(parsed).toMutableList()
    }

    private fun coerce(source: List<DockItem>): List<DockItem> {
        val defaults = defaults()
        val result = ArrayList<DockItem>(3)
        for (i in 0 until 3) {
            val raw = source.getOrNull(i)
            val url = raw?.url?.trim().orEmpty()
            if (url.isEmpty()) {
                result.add(defaults[i])
            } else {
                val label = raw?.label?.trim().orEmpty().ifEmpty { hostLabel(url) }
                val icon = raw?.icon?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    .takeIf { it in ICON_KEYS }
                    ?: "letter"
                result.add(DockItem(label, url, icon))
            }
        }
        return result
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (item in stored) {
            array.put(
                JSONObject()
                    .put("label", item.label)
                    .put("url", item.url)
                    .put("icon", item.icon)
            )
        }
        file.write(JSONObject().put("version", 1).put("items", array))
    }

    private fun hostLabel(url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        val clean = host?.removePrefix("www.")?.trim().orEmpty()
        return clean.ifEmpty { "ลิงก์" }
    }

    private fun defaults(): List<DockItem> = listOf(
        DockItem("Instagram", "https://www.instagram.com/", "instagram"),
        DockItem("Discord", "https://discord.com/", "discord"),
        DockItem("TikTok", "https://www.tiktok.com/", "tiktok")
    )

    private companion object {
        val ICON_KEYS = setOf(
            "instagram",
            "discord",
            "tiktok",
            "youtube",
            "github",
            "google",
            "chatgpt",
            "letter"
        )
    }
}
