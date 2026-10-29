package com.suidvck.any.features.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class MiniApp(
    val id: String,
    val name: String,
    val icon: String,
    val html: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Stores the mini-app library as a small index (miniapps/index.json) plus one atomically
 * written HTML file per app (miniapps/<id>.html).
 */
class MiniAppStore(private val dir: File) {

    constructor(context: Context) : this(
        File(context.applicationContext?.filesDir ?: context.filesDir, "miniapps")
    )

    private val indexFile = JsonFile(File(dir, "index.json"))
    private val lock = Any()

    private var loaded = false
    private val apps = ArrayList<MiniApp>()
    private val htmlCache = HashMap<String, String>()

    fun list(): List<MiniApp> = synchronized(lock) {
        ensureLoaded()
        apps.sortedByDescending { it.updatedAt }
            .map { app -> app.copy(html = htmlFor(app.id)) }
    }

    fun get(id: String): MiniApp? = synchronized(lock) {
        ensureLoaded()
        apps.firstOrNull { it.id == id }?.let { app -> app.copy(html = htmlFor(app.id)) }
    }

    /**
     * Saves an app. Returns null when the HTML file could not be written, in which case the
     * index is deliberately left untouched so a failed save can never replace a working app
     * with an entry pointing at a missing file.
     */
    fun save(
        html: String,
        name: String? = null,
        icon: String? = null,
        id: String? = null
    ): MiniApp? = synchronized(lock) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val wantedId = id?.trim()?.takeIf { it.isNotEmpty() }
        val existing = wantedId?.let { key -> apps.firstOrNull { it.id == key } }
        val finalId = wantedId ?: newId()
        val finalName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.name
            ?: suggestName(html, DEFAULT_NAME)
        val finalIcon = icon?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.icon
            ?: suggestIcon(html)
        val finalCreatedAt = existing?.createdAt ?: now
        val app = MiniApp(finalId, finalName, finalIcon, html, finalCreatedAt, now)

        // Write the HTML first, then publish the index entry so a failed index write never
        // leaves a half-written page behind. If the HTML cannot be written, stop here:
        // publishing an index entry without its file produces an app that opens blank.
        if (!JsonFile(htmlFile(finalId)).write(html)) {
            Log.e(TAG, "could not write html for $finalId; keeping previous state")
            return@synchronized null
        }
        htmlCache[finalId] = html
        apps.removeAll { it.id == finalId }
        apps.add(app)
        persistLocked()
        app
    }

    fun rename(id: String, name: String, icon: String) {
        synchronized(lock) {
            ensureLoaded()
            val index = apps.indexOfFirst { it.id == id }
            if (index >= 0) {
                val old = apps[index]
                val newName = name.trim().ifEmpty { old.name }
                val newIcon = icon.trim().ifEmpty { old.icon }
                apps[index] = old.copy(
                    name = newName,
                    icon = newIcon,
                    updatedAt = System.currentTimeMillis()
                )
                persistLocked()
            }
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            ensureLoaded()
            val changed = apps.removeAll { it.id == id }
            if (changed) {
                htmlCache.remove(id)
                try {
                    htmlFile(id).delete()
                } catch (_: Throwable) {
                    // A stale HTML file is harmless; the index no longer references it.
                }
                persistLocked()
            }
        }
    }

    fun originFor(id: String): String = "https://app-$id.anyapp.local/"

    fun suggestName(html: String, fallback: String): String = rawTitle(html) ?: fallback

    fun suggestIcon(html: String): String {
        val title = rawTitle(html).orEmpty()
        val sample = (title + "\n" + html.take(6000)).lowercase(Locale.ROOT)
        for ((keywords, icon) in iconRules) {
            if (keywords.any { sample.contains(it) }) return icon
        }
        return DEFAULT_ICON
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        when (val read = indexFile.readJson()) {
            is JsonFile.Read.Ok -> {
                if (!parseIndex(read.text)) {
                    // Valid JSON with the wrong shape is just as unusable as broken JSON.
                    val preserved = indexFile.quarantine()
                    Log.w(TAG, "unusable index quarantined at ${preserved?.name ?: "?"}")
                    rebuildFromDisk("unusable")
                }
            }
            JsonFile.Read.Missing -> rebuildFromDisk("missing")
            JsonFile.Read.Corrupt -> {
                // Preserve the damaged bytes for manual recovery, then rebuild the index from
                // the per-app HTML files. Never start from an empty list and then persist it:
                // that is what turns one damaged byte into a wiped library.
                val preserved = indexFile.quarantine()
                Log.w(TAG, "corrupt index quarantined at ${preserved?.name ?: "?"}")
                rebuildFromDisk("corrupt")
            }
        }
    }

    /** Returns false when [text] is valid JSON but does not contain an apps array. */
    private fun parseIndex(text: String): Boolean {
        val root = try {
            JSONObject(text)
        } catch (_: Throwable) {
            return false
        }
        val array = root.optJSONArray("apps") ?: return false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isEmpty() || apps.any { it.id == id }) continue
            val name = obj.optString("name").trim().ifEmpty { DEFAULT_NAME }
            // v1.2 and earlier wrote an emoji under "emoji"; v1.3+ writes an
            // icon name under "icon". Keep reading both so saved apps survive.
            val icon = obj.optString("icon").trim()
                .ifEmpty { obj.optString("emoji").trim() }
                .ifEmpty { DEFAULT_ICON }
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val updatedAt = obj.optLong("updatedAt", createdAt).takeIf { it > 0L } ?: createdAt
            apps.add(MiniApp(id, name, icon, "", createdAt, updatedAt))
        }
        return true
    }

    /**
     * Recreates index entries from the HTML files that are still on disk. The file name is the
     * app id, so previously saved apps remain openable even when index.json is gone. Only runs
     * when the index is missing or corrupt; a healthy index is never second-guessed.
     */
    private fun rebuildFromDisk(reason: String) {
        val files = try {
            dir.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return
        for (file in files) {
            if (!file.isFile) continue
            if (!file.name.endsWith(".html", ignoreCase = true)) continue
            val id = file.name.dropLast(HTML_EXTENSION.length)
            if (id.isBlank() || id == "index") continue
            if (apps.any { it.id == id }) continue
            val html = try {
                file.readText(Charsets.UTF_8)
            } catch (_: Throwable) {
                continue
            }
            if (html.isBlank()) continue
            val stamp = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            apps.add(
                MiniApp(
                    id = id,
                    name = suggestName(html, DEFAULT_NAME),
                    icon = suggestIcon(html),
                    html = "",
                    createdAt = stamp,
                    updatedAt = stamp
                )
            )
        }
        if (apps.isNotEmpty()) {
            // Repersisting also resumes normal writes: leaving the index missing would make the
            // next corrupt read look like a first run all over again.
            persistLocked()
            Log.w(TAG, "rebuilt $reason index with ${apps.size} app(s) from html files")
        }
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (app in apps.sortedByDescending { it.updatedAt }) {
            array.put(
                JSONObject()
                    .put("id", app.id)
                    .put("name", app.name)
                    .put("icon", app.icon)
                    .put("createdAt", app.createdAt)
                    .put("updatedAt", app.updatedAt)
            )
        }
        indexFile.write(JSONObject().put("version", 1).put("apps", array))
    }

    private fun htmlFor(id: String): String {
        val cached = htmlCache[id]
        if (cached != null) return cached
        val text = JsonFile(htmlFile(id)).readText().orEmpty()
        htmlCache[id] = text
        return text
    }

    private fun htmlFile(id: String): File = File(dir, safeFileName(id) + HTML_EXTENSION)

    private fun newId(): String {
        var id: String
        do {
            id = UUID.randomUUID().toString().take(8)
        } while (apps.any { it.id == id })
        return id
    }

    private fun rawTitle(html: String): String? {
        val match = TITLE_REGEX.find(html) ?: return null
        val raw = match.groupValues.getOrNull(1) ?: return null
        val text = decodeEntities(raw.replace(TAG_REGEX, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return text.ifEmpty { null }
    }

    private fun safeFileName(id: String): String =
        id.trim().replace(UNSAFE_FILE_CHARS, "_").ifEmpty { "app" }

    private fun decodeEntities(input: String): String {
        var out = input
        out = out.replace("&nbsp;", " ", ignoreCase = true)
        out = out.replace("&lt;", "<", ignoreCase = true)
        out = out.replace("&gt;", ">", ignoreCase = true)
        out = out.replace("&quot;", "\"", ignoreCase = true)
        out = out.replace("&#39;", "'", ignoreCase = true)
        out = out.replace("&#x27;", "'", ignoreCase = true)
        // Decode ampersands last so "&amp;lt;" becomes "&lt;" rather than "<".
        out = out.replace("&amp;", "&", ignoreCase = true)
        return out
    }

    private companion object {
        const val TAG = "MiniAppStore"
        const val HTML_EXTENSION = ".html"
        const val DEFAULT_NAME = "แอพใหม่"
        const val DEFAULT_ICON = "cube"

        val TITLE_REGEX = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        val TAG_REGEX = Regex("<[^>]+>")
        val WHITESPACE_REGEX = Regex("\\s+")
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

        val iconRules: List<Pair<List<String>, String>> = listOf(
            listOf("calculator", "เครื่องคิดเลข", "คำนวณ") to "calculator",
            listOf("todo", "to-do", "task list", "checklist", "รายการงาน", "งานที่ต้อง") to "list-check",
            listOf("calendar", "ปฏิทิน", "ตารางนัด") to "calendar-days",
            listOf("timer", "stopwatch", "pomodoro", "นาฬิกาจับเวลา") to "stopwatch",
            listOf("clock", "นาฬิกา", "world time", "เวลาโลก") to "clock",
            listOf("weather", "พยากรณ์", "อุณหภูมิ", "อากาศ") to "cloud-sun",
            listOf("music", "เพลง", "spotify", "soundcloud", "song") to "music",
            listOf("movie", "film", "หนัง", "video player", "youtube") to "film",
            listOf("camera", "กล้อง", "photo", "รูปภาพ") to "camera",
            listOf("note", "memo", "โน้ต", "จดบันทึก", "journal", "diary") to "note-sticky",
            listOf("chatgpt", "openai", "gpt", "ปัญญาประดิษฐ์", "ai assistant") to "robot",
            listOf("chat", "แชท", "แชต", "messenger") to "comment",
            listOf("money", "finance", "budget", "การเงิน", "ลงทุน", "crypto", "stock") to "sack-dollar",
            listOf("shop", "store", "ร้านค้า", "ecommerce", "cart", "สินค้า") to "bag-shopping",
            listOf("map", "แผนที่", "travel", "เที่ยว", "นำทาง") to "map",
            listOf("game", "เกม", "arcade", "puzzle") to "gamepad",
            listOf("quiz", "trivia", "แบบทดสอบ", "คำถาม") to "brain",
            listOf("fitness", "workout", "gym", "ออกกำลัง", "สุขภาพ") to "dumbbell",
            listOf("food", "อาหาร", "recipe", "สูตรอาหาร", "เมนู") to "burger",
            listOf("translate", "แปลภาษา", "language") to "language",
            listOf("email", "mail", "อีเมล", "inbox") to "envelope",
            listOf("chart", "graph", "dashboard", "สถิติ", "กราฟ") to "chart-line",
            listOf("dice", "สุ่ม", "random") to "dice"
        )
    }
}
