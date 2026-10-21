package com.suidvck.any.features.library

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.R
import com.suidvck.any.features.data.MiniApp
import com.suidvck.any.features.data.MiniAppStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "แอพของฉัน" bottom sheet: every mini-app saved by [MiniAppStore].
 *
 * Tap a card to open the app, long-press to rename it (name + emoji) or delete it.
 */
object LibrarySheet {

    fun show(activity: Activity, store: MiniAppStore, onOpen: (MiniApp) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        val density = activity.resources.displayMetrics.density
        val dp: (Int) -> Int = { value -> (value * density + 0.5f).toInt() }

        val dialog = BottomSheetDialog(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(LIB_SHEET_BG, dp(22).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(14))
            elevation = dp(18).toFloat()
        }

        // Drag handle
        val handle = View(activity).apply {
            background = roundedBackground(0x33FFFFFF, dp(2).toFloat())
        }
        root.addView(
            handle,
            LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
                bottomMargin = dp(14)
            }
        )

        // Header: title + count
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(activity).apply {
            text = "แอพของฉัน"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(LIB_TEXT)
        }
        val countView = TextView(activity).apply {
            textSize = 12f
            setTextColor(LIB_MUTED)
        }
        header.addView(titleView)
        header.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(countView)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val hintView = TextView(activity).apply {
            text = "แตะเพื่อเปิด · กดค้างเพื่อแก้ไขหรือลบ"
            textSize = 11f
            setTextColor(LIB_MUTED)
        }
        root.addView(
            hintView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
                bottomMargin = dp(12)
            }
        )

        val listHost = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(listHost, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        var refresh: () -> Unit = {}
        var resize: (Int) -> Unit = {}

        fun showEditor(app: MiniApp) {
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, 0)
            }

            val nameInput = EditText(activity).apply {
                setText(app.name)
                hint = "ชื่อแอพ"
                textSize = 15f
                setTextColor(LIB_TEXT)
                setHintTextColor(LIB_MUTED)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                background = roundedBackground(LIB_FIELD_BG, dp(12).toFloat())
                setPadding(dp(14), dp(11), dp(14), dp(11))
                setSelection(text.length)
            }
            content.addView(
                labelView(activity, "ชื่อแอพ"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            content.addView(
                nameInput,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            )

            val emojiInput = EditText(activity).apply {
                setText(app.emoji)
                hint = "อิโมจิ"
                textSize = 18f
                setTextColor(LIB_TEXT)
                setHintTextColor(LIB_MUTED)
                setSingleLine(true)
                filters = arrayOf<InputFilter>(InputFilter.LengthFilter(12))
                background = roundedBackground(LIB_FIELD_BG, dp(12).toFloat())
                setPadding(dp(14), dp(11), dp(14), dp(11))
                setSelection(text.length)
            }
            content.addView(
                labelView(activity, "อิโมจิ"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14)
                }
            )
            content.addView(
                emojiInput,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            )

            val editDialog = MaterialAlertDialogBuilder(activity, R.style.LibraryDialogTheme)
                .setTitle("แก้ไขแอพ")
                .setView(content)
                .setPositiveButton("บันทึก", null)
                .setNeutralButton("ลบ", null)
                .setNegativeButton("ยกเลิก", null)
                .create()

            editDialog.setOnShowListener {
                editDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(LIB_ROSE)
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val newName = nameInput.text?.toString()?.trim().orEmpty()
                    val newEmoji = emojiInput.text?.toString()?.trim().orEmpty()
                    if (newName.isEmpty()) {
                        nameInput.error = "ใส่ชื่อแอพก่อนนะ"
                        nameInput.requestFocus()
                        return@setOnClickListener
                    }
                    runCatching {
                        store.rename(app.id, newName, newEmoji.ifEmpty { app.emoji.ifBlank { "🧩" } })
                    }
                    editDialog.dismiss()
                    refresh()
                    RecentAppsRowRegistry.refreshAll()
                }
                editDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    editDialog.dismiss()
                    val confirm = MaterialAlertDialogBuilder(activity, R.style.LibraryDialogTheme)
                        .setTitle("ลบแอพนี้?")
                        .setMessage("ลบ “${app.name}” ออกถาวรไหม? กู้คืนไม่ได้นะ")
                        .setPositiveButton("ลบ", null)
                        .setNegativeButton("ยกเลิก", null)
                        .create()
                    confirm.setOnShowListener {
                        confirm.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(LIB_ROSE)
                        confirm.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                            runCatching { store.delete(app.id) }
                            confirm.dismiss()
                            refresh()
                            RecentAppsRowRegistry.refreshAll()
                        }
                    }
                    confirm.show()
                }
            }
            editDialog.show()
        }

        fun render() {
            val apps: List<MiniApp> = runCatching { store.list() }.getOrElse { emptyList() }
            countView.text = "${apps.size} แอพ"
            listHost.removeAllViews()

            if (apps.isEmpty()) {
                listHost.addView(buildEmptyState(activity, dp))
            } else {
                var index = 0
                while (index < apps.size) {
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                    }
                    for (slot in 0 until 2) {
                        val itemIndex = index + slot
                        if (itemIndex < apps.size) {
                            val app = apps[itemIndex]
                            val tile = buildTile(
                                activity,
                                app,
                                dp,
                                onClick = {
                                    onOpen(app)
                                    dialog.dismiss()
                                },
                                onLongClick = { showEditor(app) }
                            )
                            row.addView(
                                tile,
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginStart = if (slot == 0) 0 else dp(5)
                                    marginEnd = if (slot == 0) dp(5) else 0
                                }
                            )
                        } else {
                            row.addView(
                                View(activity),
                                LinearLayout.LayoutParams(0, dp(1), 1f).apply { marginStart = dp(5) }
                            )
                        }
                    }
                    listHost.addView(
                        row,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(10)
                        }
                    )
                    index += 2
                }
            }
            resize(apps.size)
        }

        refresh = { render() }

        resize = { count ->
            val metrics = activity.resources.displayMetrics
            val maxHeight = (metrics.heightPixels * 0.72f).toInt()
            val rows = (count + 1) / 2
            val desired = dp(104) + rows * dp(120)
            val height = if (count == 0) ViewGroup.LayoutParams.WRAP_CONTENT else minOf(desired, maxHeight)

            val lp = root.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM)
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = height
            lp.gravity = Gravity.BOTTOM
            lp.leftMargin = dp(10)
            lp.rightMargin = dp(10)
            lp.bottomMargin = dp(10)
            root.layoutParams = lp

            val wrap = height == ViewGroup.LayoutParams.WRAP_CONTENT
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (wrap) ViewGroup.LayoutParams.WRAP_CONTENT else 0,
                if (wrap) 0f else 1f
            )
        }

        render()

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(dp(16), dp(8), dp(16), dp(14) + bottom)
            insets
        }

        dialog.setContentView(root)
        dialog.show()

        // Flatten the stock sheet background so the rounded floating card shows.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // setContentView may replace the content layout params, so re-apply ours.
        resize(runCatching { store.list().size }.getOrDefault(0))
    }
}

