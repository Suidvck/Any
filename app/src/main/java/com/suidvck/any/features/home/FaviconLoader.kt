package com.suidvck.any.features.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Loads the real icon a website declares (apple-touch-icon / `<link rel="icon">` /
 * favicon.ico), normalises it to a small square PNG and caches it on disk.
 *
 * Everything happens off the main thread; callbacks are posted back to the main
 * thread. A host is only tried once per process after a failure so a dead link
 * cannot keep the dock busy.
 */
object FaviconLoader {

    private const val DIR = "favicons"
    private const val TARGET_PX = 128
    private const val MAX_ICON_BYTES = 1536 * 1024
    private const val MAX_PAGE_BYTES = 400 * 1024
    private const val MAX_DISK_BYTES = 10 * 1024

    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val failed = HashSet<String>()

    private val linkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val relAttr = Regex("""rel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val hrefAttr = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val sizesAttr = Regex("""sizes\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private data class Candidate(val url: String, val rank: Int, val size: Int)

    /** Loads [pageUrl]'s icon; [onResult] always runs on the main thread. */
    fun load(context: Context, pageUrl: String, onResult: (Bitmap?) -> Unit) {
        val key = keyFor(pageUrl)
        if (key == null) {
            onResult(null)
            return
        }
        memory.get(key)?.let {
            onResult(it)
            return
        }
        val app = context.applicationContext ?: context
        executor.execute {
            val alreadyFailed = synchronized(failed) { failed.contains(key) }
            val bitmap = if (alreadyFailed) {
                null
            } else {
                loadFromDisk(app, key) ?: runCatching { fetch(app, key, pageUrl) }.getOrNull()
            }

            if (bitmap == null) {
                synchronized(failed) { failed.add(key) }
            } else {
                memory.put(key, bitmap)
            }
            main.post { onResult(bitmap) }
        }
    }

    private fun keyFor(pageUrl: String): String? {
        val host = runCatching { URI(pageUrl.trim()).host }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return sha1(host)
    }

    private fun fetch(app: Context, key: String, pageUrl: String): Bitmap? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val scheme = if (uri.scheme.equals("http", true)) "http" else "https"
        val origin = "$scheme://$host"

        val candidates = ArrayList<Candidate>()
        val html = fetchText("$origin/")
        if (html != null) candidates += parseIcons(html, "$origin/")

        // Common fallbacks, in order of usefulness.
        candidates += Candidate("$origin/apple-touch-icon.png", 1, 180)
        candidates += Candidate("$origin/favicon.ico", 3, 32)
        candidates += Candidate("$origin/favicon.png", 3, 32)

        val seen = HashSet<String>()
        for (candidate in candidates.sortedWith(compareBy({ it.rank }, { -it.size }))) {
            if (!seen.add(candidate.url)) continue
            val bytes = fetchBytes(candidate.url) ?: continue
            val decoded = decodeIcon(bytes) ?: continue
            val square = square(decoded) ?: continue
            saveToDisk(app, key, square)
            return square
        }
        return null
    }

