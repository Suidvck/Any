package com.suidvck.any

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen: stacked pixel clock (hours over minutes), subtle running-app
 * hints (only visible when something is active), a minimal media row that
 * controls the video playing inside the WebView, and a "Type Something ..."
 * bar that opens the AI chat sheet.
 *
 * The AI can answer with a full HTML app (previewed fullscreen in the WebView)
 * or with "OPEN_URL: ..." to open a website. The status bar is always hidden.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var mainContent: FrameLayout
    private lateinit var previewContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var scrim: View
    private lateinit var sheetContainer: LinearLayout
    private lateinit var chatPanel: LinearLayout
    private lateinit var messagesContainer: LinearLayout
    private lateinit var messagesScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton

    private lateinit var clockText: TextView
    private lateinit var runningRows: LinearLayout
    private lateinit var mediaControls: LinearLayout
    private lateinit var timesRow: LinearLayout
    private lateinit var playingProgress: ProgressBar
    private lateinit var playingPos: TextView
    private lateinit var playingDur: TextView
    private lateinit var btnPlayPause: ImageButton

    private var sheetOpen = false
    private var greeted = false
    private var generating = false
    private val history = mutableListOf<Pair<String, String>>() // role to content, oldest first

    private data class Task(val host: String, val title: String)

    private val tasks = mutableListOf<Task>() // newest first
    private var lastPrompt = ""
    private var hasContent = false
    private var videoActive = false
    private var lastPlaying = false
    private var polling = false
    private var lastSchemeToast = 0L

    @Volatile private var adBlockEnabled = true
    private var ytAdBlockHandle: ScriptHandler? = null

    // HTML5 video fullscreen (WebChromeClient custom view).
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Voice input (long-press on the floating chat ring).
    private var speech: SpeechRecognizer? = null
    private var voiceActive = false
    private var voiceBubble: View? = null

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                beginVoiceListening()
            } else if (sheetOpen) {
                addMessage(you = false, text = getString(R.string.voice_need_permission))
            }
        }

    private val ui = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    private val clockTick = object : Runnable {
        override fun run() {
            // Stacked pixel clock: hours on the first line, minutes on the second.
            clockText.text = timeFormat.format(Date()).replace(':', '\n')
            ui.postDelayed(this, 1000)
        }
    }

    private val videoPoll = object : Runnable {
        override fun run() {
            if (!polling || isFinishing) return
            pollOnce()
            ui.postDelayed(this, if (videoActive) 700L else 1500L)
        }
    }

    private val codeBlockRegex = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val openUrlRegex = Regex("OPEN_URL:\\s*(https?://\\S+)")

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (customView != null) exitVideoFullscreen()
            else if (sheetOpen) closeSheet()
            else if (previewContainer.isVisible) hidePreview()
        }
    }

    private data class Provider(val name: String, val base: String, val model: String)

    private val providers = listOf(
        Provider("Gemini (ฟรี)", AiClient.DEFAULT_BASE, AiClient.DEFAULT_MODEL),
        Provider("OpenAI", "https://api.openai.com/v1/", "gpt-4o-mini"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1/", "google/gemini-2.5-flash"),
        Provider("Groq", "https://api.groq.com/openai/v1/", "llama-3.3-70b-versatile"),
        Provider("Custom", "", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        mainContent = findViewById(R.id.main_content)
        previewContainer = findViewById(R.id.preview_container)
        webView = findViewById(R.id.preview_webview)
        scrim = findViewById(R.id.scrim)
        sheetContainer = findViewById(R.id.sheet_container)
        chatPanel = findViewById(R.id.chat_panel)
        messagesContainer = findViewById(R.id.messages_container)
        messagesScroll = findViewById(R.id.messages_scroll)
        input = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.send_button)

        clockText = findViewById(R.id.clock_time)
        runningRows = findViewById(R.id.running_rows)
        mediaControls = findViewById(R.id.media_controls)
        timesRow = findViewById(R.id.times_row)
        playingProgress = findViewById(R.id.playing_progress)
        playingPos = findViewById(R.id.playing_pos)
        playingDur = findViewById(R.id.playing_dur)
        btnPlayPause = findViewById(R.id.btn_playpause)

        hideStatusBar()
        setupWebView()
        adBlockEnabled = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("adblock", true)
        applyAdBlockScript()

        // Pixel clock font (Doto), heavyweight instance.
        val clockFont = ResourcesCompat.getFont(this, R.font.doto)
        clockText.typeface =
            if (Build.VERSION.SDK_INT >= 28 && clockFont != null) Typeface.create(clockFont, 800, false)
            else clockFont
        ui.post(clockTick)

        val previewFab: FloatingActionButton = findViewById(R.id.fab_chat_preview)
        val composeBox: TextView = findViewById(R.id.compose_box)
        val btnPrev: ImageButton = findViewById(R.id.btn_prev)
        val btnNext: ImageButton = findViewById(R.id.btn_next)
        val btnYt: View = findViewById(R.id.btn_yt)
        val btnTiktok: ImageButton = findViewById(R.id.btn_tiktok)
        val btnDiscord: ImageButton = findViewById(R.id.btn_discord)
        val btnInstagram: ImageButton = findViewById(R.id.btn_instagram)

        previewFab.setOnClickListener { openSheet() }
        previewFab.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            startVoiceCommand()
            true
        }
        scrim.setOnClickListener { closeSheet() }
        sendButton.setOnClickListener { sendMessage() }
        composeBox.setOnClickListener { openSheet() }
        btnTiktok.setOnClickListener { showPreview(url = "https://www.tiktok.com/") }
        btnDiscord.setOnClickListener { showPreview(url = "https://discord.com/") }
        btnInstagram.setOnClickListener { showPreview(url = "https://www.instagram.com/") }
        btnYt.setOnClickListener { showPreview(url = "https://www.youtube.com") }
        btnPlayPause.setOnClickListener {
            evalJs(PLAY_PAUSE_JS)
            ui.postDelayed({ pollOnce() }, 350)
        }
        btnPrev.setOnClickListener {
            evalJs(SEEK_BACK_JS)
            ui.postDelayed({ pollOnce() }, 250)
        }
        btnNext.setOnClickListener {
            evalJs(SEEK_FWD_JS)
            ui.postDelayed({ pollOnce() }, 250)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        onBackPressedDispatcher.addCallback(this, backCallback)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            mainContent.setPadding(0, bars.top, 0, bars.bottom)
            previewContainer.updatePadding(bottom = bars.bottom)
            val bottom = if (ime.bottom > bars.bottom) ime.bottom else bars.bottom
            chatPanel.updatePadding(bottom = dp(14) + bottom)
            insets
        }

        renderRunning()

        // QA hooks: `--ez openChat true` opens the sheet; `--es autoPrompt "<text>"`
        // opens the sheet and sends that prompt automatically; `--ez voice true`
        // starts voice listening as if the chat ring was long-pressed.
        root.postDelayed({ handleQaExtras(intent) }, 450)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("AnyQA", "onNewIntent extras=${intent.extras}")
        handleQaExtras(intent)
    }

    private fun handleQaExtras(intent: Intent?) {
        if (intent == null || isFinishing) return
        Log.d("AnyQA", "handleQaExtras extras=${intent.extras}")
        val autoPrompt = intent.getStringExtra("autoPrompt")
        val openUrl = intent.getStringExtra("openUrl")
        val navTest = intent.getStringExtra("navTest")
        val modelSearchBase = intent.getStringExtra("modelSearchBase")
        val runJs = intent.getStringExtra("runJs")
        val pipAction = intent.getStringExtra("pipAction")
        when {
            !pipAction.isNullOrBlank() -> root.post { handlePipAction(pipAction) }
            !runJs.isNullOrBlank() -> root.post { evalJs(runJs) }
            !navTest.isNullOrBlank() -> root.post { handleNavigation(navTest, true) }
            intent.getBooleanExtra("enterPip", false) -> root.post { enterPip() }
            intent.hasExtra("adblock") ->
                root.post { setAdBlock(intent.getBooleanExtra("adblock", true)) }
            !autoPrompt.isNullOrBlank() -> root.post {
                openSheet()
                input.setText(autoPrompt)
                sendMessage()
            }
            !openUrl.isNullOrBlank() -> root.post { showPreview(url = openUrl) }
            intent.getBooleanExtra("showHome", false) -> root.post { hidePreview() }
            intent.getBooleanExtra("openSettings", false) -> root.post { showSettings() }
            !modelSearchBase.isNullOrBlank() -> root.post {
                showModelPicker(modelSearchBase, intent.getStringExtra("modelSearchKey").orEmpty()) {}
            }
            intent.getBooleanExtra("openChat", false) -> root.post { openSheet() }
            intent.getBooleanExtra("voice", false) -> root.post { startVoiceCommand() }
        }
    }

    override fun onResume() {
        super.onResume()
        evalJs("window.__anyForcePlay=false")
        if (hasContent) startVideoPolling()
    }

    override fun onStop() {
        super.onStop()
        // Screen off / app backgrounded: stop polling (nothing to update) but keep
        // whatever was playing alive if the site tries to pause itself.
        polling = false
        ui.removeCallbacks(videoPoll)
        if (hasContent && lastPlaying) {
            evalJs(
                "window.__anyForcePlay=true;var v=document.querySelector('video,audio');" +
                    "if(v&&v.paused&&!v.ended){try{v.play()}catch(e){}}"
            )
        }
        // Release the mic if the app goes fully hidden mid-listening.
        stopVoiceListening()
        clearVoiceBubble()
    }

    override fun onDestroy() {
        stopVoiceListening()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Below Android 12, enter Picture-in-Picture when leaving with a video playing.
        if (Build.VERSION.SDK_INT < 31 && hasContent && previewContainer.isVisible && videoActive && lastPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        findViewById<View>(R.id.fab_chat_preview)?.visibility = when {
            inPip -> View.GONE
            previewContainer.isVisible -> View.VISIBLE
            else -> View.GONE
        }
        if (inPip) {
            if (sheetOpen) closeSheet()
        } else {
            // If the PiP window was dismissed (not expanded), the app stays stopped:
            // pause the video so no hidden sound keeps playing.
            ui.postDelayed({
                if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    evalJs(
                        "window.__anyForcePlay=false;var v=document.querySelector('video,audio');" +
                            "if(v&&!v.paused){try{v.pause()}catch(e){}}"
                    )
                }
            }, 450)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun hideStatusBar() {
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        WebView.setWebContentsDebuggingEnabled(true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Fool pages (e.g. YouTube) into thinking they stay visible, so they do
            // not pause playback when the app is hidden or the screen turns off.
            WebViewCompat.addDocumentStartJavaScript(webView, KEEP_ALIVE_JS, setOf("*"))
        }
        webView.webViewClient = object : WebViewClient() {
            // Keep every navigation inside the WebView; deep links (intent://, market://,
            // youtube:// …) are blocked so pages cannot bounce the user into other apps.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleNavigation(request.url?.toString().orEmpty(), request.isForMainFrame)

            // Ad blocker: silently drop requests to known ad / tracker hosts.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (adBlockEnabled && !request.isForMainFrame &&
                    request.url != null && AdBlock.isAd(request.url)
                ) {
                    Log.d("AnyQA", "adblock ${request.url}")
                    return WebResourceResponse(
                        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (title.isNullOrBlank()) return
                val first = tasks.firstOrNull() ?: return
                if (first.host == HOST_MINIAPP) return
                tasks[0] = first.copy(title = title.take(64))
                renderRunning()
            }

            // Surface the built-in YouTube ad skipper's logs (tag [any-ytad]) in logcat.
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val m = msg.message()
                if (m.contains("[any-ytad]")) Log.d("AnyQA", m)
                return true
            }

            // HTML5 video fullscreen: show the WebView's custom view over everything.
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                hideStatusBar()
            }

            override fun onHideCustomView() = exitVideoFullscreen()
        }
    }

    // Only http(s) (and harmless internal schemes) keep navigating inside the WebView.
    // Anything else — intent://, market://, youtube://, tiktok:// … — would bounce the
    // user into another app, so it is blocked. An intent:// link from the main frame
    // that carries a web fallback quietly opens that fallback here instead.
    private fun handleNavigation(url: String, isMainFrame: Boolean): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull().orEmpty()
        if (scheme == "http" || scheme == "https" || scheme == "about" ||
            scheme == "data" || scheme == "blob"
        ) {
            return false
        }
        Log.d("AnyQA", "blocked nav main=$isMainFrame scheme=$scheme url=$url")
        if (scheme == "intent" && isMainFrame) {
            val fallback = intentFallback(url)
            if (fallback != null &&
                (fallback.startsWith("http://") || fallback.startsWith("https://"))
            ) {
                webView.post { webView.loadUrl(fallback) }
                return true
            }
        }
        if (isMainFrame) showBlockedToast()
        return true
    }

    private fun intentFallback(url: String): String? {
        val marker = "S.browser_fallback_url="
        val start = url.indexOf(marker)
        if (start < 0) return null
        val rest = url.substring(start + marker.length)
        val end = rest.indexOf(';')
        val raw = if (end >= 0) rest.substring(0, end) else rest
        return runCatching { Uri.decode(raw) }.getOrNull()
    }

    private fun showBlockedToast() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSchemeToast < 1500) return
        lastSchemeToast = now
        Toast.makeText(this, getString(R.string.link_blocked), Toast.LENGTH_SHORT).show()
    }

    // ---------- Home screen status cards ----------

    private fun renderRunning() {
        runningRows.removeAllViews()
        val show = tasks.take(2)
        runningRows.isVisible = show.isNotEmpty()
        show.forEach { t -> runningRows.addView(makeRow(t.host, t.title)) }
    }

    private fun makeRow(host: String, text: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(5))
            background = getDrawable(R.drawable.bg_row_dot)
        }
        val tv = TextView(this).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(9) }
            alpha = 0.85f
        }
        val full = "$host : $text"
        val sp = SpannableString(full)
        sp.setSpan(
            ForegroundColorSpan(getColor(R.color.home_muted)),
            0,
            host.length + 3,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = sp
        tv.setTextColor(getColor(R.color.home_text))
        row.addView(dot)
        row.addView(tv)
        return row
    }

    private fun addTask(host: String, title: String) {
        tasks.add(0, Task(host, title))
        while (tasks.size > 4) tasks.removeAt(tasks.size - 1)
        renderRunning()
    }

    private fun hostOf(url: String): String = runCatching {
        Uri.parse(url).host?.removePrefix("www.") ?: url
    }.getOrDefault(url)

    // ---------- Now playing (video inside the WebView) ----------

    private fun startVideoPolling() {
        if (!polling) {
            polling = true
            ui.post(videoPoll)
        }
    }

    private fun pollOnce() {
        if (!hasContent || !::webView.isInitialized) return
        webView.evaluateJavascript(VIDEO_JS) { result -> handleVideoState(result) }
    }

    private fun evalJs(js: String) {
        if (!hasContent || !::webView.isInitialized) return
        webView.evaluateJavascript(js, null)
    }

    private fun handleVideoState(result: String?) {
        val state = runCatching {
            val v = JSONTokener(result ?: "null").nextValue()
            if (v is String && v.isNotBlank()) JSONObject(v) else null
        }.getOrNull()

        if (state == null) {
            if (videoActive || lastPlaying) {
                videoActive = false
                lastPlaying = false
                updatePipState()
            }
            setMediaDimmed(true)
            playingProgress.progress = 0
            playingPos.text = "0:00"
            playingDur.text = "0:00"
            btnPlayPause.setImageResource(R.drawable.ic_play)
            return
        }

        videoActive = true
        val playingNow = !state.optBoolean("p")
        if (playingNow != lastPlaying) {
            lastPlaying = playingNow
            updatePipState()
        }
        setMediaDimmed(false)
        val t = state.optDouble("t", 0.0)
        val d = state.optDouble("d", 0.0)
        playingProgress.progress = if (d > 0) ((t / d) * 100).toInt().coerceIn(0, 100) else 0
        playingPos.text = fmtTime(t)
        playingDur.text = fmtTime(d)
        btnPlayPause.setImageResource(if (state.optBoolean("p")) R.drawable.ic_play else R.drawable.ic_pause)
    }

    private fun setMediaDimmed(dimmed: Boolean) {
        val a = if (dimmed) 0.45f else 1f
        mediaControls.alpha = a
        timesRow.alpha = a
        playingProgress.alpha = a
    }

    private fun fmtTime(seconds: Double): String {
        val s = seconds.toInt().coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ---------- Chat sheet ----------

    private fun openSheet() {
        if (sheetOpen) return
        sheetOpen = true
        updateBack()
        sheetContainer.isVisible = true
        if (!greeted) {
            greeted = true
            addMessage(you = false, text = getString(R.string.greeting))
        }
        chatPanel.post {
            chatPanel.translationY = chatPanel.height.toFloat()
            chatPanel.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        scrim.alpha = 0f
        scrim.isVisible = true
        scrim.animate().alpha(1f).setDuration(220).start()
    }

    private fun closeSheet() {
        if (!sheetOpen) return
        sheetOpen = false
        stopVoiceListening()
        clearVoiceBubble()
        updateBack()
        chatPanel.animate()
            .translationY(chatPanel.height.toFloat())
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { sheetContainer.isVisible = false }
            .start()
        scrim.animate().alpha(0f).setDuration(200)
            .withEndAction { scrim.isVisible = false }
            .start()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    // ---------- Voice input (long-press the floating chat ring) ----------

    private fun startVoiceCommand() {
        openSheet()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage(you = false, text = getString(R.string.voice_unavailable))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginVoiceListening()
    }

    private fun beginVoiceListening() {
        stopVoiceListening()
        clearVoiceBubble()
        voiceActive = true
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speech = recognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!voiceActive) return
                voiceBubble = addMessage(you = false, text = getString(R.string.voice_listening))
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (!voiceActive) return
                setVoiceBubbleText(getString(R.string.voice_processing))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!voiceActive) return
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!heard.isNullOrBlank()) setVoiceBubbleText("🎤 $heard")
            }

            override fun onResults(results: Bundle?) {
                if (!voiceActive) return
                voiceActive = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                clearVoiceBubble()
                stopVoiceListening()
                if (heard.isBlank()) {
                    addMessage(you = false, text = getString(R.string.voice_not_heard))
                } else {
                    input.setText(heard)
                    sendMessage()
                }
            }

            override fun onError(error: Int) {
                if (!voiceActive) return
                voiceActive = false
                clearVoiceBubble()
                stopVoiceListening()
                val res = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    R.string.voice_need_permission
                } else {
                    R.string.voice_not_heard
                }
                addMessage(you = false, text = getString(res))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun setVoiceBubbleText(text: String) {
        val item = voiceBubble as? ViewGroup ?: return
        (item.getChildAt(0) as? TextView)?.text = text
    }

    private fun clearVoiceBubble() {
        val item = voiceBubble ?: return
        (item.parent as? ViewGroup)?.removeView(item)
        voiceBubble = null
    }

    private fun stopVoiceListening() {
        voiceActive = false
        speech?.cancel()
        speech?.destroy()
        speech = null
    }

    // ---------- AI flow ----------

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || generating) return
        input.setText("")
        // Typing /setting (or /settings) opens the AI settings dialog.
        if (text.equals("/setting", ignoreCase = true) || text.equals("/settings", ignoreCase = true)) {
            showSettings()
            return
        }
        // Typing /clear wipes the whole conversation.
        if (text.equals("/clear", ignoreCase = true)) {
            history.clear()
            messagesContainer.removeAllViews()
            addMessage(you = false, text = getString(R.string.chat_cleared))
            return
        }
        lastPrompt = text
        addMessage(you = true, text = text)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val base = prefs.getString("base", AiClient.DEFAULT_BASE).orEmpty().ifBlank { AiClient.DEFAULT_BASE }
        val model = prefs.getString("model", AiClient.DEFAULT_MODEL).orEmpty().ifBlank { AiClient.DEFAULT_MODEL }
        val key = prefs.getString("key", "").orEmpty()
        if (key.isBlank()) {
            addMessage(you = false, text = getString(R.string.no_api_key))
            return
        }

        generating = true
        sendButton.isEnabled = false
        sendButton.alpha = 0.5f
        val thinking = addMessage(you = false, text = getString(R.string.thinking))
        history += "user" to text
        val maxContext = prefs.getInt("maxContext", DEFAULT_MAX_CONTEXT).coerceIn(2, 200)
        while (history.size > maxContext) history.removeAt(0)

        AiClient.chat(base, key, model, history) { result ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                generating = false
                sendButton.isEnabled = true
                sendButton.alpha = 1f
                messagesContainer.removeView(thinking)
                result.onSuccess { reply ->
                    history += "assistant" to reply
                    handleReply(reply)
                }.onFailure { e ->
                    addMessage(you = false, text = getString(R.string.error_prefix) + (e.message ?: "unknown error"))
                }
            }
        }
    }

    private fun handleReply(reply: String) {
        val urlMatch = openUrlRegex.find(reply)
        val codeMatches = codeBlockRegex.findAll(reply).toList()

        var visible = reply
        codeMatches.forEach { visible = visible.replace(it.value, "") }
        urlMatch?.let { visible = visible.replace(it.value, "") }
        visible = visible.replace("**", "").replace("`", "").trim()
        if (visible.isNotEmpty()) addMessage(you = false, text = visible)

        val html = codeMatches.firstOrNull()?.groupValues?.get(1)?.trim()
        val url = urlMatch?.groupValues?.get(1)?.trimEnd(',', '.', ')', '"')
        when {
            !html.isNullOrEmpty() -> showPreview(html = html)
            url != null -> showPreview(url = url)
        }
    }

    // ---------- Ad blocker ----------

    private fun setAdBlock(on: Boolean) {
        adBlockEnabled = on
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("adblock", on).apply()
        applyAdBlockScript()
        Log.d("AnyQA", "adblock=$on")
    }

    // ---------- Built-in YouTube ad skipper (part of the ad blocker) ----------

    /**
     * Injected at document start on YouTube while the ad blocker is enabled. YouTube video
     * ads stream from the same domains as the video itself, so host blocking cannot stop
     * them; instead the player API response is pruned of its ad fields before the page
     * reads it, with a fast-forward/skip fallback for any ad that still starts.
     */
    private fun applyAdBlockScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        ytAdBlockHandle?.let { runCatching { it.remove() } }
        ytAdBlockHandle = null
        if (!adBlockEnabled) return
        runCatching {
            ytAdBlockHandle = WebViewCompat.addDocumentStartJavaScript(
                webView, YT_ADBLOCK_JS,
                setOf(
                    "https://youtube.com", "https://*.youtube.com",
                    "http://youtube.com", "http://*.youtube.com"
                )
            )
        }.onFailure { Log.d("AnyQA", "yt adblock failed: $it") }
    }

    private val YT_ADBLOCK_JS = """
        (function () {
          var API = '/youtubei/v1/player';
          function prune(o) {
            if (!o || typeof o !== 'object') return;
            try { delete o.adPlacements; } catch (e) {}
            try { delete o.playerAds; } catch (e) {}
            try { delete o.adSlots; } catch (e) {}
            try { delete o.adBreakHeartbeatParams; } catch (e) {}
            if (o.playerResponse) prune(o.playerResponse);
          }
          function log(s) { try { console.log('[any-ytad] ' + s); } catch (e) {} }

          var of = window.fetch;
          if (of) {
            window.fetch = function () {
              var a = arguments;
              return of.apply(this, a).then(function (res) {
                try {
                  var u = typeof a[0] === 'string' ? a[0] : (a[0] && a[0].url) || '';
                  if (u.indexOf(API) >= 0) {
                    return res.clone().json().then(function (j) {
                      prune(j); log('pruned (fetch)');
                      return new Response(JSON.stringify(j), {
                        status: res.status, statusText: res.statusText, headers: res.headers
                      });
                    }, function () { return res; });
                  }
                } catch (e) {}
                return res;
              });
            };
          }

          var dT = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
          if (dT && dT.get) {
            Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
              configurable: true,
              get: function () {
                var t = dT.get.call(this);
                try {
                  if (this.readyState === 4 && (this.__anyU || '').indexOf(API) >= 0 &&
                      typeof t === 'string' && t.indexOf('{') === 0) {
                    if (!this.__anyP) {
                      this.__anyP = true;
                      var j = JSON.parse(t); prune(j); this.__anyT = JSON.stringify(j);
                      log('pruned (xhr text)');
                    }
                    if (this.__anyT) return this.__anyT;
                  }
                } catch (e) {}
                return t;
              }
            });
          }
          var dR = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
          if (dR && dR.get) {
            Object.defineProperty(XMLHttpRequest.prototype, 'response', {
              configurable: true,
              get: function () {
                var r = dR.get.call(this);
                try {
                  if (this.readyState === 4 && (this.__anyU || '').indexOf(API) >= 0 &&
                      r && typeof r === 'object' && !this.__anyP) {
                    this.__anyP = true; prune(r); log('pruned (xhr json)');
                  }
                } catch (e) {}
                return r;
              }
            });
          }
          var oo = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (m, u) {
            try { this.__anyU = String(u); } catch (e) {}
            return oo.apply(this, arguments);
          };

          function tick() {
            try {
              var p = document.querySelector('.html5-video-player');
              if (p && p.classList.contains('ad-showing')) {
                var v = document.querySelector('video');
                if (v && isFinite(v.duration) && v.duration > 0 &&
                    v.currentTime < v.duration - 0.5) {
                  v.currentTime = v.duration; log('ad skipped (jump)');
                } else if (v && v.paused) { v.play(); }
              }
              var s = document.querySelector(
                '.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern'
              );
              if (s && s.offsetParent !== null) { s.click(); log('skip clicked'); }
              var o = document.querySelector('.ytp-ad-overlay-close-button');
              if (o) o.click();
            } catch (e) {}
          }
          setInterval(tick, 400);

          try {
            var st = document.createElement('style');
            st.textContent = 'ytm-promoted-video-renderer,ytm-compact-promoted-video-renderer,' +
              'ytm-promoted-sparkles-web-renderer,ytm-display-ad-renderer,ytm-ad-slot-renderer,' +
              'ytm-companion-slot-renderer{display:none !important}';
            document.documentElement.appendChild(st);
          } catch (e) {}
        })();
    """.trimIndent()

    // ---------- Fullscreen preview ----------

    private fun showPreview(html: String? = null, url: String? = null) {
        previewContainer.isVisible = true
        previewContainer.alpha = 0f
        previewContainer.animate().alpha(1f).setDuration(200).start()
        if (url != null) {
            addTask(hostOf(url), getString(R.string.loading_dots))
            webView.loadUrl(url)
        } else {
            addTask(HOST_MINIAPP, lastPrompt.ifBlank { "แอพใหม่" }.take(48))
            webView.loadDataWithBaseURL("https://app.local/", html.orEmpty(), "text/html", "UTF-8", null)
        }
        hasContent = true
        renderRunning()
        startVideoPolling()
        updatePipState()
        if (sheetOpen) closeSheet()
        updateBack()
    }

    private fun hidePreview() {
        webView.stopLoading()
        previewContainer.isVisible = false
        updatePipState()
        updateBack()
    }

    private fun updateBack() {
        backCallback.isEnabled = sheetOpen || previewContainer.isVisible || customView != null
    }

    /** Leaves HTML5 video fullscreen and hands the custom view back to the WebView. */
    private fun exitVideoFullscreen() {
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        hideStatusBar()
    }

    // ---------- Picture-in-Picture ----------

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    private fun updatePipState() {
        if (Build.VERSION.SDK_INT < 26) return
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(
                hasContent && previewContainer.isVisible && videoActive && lastPlaying
            )
        }
        if (videoActive) builder.setActions(buildPipActions())
        runCatching { setPictureInPictureParams(builder.build()) }
    }

    private fun buildPipActions(): List<RemoteAction> {
        fun pi(action: String, code: Int): PendingIntent = PendingIntent.getActivity(
            this,
            code,
            Intent(this, MainActivity::class.java)
                .putExtra("pipAction", action)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val back = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_prev),
            "ย้อนกลับ 10 วิ", "ย้อนกลับ 10 วิ", pi("back", 9901)
        )
        val toggle = RemoteAction(
            Icon.createWithResource(this, if (lastPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play),
            "เล่น/หยุด", "เล่นหรือหยุดชั่วคราว", pi("toggle", 9902)
        )
        val fwd = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_next),
            "ไปข้างหน้า 10 วิ", "ไปข้างหน้า 10 วิ", pi("fwd", 9903)
        )
        return listOf(back, toggle, fwd)
    }

    private fun handlePipAction(action: String) {
        when (action) {
            "toggle" -> {
                evalJs(PLAY_PAUSE_JS)
                lastPlaying = !lastPlaying
                updatePipState()
                ui.postDelayed({ pollOnce() }, 400)
            }
            "back" -> {
                evalJs(SEEK_BACK_JS)
                ui.postDelayed({ pollOnce() }, 250)
            }
            "fwd" -> {
                evalJs(SEEK_FWD_JS)
                ui.postDelayed({ pollOnce() }, 250)
            }
        }
    }

    // ---------- Settings dialog ----------

    private fun showSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedProvider = prefs.getInt("provider", 0).coerceIn(0, providers.size - 1)
        val pad = dp(20)

        fun lp() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(6), pad, dp(4))
            minimumWidth = dp(280)
        }

        val providerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                providers.map { it.name }
            )
        }

        val baseInput = EditText(this).apply {
            hint = getString(R.string.base_url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(prefs.getString("base", AiClient.DEFAULT_BASE))
        }
        val modelInput = EditText(this).apply {
            hint = getString(R.string.model)
            setSingleLine()
            setText(prefs.getString("model", AiClient.DEFAULT_MODEL))
        }
        val keyInput = EditText(this).apply {
            hint = getString(R.string.api_key)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setText(prefs.getString("key", ""))
        }
        val maxCtxInput = EditText(this).apply {
            hint = getString(R.string.max_context)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("maxContext", DEFAULT_MAX_CONTEXT).toString())
        }
        val adBlockCheck = CheckBox(this).apply {
            setText(R.string.adblock_label)
            isChecked = prefs.getBoolean("adblock", true)
        }

        var lastProvider = savedProvider
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == lastProvider) return
                lastProvider = position
                val p = providers[position]
                if (p.base.isNotEmpty()) {
                    baseInput.setText(p.base)
                    modelInput.setText(p.model)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        providerSpinner.setSelection(savedProvider)

        col.addView(TextView(this).apply {
            setText(R.string.provider)
            textSize = 12f
            setTextColor(getColor(R.color.icon_muted))
        })
        val modelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modelRow.addView(
            modelInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        modelRow.addView(
            Button(this).apply {
                setText(R.string.search)
                setOnClickListener {
                    showModelPicker(
                        baseInput.text.toString().trim(),
                        keyInput.text.toString().trim()
                    ) { picked -> modelInput.setText(picked) }
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        )

        col.addView(providerSpinner)
        col.addView(baseInput, lp())
        col.addView(modelRow, lp())
        col.addView(keyInput, lp())
        col.addView(TextView(this).apply {
            setText(R.string.max_context)
            textSize = 12f
            setTextColor(getColor(R.color.icon_muted))
        }, lp())
        col.addView(maxCtxInput)
        col.addView(adBlockCheck, lp())

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.edit()
                    .putInt("provider", lastProvider)
                    .putString("base", baseInput.text.toString().trim().ifBlank { AiClient.DEFAULT_BASE })
                    .putString("model", modelInput.text.toString().trim().ifBlank { AiClient.DEFAULT_MODEL })
                    .putString("key", keyInput.text.toString().trim())
                    .putInt(
                        "maxContext",
                        (maxCtxInput.text.toString().toIntOrNull() ?: DEFAULT_MAX_CONTEXT).coerceIn(2, 200)
                    )
                    .putBoolean("adblock", adBlockCheck.isChecked)
                    .apply()
                adBlockEnabled = adBlockCheck.isChecked
                addMessage(you = false, text = getString(R.string.saved))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showModelPicker(baseUrl: String, apiKey: String, onPick: (String) -> Unit) {
        if (baseUrl.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.model_search)
                .setMessage(R.string.models_need_base)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val loading = AlertDialog.Builder(this)
            .setMessage(R.string.models_loading)
            .setCancelable(false)
            .create()
        loading.show()

        AiClient.listModels(baseUrl, apiKey) { result ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                loading.dismiss()
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.model_search)
                            .setMessage(R.string.models_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        return@onSuccess
                    }

                    val filter = EditText(this).apply {
                        hint = getString(R.string.models_filter_hint)
                        setSingleLine()
                        inputType = InputType.TYPE_CLASS_TEXT
                    }
                    val list = ListView(this)
                    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList(models))
                    list.adapter = adapter
                    list.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(320)
                    )

                    lateinit var picker: AlertDialog
                    list.setOnItemClickListener { _, _, position, _ ->
                        val picked = list.getItemAtPosition(position) as? String
                        if (picked != null) {
                            onPick(picked)
                            picker.dismiss()
                        }
                    }
                    filter.doAfterTextChanged { text ->
                        val q = text?.toString()?.trim().orEmpty()
                        adapter.clear()
                        if (q.isEmpty()) {
                            adapter.addAll(models)
                        } else {
                            adapter.addAll(models.filter { m -> m.contains(q, ignoreCase = true) })
                        }
                        adapter.notifyDataSetChanged()
                    }

                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(8), dp(16), 0)
                    }
                    col.addView(filter)
                    col.addView(list)

                    picker = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.model_search) + " (" + models.size + ")")
                        .setView(col)
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                    picker.show()
                }.onFailure { e ->
                    AlertDialog.Builder(this)
                        .setTitle(R.string.model_search)
                        .setMessage(getString(R.string.error_prefix) + (e.message ?: "unknown error"))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    // ---------- Message bubbles ----------

    private fun addMessage(you: Boolean, text: String): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val bubble = TextView(this).apply {
            setText(text)
            textSize = 15f
            setTextColor(getColor(if (you) R.color.white else R.color.text_primary_dark))
            setBackgroundResource(if (you) R.drawable.bubble_you else R.drawable.bubble_assistant)
            setPadding(dp(15), dp(11), dp(15), dp(11))
            maxWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (you) Gravity.END else Gravity.START
            }
        }

        item.addView(bubble)
        messagesContainer.addView(item)
        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
        return item
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val HOST_MINIAPP = "miniapp"
        private const val DEFAULT_MAX_CONTEXT = 40

        // Injected at document start in every frame: pretend the page is always
        // visible (so players like YouTube do not pause on screen-off/background)
        // plus a guard that undoes site-initiated pauses while the app is hidden.
        private const val KEEP_ALIVE_JS =
            "(function(){try{" +
                "Object.defineProperty(document,'hidden',{configurable:true,get:function(){return false;}});" +
                "Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return 'visible';}});" +
                "Object.defineProperty(document,'webkitHidden',{configurable:true,get:function(){return false;}});" +
                "Object.defineProperty(document,'webkitVisibilityState',{configurable:true,get:function(){return 'visible';}});" +
                "document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);" +
                "window.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);" +
                "window.__anyForcePlay=false;" +
                "document.addEventListener('pause',function(e){try{" +
                "if(!window.__anyForcePlay)return;" +
                "var v=e.target;" +
                "if(v&&(v.tagName==='VIDEO'||v.tagName==='AUDIO')&&!v.ended){" +
                "setTimeout(function(){try{v.play()}catch(err){}},400);}" +
                "}catch(err){}},true);" +
                "}catch(e){}})();"

        private const val VIDEO_JS =
            "(function(){var v=document.querySelector('video');" +
                "if(!v||!isFinite(v.duration)||v.duration<=0)return null;" +
                "return JSON.stringify({t:v.currentTime||0,d:v.duration,p:!!v.paused,title:document.title||''});})()"

        private const val PLAY_PAUSE_JS =
            "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()"

        private const val SEEK_BACK_JS =
            "(function(){var v=document.querySelector('video');if(v){v.currentTime=Math.max(0,v.currentTime-10);}})()"

        private const val SEEK_FWD_JS =
            "(function(){var v=document.querySelector('video');if(v){v.currentTime=Math.min(v.duration||1e9,v.currentTime+10);}})()"
    }
}
