package com.suidvck.any.features.chat

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import com.suidvck.any.features.data.ChatStore
import com.suidvck.any.features.data.Conversation
import com.suidvck.any.ui.AnyChrome
import java.util.Calendar
import kotlin.math.min

/**
 * Dark, Thai-language conversation history sheet.
 *
 * Tap a row to replay it, long-press to rename or delete. The sheet refreshes
 * itself after a rename/delete; the optional [show] overload reports dismissal
 * so the host can re-sync the currently displayed conversation.
 */
object ChatHistorySheet {

    private const val COLOR_SHEET = 0xFF17171B.toInt()
    private const val COLOR_CARD = 0xFF23232B.toInt()
    private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_MUTED = 0xFF9A9AA5.toInt()
    private const val COLOR_BRAND = 0xFF5F6CF5.toInt()
    private const val COLOR_ROSE = 0xFFE2708A.toInt()

    fun show(
        activity: Activity,
        store: ChatStore,
        onPick: (Conversation) -> Unit,
        onNew: () -> Unit
    ) {
        show(activity, store, onPick, onNew, null)
    }

    /**
     * Extra overload (contract-compatible): [onDismiss] fires once when the
     * sheet closes. Ordering relative to [onPick]/[onNew] is not guaranteed,
     * so use it only to re-sync the displayed conversation from the store.
     */
    fun show(
        activity: Activity,
        store: ChatStore,
        onPick: (Conversation) -> Unit,
        onNew: () -> Unit,
        onDismiss: (() -> Unit)?
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            HistorySheet(activity, store, onPick, onNew, onDismiss).open()
        } catch (_: Throwable) {
            // Never let a history UI failure take the caller down.
        }
    }

    private class HistorySheet(
        private val activity: Activity,
        private val store: ChatStore,
        private val onPick: (Conversation) -> Unit,
        private val onNew: () -> Unit,
        private val onDismiss: (() -> Unit)?
    ) {
        private lateinit var dialog: BottomSheetDialog
        private lateinit var rows: LinearLayout
        private lateinit var scroll: ScrollView
        private lateinit var empty: LinearLayout
        private var dismissed = false

        fun open() {
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = sheetBackground()
                setPadding(dp(16), dp(10), dp(16), dp(18))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            root.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(0x33FFFFFF)
                }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(2)
                    bottomMargin = dp(10)
                }
            })

            root.addView(buildHeader())

            root.addView(TextView(activity).apply {
                text = activity.getString(R.string.chat_hist_subtitle)
                textSize = 12f
                setTextColor(COLOR_MUTED)
                setPadding(0, dp(3), 0, 0)
            })

            empty = buildEmptyState()
            root.addView(empty)

            rows = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            scroll = ScrollView(activity).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                addView(
                    rows,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            root.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                view.setPadding(dp(16), dp(10), dp(16), dp(18) + bottom)
                insets
            }

            dialog = BottomSheetDialog(activity)
            dialog.setContentView(root)
            dialog.window?.setDimAmount(0.6f)
            dialog.setOnShowListener {
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundColor(Color.TRANSPARENT)
                runCatching {
                    dialog.behavior.skipCollapsed = true
                    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
            dialog.setOnDismissListener {
                if (dismissed) return@setOnDismissListener
                dismissed = true
                if (!activity.isFinishing && !activity.isDestroyed) onDismiss?.invoke()
            }

            refresh()
            dialog.show()
            AnyChrome.sheet(dialog)
        }

        private fun buildHeader(): View {
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.chat_hist_title)
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(COLOR_TEXT)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            header.addView(TextView(activity).apply {
                text = activity.getString(R.string.chat_hist_new)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_BRAND)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = pill(0x1F5F6CF5, 999f, 0x335F6CF5)
                isClickable = true
                setOnClickListener {
                    dismissSheet()
                    onNew()
                }
            })
            return header
        }

        private fun buildEmptyState(): LinearLayout {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(30), dp(16), dp(36))
                visibility = View.GONE
            }
            box.addView(TextView(activity).apply {
                text = activity.getString(R.string.chat_hist_empty)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
                gravity = Gravity.CENTER
            })
            box.addView(TextView(activity).apply {
                text = activity.getString(R.string.chat_hist_empty_hint)
                textSize = 12.5f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            return box
        }

        private fun refresh() {
            val conversations = try {
                store.conversations()
            } catch (_: Throwable) {
                emptyList()
            }
            rows.removeAllViews()
            if (conversations.isEmpty()) {
                empty.visibility = View.VISIBLE
                scroll.visibility = View.GONE
                return
            }
            empty.visibility = View.GONE
            scroll.visibility = View.VISIBLE
            conversations.forEach { conversation ->
                rows.addView(buildRow(conversation))
            }

            val screenHeight = activity.resources.displayMetrics.heightPixels
            val maxHeight = (screenHeight * 0.62f).toInt()
            val wanted = conversations.size * dp(72) + dp(10)
            val height = min(wanted, maxHeight)
            (scroll.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                if (lp.height != height) {
                    lp.height = height
                    scroll.layoutParams = lp
                }
            }
        }

        private fun buildRow(conversation: Conversation): View {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = ripple(COLOR_CARD, 14f)
                isClickable = true
                isLongClickable = true
            }

            val textColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            textColumn.addView(TextView(activity).apply {
                text = conversation.title.ifBlank { activity.getString(R.string.chat_hist_untitled) }
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            textColumn.addView(TextView(activity).apply {
                text = previewOf(conversation)
                textSize = 12.5f
                setTextColor(COLOR_MUTED)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })

            card.addView(
                textColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            card.addView(TextView(activity).apply {
                text = relativeTime(conversation.updatedAt)
                textSize = 11f
                setTextColor(COLOR_MUTED)
                setPadding(dp(10), 0, 0, 0)
            })

            card.setOnClickListener {
                dismissSheet()
                onPick(conversation)
            }
            card.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showEditDialog(conversation)
                true
            }
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            return card
        }

        private fun previewOf(conversation: Conversation): String {
            val last = conversation.messages.lastOrNull()
                ?: return activity.getString(R.string.chat_hist_no_message)
            val text = oneLine(last.text)
            return when {
                text.isNotEmpty() && last.role == "user" ->
                    activity.getString(R.string.chat_hist_you, text)
                text.isNotEmpty() -> text
                last.imageDataUrl != null -> activity.getString(R.string.chat_hist_image)
                else -> activity.getString(R.string.chat_hist_no_message)
            }
        }

        /** Long-press: rename (with a delete shortcut) then a delete confirmation. */
        private fun showEditDialog(conversation: Conversation) {
            val input = EditText(activity).apply {
                setText(conversation.title)
                setSelection(text.length)
                hint = activity.getString(R.string.chat_hist_rename_hint)
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT
                textSize = 15f
                setTextColor(COLOR_TEXT)
                setHintTextColor(COLOR_MUTED)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(COLOR_CARD)
                }
            }
            val wrap = FrameLayout(activity).apply {
                minimumWidth = dp(260)
                setPadding(dp(22), dp(4), dp(22), 0)
                addView(
                    input,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            val alert = MaterialAlertDialogBuilder(activity)
                .setCustomTitle(dialogTitle(activity.getString(R.string.chat_hist_rename_title)))
                .setView(wrap)
                .setPositiveButton(R.string.chat_hist_save) { _, _ ->
                    val title = input.text.toString().trim()
                    if (title.isNotEmpty()) {
                        try {
                            store.rename(conversation.id, title)
                            store.saveAllNow()
                        } catch (_: Throwable) {
                        }
                        refresh()
                    }
                }
                .setNeutralButton(R.string.chat_hist_delete) { _, _ ->
                    // Let this dialog dismiss first, then stack the confirmation.
                    val decor = activity.window?.decorView
                    if (decor != null) {
                        decor.post { confirmDelete(conversation) }
                    } else {
                        confirmDelete(conversation)
                    }
                }
                .setNegativeButton(R.string.chat_hist_cancel, null)
                .create()
            styleDialog(alert, positive = COLOR_BRAND, neutral = COLOR_ROSE)
            alert.show()
            AnyChrome.dialog(alert)
        }

        private fun confirmDelete(conversation: Conversation) {
            val title = conversation.title.ifBlank {
                activity.getString(R.string.chat_hist_untitled)
            }
            val alert = MaterialAlertDialogBuilder(activity)
                .setCustomTitle(dialogTitle(activity.getString(R.string.chat_hist_delete_title)))
                .setMessage(activity.getString(R.string.chat_hist_delete_message, title))
                .setPositiveButton(R.string.chat_hist_delete) { _, _ ->
                    try {
                        store.delete(conversation.id)
                        store.saveAllNow()
                    } catch (_: Throwable) {
                    }
                    refresh()
                }
                .setNegativeButton(R.string.chat_hist_cancel, null)
                .create()
            styleDialog(alert, positive = COLOR_ROSE, neutral = COLOR_MUTED)
            alert.show()
            AnyChrome.dialog(alert)
        }

        private fun dialogTitle(text: String): TextView = TextView(activity).apply {
            this.text = text
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            setPadding(dp(22), dp(20), dp(22), dp(6))
        }

        private fun styleDialog(alert: AlertDialog, positive: Int, neutral: Int) {
            val radius = dp(20).toFloat()
            alert.window?.setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = radius
                setColor(COLOR_SHEET)
            })
            alert.window?.setDimAmount(0.55f)
            alert.setOnShowListener {
                alert.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(positive)
                alert.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(neutral)
                alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(COLOR_MUTED)
                alert.findViewById<TextView>(android.R.id.message)?.let { message ->
                    message.setTextColor(COLOR_MUTED)
                    message.textSize = 14f
                }
            }
        }

        private fun dismissSheet() {
            try {
                if (dialog.isShowing) dialog.dismiss()
            } catch (_: Throwable) {
            }
        }

        private fun relativeTime(ts: Long): String {
            if (ts <= 0L) return ""
            val diff = System.currentTimeMillis() - ts
            if (diff < 0) return activity.getString(R.string.chat_hist_just_now)
            if (diff < 60_000L) return activity.getString(R.string.chat_hist_just_now)
            if (diff < 3_600_000L) return "${diff / 60_000L} นาที"
            if (diff < 86_400_000L) return "${diff / 3_600_000L} ชม."
            val days = diff / 86_400_000L
            if (days < 7L) return "$days วัน"

            val then = Calendar.getInstance().apply { timeInMillis = ts }
            val now = Calendar.getInstance()
            val months = arrayOf(
                "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
                "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
            )
            val date = "${then.get(Calendar.DAY_OF_MONTH)} ${months[then.get(Calendar.MONTH)]}"
            return if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                date
            } else {
                "$date ${then.get(Calendar.YEAR)}"
            }
        }

        private fun oneLine(raw: String): String {
            val flat = raw.replace(Regex("\\s+"), " ").trim()
            return if (flat.length > 140) flat.take(140) + "…" else flat
        }

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density + 0.5f).toInt()

        private fun sheetBackground(): GradientDrawable {
            val radius = dp(22).toFloat()
            return GradientDrawable().apply {
                setColor(COLOR_SHEET)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }

        private fun ripple(color: Int, radiusDp: Float, rippleColor: Int = 0x22FFFFFF): RippleDrawable {
            val radius = dp(radiusDp.toInt()).toFloat()
            val content = GradientDrawable().apply {
                cornerRadius = radius
                setColor(color)
            }
            val mask = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.WHITE)
            }
            return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
        }

        private fun pill(fill: Int, radiusDp: Float, stroke: Int): RippleDrawable {
            val radius = dp(radiusDp.toInt()).toFloat()
            val content = GradientDrawable().apply {
                cornerRadius = radius
                setColor(fill)
                setStroke(dp(1), stroke)
            }
            val mask = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.WHITE)
            }
            return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask)
        }
    }
}
