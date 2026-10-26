package com.suidvck.any.features.browser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.suidvck.any.R
import com.suidvck.any.ui.FaIcons

/**
 * Floating "find on page" bar for the preview WebView.
 *
 * Attach it once to the preview container (a [FrameLayout]); the bar guards
 * against a second instance on the same host. Typing searches with a short
 * debounce, the keyboard action / Enter searches immediately, the counter is
 * fed by [WebTools]' find listener.
 */
class FindBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var webView: WebView? = null

    private val input = EditText(context)
    private val counter = TextView(context)

    private val searchRunnable = Runnable { performSearch() }
    private val focusRunnable = Runnable { focusInput() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.webtools_find_bg)
        elevation = dp(6).toFloat()
        setPadding(dp(14), dp(2), dp(4), dp(2))
        minimumHeight = dp(46)

        input.apply {
            hint = context.getString(R.string.webtools_find_hint)
            setHintTextColor(MUTED)
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, 0, dp(8), 0)
            setOnEditorActionListener { _, actionId, event ->
                val enterDown = event != null &&
                    event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_SEARCH || enterDown) {
                    removeCallbacks(searchRunnable)
                    performSearch()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    removeCallbacks(searchRunnable)
                    if (s.isNullOrBlank()) {
                        webView?.let { WebTools.clearFind(it) }
                        setCounter(0, 0)
                    } else if (visibility == VISIBLE) {
                        postDelayed(searchRunnable, 300L)
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        counter.apply {
            text = "0/0"
            setTextColor(MUTED)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            minWidth = dp(42)
            setPadding(dp(2), 0, dp(2), 0)
        }
        addView(counter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))

        addView(
            navButton("arrow-up", context.getString(R.string.webtools_find_prev)) {
                webView?.let { WebTools.findNext(it, false) }
            }
        )
        addView(
            navButton("arrow-down", context.getString(R.string.webtools_find_next)) {
                webView?.let { WebTools.findNext(it, true) }
            }
        )
        addView(
            navButton("xmark", context.getString(R.string.webtools_close), close = true) {
                close()
            }
        )

        visibility = View.GONE
    }

    // ------------------------------------------------------------------- API

    /** Adds this bar to [host] and binds it to [webView]. No-op when the host already has a bar. */
    fun attachTo(host: FrameLayout, webView: WebView) {
        if (this.parent === host) {
            bind(webView)
            return
        }
        if (host.findViewWithTag<FindBar>(TAG) != null) return

        unbind()
        (parent as? ViewGroup)?.removeView(this)

        tag = TAG
        visibility = View.GONE
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = dp(12)
            marginStart = dp(12)
            marginEnd = dp(12)
        }
        host.addView(this, params)
        bind(webView)
    }

    fun open() {
        if (parent == null) return
        bringToFront()
        removeCallbacks(focusRunnable)
        visibility = View.VISIBLE
        alpha = 0f
        translationY = -dp(8).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(140L).start()
        if (input.text.isNullOrEmpty()) {
            setCounter(0, 0)
        } else {
            removeCallbacks(searchRunnable)
            postDelayed(searchRunnable, 80L)
        }
        postDelayed(focusRunnable, 120L)
    }

    fun close() {
        removeCallbacks(searchRunnable)
        removeCallbacks(focusRunnable)
        hideKeyboard()
        visibility = View.GONE
        webView?.let { WebTools.clearFind(it) }
        setCounter(0, 0)
    }

    fun isOpen(): Boolean = visibility == View.VISIBLE && parent != null

    // --------------------------------------------------------------- internals

    private fun bind(target: WebView) {
        if (webView !== target) {
            unbind()
            webView = target
        }
        WebTools.setFindResultListener(target) { active, total -> setCounter(active, total) }
    }

    private fun unbind() {
        webView?.let { WebTools.setFindResultListener(it, null) }
        webView = null
    }

    private fun performSearch() {
        val target = webView ?: return
        val query = input.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            WebTools.clearFind(target)
            setCounter(0, 0)
            return
        }
        setCounter(0, 0)
        WebTools.find(target, query)
    }

    private fun setCounter(active: Int, total: Int) {
        counter.text = "$active/$total"
        counter.setTextColor(if (total > 0) 0xFFE6E6EE.toInt() else MUTED)
    }

    private fun focusInput() {
        if (visibility != VISIBLE) return
        input.requestFocus()
        val length = input.text?.length ?: 0
        input.setSelection(length)
        if (length > 0) input.selectAll()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, 0)
    }

    private fun hideKeyboard() {
        val token = input.windowToken
        if (token != null) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(token, 0)
        }
        input.clearFocus()
    }

    private fun navButton(
        icon: String,
        description: String,
        close: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        FaIcons.setIcon(
            this,
            icon,
            if (close) 14f else 13f,
            if (close) 0xFFC7C7D2.toInt() else 0xFFE6E6EE.toInt()
        )
        gravity = Gravity.CENTER
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = borderlessBackground()
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(40))
    }

    private fun borderlessBackground(): Drawable? {
        val value = TypedValue()
        val found = context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            value,
            true
        )
        return if (found && value.resourceId != 0) {
            ContextCompat.getDrawable(context, value.resourceId)
        } else {
            null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        removeCallbacks(searchRunnable)
        removeCallbacks(focusRunnable)
        unbind()
        hideKeyboard()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TAG = "any_find_bar"
        val MUTED = 0xFF9A9AA5.toInt()
    }
}
