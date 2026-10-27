package com.suidvck.any.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.TextView

/**
 * Font Awesome Free 6 icons, rendered from the bundled fonts.
 *
 * The fonts are loaded lazily and cached, so every icon view in the app shares
 * a single [Typeface]. Legacy mini-apps stored emoji in the `emoji` field; those
 * values are mapped to a matching Font Awesome icon at display time.
 */
object FaIcons {

    private const val FONT_ASSET = "fonts/fa-solid-900.ttf"
    private const val REGULAR_FONT_ASSET = "fonts/fa-regular-400.ttf"
    private const val DEFAULT_ICON = "cube"

    @Volatile
    private var solidTypeface: Typeface? = null

    @Volatile
    private var regularTypeface: Typeface? = null

    private val glyphs = mapOf(
        "clock-rotate-left" to "\uF1DA",
        "layer-group" to "\uF5FD",
        "table-columns" to "\uF0DB",
        "magnifying-glass" to "\uF002",
        "bell" to "\uF0F3",
        "palette" to "\uF53F",
        "volume-high" to "\uF028",
        "lock" to "\uF023",
        "cloud-arrow-up" to "\uF0EE",
        "circle-info" to "\uF05A",
        "folder-open" to "\uF07C",
        "image" to "\uF03E",
        "paperclip" to "\uF0C6",
        "microphone" to "\uF130",
        "pen" to "\uF304",
        "check" to "\uF00C",
        "xmark" to "\uF00D",
        "house" to "\uF015",
        "ellipsis" to "\uF141",
        "arrow-up" to "\uF062",
        "arrow-down" to "\uF063",
        "star" to "\uF005",
        "trash" to "\uF1F8",
        "plus" to "\u002B",
        "note-sticky" to "\uF249",
        "file-lines" to "\uF15C",
        "globe" to "\uF0AC",
        "calculator" to "\uF1EC",
        "list-check" to "\uF0AE",
        "calendar-days" to "\uF073",
        "stopwatch" to "\uF2F2",
        "clock" to "\uF017",
        "cloud-sun" to "\uF6C4",
        "music" to "\uF001",
        "film" to "\uF008",
        "camera" to "\uF030",
        "robot" to "\uF544",
        "comment" to "\uF075",
        "sack-dollar" to "\uF81D",
        "bag-shopping" to "\uF290",
        "map" to "\uF279",
        "gamepad" to "\uF11B",
        "brain" to "\uF5DC",
        "dumbbell" to "\uF44B",
        "burger" to "\uF805",
        "language" to "\uF1AB",
        "envelope" to "\uF0E0",
        "chart-line" to "\uF201",
        "dice" to "\uF522",
        "cube" to "\uF1B2",
        "desktop" to "\uF390",
        "mobile-screen" to "\uF3CF",
        "moon" to "\uF186",
        "download" to "\uF019",
        "bookmark" to "\uF02E",
        "play" to "\uF04B",
        "pause" to "\uF04C",
        "backward-step" to "\uF048",
        "forward-step" to "\uF051",
        "gear" to "\uF013",
        "rotate-right" to "\uF2F9",
        "link" to "\uF0C1",
        "copy" to "\uF0C5",
        "book-open" to "\uF518",
        "window-restore" to "\uF2D2",
        "circle-xmark" to "\uF057",
        "circle-check" to "\uF058",
        "square-plus" to "\uF0FE",
        "pen-to-square" to "\uF044",
        "floppy-disk" to "\uF0C7",
        "grip-lines" to "\uF7A4",
        "hand" to "\uF256",
        "broom" to "\uF51A",
        "ban" to "\uF05E",
        "circle-question" to "\uF059",
        "chevron-right" to "\uF054",
        "chevron-left" to "\uF053",
        "chevron-down" to "\uF078",
        "chevron-up" to "\uF077",
        "circle-half-stroke" to "\uF042",
        "bars" to "\uF0C9",
        "wand-magic-sparkles" to "\uE2CA",
        "power-off" to "\uF011",
        "lightbulb" to "\uF0EB"
    )

