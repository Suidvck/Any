package com.suidvck.any

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.suidvck.any.features.ai.AiMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal OpenAI-compatible chat client (works with Gemini, OpenAI, OpenRouter, etc.).
 *
 * [chat] is the original whole-reply call. [streamChat] streams Server-Sent-Events deltas so the
 * UI can render the answer while it is being generated. Both run their HTTP work on a background
 * thread and report back on the main thread. The API key is only ever written to the request
 * header; it is never logged.
 */
object AiClient {

    const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta/openai/"
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 180_000
    private const val MAX_HISTORY = 12
    private const val MAX_JSON_FALLBACK_CHARS = 2_000_000
    private const val MAX_ERROR_BODY_CHARS = 4_000
    private const val DELTA_POST_INTERVAL_MS = 40L
    private const val USER_AGENT = "AnyApp/1.0"

    private val SYSTEM_PROMPT = """
        You are the assistant inside a small Thai Android app called Any.
        The app can do three special things with your answer:
        (1) run a complete self-contained HTML page that you write (games, tools, pages),
        (2) open a website URL, or
        (3) show a small floating overlay on top of what is currently on screen.

        Rules:
        1) If the user asks to create / make anything (game, tool, calculator, page, app, etc.):
           reply with 1-2 short Thai sentences, then append exactly ONE fenced code block:

           ```html
           <!DOCTYPE html> ... complete single-file document ...
           ```

           The HTML must be a COMPLETE single-file document: viewport meta tag, all CSS and JS
           inline, no external CDN or resources, mobile-first and touch friendly (for games use
           touch controls and make the canvas/page fill the screen), polished modern look
           (dark theme preferred), Thai or English text is fine.
        2) If the user asks to open a website or service (for example "เปิด youtube",
           "เปิด facebook", "open google"): reply with 1 short Thai sentence, then on a new
           last line write exactly:
           OPEN_URL: https://www.youtube.com
           Use the real site URL (for a search use the site's search URL).
        3) If the user asks for something that floats on top of what they are looking at
           right now — an overlay, ส่วนเสริม, a floating clock / notes / buttons / widget
           on the current screen ("ทำ overlay", "ลอยทับหน้าจอ", "วางทับเว็บนี้"): reply
           with 1 short Thai sentence, then append exactly ONE fenced code block tagged
           html-overlay:

           ```html-overlay
           <html> ... compact self-contained overlay UI ... </html>
           ```

           The overlay runs in a small draggable floating panel: keep it compact and start
           from a fully transparent page (html,body{margin:0;background:transparent}) —
           draw your own rounded card inside only if you want a visible panel. No
           fullscreen layouts, no external resources.
        4) Otherwise just reply briefly in Thai.
        Never mix OPEN_URL with a code block. Never output more than one code block.
        Keep replies short. You have no memory of previous conversations;
        answer the latest request standalone.
    """.trimIndent()

