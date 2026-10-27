package com.suidvck.any.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.features.home.Appearance
import kotlin.math.roundToInt

/**
 * A small dark bottom sheet used instead of the platform Spinner / single-choice
 * dialog. Rows are rounded cards and the active option carries a check icon.
 */
object ChoiceSheet {

    fun show(
        activity: Activity,
        title: String,
        subtitle: String? = null,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val accent = runCatching { Appearance.accentColor(activity) }.getOrDefault(0xFF5F6CF5.toInt())
        val dialog = BottomSheetDialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.home_sheet_bg)
            setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 20))
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
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        )
        if (!subtitle.isNullOrBlank()) {
            root.addView(
                TextView(activity).apply {
                    text = subtitle
                    setTextColor(0xFF9A9AA5.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(activity, 4), 0, 0)
                }
            )
        }

        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 14), 0, 0)
        }
        options.forEachIndexed { index, label ->
            rows.addView(
                row(activity, label, index == selected, accent) {
                    dialog.dismiss()
                    onPick(index)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 6) }
            )
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(rows, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        dialog.setContentView(root)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isFitToContents = true
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.56f).toInt()
        scroll.post {
            if (dialog.isShowing && scroll.height > maxHeight) {
                scroll.layoutParams = (scroll.layoutParams as LinearLayout.LayoutParams).apply {
                    height = maxHeight
                }
            }
        }
        runCatching { dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        AnyChrome.sheet(dialog)
    }

    private fun row(
        activity: Activity,
        label: String,
        selected: Boolean,
        accent: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 14).toFloat()
                setColor(
                    if (selected) (accent and 0x00FFFFFF) or 0x26000000
                    else 0xFF23232B.toInt()
                )
            }
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }
        row.addView(
            TextView(activity).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (selected) {
            row.addView(
                FaIcons.make(activity, "check", 15f, accent),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(activity, 10) }
            )
        }
        return row
    }

    private fun dp(context: Activity, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
