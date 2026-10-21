package com.suidvck.any.features.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class TabEntry(
    val id: String,
    val title: String,
    val url: String?,
    val appId: String?,
    val updatedAt: Long
)

class TabStore(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val file = JsonFile(File(appContext.filesDir, "tabs.json"))
    private val lock = Any()

    private var loaded = false
    private var currentId: String? = null
    private val tabs = ArrayList<TabEntry>()

    fun list(): List<TabEntry> = synchronized(lock) {
        ensureLoaded()
        tabs.sortedByDescending { it.updatedAt }.take(MAX_TABS)
    }

    fun current(): TabEntry? = synchronized(lock) {
        ensureLoaded()
        tabs.firstOrNull { it.id == currentId }
    }

    fun open(url: String?, appId: String?, title: String?): TabEntry = synchronized(lock) {
        ensureLoaded()
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
        val cleanAppId = appId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanTitle = tabTitle(title, cleanUrl, cleanAppId)
        val now = System.currentTimeMillis()
        val existing = tabs.firstOrNull { it.id == currentId }
        if (existing == null) {
            val created = newTabLocked(cleanUrl, cleanAppId, cleanTitle, now)
            persistLocked()
            created
        } else {
            val updated = existing.copy(
                title = cleanTitle,
                url = cleanUrl,
                appId = cleanAppId,
                updatedAt = now
            )
            tabs[tabs.indexOfFirst { it.id == existing.id }] = updated
            currentId = updated.id
            persistLocked()
            updated
        }
    }

    fun addTab(url: String?, appId: String?, title: String?): TabEntry = synchronized(lock) {
        ensureLoaded()
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
        val cleanAppId = appId?.trim()?.takeIf { it.isNotEmpty() }
        val created = newTabLocked(
            cleanUrl,
            cleanAppId,
            tabTitle(title, cleanUrl, cleanAppId),
            System.currentTimeMillis()
        )
        persistLocked()
        created
    }

    fun select(id: String): TabEntry? = synchronized(lock) {
        ensureLoaded()
        val entry = tabs.firstOrNull { it.id == id }
        if (entry != null && currentId != entry.id) {
            currentId = entry.id
            persistLocked()
        }
        entry
    }

    fun close(id: String): TabEntry? = synchronized(lock) {
        ensureLoaded()
        val existed = tabs.any { it.id == id }
        if (existed) {
            tabs.removeAll { it.id == id }
            if (currentId == id || tabs.none { it.id == currentId }) {
                currentId = tabs.maxByOrNull { it.updatedAt }?.id
            }
            persistLocked()
        }
        tabs.firstOrNull { it.id == currentId }
    }

    fun updateTitle(title: String?) {
        synchronized(lock) {
            ensureLoaded()
            val clean = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val index = tabs.indexOfFirst { it.id == currentId }
            if (index >= 0 && tabs[index].title != clean) {
                tabs[index] = tabs[index].copy(
                    title = clean,
                    updatedAt = System.currentTimeMillis()
                )
                persistLocked()
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val root = file.readObject() ?: return
        currentId = if (root.isNull("currentId")) {
            null
        } else {
            root.optString("currentId").trim().ifEmpty { null }
        }
        val array = root.optJSONArray("tabs") ?: return
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            var id = obj.optString("id").trim()
            if (id.isEmpty()) id = newId()
            if (tabs.any { it.id == id }) continue
            val title = obj.optString("title").trim().ifEmpty { DEFAULT_TITLE }
            val url = if (obj.isNull("url")) {
                null
            } else {
                obj.optString("url").trim().ifEmpty { null }
            }
            val appId = if (obj.isNull("appId")) {
                null
            } else {
                obj.optString("appId").trim().ifEmpty { null }
            }
            val updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis()
            tabs.add(TabEntry(id, title, url, appId, updatedAt))
        }
        normalizeLocked()
    }

    private fun normalizeLocked() {
        tabs.sortByDescending { it.updatedAt }
        trimLocked()
        if (tabs.none { it.id == currentId }) {
            currentId = tabs.firstOrNull()?.id
        }
    }

    private fun newTabLocked(url: String?, appId: String?, title: String, now: Long): TabEntry {
        val entry = TabEntry(newId(), title, url, appId, now)
        tabs.add(0, entry)
        currentId = entry.id
        trimLocked()
        return entry
    }

    private fun trimLocked() {
        while (tabs.size > MAX_TABS) {
            val victim = tabs.filter { it.id != currentId }
                .minByOrNull { it.updatedAt }
                ?: break
            tabs.remove(victim)
        }
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (tab in tabs.sortedByDescending { it.updatedAt }) {
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("title", tab.title)
                    .put("url", tab.url ?: JSONObject.NULL)
                    .put("appId", tab.appId ?: JSONObject.NULL)
                    .put("updatedAt", tab.updatedAt)
            )
        }
        file.write(
            JSONObject()
                .put("version", 1)
                .put("currentId", currentId ?: JSONObject.NULL)
                .put("tabs", array)
        )
    }

    private fun newId(): String {
        var id: String
        do {
            id = ID_PREFIX + UUID.randomUUID().toString().take(8)
        } while (tabs.any { it.id == id })
        return id
    }

    private fun tabTitle(title: String?, url: String?, appId: String?): String {
        val given = title?.trim()?.takeIf { it.isNotEmpty() }
        if (given != null) return given
        if (appId != null) return "แอพ"
        if (url != null) {
            val host = runCatching { Uri.parse(url).host }.getOrNull()
            if (!host.isNullOrBlank()) {
                return host.removePrefix("www.")
            }
        }
        return DEFAULT_TITLE
    }

    private companion object {
        const val MAX_TABS = 12
        const val ID_PREFIX = "tabs_"
        const val DEFAULT_TITLE = "แท็บใหม่"
    }
}

