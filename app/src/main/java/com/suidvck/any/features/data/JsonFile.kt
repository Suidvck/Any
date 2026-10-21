package com.suidvck.any.features.data

import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Crash-proof JSON storage for a single file.
 *
 * Reads are best-effort: a missing, unreadable, empty, or malformed file simply yields null.
 * Writes are atomic (temporary file in the same directory, then rename) and are serialized
 * process-wide so concurrent stores cannot interleave temporary files on disk.
 */
internal class JsonFile(file: File) {

    private val target: File = file
    private val lock = Any()

    fun exists(): Boolean = try {
        target.isFile
    } catch (_: Throwable) {
        false
    }

    fun readText(): String? = synchronized(lock) {
        try {
            if (!target.isFile) null else target.readText(Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    fun readObject(): JSONObject? {
        val text = readText()
        if (text.isNullOrBlank()) return null
        return try {
            JSONObject(text)
        } catch (_: Throwable) {
            null
        }
    }

    fun readArray(): JSONArray? {
        val text = readText()
        if (text.isNullOrBlank()) return null
        return try {
            JSONArray(text)
        } catch (_: Throwable) {
            null
        }
    }

    fun write(text: String): Boolean = synchronized(lock) {
        atomicWrite(target, text)
    }

    fun write(json: JSONObject): Boolean = write(json.toString())

    fun write(array: JSONArray): Boolean = write(array.toString())

    fun delete(): Boolean = synchronized(lock) {
        try {
            if (!target.exists()) true else target.delete()
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private val writeLock = Any()

        fun atomicWrite(target: File, text: String): Boolean = synchronized(writeLock) {
            val dir = target.parentFile
            if (dir == null) {
                false
            } else {
                var temp: File? = null
                try {
                    if (!dir.isDirectory) {
                        dir.mkdirs()
                    }
                    if (!dir.isDirectory) {
                        false
                    } else {
                        val rawPrefix = target.name + "."
                        val prefix = if (rawPrefix.length < 3) "tmp-$rawPrefix" else rawPrefix
                        val tmp = File.createTempFile(prefix, ".tmp", dir)
                        temp = tmp
                        FileOutputStream(tmp).use { out ->
                            out.write(text.toByteArray(Charsets.UTF_8))
                            out.flush()
                            try {
                                out.fd.sync()
                            } catch (_: Throwable) {
                                // Not every filesystem supports fsync; the rename still protects us.
                            }
                        }
                        if (!tmp.renameTo(target)) {
                            // Last-resort fallback for filesystems that cannot rename over a file.
                            target.writeText(text, Charsets.UTF_8)
                            tmp.delete()
                        }
                        true
                    }
                } catch (_: Throwable) {
                    false
                } finally {
                    try {
                        temp?.delete()
                    } catch (_: Throwable) {
                        // Ignore cleanup failures.
                    }
                }
            }
        }
    }
}
