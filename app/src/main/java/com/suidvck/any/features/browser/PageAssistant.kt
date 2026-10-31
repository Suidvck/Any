package com.suidvck.any.features.browser

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.R
import com.suidvck.any.features.ai.AiPrompts
import com.suidvck.any.ui.AnyChrome
import com.suidvck.any.ui.FaIcons

/**
 * Dark "page assistant" dialog: summarize the current page, translate it to
 * Thai, or ask a free-form question about it.
 *
 * The caller supplies two lambdas so this object stays independent of the AI
 * client and of the overlay renderer:
 *  - [ask] receives the ready prompt and must eventually call back with the
 *    model answer (any thread is fine, the UI hop is handled here);
 *  - [showResult] receives a self-contained HTML card and should render it in
 *    the app's floating overlay.
 */
object PageAssistant {

    private const val MAX_PAGE_CHARS = 7000
    private const val MAX_CARD_CHARS = 12000

    private val BRAND = 0xFF7C5CBF.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFFCAC4D0.toInt()

    fun show(
        activity: Activity,
        webView: WebView,
        ask: (prompt: String, cb: (Result<String>) -> Unit) -> Unit,
        showResult: (html: String) -> Unit
    ) {
        var dialog: AlertDialog? = null
        var busy = false

        fun toast(message: String) {
            if (activity.isFinishing || activity.isDestroyed) return
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }

        // ------------------------------------------------------------ views

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(TextView(activity).apply {
            text = activity.getString(R.string.webtools_assistant_title)
            setTextColor(TEXT)
            textSize = 17f
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        titleColumn.addView(TextView(activity).apply {
            text = activity.getString(R.string.webtools_assistant_subtitle)
            setTextColor(MUTED)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val closeButton = TextView(activity).apply {
            gravity = Gravity.CENTER
            FaIcons.setIcon(this, "xmark", 14f, 0xFFE6E1E9.toInt())
            contentDescription = activity.getString(R.string.webtools_close)
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4))
            setOnClickListener { dialog?.dismiss() }
        }
        header.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(closeButton)

        val summaryRow = actionRow(
            activity,
            "file-lines",
            activity.getString(R.string.webtools_summary_title),
            activity.getString(R.string.webtools_summary_sub)
        )
        val translateRow = actionRow(
            activity,
            "globe",
            activity.getString(R.string.webtools_translate_title),
            activity.getString(R.string.webtools_translate_sub)
        )

        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        statusRow.addView(
            ProgressBar(activity, null, android.R.attr.progressBarStyleSmall).apply {
                indeterminateTintList = ColorStateList.valueOf(BRAND)
            },
            LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18))
        )
        val statusText = TextView(activity).apply {
            setTextColor(MUTED)
            textSize = 12f
            setPadding(dp(activity, 8), 0, 0, 0)
        }
        statusRow.addView(statusText)

        val inputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val questionInput = EditText(activity).apply {
            hint = activity.getString(R.string.webtools_question_hint)
            setHintTextColor(MUTED)
            setTextColor(Color.WHITE)
            textSize = 14f
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = ContextCompat.getDrawable(activity, R.drawable.webtools_input_bg)
            setPadding(dp(activity, 14), 0, dp(activity, 14), 0)
        }
        val askButton = TextView(activity).apply {
            text = activity.getString(R.string.webtools_ask)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setBackgroundResource(R.drawable.webtools_btn_bg)
            isClickable = true
            isFocusable = true
            minWidth = dp(activity, 64)
            setPadding(dp(activity, 16), 0, dp(activity, 16), 0)
        }
        inputRow.addView(
            questionInput,
            LinearLayout.LayoutParams(0, dp(activity, 46), 1f)
        )
        inputRow.addView(
            askButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(activity, 46)
            ).apply { marginStart = dp(activity, 8) }
        )

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 16)
            )
        }
        content.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            summaryRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 14) }
        )
        content.addView(
            translateRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 8) }
        )
        content.addView(
            statusRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        )
        content.addView(
            inputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 12) }
        )

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // ------------------------------------------------------------ state

        fun setBusy(label: String?) {
            val working = label != null
            statusRow.visibility = if (working) View.VISIBLE else View.GONE
            if (label != null) statusText.text = label
            for (row in arrayOf(summaryRow, translateRow)) {
                row.isEnabled = !working
                row.alpha = if (working) 0.45f else 1f
            }
            questionInput.isEnabled = !working
            askButton.isEnabled = !working
            askButton.alpha = if (working) 0.5f else 1f
        }

        fun start(kind: Int, question: String) {
            if (busy) return
            busy = true
            setBusy(activity.getString(R.string.webtools_status_reading))
            hideKeyboard(activity, questionInput)

            WebTools.extractText(webView) { raw ->
                val page = raw.trim()
                if (page.isEmpty()) {
                    busy = false
                    setBusy(null)
                    toast(activity.getString(R.string.webtools_unreadable))
                    return@extractText
                }
                val text = if (page.length > MAX_PAGE_CHARS) {
                    page.substring(0, MAX_PAGE_CHARS)
                } else {
                    page
                }
                val prompt = when (kind) {
                    0 -> AiPrompts.pageSummary(text)
                    1 -> AiPrompts.pageTranslate(text)
                    else -> AiPrompts.pageQuestion(text, question)
                }
                setBusy(activity.getString(R.string.webtools_status_thinking))

                var answered = false
                val callback: (Result<String>) -> Unit = { result ->
                    activity.runOnUiThread {
                        if (answered) return@runOnUiThread
                        answered = true
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        busy = false
                        setBusy(null)
                        result.onSuccess { answer ->
                            val clean = cleanAnswer(answer)
                            if (clean.isEmpty()) {
                                toast(activity.getString(R.string.webtools_failed))
                            } else {
                                dialog?.dismiss()
                                runCatching { showResult(cardHtml(activity, clean)) }
                            }
                        }.onFailure {
                            toast(activity.getString(R.string.webtools_failed))
                        }
                    }
                }

                val issued = runCatching { ask(prompt, callback) }.isSuccess
                if (!issued) {
                    busy = false
                    setBusy(null)
                    toast(activity.getString(R.string.webtools_failed))
                }
            }
        }

        fun askQuestion() {
            val question = questionInput.text?.toString()?.trim().orEmpty()
            if (question.isEmpty()) {
                toast(activity.getString(R.string.webtools_question_empty))
                questionInput.requestFocus()
                return
            }
            start(2, question)
        }

        // ------------------------------------------------------------ events

        summaryRow.setOnClickListener { start(0, "") }
        translateRow.setOnClickListener { start(1, "") }
        askButton.setOnClickListener { askQuestion() }
        questionInput.setOnEditorActionListener { _, actionId, event ->
            val enterDown = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enterDown) {
                askQuestion()
                true
            } else {
                false
            }
        }

        // ------------------------------------------------------------- show

        val created = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setBackground(ContextCompat.getDrawable(activity, R.drawable.webtools_sheet_bg))
            .setBackgroundInsetStart(dp(activity, 24))
            .setBackgroundInsetEnd(dp(activity, 24))
            .setBackgroundInsetTop(dp(activity, 24))
            .setBackgroundInsetBottom(dp(activity, 24))
            .create()
        dialog = created
        created.setOnDismissListener {
            busy = false
            hideKeyboard(activity, questionInput)
        }
        created.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        created.show()
        AnyChrome.dialog(created)
    }

    // ---------------------------------------------------------------- helpers

    private fun actionRow(
        activity: Activity,
        icon: String,
        title: String,
        subtitle: String
    ): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.webtools_row_bg)
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setPadding(
                dp(activity, 14), dp(activity, 11),
                dp(activity, 12), dp(activity, 11)
            )
        }
        val iconView = TextView(activity).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34))
        }
        FaIcons.setIcon(iconView, icon, 18f, BRAND)
        row.addView(iconView)
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(activity).apply {
            text = title
            setTextColor(TEXT)
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        column.addView(TextView(activity).apply {
            text = subtitle
            setTextColor(MUTED)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(
            column,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val arrow = TextView(activity).apply {
            gravity = Gravity.CENTER
        }
        FaIcons.setIcon(arrow, "chevron-right", 14f, MUTED)
        row.addView(arrow)
        return row
    }

    private fun cleanAnswer(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```html")
                .removePrefix("```HTML")
                .removePrefix("```")
                .trim()
            val end = text.lastIndexOf("```")
            if (end > 0) text = text.substring(0, end).trim()
        }
        if (text.length > MAX_CARD_CHARS) text = text.substring(0, MAX_CARD_CHARS)
        return text
    }

    /** Escapes the model answer and wraps it in the compact dark overlay card. */
    private fun cardHtml(activity: Activity, answer: String): String {
        val body = TextUtils.htmlEncode(answer)
            .replace("\r\n", "\n")
            .replace("\n", "<br>")
        val label = TextUtils.htmlEncode(
            activity.getString(R.string.webtools_result_label)
        )
        return "<style>html,body{margin:0;padding:0;background:transparent;}" +
            "</style>" +
            "<div style=\"box-sizing:border-box;background:#141218;color:#E6E1E9;" +
            "border:1px solid rgba(255,255,255,0.08);border-radius:16px;" +
            "padding:14px 16px;font-family:-apple-system,'Segoe UI',Roboto,sans-serif;" +
            "font-size:15px;line-height:1.55;max-height:280px;overflow:auto;" +
            "-webkit-overflow-scrolling:touch;word-break:break-word;\">" +
            "<div style=\"font-size:11px;font-weight:600;letter-spacing:.04em;" +
            "color:#CAC4D0;margin-bottom:8px;\">$label</div>" +
            "<div>$body</div></div>"
    }

    private fun hideKeyboard(activity: Activity, view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
