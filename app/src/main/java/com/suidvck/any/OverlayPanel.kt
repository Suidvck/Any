package com.suidvck.any

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating panel that renders AI-written "overlay" HTML on top of the preview
 * WebView. Drag it by the handle bar, close it with the ✕ button. This is an
 * in-app overlay — it only ever shows inside Any, never on top of other apps.
 */
@SuppressLint("SetJavaScriptEnabled")
class OverlayPanel(private val context: Context, private val parent: FrameLayout) {

    private val density = context.resources.displayMetrics.density
    private val host = FrameLayout(context)
    private val web = WebView(context)
    private var grabX = 0f
    private var grabY = 0f

    val isVisible: Boolean get() = host.visibility == View.VISIBLE

    private fun dp(value: Int): Int = (value * density).toInt()

    init {
        val metrics = context.resources.displayMetrics
        val wpx = metrics.widthPixels

        web.setBackgroundColor(Color.TRANSPARENT)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.post { measureContent() }
            }
        }

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = GradientDrawable().apply {
            setColor(0x33000000)
            cornerRadius = dp(12).toFloat()
        }
        val grip = TextView(context)
        grip.text = "⠿"
        grip.textSize = 12f
        grip.setTextColor(0x66FFFFFF)
        grip.setPadding(dp(10), 0, dp(4), 0)
        bar.addView(grip)
        val spacer = View(context)
        bar.addView(spacer, LinearLayout.LayoutParams(0, dp(2), 1f))
        val close = TextView(context)
        close.text = "✕"
        close.textSize = 13f
        close.setTextColor(0xCCFFFFFF.toInt())
        close.gravity = Gravity.CENTER
        close.setPadding(dp(12), dp(4), dp(12), dp(4))
        close.setOnClickListener { hide() }
        bar.addView(close)
        bar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grabX = event.rawX - host.x
                    grabY = event.rawY - host.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (parent.width - host.width).coerceAtLeast(0)
                    val maxY = (parent.height - host.height).coerceAtLeast(0)
                    host.x = (event.rawX - grabX).coerceIn(0f, maxX.toFloat())
                    host.y = (event.rawY - grabY).coerceIn(0f, maxY.toFloat())
                    true
                }
                else -> false
            }
        }

        host.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = dp(24) }
        )
        host.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)).apply {
                gravity = Gravity.TOP
            }
        )
        host.visibility = View.GONE

        val w0 = minOf(dp(300), wpx - dp(40))
        parent.addView(
            host,
            FrameLayout.LayoutParams(w0, dp(150)).apply {
                leftMargin = wpx - w0 - dp(14)
                topMargin = dp(84)
            }
        )
    }

    fun show(html: String) {
        host.visibility = View.VISIBLE
        web.loadDataWithBaseURL("https://overlay.local/", html, "text/html", "UTF-8", null)
    }

    fun hide() {
        host.visibility = View.GONE
    }

    fun reveal() {
        host.visibility = View.VISIBLE
    }

    /** The floating panel view itself; the activity root parents it. */
    val panelView: FrameLayout get() = host

    /** Sizes the panel after its content: shrink-wraps small widgets, caps huge ones. */
    private fun measureContent() {
        web.evaluateJavascript(
            "(function(){var w=0,h=0,e=document.body?document.body.children:null;" +
                "if(e){for(var i=0;i<e.length;i++){var b=e[i].getBoundingClientRect();" +
                "if(b.right>w)w=b.right;if(b.bottom>h)h=b.bottom;}}" +
                "if(w<=0)w=(window.innerWidth||320);if(h<=0)h=(window.innerHeight||150);" +
                "return Math.ceil(w)+','+Math.ceil(h);})()"
        ) { value ->
            val parts = value?.trim('"')?.split(',') ?: return@evaluateJavascript
            val contentW = parts.getOrNull(0)?.toIntOrNull() ?: return@evaluateJavascript
            val contentH = parts.getOrNull(1)?.toIntOrNull() ?: return@evaluateJavascript
            val metrics = context.resources.displayMetrics
            val lp = host.layoutParams
            lp.width = ((contentW * density).toInt() + dp(10))
                .coerceIn(dp(110), minOf(dp(380), metrics.widthPixels - dp(32)))
            lp.height = ((contentH * density).toInt() + dp(30))
                .coerceIn(dp(70), (metrics.heightPixels * 0.62f).toInt())
            host.layoutParams = lp
            clamp()
        }
    }

    private fun clamp() {
        if (parent.width <= 0 || parent.height <= 0) return
        val maxX = (parent.width - host.width).coerceAtLeast(0)
        val maxY = (parent.height - host.height).coerceAtLeast(0)
        host.x = host.x.coerceIn(0f, maxX.toFloat())
        host.y = host.y.coerceIn(0f, maxY.toFloat())
    }
}
