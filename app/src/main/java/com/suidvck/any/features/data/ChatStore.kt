package com.suidvck.any.features.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(
    val role: String,
    val text: String,
    val imageDataUrl: String? = null,
    val ts: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String,
    val title: String,
    val messages: MutableList<StoredMessage>,
    var updatedAt: Long
)

/**
 * Conversation storage. Writes are debounced for chatty streaming updates and can be forced
 * with [saveAllNow] (call it from Activity.onStop / onDestroy).
 */
class ChatStore(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val file = JsonFile(File(appContext.filesDir, "conversations.json"))
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { flush() }

    private var loaded = false
    private var dirty = false
    private var currentId: String? = null
    private val conversations = ArrayList<Conversation>()

    fun conversations(): List<Conversation> = synchronized(lock) {
        ensureLoaded()
        conversations.sortedByDescending { it.updatedAt }.toList()
    }

    fun current(): Conversation = synchronized(lock) {
        ensureLoaded()
        currentLocked()
    }

    fun append(message: StoredMessage) {
        synchronized(lock) {
            ensureLoaded()
            val conversation = currentLocked()
            conversation.messages.add(message)
            if (conversation.messages.size > MAX_MESSAGES) {
                val drop = conversation.messages.size - MAX_MESSAGES
                conversation.messages.subList(0, drop).clear()
            }
            val title = if (message.role == "user" && conversation.title.trim() == DEFAULT_TITLE) {
                autoTitle(message.text)
            } else {
                ""
            }
            // Conversation.title is a val (shared contract), so replace the list entry.
            val updated = if (title.isNotEmpty()) conversation.copy(title = title) else conversation
            updated.updatedAt = System.currentTimeMillis()
            val index = conversations.indexOfFirst { it.id == updated.id }
            if (index >= 0) conversations[index] = updated
            trimConversationsLocked()
            scheduleSaveLocked()
        }
    }

    fun newConversation(): Conversation = synchronized(lock) {
        ensureLoaded()
        createConversationLocked(DEFAULT_TITLE)
    }

    fun select(id: String): Conversation? = synchronized(lock) {
        ensureLoaded()
        val conversation = conversations.firstOrNull { it.id == id }
        if (conversation != null && currentId != conversation.id) {
            currentId = conversation.id
            scheduleSaveLocked()
        }
        conversation
    }

    fun rename(id: String, title: String) {
        synchronized(lock) {
            ensureLoaded()
            val conversation = conversations.firstOrNull { it.id == id }
            val clean = title.trim()
            if (conversation != null && clean.isNotEmpty() && conversation.title != clean) {
                val index = conversations.indexOfFirst { it.id == id }
                if (index >= 0) conversations[index] = conversation.copy(title = clean)
                scheduleSaveLocked()
            }
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            ensureLoaded()
            val changed = conversations.removeAll { it.id == id }
            if (changed) {
                if (conversations.none { it.id == currentId }) {
                    currentId = conversations.maxByOrNull { it.updatedAt }?.id
                }
                scheduleSaveLocked()
            }
        }
    }

    fun clearCurrent() {
        synchronized(lock) {
            ensureLoaded()
            val conversation = currentLocked()
            conversation.messages.clear()
            conversation.updatedAt = System.currentTimeMillis()
            scheduleSaveLocked()
        }
    }

    fun saveAllNow() {
        try {
            handler.removeCallbacks(saveRunnable)
        } catch (_: Throwable) {
            // Handler could only fail if the looper is gone; the write below still runs.
        }
        flush()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val text = file.readText()
        if (text.isNullOrBlank()) return

        val objectRoot = try {
            JSONObject(text)
        } catch (_: Throwable) {
            null
        }
        if (objectRoot != null) {
            currentId = if (objectRoot.isNull("currentId")) {
                null
            } else {
                objectRoot.optString("currentId").trim().ifEmpty { null }
            }
            val array = objectRoot.optJSONArray("conversations")
            if (array != null) {
                parseConversations(array)
                normalizeLocked()
            }
            return
        }

        // Tolerate a legacy bare-array file.
        val arrayRoot = try {
            JSONArray(text)
        } catch (_: Throwable) {
            null
        }
        if (arrayRoot != null) {
            parseConversations(arrayRoot)
            normalizeLocked()
        } else {
            conversations.clear()
            currentId = null
        }
    }

    private fun parseConversations(array: JSONArray) {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            var id = obj.optString("id").trim()
            if (id.isEmpty()) {
                id = newId()
            }
            if (conversations.any { it.id == id }) continue
            val title = obj.optString("title").trim().ifEmpty { DEFAULT_TITLE }
            val updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val messages = ArrayList<StoredMessage>()
            val messageArray = obj.optJSONArray("messages")
            if (messageArray != null) {
                for (j in 0 until messageArray.length()) {
                    val raw = messageArray.optJSONObject(j) ?: continue
                    val role = raw.optString("role", "assistant")
                        .takeIf { it == "user" || it == "assistant" }
                        ?: "assistant"
                    val messageText = if (raw.isNull("text")) "" else raw.optString("text")
                    val image = if (raw.isNull("imageDataUrl")) {
                        null
                    } else {
                        raw.optString("imageDataUrl").ifEmpty { null }
                    }
                    val ts = raw.optLong("ts", updatedAt)
                    messages.add(StoredMessage(role, messageText, image, ts))
                }
            }
            if (messages.size > MAX_MESSAGES) {
                messages.subList(0, messages.size - MAX_MESSAGES).clear()
            }
            conversations.add(Conversation(id, title, messages, updatedAt))
        }
    }

    private fun normalizeLocked() {
        conversations.sortByDescending { it.updatedAt }
        while (conversations.size > MAX_CONVERSATIONS) {
            val victim = conversations.indexOfLast { it.id != currentId }
            if (victim < 0) break
            conversations.removeAt(victim)
        }
        if (conversations.none { it.id == currentId }) {
            currentId = conversations.firstOrNull()?.id
        }
    }

    private fun currentLocked(): Conversation {
        conversations.firstOrNull { it.id == currentId }?.let { return it }
        val recent = conversations.maxByOrNull { it.updatedAt }
        if (recent != null) {
            currentId = recent.id
            scheduleSaveLocked()
            return recent
        }
        return createConversationLocked(DEFAULT_TITLE)
    }

    private fun createConversationLocked(title: String): Conversation {
        val conversation = Conversation(
            id = newId(),
            title = title,
            messages = ArrayList(),
            updatedAt = System.currentTimeMillis()
        )
        conversations.add(conversation)
        currentId = conversation.id
        trimConversationsLocked()
        scheduleSaveLocked()
        return conversation
    }

    private fun trimConversationsLocked() {
        while (conversations.size > MAX_CONVERSATIONS) {
            val victim = conversations.filter { it.id != currentId }
                .minByOrNull { it.updatedAt }
                ?: break
            conversations.remove(victim)
        }
    }

    private fun newId(): String {
        var id: String
        do {
            id = UUID.randomUUID().toString().take(8)
        } while (conversations.any { it.id == id })
        return id
    }

    private fun autoTitle(text: String): String {
        val clean = text.trim().replace(WHITESPACE_REGEX, " ")
        if (clean.isEmpty()) return ""
        var index = 0
        var count = 0
        while (index < clean.length && count < TITLE_MAX) {
            index += Character.charCount(clean.codePointAt(index))
            count++
        }
        return clean.substring(0, index)
    }

    private fun scheduleSaveLocked() {
        dirty = true
        try {
            handler.removeCallbacks(saveRunnable)
            handler.postDelayed(saveRunnable, SAVE_DELAY_MS)
        } catch (_: Throwable) {
            // If posting is impossible the data stays dirty and saveAllNow can still write it.
        }
    }

    private fun flush() {
        synchronized(lock) {
            if (!loaded || !dirty) return
            if (writeLocked()) dirty = false
        }
    }

    private fun writeLocked(): Boolean {
        val array = JSONArray()
        for (conversation in conversations) {
            val messages = JSONArray()
            for (message in conversation.messages) {
                messages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("text", message.text)
                        .put(
                            "imageDataUrl",
                            message.imageDataUrl ?: JSONObject.NULL
                        )
                        .put("ts", message.ts)
                )
            }
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title)
                    .put("updatedAt", conversation.updatedAt)
                    .put("messages", messages)
            )
        }
        val root = JSONObject()
            .put("version", 1)
            .put("currentId", currentId ?: JSONObject.NULL)
            .put("conversations", array)
        return file.write(root)
    }

    private companion object {
        const val DEFAULT_TITLE = "แชทใหม่"
        const val TITLE_MAX = 32
        const val MAX_CONVERSATIONS = 60
        const val MAX_MESSAGES = 200
        const val SAVE_DELAY_MS = 400L
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
