package com.suidvck.any.features.home

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.R
import com.suidvck.any.features.data.DockItem
import com.suidvck.any.features.data.DockStore
import kotlin.math.roundToInt

/**
 * The three circular shortcuts on the home screen. Content comes from
 * [DockStore]; long-press on a slot opens a small editor (preset or custom URL).
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private data class Preset(val key: String, val labelRes: Int, val url: String)

    private val presets = listOf(
        Preset("instagram", R.string.home_preset_instagram, "https://www.instagram.com/"),
        Preset("discord", R.string.home_preset_discord, "https://discord.com/"),
        Preset("tiktok", R.string.home_preset_tiktok, "https://www.tiktok.com/"),
        Preset("youtube", R.string.home_preset_youtube, "https://www.youtube.com/"),
        Preset("github", R.string.home_preset_github, "https://github.com/"),
        Preset("google", R.string.home_preset_google, "https://www.google.com/"),
        Preset("chatgpt", R.string.home_preset_chatgpt, "https://chatgpt.com/")
    )

    private val fallbackItems = listOf(
        DockItem("Instagram", "https://www.instagram.com/", "instagram"),
        DockItem("Discord", "https://discord.com/", "discord"),
        DockItem("TikTok", "https://www.tiktok.com/", "tiktok")
    )

    private var store: DockStore? = null
    private var openHandler: ((DockItem) -> Unit)? = null
    private var items: List<DockItem> = fallbackItems
    private var bound = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Renders three slots and wires tap-to-open. Safe to call repeatedly. */
    fun bind(onOpen: (DockItem) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { bind(onOpen) }
            return
        }
        openHandler = onOpen
        store = runCatching { DockStore(context) }.getOrNull()
        val loaded = runCatching { store?.items() }.getOrNull().orEmpty()
        items = normalize(loaded)
        bound = true
        render()
    }

    /** Re-renders with the current accent color (no-op before the first bind). */
    fun refreshTint() {
        if (!bound) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { refreshTint() }
            return
        }
        render()
    }

    private fun render() {
        removeAllViews()
        val accent = runCatching { Appearance.accentColor(context) }
            .getOrDefault(ContextCompat.getColor(context, R.color.home_icon))
        items.forEachIndexed { index, item ->
            addView(createSlot(item, index, accent))
        }
    }

    private fun createSlot(item: DockItem, index: Int, accent: Int): View {
        val slot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                if (index > 0) marginStart = dp(26)
            }
            background = ContextCompat.getDrawable(context, R.drawable.home_dock_slot)
            val out = TypedValue()
            if (context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, out, true
                ) && out.resourceId != 0
            ) {
                runCatching {
                    foreground = ContextCompat.getDrawable(context, out.resourceId)
                }
            }
            isClickable = true
            isFocusable = true
            contentDescription = item.label
        }

        val iconRes = iconFor(item.icon)
        if (iconRes != 0) {
            val icon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(11), dp(11), dp(11), dp(11))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            slot.addView(icon)
        } else {
            // No brand icon: circular letter badge filled with the accent color.
            slot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
            val letter = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                text = letterFor(item)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            slot.addView(letter)
        }

        slot.setOnClickListener { openHandler?.invoke(item) }
        slot.setOnLongClickListener {
            showEditor(index)
            true
        }
        return slot
    }

    private fun showEditor(index: Int) {
        if (index !in items.indices) return
        val current = items[index]
        val view = LayoutInflater.from(context).inflate(R.layout.home_dock_editor, null, false)
        val spinner = view.findViewById<Spinner>(R.id.dock_preset)
        val urlInput = view.findViewById<EditText>(R.id.dock_url)
        val labelInput = view.findViewById<EditText>(R.id.dock_label)
        val hint = view.findViewById<TextView>(R.id.dock_hint)

        val names = presets.map { context.getString(it.labelRes) } +
            context.getString(R.string.home_preset_custom)
        val adapter = ArrayAdapter(context, R.layout.home_spinner_item, names)
        adapter.setDropDownViewResource(R.layout.home_spinner_dropdown)
        spinner.adapter = adapter

        val currentPreset = presets.indexOfFirst { it.key == current.icon }
        val isCustom = currentPreset < 0 || current.icon == "letter"
        val initialPosition = if (isCustom) presets.size else currentPreset

        fun applyPosition(position: Int, forceValues: Boolean) {
            val custom = position < 0 || position >= presets.size
            urlInput.isEnabled = custom
            labelInput.isEnabled = custom
            urlInput.alpha = if (custom) 1f else 0.55f
            labelInput.alpha = if (custom) 1f else 0.55f
            hint.visibility = if (custom) View.VISIBLE else View.GONE
            if (!custom && forceValues) {
                val preset = presets[position]
                urlInput.setText(preset.url)
                labelInput.setText(context.getString(preset.labelRes))
            }
        }

        spinner.setSelection(initialPosition, false)
        applyPosition(initialPosition, forceValues = !isCustom)
        if (isCustom) {
            urlInput.setText(current.url)
            labelInput.setText(current.label)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                selectedView: View?,
                position: Int,
                id: Long
            ) {
                applyPosition(position, forceValues = position in presets.indices)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.home_dock_edit_title)
            .setView(view)
            .setPositiveButton(R.string.home_save, null)
            .setNegativeButton(R.string.home_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val position = spinner.selectedItemPosition
                val custom = position < 0 || position >= presets.size
                val preset = presets.getOrNull(position)

                val url = if (custom) {
                    normalizeUrl(urlInput.text.toString())
                } else {
                    preset?.url
                }
                if (url == null || url.isBlank()) {
                    Toast.makeText(context, R.string.home_dock_url_invalid, Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                val label = if (custom) {
                    labelInput.text.toString().trim().ifBlank { hostOf(url) }
                } else {
                    context.getString(preset!!.labelRes)
                }
                val iconKey = if (custom) "letter" else preset!!.key

                val updated = items.toMutableList()
                updated[index] = DockItem(label.take(24), url, iconKey)

                val saved = runCatching {
                    val s = store ?: DockStore(context).also { store = it }
                    s.setItems(updated)
                }
                if (saved.isFailure) {
                    Toast.makeText(context, R.string.home_dock_save_failed, Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                val reloaded = runCatching { store?.items() }.getOrNull().orEmpty()
                items = normalize(if (reloaded.isEmpty()) updated else reloaded)
                render()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun normalize(source: List<DockItem>): List<DockItem> {
        val result = source.take(3).toMutableList()
        if (result.size < 3) result.addAll(fallbackItems.drop(result.size))
        return result.map { item ->
            val preset = presets.firstOrNull { it.key == item.icon }
            DockItem(
                label = item.label.ifBlank {
                    preset?.let { context.getString(it.labelRes) } ?: "Link"
                },
                url = item.url.ifBlank {
                    preset?.url ?: fallbackItems.first().url
                },
                icon = item.icon.ifBlank { "letter" }
            )
        }
    }

    private fun iconFor(key: String): Int = when (key) {
        "instagram" -> R.drawable.ic_instagram
        "discord" -> R.drawable.ic_discord
        "tiktok" -> R.drawable.ic_tiktok
        "youtube" -> R.drawable.ic_youtube
        else -> 0
    }

    private fun letterFor(item: DockItem): String {
        val base = item.label.trim().ifEmpty { item.icon.trim() }
        val first = base.firstOrNull()?.toString().orEmpty()
        return if (first.isBlank()) "?" else first.uppercase()
    }

    private fun normalizeUrl(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) return null
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "https://$value"
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || !host.contains(".")) return null
        return value
    }

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
            ?: "Link"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
