package com.suidvck.any.features.data

import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Crash-safe JSON storage for a single file.
 *
 * Reads deliberately distinguish **missing** from **corrupt**. Treating a damaged file as
 * "empty" is dangerous: the next persist would write the empty state back and destroy every
 * record that was in the file. Stores must inspect [Read] and choose a recovery path instead
 * of continuing with an empty list.
 *
 * Writes are atomic (temporary file in the same directory, fsync, rename) and serialised
 * process-wide. There is intentionally no in-place fallback when the rename fails: leaving the
 * previous file untouched is always better than truncating it with a partial write.
 */
internal class JsonFile(file: File) {

    private val target: File = file
    private val lock = Any()

    sealed class Read {
        /** The file does not exist (or was never written). */
        object Missing : Read()

        /** The file exists and contains parseable JSON. [text] is the raw content. */
        data class Ok(val text: String) : Read()

        /** The file exists but is empty, unreadable, or not valid JSON. */
        object Corrupt : Read()
    }

    fun exists(): Boolean = try {
        target.isFile
    } catch (_: Throwable) {
        false
    }

    fun readJson(): Read = synchronized(lock) {
        parseFile(target)
    }

    /**
     * Reads a plain text file (for example a mini-app's HTML). A missing or unreadable file
     * simply yields null; HTML has no structural integrity to protect beyond its presence.
     */
    fun readText(): String? = synchronized(lock) {
        try {
            if (target.isFile) target.readText(Charsets.UTF_8) else null
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

    /**
     * Moves a damaged file aside so a later write cannot destroy the original bytes, while
     * keeping it available for manual recovery. Returns the preserved file, or null when
     * there was nothing to move.
     */
    fun quarantine(): File? = synchronized(lock) {
        if (!target.isFile) return null
        val stamp = System.currentTimeMillis()
        val preserved = File(target.parentFile, target.name + ".corrupt-" + stamp)
        try {
            if (target.renameTo(preserved)) preserved else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseFile(file: File): Read {
        if (!file.isFile) return Read.Missing
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Throwable) {
            return Read.Corrupt
        }
        if (text.isBlank()) return Read.Corrupt
        val valid = try {
            JSONObject(text)
            true
        } catch (_: Throwable) {
            try {
                JSONArray(text)
                true
            } catch (_: Throwable) {
                false
            }
        }
        return if (valid) Read.Ok(text) else Read.Corrupt
    }

    companion object {
        private val writeLock = Any()

        fun atomicWrite(target: File, text: String): Boolean = synchronized(writeLock) {
            val dir = target.parentFile ?: return false
            var temp: File? = null
            try {
                if (!dir.isDirectory) dir.mkdirs()
                if (!dir.isDirectory) return false

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
                // No in-place fallback on purpose. A failed rename returns false and leaves
                // the previous file intact instead of replacing it with a partial write.
                tmp.renameTo(target)
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
