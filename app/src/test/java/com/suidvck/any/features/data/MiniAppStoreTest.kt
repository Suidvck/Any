package com.suidvck.any.features.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MiniAppStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun dir(): File = File(temp.root, "miniapps")

    private fun html(title: String) =
        "<!DOCTYPE html><html><head><title>$title</title></head><body></body></html>"

    @Test
    fun `save writes the html file and an index entry`() {
        val store = MiniAppStore(dir())

        val app = store.save(html("Timer"), id = "abc123")
        assertNotNull(app)
        val saved = app!!

        assertEquals("abc123", saved.id)
        assertEquals("Timer", saved.name)
        assertEquals(html("Timer"), File(dir(), "abc123.html").readText())
        val index = File(dir(), "index.json").readText()
        assertTrue(index, index.contains("\"abc123\""))
        assertTrue(index, index.contains("\"Timer\""))
    }

    @Test
    fun `a fresh instance reads back the saved html`() {
        MiniAppStore(dir()).save(html("Notes"), id = "n1")

        val reopened = MiniAppStore(dir())
        val app = reopened.get("n1")
        assertNotNull(app)
        val saved = app!!

        assertEquals("Notes", saved.name)
        assertEquals(html("Notes"), saved.html)
        assertEquals(1, reopened.list().size)
    }

    @Test
    fun `missing index is rebuilt from html files`() {
        val folder = dir()
        folder.mkdirs()
        File(folder, "timer1.html").writeText(html("Timer"))
        File(folder, "notes2.html").writeText(html("Notes"))

        val store = MiniAppStore(folder)
        val apps = store.list()

        assertEquals(2, apps.size)
        assertEquals(setOf("timer1", "notes2"), apps.map { it.id }.toSet())
        assertEquals("Timer", apps.first { it.id == "timer1" }.name)
        assertEquals(html("Timer"), apps.first { it.id == "timer1" }.html)
        // The rebuilt index must be persisted so the next launch reads a healthy file.
        assertTrue(File(folder, "index.json").isFile)
    }

    @Test
    fun `corrupt index is quarantined and rebuilt from html files`() {
        val folder = dir()
        folder.mkdirs()
        File(folder, "keepme.html").writeText(html("Keep Me"))
        val damaged = """{"version":1,"apps":[{"id":"keepme","""
        File(folder, "index.json").writeText(damaged)

        val apps = MiniAppStore(folder).list()

        assertEquals(1, apps.size)
        assertEquals("keepme", apps[0].id)
        assertEquals(html("Keep Me"), apps[0].html)
        val quarantined = folder.listFiles().orEmpty()
            .filter { it.name.startsWith("index.json.corrupt-") }
        assertEquals(1, quarantined.size)
        assertEquals(damaged, quarantined[0].readText())
    }

    @Test
    fun `corrupt index with no html files does not create an empty index`() {
        val folder = dir()
        folder.mkdirs()
        File(folder, "index.json").writeText("{not json")

        val store = MiniAppStore(folder)
        assertTrue(store.list().isEmpty())

        assertTrue(folder.listFiles().orEmpty().any { it.name.startsWith("index.json.corrupt-") })
        // Nothing was recovered, so there is nothing worth persisting yet.
        assertFalse(File(folder, "index.json").exists())
    }

    @Test
    fun `save returns null and keeps the old state when the html cannot be written`() {
        // Make the store directory a regular file so every write into it must fail.
        val blocker = File(temp.root, "blocked")
        blocker.writeText("not a directory")
        val store = MiniAppStore(blocker)

        val app = store.save(html("Doomed"))

        assertNull(app)
        assertEquals("not a directory", blocker.readText())
    }

    @Test
    fun `an explicit edit keeps the id, name and createdAt`() {
        val store = MiniAppStore(dir())
        val created = store.save(html("Old"), id = "same")!!

        val edited = store.save(html("New"), id = "same")!!

        assertEquals("same", edited.id)
        assertEquals(created.createdAt, edited.createdAt)
        // A user-facing rename must survive an AI edit, so the stored name wins over <title>.
        assertEquals("Old", edited.name)
        assertEquals(html("New"), File(dir(), "same.html").readText())
        assertEquals(1, store.list().size)
    }
}