    /** First code point of a legacy emoji -> Font Awesome icon name. */
    private val emojiToIcon = mapOf(
        0x1F558 to "clock-rotate-left",
        0x1F4E6 to "layer-group",
        0x1F5C2 to "folder-open",
        0x1F4C1 to "folder-open",
        0x1F4C2 to "folder-open",
        0x1F5C3 to "layer-group",
        0x1F5C4 to "layer-group",
        0x1F50D to "magnifying-glass",
        0x23F0 to "bell",
        0x23F1 to "stopwatch",
        0x1F3A8 to "palette",
        0x1F50A to "volume-high",
        0x1F512 to "lock",
        0x2B06 to "cloud-arrow-up",
        0x1F4E4 to "cloud-arrow-up",
        0x2139 to "circle-info",
        0x1F9E9 to "cube",
        0x1F9EE to "calculator",
        0x2705 to "list-check",
        0x1F4C5 to "calendar-days",
        0x1F552 to "clock",
        0x26C5 to "cloud-sun",
        0x1F3B5 to "music",
        0x1F3AC to "film",
        0x1F4F7 to "camera",
        0x1F4DD to "note-sticky",
        0x1F916 to "robot",
        0x1F4AC to "comment",
        0x1F4B0 to "sack-dollar",
        0x1F6CD to "bag-shopping",
        0x1F5FA to "map",
        0x1F3AE to "gamepad",
        0x1F9E0 to "brain",
        0x1F4AA to "dumbbell",
        0x1F354 to "burger",
        0x1F310 to "globe",
        0x2709 to "envelope",
        0x1F4CA to "chart-line",
        0x1F3B2 to "dice",
        0x1F5BC to "image",
        0x1F4CE to "paperclip",
        0x1F3A4 to "microphone",
        0x1F399 to "microphone",
        0x270F to "pen",
        0x2715 to "xmark",
        0x2605 to "star",
        0x2606 to "star",
        0x2B50 to "star",
        0x2191 to "arrow-up",
        0x2193 to "arrow-down",
        0x2B07 to "download",
        0x2630 to "bars",
        0x2728 to "wand-magic-sparkles",
        0x203A to "chevron-right",
        0x283F to "grip-lines",
        0x1F9F9 to "broom",
        0x1F6AB to "ban",
        0x1F44B to "hand",
        0x1F5A5 to "desktop",
        0x1F4BB to "desktop",
        0x1F4F1 to "mobile-screen",
        0x1F319 to "moon",
        0x1F516 to "bookmark",
        0x1F5D1 to "trash",
        0x2795 to "plus",
        0x1F517 to "link",
        0x1F4A1 to "lightbulb",
        0x1F4E5 to "download"
    )

    /** A compact, curated set shown in the mini-app icon picker. */
    val choices: List<String> = listOf(
        "cube",
        "calculator",
        "note-sticky",
        "calendar-days",
        "gamepad",
        "chart-line",
        "music",
        "image",
        "map",
        "comment"
    )

    fun typeface(context: Context): Typeface {
        solidTypeface?.let { return it }
        synchronized(this) {
            solidTypeface?.let { return it }
            val loaded = load(context, FONT_ASSET)
            solidTypeface = loaded
            return loaded
        }
    }

    /** The outline (regular) face, used where a filled glyph would read as active. */
    fun regularTypeface(context: Context): Typeface {
        regularTypeface?.let { return it }
        synchronized(this) {
            regularTypeface?.let { return it }
            val loaded = load(context, REGULAR_FONT_ASSET)
            regularTypeface = loaded
            return loaded
        }
    }

    private fun load(context: Context, asset: String): Typeface = runCatching {
        Typeface.createFromAsset(context.applicationContext.assets, asset)
    }.getOrNull() ?: Typeface.DEFAULT

    fun glyph(name: String): String = glyphs[name] ?: glyphs.getValue(DEFAULT_ICON)

    /** Applies an icon to an existing [TextView]. */
    fun setIcon(
        view: TextView,
        name: String,
        sizeSp: Float? = null,
        color: Int? = null,
        regular: Boolean = false
    ) {
        if (sizeSp != null) view.textSize = sizeSp
        if (color != null) view.setTextColor(color)
        view.typeface = if (regular) regularTypeface(view.context) else typeface(view.context)
        view.text = glyph(name)
        view.includeFontPadding = false
    }

    /** Creates a centered icon-only view. */
    fun make(
        context: Context,
        name: String,
        sizeSp: Float,
        color: Int,
        regular: Boolean = false
    ): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            setIcon(this, name, sizeSp, color, regular)
        }

    /**
     * An icon followed by normal text in a single [CharSequence]. Only the icon
     * range gets the Font Awesome typeface, so Thai and Latin labels keep the
     * system font.
     */
    fun labeled(context: Context, name: String, label: String): CharSequence {
        val icon = glyph(name)
        val text = SpannableString("$icon  $label")
        text.setSpan(
            IconTypefaceSpan(typeface(context)),
            0,
            icon.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }

    /** Accepts either a Font Awesome icon name or a legacy emoji string. */
    fun resolve(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_ICON
        if (glyphs.containsKey(raw)) return raw
        return emojiToIcon[raw.codePointAt(0)] ?: DEFAULT_ICON
    }

    private class IconTypefaceSpan(
        private val iconTypeface: Typeface
    ) : TypefaceSpan("sans-serif") {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = iconTypeface
        }

        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = iconTypeface
        }
    }
}
