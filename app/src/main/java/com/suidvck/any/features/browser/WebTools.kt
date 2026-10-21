package com.suidvck.any.features.browser

import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONTokener
import java.util.WeakHashMap

/**
 * Small helpers around the preview [WebView]: desktop user agent, force-dark,
 * page text extraction and in-page find.
 *
 * All entry points are safe to call from the main thread only. If a call arrives
 * from another thread it is posted to the main thread instead.
 */
object WebTools {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    /** Reads visible text; empty string for pages without a body or when JS fails. */
    private const val EXTRACT_JS =
        "(function(){try{var b=document.body;if(!b){return '';}" +
            "var t=b.innerText;if(t==null){t=b.textContent||'';}" +
            "t=String(t);if(t.length>200000){t=t.slice(0,200000);}return t;}" +
            "catch(e){return '';}})()"

    private class PageState(
        val mobileUa: String,
        val overview: Boolean,
        val wide: Boolean
    )

    private class FindState {
        var active = 0
        var total = 0
        var query: String? = null
        var countedQuery: String? = null
        var listener: ((active: Int, total: Int) -> Unit)? = null
    }

    private val pageStates = WeakHashMap<WebView, PageState>()
    private val findStates = WeakHashMap<WebView, FindState>()
    private val main = Handler(Looper.getMainLooper())

    // ---------------------------------------------------------------- desktop

    /**
     * Switches the WebView between a desktop Chrome user agent and the mobile
     * one it started with. The original mobile UA (and layout flags) are
     * captured once per WebView and restored when [on] is false.
     */
    fun applyDesktopMode(webView: WebView, on: Boolean) {
        onMain {
            runCatching {
                val settings = webView.settings
                val state = pageStates.getOrPut(webView) {
                    val current = settings.userAgentString
                        ?: WebSettings.getDefaultUserAgent(webView.context)
                    val isMobile = current.contains("Android", true) &&
                        current.contains("Mobile", true)
                    PageState(
                        mobileUa = if (isMobile) current else MOBILE_UA,
                        overview = settings.loadWithOverviewMode,
                        wide = settings.useWideViewPort
                    )
                }
                if (on) {
                    settings.userAgentString = DESKTOP_UA
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                } else {
                    settings.userAgentString = state.mobileUa
                    settings.loadWithOverviewMode = state.overview
                    settings.useWideViewPort = state.wide
                }
            }
        }
    }

    // -------------------------------------------------------------- force dark

    /**
     * Preferred API is algorithmic darkening (WebView 105+ / Android 13+).
     * Older WebViews fall back to the deprecated force-dark flag; if neither is
     * supported this is a no-op.
     */
    @Suppress("DEPRECATION")
    fun applyForceDark(webView: WebView, on: Boolean) {
        onMain {
            runCatching {
                val settings = webView.settings
                when {
                    WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, on)

                    WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
                        WebSettingsCompat.setForceDark(
                            settings,
                            if (on) WebSettingsCompat.FORCE_DARK_ON
                            else WebSettingsCompat.FORCE_DARK_OFF
                        )

                    else -> Unit // not supported on this WebView
                }
            }
        }
    }

    // ------------------------------------------------------------- text extract

    /** Returns the page's `document.body.innerText` (or "" on failure) on the main thread. */
    fun extractText(webView: WebView, cb: (String) -> Unit) {
        var replied = false

        fun reply(text: String) {
            if (replied) return
            replied = true
            if (Looper.myLooper() == Looper.getMainLooper()) {
                cb(text)
            } else {
                main.post { cb(text) }
            }
        }

        onMain {
            val ok = runCatching {
                webView.evaluateJavascript(EXTRACT_JS) { raw ->
                    reply(decodeJsString(raw))
                }
            }.isSuccess
            if (!ok) reply("")
        }
    }

    // -------------------------------------------------------------------- find

    /**
     * Starts an asynchronous find. Returns the already-known match count when
     * the same query was counted before, otherwise 0 — the real count arrives
     * through [setFindResultListener].
     */
    fun find(webView: WebView, query: String): Int {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { find(webView, query) }
            return 0
        }
        val q = query.trim()
        val state = findState(webView)
        state.query = q
        if (q.isEmpty()) {
            state.active = 0
            state.total = 0
            state.countedQuery = null
            runCatching { webView.clearMatches() }
            state.listener?.invoke(0, 0)
            return 0
        }
        val launched = runCatching { webView.findAllAsync(q) }.isSuccess
        return if (launched && state.countedQuery == q) state.total else 0
    }

    /** Moves to the previous/next match. Returns false when there is no active query. */
    fun findNext(webView: WebView, forward: Boolean): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { findNext(webView, forward) }
            return false
        }
        val state = findStates[webView]
        if (state?.query.isNullOrEmpty()) return false
        return runCatching { webView.findNext(forward) }.isSuccess
    }

    /** Clears highlights and resets the reported counter. */
    fun clearFind(webView: WebView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { clearFind(webView) }
            return
        }
        val state = findStates[webView]
        runCatching { webView.clearMatches() }
        if (state != null) {
            state.active = 0
            state.total = 0
            state.query = null
            state.countedQuery = null
            state.listener?.invoke(0, 0)
        }
    }

    /**
     * Extra (not part of the shared contract): observes match counts for one
     * WebView. [FindBar] uses this because [WebView.findAllAsync] is async and
     * the WebView can only have a single internal find listener.
     */
    internal fun setFindResultListener(
        webView: WebView,
        listener: ((active: Int, total: Int) -> Unit)?
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { setFindResultListener(webView, listener) }
            return
        }
        val state = findState(webView)
        state.listener = listener
        if (listener != null) listener(state.active, state.total)
    }

    // ----------------------------------------------------------------- helpers

    private fun findState(webView: WebView): FindState {
        findStates[webView]?.let { return it }
        val state = FindState()
        findStates[webView] = state
        runCatching {
            webView.setFindListener { active, total, done ->
                if (!done) return@setFindListener
                state.active = active
                state.total = total
                state.countedQuery = state.query
                state.listener?.invoke(active, total)
            }
        }
        return state
    }

    private fun decodeJsString(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return ""
        val parsed = runCatching { JSONTokener(value).nextValue() }.getOrNull()
        return when (parsed) {
            is String -> parsed
            null -> value.removeSurrounding("\"")
            else -> ""
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }
}
