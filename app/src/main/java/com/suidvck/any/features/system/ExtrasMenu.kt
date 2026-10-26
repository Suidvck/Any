package com.suidvck.any.features.system

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.ui.FaIcons

/**
 * "Extras" bottom sheet: a plain list of entry points. It contains no business
 * logic — every tap dismisses the sheet and hands its key to the caller.
 */
object ExtrasMenu {

    private data class Item(
        val key: String,
        val icon: String,
        val title: Int,
        val subtitle: Int
    )

    private val items = listOf(
        Item("history", "clock-rotate-left", R.string.system_menu_history_title, R.string.system_menu_history_sub),
        Item("library", "layer-group", R.string.system_menu_library_title, R.string.system_menu_library_sub),
        Item("tabs", "table-columns", R.string.system_menu_tabs_title, R.string.system_menu_tabs_sub),
        Item("find", "magnifying-glass", R.string.system_menu_find_title, R.string.system_menu_find_sub),
        Item("automations", "bell", R.string.system_menu_automations_title, R.string.system_menu_automations_sub),
        Item("appearance", "palette", R.string.system_menu_appearance_title, R.string.system_menu_appearance_sub),
        Item("voice", "volume-high", R.string.system_menu_voice_title, R.string.system_menu_voice_sub),
        Item("lock", "lock", R.string.system_menu_lock_title, R.string.system_menu_lock_sub),
        Item("update", "cloud-arrow-up", R.string.system_menu_update_title, R.string.system_menu_update_sub),
        Item("about", "circle-info", R.string.system_menu_about_title, R.string.system_menu_about_sub)
    )

    fun show(activity: Activity, onPick: (String) -> Unit) {
        val dialog = BottomSheetDialog(activity)
        var pickedKey: String? = null

        dialog.setOnDismissListener {
            val key = pickedKey
            if (key != null) {
                pickedKey = null
                val decor = activity.window?.decorView
                if (decor != null) decor.post { onPick(key) } else onPick(key)
            }
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.system_sheet_bg)
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 12))
        }

        // Grabber.
        root.addView(
            View(activity).apply { setBackgroundResource(R.drawable.system_handle_bg) },
            LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(activity, 8)
            }
        )

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(activity, 2))
        }

        for (item in items) {
            val title = activity.getString(item.title)
            val subtitle = activity.getString(item.subtitle)

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(activity, 54)
                setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7))
                setBackgroundResource(R.drawable.system_row_bg)
                isClickable = true
                isFocusable = true
                contentDescription = "$title, $subtitle"
            }

            val icon = TextView(activity).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.system_emoji_bg)
                FaIcons.setIcon(
                    this,
                    item.icon,
                    19f,
                    ContextCompat.getColor(activity, R.color.system_text)
                )
            }
            row.addView(
                icon,
                LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40))
            )

            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            textColumn.addView(
                TextView(activity).apply {
                    text = title
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(ContextCompat.getColor(activity, R.color.system_text))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                }
            )
            textColumn.addView(
                TextView(activity).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(activity, R.color.system_muted))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(activity, 2) }
            )
            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(activity, 12)
                }
            )

            row.addView(
                TextView(activity).apply {
                    alpha = 0.55f
                    gravity = Gravity.CENTER
                    FaIcons.setIcon(
                        this,
                        "chevron-right",
                        14f,
                        ContextCompat.getColor(activity, R.color.system_muted)
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(activity, 4) }
            )

            row.setOnClickListener {
                pickedKey = item.key
                dialog.dismiss()
            }

            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 2) }
            )
        }

        val scroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }
        scroll.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val version = versionName(activity)
        root.addView(
            TextView(activity).apply {
                text = if (version.isBlank()) {
                    activity.getString(R.string.app_name)
                } else {
                    activity.getString(R.string.system_menu_version, version)
                }
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(activity, R.color.system_muted))
                alpha = 0.8f
                includeFontPadding = false
                setPadding(0, dp(activity, 10), 0, dp(activity, 2))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(root)
        dialog.behavior.skipCollapsed = true

        dialog.setOnShowListener {
            dialog.window?.setDimAmount(0.58f)
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)

            val maxRows = (activity.resources.displayMetrics.heightPixels * 0.64f).toInt()
            scroll.post {
                if (!dialog.isShowing) return@post
                if (scroll.height > maxRows && maxRows > 0) {
                    scroll.layoutParams = (scroll.layoutParams as LinearLayout.LayoutParams).apply {
                        height = maxRows
                    }
                }
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun versionName(activity: Activity): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    } catch (t: Throwable) {
        ""
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