    private fun parseIcons(html: String, base: String): List<Candidate> {
        val result = ArrayList<Candidate>()
        val root = runCatching { URI(base) }.getOrNull() ?: return result
        for (match in linkTag.findAll(html)) {
            val tag = match.value
            val rel = relAttr.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
            if (!rel.contains("icon")) continue
            if (rel.contains("mask-icon")) continue
            val href = hrefAttr.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isBlank() || href.startsWith("data:", true)) continue
            val resolved = runCatching {
                root.resolve(href.replace(" ", "%20")).toString()
            }.getOrNull() ?: continue
            if (!resolved.startsWith("http://", true) && !resolved.startsWith("https://", true)) continue

            val size = sizesAttr.find(tag)?.groupValues?.get(1)
                ?.substringBefore('x')
                ?.toIntOrNull() ?: 0
            val rank = when {
                rel.contains("apple-touch-icon") -> 0
                size >= 152 -> 1
                size == 0 -> 4
                size >= 96 -> 2
                else -> 3
            }
            result += Candidate(resolved, rank, size)
        }
        return result
    }

    private fun loadFromDisk(app: Context, key: String): Bitmap? {
        val file = cacheFile(app, key)
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_DISK_BYTES) return null
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
        }
        return bitmap
    }

    private fun saveToDisk(app: Context, key: String, bitmap: Bitmap) {
        runCatching {
            val dir = File(app.filesDir, DIR).apply { mkdirs() }
            val target = File(dir, "$key.png")
            val tmp = File(dir, "$key.tmp")
            tmp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun cacheFile(app: Context, key: String): File =
        File(File(app.filesDir, DIR), "$key.png")

    private fun fetchText(url: String): String? {
        val bytes = fetchBytes(url, MAX_PAGE_BYTES) ?: return null
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }

    private fun fetchBytes(url: String, limit: Int = MAX_ICON_BYTES): ByteArray? {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/*,text/html;q=0.8,*/*;q=0.5")
            }
            val code = connection!!.responseCode
            if (code !in 200..299) return null
            val stream: InputStream = connection!!.inputStream
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > limit) break
                    out.write(buffer, 0, read)
                }
            }
            if (total == 0) null else out.toByteArray()
        }.getOrNull().also {
            runCatching { connection?.disconnect() }
        }
    }

    private fun decodeIcon(bytes: ByteArray): Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: decodeIco(bytes)
    }.getOrNull()

    /** Minimal ICO reader: PNG-compressed entries plus uncompressed 24/32-bit DIBs. */
    private fun decodeIco(bytes: ByteArray): Bitmap? {
        if (bytes.size < 6) return null
        if (le16(bytes, 0) != 0 || le16(bytes, 2) != 1) return null
        val count = le16(bytes, 4)
        if (count <= 0) return null

        var bestOffset = -1
        var bestScore = -1
        for (index in 0 until count) {
            val entry = 6 + index * 16
            if (entry + 16 > bytes.size) break
            val width = (bytes[entry].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val height = (bytes[entry + 1].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val score = width * height
            if (score > bestScore) {
                bestScore = score
                bestOffset = entry
            }
        }
        if (bestOffset < 0) return null

        val length = le32(bytes, bestOffset + 8)
        val offset = le32(bytes, bestOffset + 12)
        if (length <= 0 || offset < 0 || offset.toLong() + length > bytes.size) return null
        val payload = bytes.copyOfRange(offset, offset + length)
        if (payload.size > 8 && payload[0] == 0x89.toByte() && payload[1] == 0x50.toByte()) {
            return BitmapFactory.decodeByteArray(payload, 0, payload.size)
        }
        return decodeDib(payload)
    }

    private fun decodeDib(data: ByteArray): Bitmap? {
        if (data.size < 40) return null
        val headerSize = le32(data, 0)
        if (headerSize < 40 || headerSize > data.size) return null
        val width = le32(data, 4)
        val doubledHeight = le32(data, 8)
        if (width <= 0 || width > 512 || doubledHeight <= 1 || doubledHeight > 1024) return null
        val height = doubledHeight / 2
        val bpp = le16(data, 14)
        if (bpp != 32 && bpp != 24) return null

        val colorsUsed = if (data.size >= 36) le32(data, 32) else 0
        val palette = if (bpp <= 8) (if (colorsUsed == 0) 1 shl bpp else colorsUsed) * 4 else 0
        val pixelOffset = headerSize + palette
        val stride = ((width * bpp + 31) / 32) * 4
        if (pixelOffset < 0 || pixelOffset + stride * height > data.size) return null

        val maskStride = ((width + 31) / 32) * 4
        val maskOffset = pixelOffset + stride * height
        val pixels = IntArray(width * height)
        var anyAlpha = false

        for (row in 0 until height) {
            val src = pixelOffset + (height - 1 - row) * stride
            for (col in 0 until width) {
                val p = src + col * (bpp / 8)
                if (p + (bpp / 8) > data.size) return null
                val b = data[p].toInt() and 0xFF
                val g = data[p + 1].toInt() and 0xFF
                val r = data[p + 2].toInt() and 0xFF
                val a = if (bpp == 32) data[p + 3].toInt() and 0xFF else 0xFF
                if (a != 0) anyAlpha = true
                pixels[row * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        if (bpp == 24 && maskOffset + maskStride * height <= data.size) {
            for (row in 0 until height) {
                val maskRow = maskOffset + (height - 1 - row) * maskStride
                for (col in 0 until width) {
                    val bit = (data[maskRow + col / 8].toInt() shr (7 - col % 8)) and 1
                    if (bit == 1) pixels[row * width + col] = 0
                }
            }
        } else if (bpp == 32 && !anyAlpha) {
            // Old icons sometimes leave the alpha channel empty; treat them as opaque.
            for (index in pixels.indices) {
                pixels[index] = pixels[index] or (0xFF shl 24)
            }
        }

        return runCatching {
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun square(source: Bitmap): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val side = min(source.width, source.height)
        val cropped = if (side == source.width && side == source.height) {
            source
        } else {
            Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side
            )
        }
        val output = Bitmap.createBitmap(TARGET_PX, TARGET_PX, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            cropped,
            null,
            RectF(0f, 0f, TARGET_PX.toFloat(), TARGET_PX.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        if (cropped !== source) cropped.recycle()
        if (source !== output) source.recycle()
        return output
    }

    private fun le16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0 Mobile Safari/537.36 Any/1.3"
}
