package com.suidvck.any

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenAI-compatible chat client (works with Gemini, OpenAI, OpenRouter, etc.).
 * Runs the HTTP call on a background thread and reports back the assistant text.
 */
object AiClient {

    const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta/openai/"
    const val DEFAULT_MODEL = "gemini-2.5-flash"

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
                    setRequestProperty("User-Agent", "AnyApp/1.0")
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
                    setRequestProperty("User-Agent", "AnyApp/1.0")
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
}