data class HistoryEntry(
    val url: String,
    val title: String,
    val ts: Long,
    val bookmarked: Boolean
)

class HistoryStore(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val file = JsonFile(File(appContext.filesDir, "history.json"))
    private val lock = Any()

    private var loaded = false
    private val entries = ArrayList<HistoryEntry>()

    fun record(url: String, title: String) {
        synchronized(lock) {
            ensureLoaded()
            val cleanUrl = url.trim()
            if (cleanUrl.isEmpty()) return
            val cleanTitle = title.trim()
            val now = System.currentTimeMillis()
            val index = entries.indexOfFirst { it.url == cleanUrl }
            if (index >= 0) {
                val old = entries[index]
                entries[index] = old.copy(
                    title = cleanTitle.ifEmpty { old.title },
                    ts = now
                )
            } else {
                entries.add(
                    HistoryEntry(
                        url = cleanUrl,
                        title = cleanTitle.ifEmpty { cleanUrl },
                        ts = now,
                        bookmarked = false
                    )
                )
            }
            trimLocked()
            persistLocked()
        }
    }

    fun recent(limit: Int = 100): List<HistoryEntry> = synchronized(lock) {
        ensureLoaded()
        if (limit <= 0) emptyList() else entries.sortedByDescending { it.ts }.take(limit)
    }

    fun bookmarks(): List<HistoryEntry> = synchronized(lock) {
        ensureLoaded()
        entries.filter { it.bookmarked }.sortedByDescending { it.ts }
    }

    fun search(q: String): List<HistoryEntry> = synchronized(lock) {
        ensureLoaded()
        val needle = q.trim().lowercase(Locale.ROOT)
        val sorted = entries.sortedByDescending { it.ts }
        if (needle.isEmpty()) {
            sorted
        } else {
            sorted.filter { entry ->
                entry.url.lowercase(Locale.ROOT).contains(needle) ||
                    entry.title.lowercase(Locale.ROOT).contains(needle)
            }
        }
    }

    fun isBookmarked(url: String): Boolean = synchronized(lock) {
        ensureLoaded()
        entries.any { it.url == url && it.bookmarked }
    }

    fun toggleBookmark(url: String): Boolean = synchronized(lock) {
        ensureLoaded()
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            false
        } else {
            val index = entries.indexOfFirst { it.url == cleanUrl }
            if (index >= 0) {
                val updated = entries[index].copy(bookmarked = !entries[index].bookmarked)
                entries[index] = updated
                persistLocked()
                updated.bookmarked
            } else {
                entries.add(
                    HistoryEntry(
                        url = cleanUrl,
                        title = cleanUrl,
                        ts = System.currentTimeMillis(),
                        bookmarked = true
                    )
                )
                trimLocked()
                persistLocked()
                true
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            ensureLoaded()
            val changed = entries.removeAll { !it.bookmarked }
            if (changed) persistLocked()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val root = file.readObject() ?: return
        val array = root.optJSONArray("entries") ?: return
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (url.isEmpty()) continue
            val title = obj.optString("title").trim().ifEmpty { url }
            val ts = obj.optLong("ts", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
            entries.add(HistoryEntry(url, title, ts, obj.optBoolean("bookmarked", false)))
        }
        dedupeLocked()
        trimLocked()
    }

    private fun dedupeLocked() {
        val byUrl = LinkedHashMap<String, HistoryEntry>()
        for (entry in entries) {
            val previous = byUrl[entry.url]
            if (previous == null) {
                byUrl[entry.url] = entry
            } else {
                val newer = if (entry.ts >= previous.ts) entry else previous
                val older = if (newer === entry) previous else entry
                byUrl[entry.url] = HistoryEntry(
                    url = entry.url,
                    title = newer.title.ifBlank { older.title },
                    ts = maxOf(entry.ts, previous.ts),
                    bookmarked = previous.bookmarked || entry.bookmarked
                )
            }
        }
        entries.clear()
        entries.addAll(byUrl.values)
    }

    private fun trimLocked() {
        if (entries.size <= MAX_RECENT) return
        // Keep the total at 300, but never evict bookmarks; only drop the oldest plain entries.
        val removable = entries.filter { !it.bookmarked }
            .sortedBy { it.ts }
            .take(entries.size - MAX_RECENT)
        if (removable.isEmpty()) return
        val drop = HashSet<String>(removable.size * 2)
        for (entry in removable) drop.add(entry.url)
        entries.removeAll { !it.bookmarked && it.url in drop }
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (entry in entries.sortedByDescending { it.ts }) {
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("title", entry.title)
                    .put("ts", entry.ts)
                    .put("bookmarked", entry.bookmarked)
            )
        }
        file.write(JSONObject().put("version", 1).put("entries", array))
    }

    private companion object {
        const val MAX_RECENT = 300
    }
}
