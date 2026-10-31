package com.suidvck.any.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.features.home.Appearance
import kotlin.math.roundToInt

/**
 * App-styled replacement for the platform TimePickerDialog used by automations.
 * Two large steppers (hour / minute) with a live preview; hold an arrow to
 * repeat quickly.
 */
object TimeSheet {

    private const val TEXT = 0xFFE6E1E9.toInt()
    private const val MUTED = 0xFFCAC4D0.toInt()
    private const val CARD = 0xFF211F26.toInt()

    fun show(
        activity: Activity,
        hour: Int,
        minute: Int,
        onPick: (Int, Int) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val accent = runCatching { Appearance.accentColor(activity) }
            .getOrDefault(0xFF7C5CBF.toInt())
        var h = hour.coerceIn(0, 23)
        var m = minute.coerceIn(0, 59)

        val dialog = BottomSheetDialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.home_sheet_bg)
            setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 22))
        }

        root.addView(
            View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(activity, 999).toFloat()
                    setColor(0x33FFFFFF)
                }
            },
            LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(activity, 16)
            }
        )

        root.addView(
            TextView(activity).apply {
                text = "เลือกเวลา"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        )
        root.addView(
            TextView(activity).apply {
                text = "เลื่อนชั่วโมงและนาที แล้วกดบันทึก"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(activity, 4), 0, 0)
            }
        )

        val preview = TextView(activity).apply {
            text = label(h, m)
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, dp(activity, 20), 0, dp(activity, 14))
        }
        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        fun refresh() {
            preview.text = label(h, m)
        }

        val steppers = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        steppers.addView(
            stepper(
                activity = activity,
                labelText = "ชั่วโมง",
                initial = h,
                modulo = 24,
                accent = accent,
                onChange = {
                    h = it
                    refresh()
                }
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 6)
            }
        )
        steppers.addView(
            stepper(
                activity = activity,
                labelText = "นาที",
                initial = m,
                modulo = 60,
                accent = accent,
                onChange = {
                    m = it
                    refresh()
                }
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 6)
            }
        )
        root.addView(
            steppers,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            TextView(activity).apply {
                text = "ยกเลิก"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13))
                background = rounded(activity, 0x14FFFFFF)
                isClickable = true
                isFocusable = true
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 10)
            }
        )
        actions.addView(
            TextView(activity).apply {
                text = "บันทึก"
                setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5) 0xFF381E72.toInt() else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13))
                background = rounded(activity, accent)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    onPick(h, m)
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 10)
            }
        )
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 20) }
        )

        dialog.setContentView(root)
        dialog.behavior.skipCollapsed = true
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        runCatching { dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        AnyChrome.sheet(dialog)
    }

    private fun stepper(
        activity: Activity,
        labelText: String,
        initial: Int,
        modulo: Int,
        accent: Int,
        onChange: (Int) -> Unit
    ): View {
        var value = initial
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(activity, CARD)
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
        }
        val valueView = TextView(activity).apply {
            text = format(value)
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, dp(activity, 2), 0, dp(activity, 2))
        }

        fun bump(delta: Int) {
            value = ((value + delta) % modulo + modulo) % modulo
            valueView.text = format(value)
            onChange(value)
        }

        column.addView(
            TextView(activity).apply {
                text = labelText
                gravity = Gravity.CENTER
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        )

        val up = arrowButton(activity, "chevron-up", accent, "เพิ่ม")
        val down = arrowButton(activity, "chevron-down", MUTED, "ลด")
        up.setOnClickListener { bump(1) }
        down.setOnClickListener { bump(-1) }
        repeatOnHold(up) { bump(1) }
        repeatOnHold(down) { bump(-1) }

        column.addView(
            up,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44))
        )
        column.addView(valueView)
        column.addView(
            down,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44))
        )
        return column
    }

    private fun arrowButton(activity: Activity, icon: String, tint: Int, description: String): View =
        TextView(activity).apply {
            gravity = Gravity.CENTER
            FaIcons.setIcon(this, icon, 15f, tint)
            isClickable = true
            isFocusable = true
            contentDescription = description
        }

    private fun repeatOnHold(view: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var repeating = false
        val runner = object : Runnable {
            override fun run() {
                if (!repeating) return
                action()
                handler.postDelayed(this, 90)
            }
        }
        view.setOnLongClickListener {
            repeating = true
            handler.postDelayed(runner, 260)
            true
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                repeating = false
                handler.removeCallbacks(runner)
            }
            false
        }
    }

    private fun format(value: Int): String = value.toString().padStart(2, '0')

    private fun label(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    private fun rounded(context: Activity, color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, 16).toFloat()
            setColor(color)
        }

    private fun dp(context: Activity, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