    /**
     * Original non-streaming call. Kept for backwards compatibility.
     *
     * @param history "user"/"assistant" to content, oldest first.
     */
    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<Pair<String, String>>, // "user"/"assistant" to content, oldest first
        callback: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    history.takeLast(12).forEach { (role, content) ->
                        put(JSONObject().put("role", role).put("content", content))
                    }
                }
                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .toString()

                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code in 200..299) {
                    val content = JSONObject(body)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    if (content.isBlank() || content == "null") {
                        callback(Result.failure(Exception("คำตอบว่างเปล่า (HTTP $code)")))
                    } else {
                        callback(Result.success(content))
                    }
                } else {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    val detail = msg?.takeIf { it.isNotBlank() } ?: body.take(200)
                    callback(Result.failure(Exception("HTTP $code: $detail")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }

    /**
     * Streaming chat completion (OpenAI-compatible `POST {base}/chat/completions` with
     * `"stream": true`).
     *
     * [onDelta] receives the FULL accumulated assistant text each time (not just the latest
     * delta). All callbacks are posted to the main thread. Exactly one of [onDone] / [onError]
     * fires per call. If the provider ignores `stream` and answers with a plain JSON document,
     * the whole reply is delivered through [onDone].
     *
     * @param systemExtra optional second system message (e.g. [com.suidvck.any.features.ai.AiPrompts.editSystem]).
     * @param history oldest-first conversation turns; the last 12 are sent.
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemExtra: String?,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val finished = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())

        fun postDelta(full: String) {
            if (finished.get()) return
            main.post { if (!finished.get()) onDelta(full) }
        }

        fun finishOk(full: String) {
            if (finished.compareAndSet(false, true)) main.post { onDone(full) }
        }

        fun finishErr(error: Exception) {
            if (finished.compareAndSet(false, true)) main.post { onError(error) }
        }

        Thread {
            var open: HttpURLConnection? = null
            try {
                val url = URL((baseUrl.ifBlank { DEFAULT_BASE }).trimEnd('/') + "/chat/completions")
                val payload = JSONObject()
                    .put("model", model.ifBlank { DEFAULT_MODEL })
                    .put("stream", true)
                    .put("messages", buildMessages(systemExtra, history))
                    .toString()

                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("User-Agent", USER_AGENT)
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }
                open = conn
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val body = readCapped(conn.errorStream)
                    finishErr(Exception("HTTP $code: ${errorDetail(body)}"))
                    return@Thread
                }

                val acc = StringBuilder()
                var lastPostAt = 0L
                var detected = false
                var isSse = false
                var done = false
                val jsonBody = StringBuilder()
                var pendingData: StringBuilder? = null

                fun pushDelta() {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPostAt >= DELTA_POST_INTERVAL_MS) {
                        lastPostAt = now
                        postDelta(acc.toString())
                    }
                }

                /** Returns true when the stream should stop (DONE or provider error). */
                fun handleEvent(raw: String): Boolean {
                    val data = raw.trim()
                    if (data.isEmpty()) return false
                    if (data == "[DONE]") return true
                    val obj = runCatching { JSONObject(data) }.getOrNull() ?: return false
                    val providerError = obj.optJSONObject("error")
                    if (providerError != null) {
                        finishErr(Exception(providerError.optString("message").ifBlank { "provider error" }))
                        return true
                    }
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                    val delta = extractText(choice?.optJSONObject("delta"))
                    if (!delta.isNullOrEmpty()) {
                        acc.append(delta)
                        pushDelta()
                    } else if (acc.isEmpty()) {
                        // Some providers emit one final `message` object instead of deltas.
                        val whole = extractText(choice?.optJSONObject("message"))
                        if (!whole.isNullOrEmpty()) {
                            acc.append(whole)
                            pushDelta()
                        }
                    }
                    return false
                }

                // Read incrementally (line by line) so SSE really streams.
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("ยกเลิกคำขอแล้ว")
                        val line = reader.readLine() ?: break

                        if (!detected) {
                            if (line.isBlank()) continue
                            detected = true
                            // A leading ":" is an SSE comment (some proxies send a ping first).
                            isSse = line.trimStart().let { it.startsWith("data:") || it.startsWith(":") }
                        }

                        if (!isSse) {
                            // Provider ignored `stream`; buffer the plain JSON document.
                            if (jsonBody.length < MAX_JSON_FALLBACK_CHARS) jsonBody.append(line).append('\n')
                            continue
                        }

                        val trimmed = line.trimStart()
                        if (line.isBlank()) {
                            val event = pendingData?.toString()
                            pendingData = null
                            if (event != null && handleEvent(event)) {
                                done = true
                                break
                            }
                        } else if (trimmed.startsWith(":")) {
                            // SSE comment / keep-alive
                        } else if (trimmed.startsWith("data:")) {
                            val piece = trimmed.substring(5).trimStart()
                            val buf = pendingData
                            if (buf == null) {
                                pendingData = StringBuilder(piece)
                            } else {
                                // Normally events are separated by a blank line; some proxies omit it,
                                // so dispatch an already-complete previous event as soon as the next
                                // data line arrives.
                                val previous = buf.toString()
                                val complete = previous == "[DONE]" ||
                                    runCatching { JSONObject(previous) }.isSuccess
                                if (complete) {
                                    pendingData = StringBuilder(piece)
                                    if (handleEvent(previous)) {
                                        done = true
                                        break
                                    }
                                } else {
                                    buf.append('\n').append(piece)
                                }
                            }
                        }
                        // other fields (event:, id:, retry:) are not needed here
                    }
                    if (!done) {
                        pendingData?.toString()?.let { if (handleEvent(it)) done = true }
                    }
                }

                if (finished.get()) return@Thread

                if (done) {
                    val full = acc.toString()
                    if (full.isBlank()) finishErr(Exception("คำตอบว่างเปล่า (HTTP $code)"))
                    else finishOk(full)
                    return@Thread
                }

                // Plain JSON fallback (or an SSE stream that ended without [DONE]).
                if (!isSse) {
                    val raw = jsonBody.toString()
                    val providerError = errorMessageOf(raw)
                    val full = fullReply(raw)
                    when {
                        providerError != null -> finishErr(Exception("HTTP $code: $providerError"))
                        !full.isNullOrBlank() -> finishOk(full)
                        else -> finishErr(Exception("คำตอบว่างเปล่า (HTTP $code)"))
                    }
                } else {
                    val full = acc.toString()
                    if (full.isBlank()) finishErr(Exception("คำตอบว่างเปล่า (HTTP $code)")) else finishOk(full)
                }
            } catch (e: Exception) {
                val cancelled = e is InterruptedException || Thread.currentThread().isInterrupted
                finishErr(if (cancelled) InterruptedException("ยกเลิกคำขอแล้ว") else e)
            } finally {
                runCatching { open?.disconnect() }
            }
        }.start()
    }

    /**
     * Fetches the provider's model list (OpenAI-compatible GET /models) and
     * returns sorted model ids. Used by the "search model" picker in settings.
     */
    fun listModels(
        baseUrl: String,
        apiKey: String,
        callback: (Result<List<String>>) -> Unit
    ) {
        Thread {
            try {
                val url = URL(baseUrl.trimEnd('/') + "/models")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code in 200..299) {
                    val arr = JSONObject(body).optJSONArray("data")
                    val ids = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
                        }
                    }
                    if (ids.isEmpty()) {
                        callback(Result.failure(Exception("ไม่พบโมเดลจากรายการที่ดึงมา")))
                    } else {
                        callback(Result.success(ids.sorted()))
                    }
                } else {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    val detail = msg?.takeIf { it.isNotBlank() } ?: body.take(200)
                    callback(Result.failure(Exception("HTTP $code: $detail")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }

    // ---------- payload / parsing helpers ----------

    private fun buildMessages(systemExtra: String?, history: List<AiMessage>): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        if (!systemExtra.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemExtra))
        }
        history.takeLast(MAX_HISTORY).forEach { message ->
            val image = message.imageDataUrl
            if (message.text.isBlank() && image.isNullOrBlank()) return@forEach
            val role = if (message.role == "assistant") "assistant" else "user"
            val entry = JSONObject().put("role", role)
            if (image.isNullOrBlank()) {
                entry.put("content", message.text)
            } else {
                val parts = JSONArray()
                parts.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", message.text.ifBlank { "ช่วยอธิบายรูปนี้" })
                )
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", image))
                )
                entry.put("content", parts)
            }
            messages.put(entry)
        }
        return messages
    }

    /** Reads delta/message content that may be a string or an array of `{type,text}` parts. */
    private fun extractText(obj: JSONObject?): String? {
        if (obj == null) return null
        when (val content = obj.opt("content")) {
            is String -> if (content.isNotEmpty() && content != "null") return content
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) sb.append(text)
                }
                if (sb.isNotEmpty()) return sb.toString()
            }
            else -> Unit
        }
        val fallback = obj.optString("text", "")
        return fallback.ifEmpty { null }
    }

    /** Whole reply from a non-streamed JSON body. */
    private fun fullReply(raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return null
        return extractText(choice.optJSONObject("message"))
            ?: extractText(choice.optJSONObject("delta"))
            ?: choice.optString("text", "").takeIf { it.isNotBlank() }
    }

    /** `error.message` style extraction, null when the body carries no error object. */
    private fun errorMessageOf(raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (val error = root.opt("error")) {
            is JSONObject -> error.optString("message", "").ifBlank { error.optString("status", "") }
                .takeIf { it.isNotBlank() }
            is JSONArray -> error.optJSONObject(0)?.optString("message", "")?.takeIf { it.isNotBlank() }
            is String -> error.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** "HTTP 401: <provider message>" detail, never the request headers. */
    private fun errorDetail(body: String): String {
        val message = errorMessageOf(body)
        if (!message.isNullOrBlank()) return message
        return body.take(200).replace(Regex("\\s+"), " ").trim().ifBlank { "ไม่ทราบสาเหตุ" }
    }

    /** Reads at most [MAX_ERROR_BODY_CHARS] characters (error bodies only). */
    private fun readCapped(stream: InputStream?): String {
        if (stream == null) return ""
        return runCatching {
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(1024)
                val sb = StringBuilder()
                while (sb.length < MAX_ERROR_BODY_CHARS) {
                    val read = reader.read(buffer, 0, minOf(buffer.size, MAX_ERROR_BODY_CHARS - sb.length))
                    if (read <= 0) break
                    sb.append(buffer, 0, read)
                }
                sb.toString()
            }
        }.getOrDefault("")
    }
}
