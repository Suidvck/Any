package com.suidvck.any.features.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Automation(
    val id: String,
    val hour: Int,
    val minute: Int,
    val kind: String,
    val value: String,
    val label: String,
    val enabled: Boolean = true
)

class AutomationStore(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val file = JsonFile(File(appContext.filesDir, "automations.json"))
    private val lock = Any()

    private var loaded = false
    private val automations = ArrayList<Automation>()

    fun list(): List<Automation> = synchronized(lock) {
        ensureLoaded()
        automations.sortedWith(
            compareBy<Automation>({ it.hour }, { it.minute }, { it.label })
        )
    }

    fun upsert(a: Automation) {
        synchronized(lock) {
            ensureLoaded()
            val id = a.id.trim().ifEmpty { newId() }
            val kind = a.kind.trim().lowercase(Locale.ROOT).takeIf { it in KINDS } ?: "url"
            val value = a.value.trim()
            val cleaned = a.copy(
                id = id,
                hour = a.hour.coerceIn(0, 23),
                minute = a.minute.coerceIn(0, 59),
                kind = kind,
                value = value,
                label = a.label.trim().ifEmpty { fallbackLabel(kind, value) }
            )
            val index = automations.indexOfFirst { it.id == id }
            if (index >= 0) {
                automations[index] = cleaned
            } else {
                automations.add(cleaned)
            }
            persistLocked()
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            ensureLoaded()
            val changed = automations.removeAll { it.id == id }
            if (changed) persistLocked()
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        synchronized(lock) {
            ensureLoaded()
            val index = automations.indexOfFirst { it.id == id }
            if (index >= 0 && automations[index].enabled != enabled) {
                automations[index] = automations[index].copy(enabled = enabled)
                persistLocked()
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val root = when (val read = file.readJson()) {
            JsonFile.Read.Missing -> return
            JsonFile.Read.Corrupt -> {
                val preserved = file.quarantine()
                Log.w("AutomationStore", "corrupt automations quarantined at ${preserved?.name ?: "?"}")
                return
            }
            is JsonFile.Read.Ok -> try {
                JSONObject(read.text)
            } catch (_: Throwable) {
                return
            }
        }
        val array = root.optJSONArray("automations") ?: return
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            var id = obj.optString("id").trim()
            if (id.isEmpty()) id = newId()
            if (automations.any { it.id == id }) continue
            val kind = obj.optString("kind", "url").trim().lowercase(Locale.ROOT)
                .takeIf { it in KINDS }
                ?: "url"
            val value = obj.optString("value").trim()
            val label = obj.optString("label").trim().ifEmpty { fallbackLabel(kind, value) }
            automations.add(
                Automation(
                    id = id,
                    hour = obj.optInt("hour", 9).coerceIn(0, 23),
                    minute = obj.optInt("minute", 0).coerceIn(0, 59),
                    kind = kind,
                    value = value,
                    label = label,
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (automation in automations) {
            array.put(
                JSONObject()
                    .put("id", automation.id)
                    .put("hour", automation.hour)
                    .put("minute", automation.minute)
                    .put("kind", automation.kind)
                    .put("value", automation.value)
                    .put("label", automation.label)
                    .put("enabled", automation.enabled)
            )
        }
        file.write(JSONObject().put("version", 1).put("automations", array))
    }

    private fun fallbackLabel(kind: String, value: String): String {
        if (value.isNotEmpty()) return value.take(40)
        return when (kind) {
            "chat" -> "แชท"
            "prompt" -> "พรอมป์"
            "app" -> "เปิดแอพ"
            else -> "เปิดเว็บ"
        }
    }

    private fun newId(): String {
        var id: String
        do {
            id = UUID.randomUUID().toString().take(8)
        } while (automations.any { it.id == id })
        return id
    }

    private companion object {
        val KINDS = setOf("url", "chat", "prompt", "app")
    }
}
