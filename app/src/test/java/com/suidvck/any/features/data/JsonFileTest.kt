package com.suidvck.any.features.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String = "store.json"): File = File(temp.root, name)

    @Test
    fun `missing file is reported as missing, not corrupt`() {
        val read = JsonFile(file()).readJson()
        assertTrue(read is JsonFile.Read.Missing)
    }

    @Test
    fun `valid object is returned as ok with its original text`() {
        val target = file()
        target.writeText("""{"version":1,"apps":[]}""")
        val read = JsonFile(target).readJson()
        assertTrue(read is JsonFile.Read.Ok)
        assertEquals("""{"version":1,"apps":[]}""", (read as JsonFile.Read.Ok).text)
    }

    @Test
    fun `valid array is returned as ok`() {
        val target = file()
        target.writeText("""[1,2,3]""")
        assertTrue(JsonFile(target).readJson() is JsonFile.Read.Ok)
    }

    @Test
    fun `blank file is corrupt`() {
        val target = file()
        target.writeText("   \n")
        assertTrue(JsonFile(target).readJson() is JsonFile.Read.Corrupt)
    }

    @Test
    fun `truncated json is corrupt`() {
        val target = file()
        target.writeText("""{"version":1,"apps":[{"id":""")
        assertTrue(JsonFile(target).readJson() is JsonFile.Read.Corrupt)
    }

    @Test
    fun `quarantine preserves the damaged bytes and clears the original`() {
        val target = file()
        val damaged = """{"apps":[/"""
        target.writeText(damaged)
        val store = JsonFile(target)

        val preserved = store.quarantine()

        assertNotNull(preserved)
        assertFalse(target.exists())
        assertEquals(damaged, preserved!!.readText())
        assertTrue(preserved.name.startsWith("store.json.corrupt-"))
        // Nothing left to quarantine on a second call.
        assertNull(store.quarantine())
    }

    @Test
    fun `write creates the directory and can be read back`() {
        val target = File(File(temp.root, "nested/deeper"), "store.json")
        val store = JsonFile(target)

        assertTrue(store.write(JSONObject().put("ok", true)))

        val read = store.readJson()
        assertTrue(read is JsonFile.Read.Ok)
        assertEquals(true, JSONObject((read as JsonFile.Read.Ok).text).getBoolean("ok"))
        assertEquals("""{"ok":true}""", store.readText())
    }

    @Test
    fun `write replaces previous content without leaving temp files`() {
        val target = file()
        val store = JsonFile(target)

        assertTrue(store.write(JSONArray().put(1)))
        assertTrue(store.write(JSONArray().put(2).put(3)))

        assertEquals("[2,3]", target.readText())
        val leftovers = temp.root.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue("temp files left behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `write fails when the parent path is a file`() {
        val blocker = File(temp.root, "blocked")
        blocker.writeText("not a directory")
        val target = File(blocker, "store.json")

        assertFalse(JsonFile(target).write("{}"))
        assertFalse(target.exists())
    }

    @Test
    fun `readText returns null for a missing file`() {
        assertNull(JsonFile(file("absent.html")).readText())
    }
}
