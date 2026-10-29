package com.suidvck.any.features.home

import android.content.Context
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.features.data.DockItem
import com.suidvck.any.features.data.DockStore
import com.suidvck.any.ui.AnyChrome
import com.suidvck.any.ui.ChoiceSheet
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

    /** Opens the editor for one slot (used by the dock editor and for QA). */
    fun openEditorFor(index: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { openEditorFor(index) }
            return
        }
        showEditor(index)
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
        val grayscale = runCatching { Appearance.isGrayscale(context) }.getOrDefault(false)
        val iconTint = if (grayscale) 0xFFC9C9D2.toInt() else accent
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
            visibility = if (iconRes == 0) View.VISIBLE else View.GONE
        }
        if (iconRes == 0) {
            slot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (grayscale) 0xFF3B3B45.toInt() else accent)
            }
        }
        slot.addView(letter)

        val iconView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(8), dp(8), dp(8), dp(8))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag = item.url
            if (iconRes != 0) {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(iconTint)
            } else {
                visibility = View.GONE
            }
            if (grayscale) colorFilter = grayscaleFilter()
        }
        slot.addView(iconView)

        // Use the site's own icon once it arrives, whatever preset label is stored.
        FaviconLoader.load(context, item.url) { bitmap ->
            if (bitmap == null || iconView.tag != item.url) return@load
            iconView.setImageBitmap(bitmap)
            iconView.imageTintList = null
            iconView.colorFilter = if (grayscale) grayscaleFilter() else null
            iconView.setPadding(dp(6), dp(6), dp(6), dp(6))
            iconView.visibility = View.VISIBLE
            letter.visibility = View.GONE
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
        val accent = runCatching { Appearance.accentColor(context) }
            .getOrDefault(ContextCompat.getColor(context, R.color.home_icon))
        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.home_sheet_bg)
            setPadding(dp(20), dp(10), dp(20), dp(22))
        }

        root.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(0x33FFFFFF)
                }
            },
            LinearLayout.LayoutParams(dp(38), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        )
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.home_dock_edit_title)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        )
        root.addView(
            TextView(context).apply {
                text = current.label
                setTextColor(0xFF9A9AA5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        root.addView(sectionLabel(R.string.home_dock_preset))

        val names = presets.map { context.getString(it.labelRes) } +
            context.getString(R.string.home_preset_custom)
        val currentPreset = presets.indexOfFirst { it.key == current.icon }
        val isCustom = currentPreset < 0 || current.icon == "letter"
        var position = if (isCustom) presets.size else currentPreset

        val presetValue = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = fieldBackground()
        }
        var applyPosition: (Int, Boolean) -> Unit = { _, _ -> }
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fieldBackground()
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.home_dock_preset)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(
                presetValue.apply {
                    setPadding(0, 0, 0, 0)
                    background = null
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                com.suidvck.any.ui.FaIcons.make(context, "chevron-down", 12f, 0xFF9A9AA5.toInt()),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            )
            setOnClickListener {
                val activity = context as? Activity ?: return@setOnClickListener
                ChoiceSheet.show(
                    activity,
                    context.getString(R.string.home_dock_preset),
                    null,
                    names,
                    position
                ) { picked ->
                    position = picked
                    applyPosition(picked, true)
                }
            }
        }
        root.addView(
            presetRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        root.addView(sectionLabel(R.string.home_dock_url))
        val urlInput = fieldInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        urlInput.hint = context.getString(R.string.home_dock_url_hint)
        root.addView(
            urlInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        root.addView(sectionLabel(R.string.home_dock_label))
        val labelInput = fieldInput(InputType.TYPE_CLASS_TEXT)
        labelInput.hint = context.getString(R.string.home_dock_label_hint)
        root.addView(
            labelInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        val hint = TextView(context).apply {
            text = context.getString(R.string.home_dock_custom_hint)
            setTextColor(0xFF9A9AA5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(hint)

        fun applyPositionValue(selected: Int, forceValues: Boolean) {
            val custom = selected < 0 || selected >= presets.size
            val preset = presets.getOrNull(selected)
            presetValue.text = names.getOrElse(selected) { names.last() }
            urlInput.isEnabled = custom
            labelInput.isEnabled = custom
            urlInput.alpha = if (custom) 1f else 0.55f
            labelInput.alpha = if (custom) 1f else 0.55f
            hint.visibility = if (custom) View.VISIBLE else View.GONE
            if (!custom && preset != null && forceValues) {
                urlInput.setText(preset.url)
                labelInput.setText(context.getString(preset.labelRes))
            }
        }
        applyPosition = { selected, force -> applyPositionValue(selected, force) }

        applyPositionValue(position, forceValues = !isCustom)
        if (isCustom) {
            urlInput.setText(current.url)
            labelInput.setText(current.label)
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            TextView(context).apply {
                text = context.getString(R.string.home_cancel)
                setTextColor(0xFF9A9AA5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(13), dp(18), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0x14FFFFFF)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            }
        )
        actions.addView(
            TextView(context).apply {
                text = context.getString(R.string.home_save)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(13), dp(18), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(accent)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val custom = position < 0 || position >= presets.size
                    val preset = presets.getOrNull(position)
                    val url = if (custom) {
                        normalizeUrl(urlInput.text.toString())
                    } else {
                        preset?.url
                    }
                    if (url == null || url.isBlank()) {
                        Toast.makeText(
                            context, R.string.home_dock_url_invalid, Toast.LENGTH_SHORT
                        ).show()
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
                        Toast.makeText(
                            context, R.string.home_dock_save_failed, Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    val reloaded = runCatching { store?.items() }.getOrNull().orEmpty()
                    items = normalize(if (reloaded.isEmpty()) updated else reloaded)
                    render()
                    dialog.dismiss()
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        )
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        )

        dialog.setContentView(root)
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        AnyChrome.sheet(dialog)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                dp(22) + bottom
            )
            insets
        }
        runCatching { dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
    }

    private fun grayscaleFilter(): ColorMatrixColorFilter =
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private fun sectionLabel(resId: Int): TextView = TextView(context).apply {
        text = context.getString(resId)
        setTextColor(0xFF9A9AA5.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(16), 0, 0)
    }

    private fun fieldBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(0xFF23232B.toInt())
    }

    private fun fieldInput(inputType: Int): EditText = EditText(context).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9A9AA5.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = fieldBackground()
        this.inputType = inputType
        setSingleLine(true)
        // importantForAutofill was added in API 26; minSdk is 24.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
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
