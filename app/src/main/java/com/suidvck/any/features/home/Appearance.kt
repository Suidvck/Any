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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.ui.AnyChrome
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
    private const val KEY_GRAYSCALE = "grayscale"
    const val DEFAULT_ACCENT = "lavender"

    val accents: List<Accent> = listOf(
        Accent("lavender", "ลาเวนเดอร์", 0xFFD0BCFF.toInt()),
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
        AnyChrome.activity(activity)
    }

    /** Grayscale tone applies to app/dock icons only; UI colors stay untouched. */
    fun isGrayscale(context: Context): Boolean = prefs(context).getBoolean(KEY_GRAYSCALE, false)

    fun setGrayscale(activity: Activity, enabled: Boolean) {
        prefs(activity).edit().putBoolean(KEY_GRAYSCALE, enabled).apply()
        applyTo(activity)
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
        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                dp(activity, 26) + bottom
            )
            insets
        }
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        AnyChrome.sheet(dialog)
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        root.post {
            val contentHeight = (root as? android.widget.ScrollView)?.getChildAt(0)?.height ?: 0
            val natural = if (contentHeight > 0) {
                contentHeight + root.paddingTop + root.paddingBottom
            } else {
                root.height
            }
            val target = natural.coerceAtMost(maxHeight)
            if (dialog.isShowing && target > 0) {
                root.layoutParams = root.layoutParams.apply { height = target }
                runCatching {
                    dialog.behavior.state =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    private fun renderSheet(activity: Activity, root: View) {
        val accent = accentColor(activity)
        val selectedKey = prefs(activity).getString(KEY_ACCENT, DEFAULT_ACCENT)
        val selectedStyle = clockStyle(activity)
        val grayscale = isGrayscale(activity)

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

        val toneBox = root.findViewById<LinearLayout>(R.id.home_tone_container)
        if (toneBox != null) {
            toneBox.removeAllViews()
            toneBox.addView(
                toneRow(
                    activity = activity,
                    title = activity.getString(R.string.home_tone_color),
                    subtitle = activity.getString(R.string.home_tone_color_sub),
                    icon = "palette",
                    selected = !grayscale,
                    accent = accent,
                    onClick = {
                        setGrayscale(activity, false)
                        renderSheet(activity, root)
                    }
                )
            )
            toneBox.addView(
                toneRow(
                    activity = activity,
                    title = activity.getString(R.string.home_tone_gray),
                    subtitle = activity.getString(R.string.home_tone_gray_sub),
                    icon = "circle-half-stroke",
                    selected = grayscale,
                    accent = accent,
                    onClick = {
                        setGrayscale(activity, true)
                        renderSheet(activity, root)
                    }
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(activity, 6) }
            )
        }

        val styleBox = root.findViewById<LinearLayout>(R.id.home_style_container)
        if (styleBox != null) {
            styleBox.removeAllViews()
            for (index in 0..2) {
                styleBox.addView(styleRow(activity, root, index, index == selectedStyle, accent))
            }
        }
    }

    private fun toneRow(
        activity: Activity,
        title: String,
        subtitle: String,
        icon: String,
        selected: Boolean,
        accent: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 14).toFloat()
                setColor(if (selected) 0x1FFFFFFF else Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "$title, $subtitle"
            setOnClickListener { onClick() }
        }

        row.addView(
            TextView(activity).apply {
                gravity = Gravity.CENTER
                val tint = if (selected) accent else 0xFFCAC4D0.toInt()
                com.suidvck.any.ui.FaIcons.setIcon(this, icon, 17f, tint)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x14FFFFFF)
                }
            },
            LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36))
        )

        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(
            TextView(activity).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }
        )
        textColumn.addView(
            TextView(activity).apply {
                text = subtitle
                setTextColor(0xFFCAC4D0.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                includeFontPadding = false
                setPadding(0, dp(activity, 2), 0, 0)
            }
        )
        row.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 12)
            }
        )

        row.addView(
            FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(activity, 2), if (selected) accent else 0x66FFFFFF)
                }
                if (selected) {
                    addView(
                        View(activity).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                dp(activity, 8), dp(activity, 8), Gravity.CENTER
                            )
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(accent)
                            }
                        }
                    )
                }
            }
        )
        return row
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
            setTextColor(if (selected) accent else 0xFFCAC4D0.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            includeFontPadding = false
        })
        return row
    }

    private fun clockStyle(context: Context): Int =
        prefs(context).getInt(KEY_CLOCK, 2).coerceIn(0, 2)

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