private val LIB_SHEET_BG = 0xFF17171B.toInt()
private val LIB_CARD_BG = 0xFF23232B.toInt()
private val LIB_FIELD_BG = 0xFF23232B.toInt()
private val LIB_TEXT = 0xFFFFFFFF.toInt()
private val LIB_MUTED = 0xFF9A9AA5.toInt()
private val LIB_ROSE = 0xFFE2708A.toInt()

private fun roundedBackground(fill: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fill)
    }

private fun rippleBackground(fill: Int, radiusPx: Float, rippleColor: Int): RippleDrawable {
    val content = roundedBackground(fill, radiusPx)
    val mask = roundedBackground(Color.WHITE, radiusPx)
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
}

private fun labelView(context: Context, label: String): TextView =
    TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(LIB_MUTED)
    }

private fun buildTile(
    context: Context,
    app: MiniApp,
    dp: (Int) -> Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): View {
    val tile = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(14), dp(12), dp(13))
        background = rippleBackground(LIB_CARD_BG, dp(16).toFloat(), 0x26FFFFFF)
        isClickable = true
        isFocusable = true
        contentDescription = "เปิดแอพ ${app.name}"
        setOnClickListener { onClick() }
        setOnLongClickListener {
            onLongClick()
            true
        }
    }

    val emojiView = TextView(context).apply {
        text = app.emoji.ifBlank { "🧩" }
        textSize = 32f
        gravity = Gravity.CENTER
        setTextColor(LIB_TEXT)
    }
    tile.addView(emojiView)

    val nameView = TextView(context).apply {
        text = app.name.ifBlank { "ไม่มีชื่อ" }
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(LIB_TEXT)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    tile.addView(
        nameView,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        }
    )

    val dateView = TextView(context).apply {
        text = relativeTime(if (app.updatedAt > 0L) app.updatedAt else app.createdAt)
        textSize = 11f
        setTextColor(LIB_MUTED)
        gravity = Gravity.CENTER
        maxLines = 1
    }
    tile.addView(
        dateView,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3)
        }
    )

    return tile
}

private fun buildEmptyState(context: Context, dp: (Int) -> Int): View {
    val box = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(8), dp(32), dp(8), dp(32))
    }

    box.addView(
        TextView(context).apply {
            text = "🗂️"
            textSize = 40f
            gravity = Gravity.CENTER
        }
    )
    box.addView(
        TextView(context).apply {
            text = "ยังไม่มีแอพที่บันทึกไว้"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(LIB_TEXT)
            gravity = Gravity.CENTER
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        }
    )
    box.addView(
        TextView(context).apply {
            text = "สั่ง AI สร้างแอพแล้วมันจะถูกเก็บที่นี่"
            textSize = 13f
            setTextColor(LIB_MUTED)
            gravity = Gravity.CENTER
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    )

    return box
}

private fun relativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "เมื่อสักครู่"
        diff < 3_600_000L -> "${diff / 60_000L} นาทีที่แล้ว"
        diff < 86_400_000L -> "${diff / 3_600_000L} ชั่วโมงที่แล้ว"
        diff < 172_800_000L -> "เมื่อวาน"
        diff < 604_800_000L -> "${diff / 86_400_000L} วันที่แล้ว"
        else -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("th-TH")).format(Date(timestamp))
    }
}
