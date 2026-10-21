package com.suidvck.any.features.home

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import kotlin.math.roundToInt

/**
 * Accent color + clock typography. Values live in the shared "settings" prefs
 * (`accent` string key, `clock_style` int 0..2) so other screens can read them.
 */
object Appearance {

    data class Accent(val key: String, val label: String, val color: Int)

    private const val PREFS = "settings"
    private const val KEY_ACCENT = "accent"
    private const val KEY_CLOCK = "clock_style"
    const val DEFAULT_ACCENT = "lavender"

    val accents: List<Accent> = listOf(
        Accent("lavender", "ลาเวนเดอร์", 0xFF5F6CF5.toInt()),
        Accent("mint", "มิ้นต์", 0xFF35B98A.toInt()),
        Accent("rose", "โรส", 0xFFE2708A.toInt()),
        Accent("amber", "แอมเบอร์", 0xFFE0A03A.toInt()),
        Accent("sky", "สกาย", 0xFF4AA3E0.toInt()),
        Accent("mono", "โมโน", 0xFF8E8E99.toInt())
    )

    /** Current accent color (falls back to lavender for unknown keys). */
    fun accentColor(context: Context): Int {
        val key = prefs(context).getString(KEY_ACCENT, DEFAULT_ACCENT)
        return accents.firstOrNull { it.key == key }?.color ?: accents.first().color
    }

    /** Applies accent + clock style to whatever home views exist. Never throws on missing ids. */
    fun applyTo(activity: Activity) {
        val accent = accentColor(activity)
        val tint = ColorStateList.valueOf(accent)

        activity.findViewById<TextView>(R.id.clock_time)?.let { clock ->
            val style = clockStyle(activity)
            clock.setTextColor(accent)
            runCatching {
                clock.typeface = typefaceFor(activity, style)
                // Some MIUI system fonts ignore Typeface.BOLD; fake bold keeps the style readable.
                clock.paint.isFakeBoldText = style != 0
                clock.invalidate()
            }
        }
        activity.findViewById<View>(R.id.btn_playpause)?.let { it.backgroundTintList = tint }
        activity.findViewById<TextView>(R.id.compose_box)?.setTextColor(accent)
        activity.findViewById<View>(R.id.send_button)?.let { it.backgroundTintList = tint }
        (activity.findViewById<View>(R.id.btn_home) as? ImageView)?.let { it.imageTintList = tint }
        refreshDocks(activity)
    }

    /** Bottom sheet with color swatches and clock style rows; applies immediately. */
    fun showSheet(activity: Activity) {
        val dialog = BottomSheetDialog(activity)
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.home_appearance_sheet, null, false)
        dialog.setContentView(root)
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        root.findViewById<View>(R.id.home_appearance_done)?.setOnClickListener {
            dialog.dismiss()
        }
        renderSheet(activity, root)
        dialog.show()
    }

    private fun renderSheet(activity: Activity, root: View) {
        val accent = accentColor(activity)
        val selectedKey = prefs(activity).getString(KEY_ACCENT, DEFAULT_ACCENT)
        val selectedStyle = clockStyle(activity)

        root.findViewById<TextView>(R.id.home_appearance_done)?.setTextColor(accent)

        val swatchRow = root.findViewById<LinearLayout>(R.id.home_swatch_row)
        if (swatchRow != null) {
            swatchRow.removeAllViews()
            accents.forEach { swatch ->
                swatchRow.addView(
                    swatchView(
                        activity = activity,
                        swatch = swatch,
                        selected = swatch.key == selectedKey,
                        onPick = {
                            prefs(activity).edit().putString(KEY_ACCENT, swatch.key).apply()
                            applyTo(activity)
                            renderSheet(activity, root)
                        }
                    )
                )
            }
            val label = accents.firstOrNull { it.key == selectedKey }?.label
                ?: accents.first().label
            root.findViewById<TextView>(R.id.home_swatch_label)?.text = label
        }

        val styleBox = root.findViewById<LinearLayout>(R.id.home_style_container)
        if (styleBox != null) {
            styleBox.removeAllViews()
            for (index in 0..2) {
                styleBox.addView(styleRow(activity, root, index, index == selectedStyle, accent))
            }
        }
    }

    private fun swatchView(
        activity: Activity,
        swatch: Accent,
        selected: Boolean,
        onPick: () -> Unit
    ): View {
        val outer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)).apply {
                marginEnd = dp(activity, 10)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(
                    dp(activity, 2),
                    if (selected) Color.WHITE else Color.TRANSPARENT
                )
            }
            isClickable = true
            isFocusable = true
            contentDescription = swatch.label
            setOnClickListener { onPick() }
        }
        outer.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(activity, 32), dp(activity, 32), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(swatch.color)
            }
        })
        return outer
    }

    private fun styleRow(
        activity: Activity,
        root: View,
        index: Int,
        selected: Boolean,
        accent: Int
    ): View {
        val nameRes = when (index) {
            1 -> R.string.home_clock_mono
            2 -> R.string.home_clock_sans
            else -> R.string.home_clock_doto
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 10)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = dp(activity, 6) }
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 14).toFloat()
                setColor(if (selected) 0x1FFFFFFF else Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(nameRes)
            setOnClickListener {
                prefs(activity).edit().putInt(KEY_CLOCK, index).apply()
                applyTo(activity)
                renderSheet(activity, root)
            }
        }

        val radio = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(
                    dp(activity, 2),
                    if (selected) accent else 0x66FFFFFF
                )
            }
        }
        if (selected) {
            radio.addView(View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(activity, 8), dp(activity, 8), Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                }
            })
        }
        row.addView(radio)

        row.addView(TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(activity, 12) }
            text = activity.getString(nameRes)
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        })

        row.addView(TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = activity.getString(R.string.home_clock_preview)
            typeface = typefaceFor(activity, index)
            paint.isFakeBoldText = index != 0
            setTextColor(if (selected) accent else 0xFF9A9AA5.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            includeFontPadding = false
        })
        return row
    }

    private fun clockStyle(context: Context): Int =
        prefs(context).getInt(KEY_CLOCK, 0).coerceIn(0, 2)

    /** Keeps any DockView already on screen in sync with a new accent. */
    private fun refreshDocks(activity: Activity) {
        val root = activity.window?.decorView ?: return
        visitForDocks(root)
    }

    private fun visitForDocks(view: View) {
        if (view is DockView) {
            view.refreshTint()
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                visitForDocks(view.getChildAt(index))
            }
        }
    }

    private fun typefaceFor(context: Context, style: Int): Typeface = when (style) {
        1 -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        2 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        else -> {
            val doto = runCatching { ResourcesCompat.getFont(context, R.font.doto) }.getOrNull()
            when {
                doto == null -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                Build.VERSION.SDK_INT >= 28 -> Typeface.create(doto, 800, false)
                else -> Typeface.create(doto, Typeface.BOLD)
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
