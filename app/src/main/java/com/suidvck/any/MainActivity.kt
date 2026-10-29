package com.suidvck.any

import android.Manifest
import android.app.DownloadManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.features.ai.AiMessage
import com.suidvck.any.features.ai.AiPrompts
import com.suidvck.any.features.ai.WebSearch
import com.suidvck.any.features.browser.BrowserMenuSheet
import com.suidvck.any.features.browser.FindBar
import com.suidvck.any.features.browser.PageAssistant
import com.suidvck.any.features.browser.WebTools
import com.suidvck.any.features.chat.AttachmentPicker
import com.suidvck.any.features.chat.ChatHistorySheet
import com.suidvck.any.features.chat.VoiceMode
import com.suidvck.any.features.data.AutomationStore
import com.suidvck.any.features.data.ChatStore
import com.suidvck.any.features.data.Conversation
import com.suidvck.any.features.data.HistoryStore
import com.suidvck.any.features.data.MiniApp
import com.suidvck.any.features.data.MiniAppStore
import com.suidvck.any.features.data.StoredMessage
import com.suidvck.any.features.data.TabStore
import com.suidvck.any.features.home.Appearance
import com.suidvck.any.features.home.DockView
import com.suidvck.any.features.home.Updater
import com.suidvck.any.features.library.LibrarySheet
import com.suidvck.any.features.library.RecentAppsRow
import com.suidvck.any.features.system.AppLock
import com.suidvck.any.features.system.AutomationScheduler
import com.suidvck.any.features.system.AutomationSheet
import com.suidvck.any.features.system.AutomationWorker
import com.suidvck.any.features.system.ExtrasMenu
import com.suidvck.any.ui.AnyChrome
import com.suidvck.any.ui.ChoiceSheet
import com.suidvck.any.ui.FaIcons
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

    // Extras (library, chats, browser tabs, voice, attachments).
    private lateinit var miniApps: MiniAppStore
    private lateinit var chatStore: ChatStore
    private lateinit var browserTabs: TabStore
    private lateinit var browserHistory: HistoryStore
    private lateinit var voiceMode: VoiceMode
    private lateinit var dockView: DockView
    private var recentAppsRow: RecentAppsRow? = null
    private var findBar: FindBar? = null
    private var webToolsRow: LinearLayout? = null
    private var currentAppId: String? = null
    private var editMode = false
    private var pendingImage: String? = null
    private var editChip: TextView? = null
    private var attachChip: TextView? = null
    private var micChip: TextView? = null
    private var handsFree = false
    private var appUnlocked = false

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

    /** True only for debug builds; QA-only intent hooks must never run in a release build. */
    private val isDebuggable: Boolean by lazy {
        (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

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

    private val codeBlockRegex = Regex("```(html-overlay|html)?[ \\t]*\\r?\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val openUrlRegex = Regex("OPEN_URL:\\s*(https?://\\S+)")

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (findBar?.isOpen() == true) findBar?.close()
            else if (customView != null) exitVideoFullscreen()
            else if (sheetOpen) closeSheet()
            else if (overlayPanel?.isVisible == true) hideOverlay()
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

        miniApps = MiniAppStore(this)
        chatStore = ChatStore(this)
        browserTabs = TabStore(this)
        browserHistory = HistoryStore(this)
        voiceMode = VoiceMode(this) { heard ->
            if (!isFinishing) {
                openSheet()
                input.setText(heard)
                sendMessage()
            }
        }
        voiceMode.onSpeechDone = {
            if (handsFree && sheetOpen && !generating) {
                root.postDelayed({ voiceMode.startListening() }, 350L)
            }
        }

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
        clockText.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            Appearance.showSheet(this)
            true
        }
        ui.post(clockTick)

        Appearance.applyTo(this)
        setupChatExtras()
        setupRecentApps()

        val previewFab: FloatingActionButton = findViewById(R.id.fab_chat_preview)
        val composeBox: TextView = findViewById(R.id.compose_box)
        val btnPrev: ImageButton = findViewById(R.id.btn_prev)
        val btnNext: ImageButton = findViewById(R.id.btn_next)
        val btnYt: View = findViewById(R.id.btn_yt)
        val btnHome: ImageButton = findViewById(R.id.btn_home)
        val btnMore: ImageButton = findViewById(R.id.btn_more)
        val btnAttach: ImageButton = findViewById(R.id.btn_attach)

        previewFab.setOnClickListener { openSheet() }
        makeFabDraggable(previewFab)
        scrim.setOnClickListener { closeSheet() }
        sendButton.setOnClickListener { sendMessage() }
        composeBox.setOnClickListener { openSheet() }
        btnAttach.setOnClickListener { AttachmentPicker.launch(this) }
        btnMore.setOnClickListener { showExtras() }
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
        btnHome.setOnClickListener { goHome() }

        dockView = DockView(this)
        findViewById<FrameLayout>(R.id.dock_host)?.addView(
            dockView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )
        dockView.bind { item -> if (item.url.isNotBlank()) showPreview(url = item.url) }

        setupHomeSwipe()

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

        AutomationScheduler.rescheduleAll(this)
        Updater.check(this, silent = true)

        // Automation notification taps are a normal product feature and must work in a release
        // build, unlike the QA hooks below. A tap is only acted on when its automation still
        // exists and is enabled.
        root.postDelayed({
            if (!handleAutomationIntent(intent)) handleQaExtras(intent)
        }, 450)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AttachmentPicker.REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val url = AttachmentPicker.read(this, data)
                if (url == null) {
                    Toast.makeText(this, "อ่านรูปไม่ได้", Toast.LENGTH_SHORT).show()
                } else {
                    setPendingImage(url)
                }
            }
            return
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isDebuggable) Log.d("AnyQA", "onNewIntent extras=${intent.extras}")
        if (!handleAutomationIntent(intent)) handleQaExtras(intent)
    }

    /**
     * Runs the action behind a tapped automation notification.
     *
     * Returns true when the intent belonged to an automation (whether or not it still exists),
     * so the caller does not also feed the same extras to the QA-only handler. The stored
     * automation is re-checked here: a notification can outlive the automation it was posted
     * for, and tapping it must then do nothing at all.
     */
    private fun handleAutomationIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val id = intent.getStringExtra(AutomationWorker.EXTRA_AUTOMATION_ID)?.trim()
        if (id.isNullOrEmpty()) return false

        // Consume the extras so a re-delivered intent (rotation, task restore) cannot fire twice.
        intent.removeExtra(AutomationWorker.EXTRA_AUTOMATION_ID)
        intent.removeExtra(AutomationWorker.EXTRA_OPEN_URL)
        intent.removeExtra(AutomationWorker.EXTRA_OPEN_CHAT)
        intent.removeExtra(AutomationWorker.EXTRA_AUTO_PROMPT)
        intent.removeExtra(AutomationWorker.EXTRA_OPEN_APP_ID)
        // Same keys are also read by the debug QA handler; clear them too.
        intent.removeExtra("openUrl")
        intent.removeExtra("openChat")
        intent.removeExtra("autoPrompt")
        intent.removeExtra("openAppId")

        if (isFinishing) return true
        val automation = runCatching {
            AutomationStore(this).list().firstOrNull { it.id == id }
        }.getOrNull()
        if (automation == null || !automation.enabled) {
            Log.i("AnyAutomation", "automation $id is gone or disabled; ignoring notification tap")
            return true
        }
        when (automation.kind) {
            AutomationWorker.KIND_URL -> {
                val url = httpUrlOnly(automation.value)
                if (url != null) root.post { showPreview(url = url) }
            }
            AutomationWorker.KIND_CHAT -> root.post { openSheet() }
            AutomationWorker.KIND_PROMPT -> root.post {
                openSheet()
                input.setText(automation.value)
                sendMessage()
            }
            AutomationWorker.KIND_APP -> root.post {
                miniApps.get(automation.value)?.let { showApp(it) }
            }
        }
        return true
    }

    private fun handleQaExtras(intent: Intent?) {
        if (intent == null || isFinishing) return
        // These hooks can inject JavaScript, load any URL and spend the user's API credits.
        // They exist for on-device QA only and must never run in a release build, where the
        // activity is exported and any installed app could send them.
        if (!isDebuggable) return
        Log.d("AnyQA", "handleQaExtras extras=${intent.extras}")
        val autoPrompt = intent.getStringExtra("autoPrompt")
        val openUrl = httpUrlOnly(intent.getStringExtra("openUrl"))
        val overlayHtml = intent.getStringExtra("overlayHtml")
        val navTest = intent.getStringExtra("navTest")
        val openAppId = intent.getStringExtra("openAppId")
        val editAppId = intent.getStringExtra("editAppId")
        val editDock = intent.getIntExtra("editDock", -1)
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
            !overlayHtml.isNullOrBlank() -> root.post { showOverlay(overlayHtml) }
            !openAppId.isNullOrBlank() -> root.post { miniApps.get(openAppId)?.let { showApp(it) } }
            !editAppId.isNullOrBlank() -> root.post { openLibrary(editAppId) }
            editDock >= 0 -> root.post {
                hidePreview()
                dockView.openEditorFor(editDock)
            }
            intent.getBooleanExtra("openLibrary", false) -> root.post { openLibrary() }
            intent.getBooleanExtra("chatHistory", false) -> root.post { openChatHistory() }
            intent.getBooleanExtra("pageAssistant", false) -> root.post { openPageAssistant() }
            intent.getBooleanExtra("findBar", false) -> root.post { toggleFindBar() }
            intent.getBooleanExtra("editMode", false) -> root.post {
                editMode = true
                updateEditChip()
                openSheet()
            }
            intent.getBooleanExtra("showExtras", false) -> root.post { showExtras() }
            intent.getBooleanExtra("browserMenu", false) -> root.post { openBrowserMenu() }
            intent.getBooleanExtra("showAutomations", false) -> root.post {
                ensureNotificationPermission()
                AutomationSheet.show(this)
            }
            intent.hasExtra("grayscale") -> root.post {
                Appearance.setGrayscale(this, intent.getBooleanExtra("grayscale", true))
            }
            intent.getBooleanExtra("appearanceMenu", false) -> root.post { Appearance.showSheet(this) }
            intent.getBooleanExtra("timeSheet", false) -> root.post {
                com.suidvck.any.ui.TimeSheet.show(this, 9, 30) { _, _ -> }
            }
            intent.getBooleanExtra("checkUpdate", false) -> root.post { Updater.check(this, false) }
            intent.getBooleanExtra("showHome", false) -> root.post { hidePreview() }
            intent.getBooleanExtra("reopenLast", false) -> root.post { reopenLastPage() }
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
        if (!appUnlocked) {
            if (AppLock.isEnabled(this)) {
                root.alpha = 0f
                AppLock.unlock(this) {
                    appUnlocked = true
                    root.alpha = 1f
                }
            } else {
                appUnlocked = true
            }
        }
        refreshRecentApps()
        evalJs("window.__anyForcePlay=false")
        evalJs(CLEAR_PIP_ZOOM_JS)
        if (hasContent && previewContainer.isVisible) startVideoPolling()
    }

    override fun onStop() {
        super.onStop()
        // Screen off / app backgrounded: stop polling (nothing to update) but keep
        // whatever was playing alive if the site tries to pause itself.
        stopVideoPolling()
        if (hasContent && lastPlaying) {
            evalJs(
                "window.__anyForcePlay=true;var v=document.querySelector('video,audio');" +
                    "if(v&&v.paused&&!v.ended){try{v.play()}catch(e){}}"
            )
        }
        // Release the mic if the app goes fully hidden mid-listening.
        stopVoiceListening()
        clearVoiceBubble()
        handsFree = false
        if (::voiceMode.isInitialized) runCatching { voiceMode.stopListening() }
        if (::chatStore.isInitialized) chatStore.saveAllNow()
    }

    override fun onDestroy() {
        // A repeating postDelayed runnable keeps the destroyed activity (and its whole view
        // tree) reachable for as long as it keeps reposting itself.
        ui.removeCallbacks(clockTick)
        stopVideoPolling()
        stopVoiceListening()
        if (::voiceMode.isInitialized) runCatching { voiceMode.release() }
        if (::chatStore.isInitialized) chatStore.saveAllNow()

        // WebView holds a renderer process, JS timers and native callbacks. Without an explicit
        // destroy every recreation (theme change, process restart) leaks one.
        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.setDownloadListener(null) }
            runCatching { webView.webChromeClient = null }
            runCatching { ytAdBlockHandle?.remove() }
            ytAdBlockHandle = null
            runCatching { overlayPanel?.hide() }
            runCatching { webView.removeAllViews() }
            runCatching { webView.destroy() }
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Below Android 12, enter Picture-in-Picture when leaving with a video playing.
        if (Build.VERSION.SDK_INT < 31 && hasContent && previewContainer.isVisible && videoActive && lastPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        Log.d("AnyQA", "pip=$inPip video=$videoActive playing=$lastPlaying custom=${customView != null}")
        findViewById<View>(R.id.fab_chat_preview)?.visibility = when {
            inPip -> View.GONE
            previewContainer.isVisible -> View.VISIBLE
            else -> View.GONE
        }
        updateWebToolsVisibility()
        if (inPip) {
            // Don't let the overlay float inside the small PiP window.
            if (overlayPanel?.isVisible == true) {
                overlayHiddenForPip = true
                overlayPanel?.hide()
            }
            if (sheetOpen) closeSheet()
            // Video-only PiP: if the video is still inline, ask it to go HTML5 fullscreen —
            // the app renders that view alone, so the PiP window shows just the video.
            if (customView == null && videoActive && lastPlaying) {
                ui.postDelayed({
                    if (isInPictureInPictureMode && customView == null && videoActive && lastPlaying) {
                        webView.evaluateJavascript(PIP_FULLSCREEN_JS) { r ->
                            Log.d("AnyQA", "pip fullscreen -> $r")
                        }
                        // If fullscreen could not engage, zoom the page so the PiP
                        // window still shows only the video.
                        ui.postDelayed({
                            if (isInPictureInPictureMode && customView == null && videoActive) {
                                webView.evaluateJavascript(PIP_ZOOM_JS) { r2 ->
                                    Log.d("AnyQA", "pip zoom -> $r2")
                                }
                            }
                        }, 900)
                    }
                }, 300)
            }
        } else {
            // Leaving PiP: bring back the overlay and undo the CSS fallback zoom.
            if (overlayHiddenForPip) {
                overlayHiddenForPip = false
                overlayPanel?.reveal()
                placeOverlay(aboveAll = customView != null)
            }
            evalJs(CLEAR_PIP_ZOOM_JS)
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
        applyWebToolsPrefs()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        WebView.setWebContentsDebuggingEnabled(isDebuggable)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Fool pages (e.g. YouTube) into thinking they stay visible, so they do
            // not pause playback when the app is hidden or the screen turns off.
            WebViewCompat.addDocumentStartJavaScript(webView, KEEP_ALIVE_JS, setOf("*"))
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val u = url ?: return
                if (!u.startsWith("http") || u.contains(".anyapp.local")) return
                browserTabs.open(u, null, null)
                currentAppId = null
                editMode = false
                updateEditChip()
                Log.d("AnyQA", "page start $u")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url ?: return
                if (!u.startsWith("http") || u.contains(".anyapp.local")) return
                val title = view?.title?.takeIf { it.isNotBlank() } ?: hostOf(u)
                browserTabs.updateTitle(title.take(80))
                browserHistory.record(u, title.take(120))
            }

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
                // Keep the AI overlay floating above the fullscreen video.
                if (overlayPanel?.isVisible == true) placeOverlay(aboveAll = true)
            }

            override fun onHideCustomView() = exitVideoFullscreen()
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimeType, contentLength)
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

    private fun stopVideoPolling() {
        polling = false
        ui.removeCallbacks(videoPoll)
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
        updateWebToolsVisibility()
        if (!greeted) {
            greeted = true
            val conv = chatStore.current()
            if (conv.messages.isNotEmpty()) renderConversation(conv)
            else addMessage(you = false, text = getString(R.string.greeting))
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
        handsFree = false
        micChip?.alpha = 0.5f
        if (::voiceMode.isInitialized) voiceMode.stopListening()
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
                if (!heard.isNullOrBlank()) setVoiceBubbleText(heard)
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
        // Slash commands are handled before the "already generating" guard so they never look
        // like a dead button while a reply is streaming.
        if (text.equals("/setting", ignoreCase = true) || text.equals("/settings", ignoreCase = true)) {
            input.setText("")
            showSettings()
            return
        }
        if (text.equals("/clear", ignoreCase = true)) {
            if (generating) {
                Toast.makeText(this, R.string.chat_busy, Toast.LENGTH_SHORT).show()
                return
            }
            input.setText("")
            history.clear()
            messagesContainer.removeAllViews()
            chatStore.clearCurrent()
            setPendingImage(null)
            greeted = true
            addMessage(you = false, text = getString(R.string.chat_cleared))
            return
        }
        if ((text.isEmpty() && pendingImage == null) || generating) return

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val base = prefs.getString("base", AiClient.DEFAULT_BASE).orEmpty().ifBlank { AiClient.DEFAULT_BASE }
        val model = prefs.getString("model", AiClient.DEFAULT_MODEL).orEmpty().ifBlank { AiClient.DEFAULT_MODEL }
        val key = prefs.getString("key", "").orEmpty()
        if (key.isBlank()) {
            addMessage(you = false, text = getString(R.string.no_api_key))
            return
        }

        // Resolve the edit target exactly once, at send time. Reading currentAppId when the
        // reply arrives is unsafe: the user may have opened another app in the meantime.
        val editingId = if (editMode) currentAppId else null
        val editApp = editingId?.let { miniApps.get(it) }
        if (editApp != null && editApp.html.length > MAX_EDIT_HTML) {
            addMessage(
                you = false,
                text = getString(R.string.edit_too_large, MAX_EDIT_HTML / 1000)
            )
            return
        }

        input.setText("")
        val image = pendingImage
        setPendingImage(null)
        val promptText = text.ifBlank { "อธิบายรูปนี้" }
        lastPrompt = promptText
        val shownText = if (image != null) "[รูปภาพ] $promptText".trim() else promptText
        addMessage(you = true, text = shownText)
        chatStore.append(StoredMessage("user", promptText, image))
        if (handsFree) voiceMode.stopListening()

        // When editing a saved app, send its current HTML as extra system context.
        val systemExtra = editApp?.let { AiPrompts.editSystem(it.html) }

        generating = true
        sendButton.isEnabled = false
        sendButton.alpha = 0.5f
        val thinking = addMessage(you = false, text = getString(R.string.thinking))
        val thinkingText = (thinking as ViewGroup).getChildAt(0) as TextView
        history += "user" to promptText
        val maxContext = prefs.getInt("maxContext", DEFAULT_MAX_CONTEXT).coerceIn(2, 200)
        while (history.size > maxContext) history.removeAt(0)

        val aiHistory = if (editApp != null) {
            listOf(AiMessage("user", promptText, image))
        } else {
            history.mapIndexed { index, (role, content) ->
                if (index == history.lastIndex && image != null) AiMessage(role, content, image)
                else AiMessage(role, content)
            }
        }
        val webSearchEnabled = prefs.getBoolean("web_search", true)
        var workingHistory = aiHistory
        var searchRounds = 0

        fun finishRequest() {
            generating = false
            sendButton.isEnabled = true
            sendButton.alpha = 1f
            messagesContainer.removeView(thinking)
        }

        fun failRequest(message: String) {
            if (isFinishing) return
            finishRequest()
            addMessage(you = false, text = getString(R.string.error_prefix) + message)
        }

        fun askAi(extra: String?) {
            AiClient.streamChat(
                baseUrl = base,
                apiKey = key,
                model = model,
                systemExtra = extra,
                history = workingHistory,
                webSearch = webSearchEnabled,
                onDelta = { full ->
                    if (!isFinishing) {
                        val trimmed = full.trimStart()
                        thinkingText.text = if (
                            trimmed.startsWith("SEARCH:", ignoreCase = true)
                        ) {
                            getString(R.string.web_search_checking)
                        } else {
                            full.ifBlank { getString(R.string.thinking) }
                        }
                        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                },
                onDone = { full ->
                    if (isFinishing) return@streamChat
                    val query = if (webSearchEnabled) WebSearch.extractQuery(full) else null
                    if (query != null) {
                        if (searchRounds >= 2) {
                            failRequest(getString(R.string.web_search_limit))
                            return@streamChat
                        }
                        searchRounds++
                        workingHistory =
                            workingHistory + AiMessage("assistant", "SEARCH: $query")
                        thinkingText.text = getString(R.string.web_searching, query)
                        WebSearch.search(query) { result ->
                            if (isFinishing) return@search
                            val note = result.fold(
                                onSuccess = { hits ->
                                    if (hits.isEmpty()) {
                                        "Web search returned no results. Answer the user's " +
                                            "latest message from your own knowledge and tell " +
                                            "them that live results were unavailable."
                                    } else {
                                        WebSearch.buildContext(query, hits)
                                    }
                                },
                                onFailure = { error ->
                                    "Web search failed (${error.message ?: "unknown error"}). " +
                                        "Answer the user's latest message from your own " +
                                        "knowledge and tell them that live search was unavailable."
                                }
                            )
                            thinkingText.text = getString(R.string.web_search_reading)
                            askAi(listOfNotNull(extra, note).joinToString("\n\n"))
                        }
                        return@streamChat
                    }
                    finishRequest()
                    if (full.isBlank()) {
                        addMessage(
                            you = false,
                            text = getString(R.string.error_prefix) + "empty reply"
                        )
                    } else {
                        history += "assistant" to full
                        handleReply(full, editingId)
                    }
                },
                onError = { e ->
                    if (isFinishing) return@streamChat
                    failRequest(e.message ?: "unknown error")
                }
            )
        }

        askAi(systemExtra)
    }

    /** True for a fence that should be treated as a mini-app document. */
    private fun isHtmlBlock(match: MatchResult): Boolean {
        when (match.groupValues[1].lowercase()) {
            "html" -> return true
            "html-overlay" -> return false
        }
        // An untagged fence only counts when it clearly holds a document. This keeps
        // ```js / ```python answers in the chat instead of saving them as mini-apps.
        val body = match.groupValues[2].trimStart()
        return body.startsWith("<!DOCTYPE", ignoreCase = true) ||
            body.startsWith("<html", ignoreCase = true)
    }

    /** Removes the HTML/overlay code blocks and OPEN_URL markers from a reply. */
    private fun visiblePart(reply: String): String {
        var visible = reply
        codeBlockRegex.findAll(reply).forEach { match ->
            val overlay = match.groupValues[1].equals("html-overlay", ignoreCase = true)
            if (overlay || isHtmlBlock(match)) visible = visible.replace(match.value, "")
        }
        openUrlRegex.findAll(reply).forEach { visible = visible.replace(it.value, "") }
        return visible.replace("**", "").replace("`", "").trim()
    }

    private fun handleReply(reply: String, editingId: String?) {
        val urlMatch = openUrlRegex.find(reply)
        val codeMatches = codeBlockRegex.findAll(reply).toList()
        val visible = visiblePart(reply)

        val overlayMatch = codeMatches.firstOrNull {
            it.groupValues[1].equals("html-overlay", ignoreCase = true)
        }
        val overlayHtml = overlayMatch?.groupValues?.get(2)?.trim()
        val fencedHtml = codeMatches.firstOrNull { isHtmlBlock(it) }?.groupValues?.get(2)?.trim()
        // Some models answer with a bare document and no fence at all.
        val bareHtml = if (fencedHtml == null && overlayHtml == null) {
            val trimmed = reply.trim()
            if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)
            ) {
                trimmed
            } else {
                null
            }
        } else {
            null
        }
        val html = fencedHtml ?: bareHtml
        val url = urlMatch?.groupValues?.get(1)?.trimEnd(',', '.', ')', '"')

        if (visible.isNotEmpty()) addMessage(you = false, text = visible)

        when {
            !overlayHtml.isNullOrEmpty() -> showOverlay(overlayHtml)
            !html.isNullOrEmpty() -> {
                // Auto-save: a new reply creates an app, an edit updates the app that was open
                // when the request was sent (not whatever happens to be open now).
                val app = if (editingId != null) miniApps.save(html, id = editingId)
                else miniApps.save(html)
                if (app == null) {
                    addMessage(
                        you = false,
                        text = getString(R.string.error_prefix) + getString(R.string.save_failed)
                    )
                } else {
                    refreshRecentApps()
                    showApp(app)
                }
            }
            url != null -> showPreview(url = url)
        }

        val display = visible.ifBlank {
            when {
                !html.isNullOrEmpty() ->
                    if (editingId != null) "อัปเดตแอพให้แล้ว" else "สร้างแอพให้แล้ว"
                url != null -> "เปิดหน้าที่ขอให้แล้ว"
                !overlayHtml.isNullOrEmpty() -> "แสดงผลให้แล้ว"
                else -> "ดำเนินการแล้ว"
            }
        }
        if (visible.isEmpty()) addMessage(you = false, text = display)
        chatStore.append(StoredMessage("assistant", display))
        chatStore.saveAllNow()

        if (visible.isNotEmpty()) voiceMode.speak(visible)
        if (handsFree && (!voiceMode.speakReplies || visible.isEmpty())) {
            root.postDelayed({
                if (handsFree && sheetOpen && !generating) voiceMode.startListening()
            }, 420L)
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

    // ---------- Home / preview switching ----------

    /** Closes the chat and returns to the home screen from wherever the user is. */
    private fun goHome() {
        if (sheetOpen) closeSheet()
        if (customView != null) exitVideoFullscreen()
        if (previewContainer.isVisible) hidePreview()
    }

    /** Swipe right on the home screen re-opens the last page (AI mini-app or site). */
    private fun setupHomeSwipe() {
        var startX = 0f
        var startY = 0f
        var tracking = false
        mainContent.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX
                    startY = ev.rawY
                    tracking = true
                    true
                }
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP -> {
                    if (tracking) {
                        val dx = ev.rawX - startX
                        val dy = ev.rawY - startY
                        if (dx > dp(72) && dx > Math.abs(dy) * 1.4f) reopenLastPage()
                    }
                    tracking = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracking = false
                    true
                }
                else -> false
            }
        }
    }

    /** Brings the last opened page back on screen without reloading it. */
    private fun reopenLastPage() {
        if (!hasContent || !::webView.isInitialized) return
        previewContainer.isVisible = true
        previewContainer.alpha = 0f
        previewContainer.animate().alpha(1f).setDuration(200).start()
        startVideoPolling()
        updatePipState()
        updateBack()
        updateWebToolsVisibility()
        Log.d("AnyQA", "reopen last page")
    }

    // ---------- Fullscreen preview ----------

    private fun showPreview(html: String? = null, url: String? = null) {
        if (html != null) {
            val app = miniApps.save(html)
            if (app == null) {
                addMessage(
                    you = false,
                    text = getString(R.string.error_prefix) + getString(R.string.save_failed)
                )
            } else {
                showApp(app)
            }
            return
        }
        // Everything that reaches the preview from outside the app (dock, automations, AI
        // replies, intents) goes through the same http(s)-only check as page navigation.
        val target = httpUrlOnly(url)
        if (url != null && target == null) {
            showBlockedToast()
            return
        }
        applyWebToolsPrefs()
        currentAppId = null
        editMode = false
        updateEditChip()
        previewContainer.isVisible = true
        previewContainer.alpha = 0f
        previewContainer.animate().alpha(1f).setDuration(200).start()
        if (target != null) {
            addTask(hostOf(target), getString(R.string.loading_dots))
            browserTabs.open(target, null, hostOf(target))
            webView.loadUrl(target)
        }
        hasContent = true
        renderRunning()
        startVideoPolling()
        updatePipState()
        if (sheetOpen) closeSheet()
        updateBack()
        updateWebToolsVisibility()
    }

    /** Returns [raw] when it is a normal http(s) URL, otherwise null. */
    private fun httpUrlOnly(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            val scheme = android.net.Uri.parse(value).scheme?.lowercase()
            if (scheme == "http" || scheme == "https") value else null
        } catch (_: Throwable) {
            null
        }
    }

    /** Opens a saved mini-app on its own origin so its localStorage stays isolated. */
    private fun showApp(app: MiniApp) {
        applyWebToolsPrefs()
        currentAppId = app.id
        updateEditChip()
        previewContainer.isVisible = true
        previewContainer.alpha = 0f
        previewContainer.animate().alpha(1f).setDuration(200).start()
        lastPrompt = app.name
        addTask(HOST_MINIAPP, app.name.ifBlank { "แอพใหม่" }.take(48))
        browserTabs.open(null, app.id, app.name)
        webView.loadDataWithBaseURL(
            miniApps.originFor(app.id),
            app.html,
            "text/html",
            "UTF-8",
            null
        )
        hasContent = true
        renderRunning()
        startVideoPolling()
        updatePipState()
        if (sheetOpen) closeSheet()
        updateBack()
        updateWebToolsVisibility()
    }

    // ---------- Floating AI overlays ----------

    private var overlayPanel: OverlayPanel? = null
    private var overlayHiddenForPip = false

    /** Renders AI-written overlay HTML in a small draggable panel above the preview. */
    private fun showOverlay(html: String) {
        if (!previewContainer.isVisible) {
            previewContainer.isVisible = true
            previewContainer.alpha = 1f
        }
        val panel = overlayPanel ?: OverlayPanel(this, root).also { overlayPanel = it }
        panel.show(html)
        placeOverlay(aboveAll = customView != null)
        if (sheetOpen) closeSheet()
        updateBack()
        updateWebToolsVisibility()
        Log.d("AnyQA", "overlay bytes=${html.length}")
    }

    /** Keeps the overlay above the video fullscreen view, or below the chat sheet otherwise. */
    private fun placeOverlay(aboveAll: Boolean) {
        val v = overlayPanel?.panelView ?: return
        root.removeView(v)
        if (aboveAll) root.addView(v)
        else root.addView(v, (root.indexOfChild(previewContainer) + 1).coerceAtMost(root.childCount))
    }

    private fun hideOverlay() {
        overlayPanel?.hide()
        updateBack()
        Log.d("AnyQA", "overlay hidden")
    }

    private fun hidePreview() {
        findBar?.close()
        webView.stopLoading()
        previewContainer.isVisible = false
        overlayPanel?.hide()
        // The poll used to keep running forever once a page had been opened, burning CPU/JS on
        // the home screen. With nothing visible there is nothing to poll.
        stopVideoPolling()
        videoActive = false
        lastPlaying = false
        updatePipState()
        updateBack()
        updateWebToolsVisibility()
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
        // Fullscreen is gone: put the overlay back below the sheet so chat covers it again.
        if (overlayPanel?.isVisible == true) placeOverlay(aboveAll = false)
    }

    /** Lets the chat bubble be dragged anywhere over the preview and remembers where. */
    private fun makeFabDraggable(fab: FloatingActionButton) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val slop = ViewConfiguration.get(this).scaledTouchSlop

        // Restore the last spot (clamped so it stays inside the preview).
        root.post {
            val sx = prefs.getFloat("fab_x", -1f)
            val sy = prefs.getFloat("fab_y", -1f)
            if (sx >= 0f && sy >= 0f) {
                fab.post {
                    fab.x = sx.coerceIn(0f, (previewContainer.width - fab.width).coerceAtLeast(0).toFloat())
                    fab.y = sy.coerceIn(0f, (previewContainer.height - fab.height).coerceAtLeast(0).toFloat())
                }
            }
        }

        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            if (!dragging) {
                longPressed = true
                fab.isPressed = false
                fab.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                startVoiceCommand()
            }
        }

        fab.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = fab.x
                    startY = fab.y
                    dragging = false
                    longPressed = false
                    fab.isPressed = true
                    ui.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        dragging = true
                        fab.isPressed = false
                        ui.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        val maxX = (previewContainer.width - fab.width).coerceAtLeast(0)
                        val maxY = (previewContainer.height - fab.height).coerceAtLeast(0)
                        fab.x = (startX + dx).coerceIn(0f, maxX.toFloat())
                        fab.y = (startY + dy).coerceIn(0f, maxY.toFloat())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(longPress)
                    fab.isPressed = false
                    if (dragging) {
                        prefs.edit().putFloat("fab_x", fab.x).putFloat("fab_y", fab.y).apply()
                    } else if (!longPressed) {
                        fab.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longPress)
                    fab.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Picture-in-Picture ----------

    /**
     * Asked while entering PiP: fullscreen the playing video so the PiP window shows it
     * alone. webkitEnterFullscreen works without a user gesture in the WebView; the
     * standard requestFullscreen is kept as a second try for other WebView versions.
     */
    private val PIP_FULLSCREEN_JS = """
        (function(){
          try{
            if(document.fullscreenElement||document.webkitFullscreenElement) return 'already';
            var v=document.querySelector('video');
            if(!v) return 'novideo';
            if(v.paused||v.ended) return 'paused';
            if(v.webkitEnterFullscreen){ try{ v.webkitEnterFullscreen(); }catch(e){} }
            if(v.webkitDisplayingFullscreen) return 'wk';
            var fn=v.requestFullscreen||v.webkitRequestFullscreen;
            if(fn){ try{ var p=fn.call(v); if(p&&p.catch)p.catch(function(){}); }catch(e){} }
            return 'sent';
          }catch(e){ return 'err:'+e.name; }
        })();
    """.trimIndent()

    /** Last-resort fallback for PiP: scale the page so the video fills the whole viewport. */
    private val PIP_ZOOM_JS = """
        (function(){
          try{
            if(window.__anyPipZoom) return 'zoom-active';
            if(document.fullscreenElement||document.webkitFullscreenElement) return 'already';
            var v=document.querySelector('video');
            if(!v||v.paused||v.ended) return 'novideo';
            var r=v.getBoundingClientRect();
            if(r.width<2||r.height<2) return 'norect';
            var s=Math.min(window.innerWidth/r.width,window.innerHeight/r.height);
            var tx=(window.innerWidth-r.width*s)/2-r.left*s;
            var ty=(window.innerHeight-r.height*s)/2-r.top*s;
            var d=document.documentElement;
            d.style.transformOrigin='0 0';
            d.style.transform='translate('+tx+'px,'+ty+'px) scale('+s+')';
            window.__anyPipZoom=true;
            return 'zoomed';
          }catch(e){ return 'err:'+e.name; }
        })();
    """.trimIndent()

    /** Undoes the CSS zoom fallback after PiP ends. */
    private val CLEAR_PIP_ZOOM_JS = """
        (function(){try{
          if(window.__anyPipZoom){
            var d=document.documentElement;
            d.style.transform='';
            d.style.transformOrigin='';
            window.__anyPipZoom=false;
          }
        }catch(e){}})();
    """.trimIndent()

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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
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

    // ---------- Extras wiring (library, chats, browser, voice) ----------

    private fun applyWebToolsPrefs() {
        if (!::webView.isInitialized) return
        val p = getSharedPreferences("settings", Context.MODE_PRIVATE)
        WebTools.applyDesktopMode(webView, p.getBoolean("desktop_mode", false))
        WebTools.applyForceDark(webView, p.getBoolean("force_dark", false))
    }

    private fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        // A download URL arrives from page content. Only http(s) is something DownloadManager
        // should fetch; file://, content:// and app-specific schemes are refused.
        if (!URLUtil.isNetworkUrl(url)) {
            Toast.makeText(this, R.string.download_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } catch (se: SecurityException) {
                request.setDestinationInExternalFilesDir(
                    this, Environment.DIRECTORY_DOWNLOADS, name
                )
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "กำลังดาวน์โหลด: $name", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "ดาวน์โหลดไม่สำเร็จ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFindBar() {
        if (!::webView.isInitialized || !previewContainer.isVisible) {
            Toast.makeText(this, "เปิดหน้าเว็บก่อนแล้วลองอีกครั้ง", Toast.LENGTH_SHORT).show()
            return
        }
        val bar = findBar ?: FindBar(this).also {
            it.attachTo(previewContainer, webView)
            findBar = it
        }
        if (bar.isOpen()) bar.close() else bar.open()
    }

    private fun openPageAssistant() {
        if (!::webView.isInitialized || !previewContainer.isVisible) return
        PageAssistant.show(
            activity = this,
            webView = webView,
            ask = { prompt, cb ->
                val p = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val base = p.getString("base", AiClient.DEFAULT_BASE).orEmpty()
                    .ifBlank { AiClient.DEFAULT_BASE }
                val model = p.getString("model", AiClient.DEFAULT_MODEL).orEmpty()
                    .ifBlank { AiClient.DEFAULT_MODEL }
                val key = p.getString("key", "").orEmpty()
                if (key.isBlank()) {
                    cb(Result.failure(IllegalStateException("ยังไม่ได้ตั้งค่า API key (/setting)")))
                } else {
                    AiClient.chat(base, key, model, listOf("user" to prompt), cb)
                }
            },
            showResult = { html -> showOverlay(html) }
        )
    }

    private fun openBrowserMenu() {
        BrowserMenuSheet.show(
            this,
            browserTabs,
            browserHistory,
            onLoad = { url, appId ->
                if (!appId.isNullOrBlank()) openAppById(appId)
                else if (!url.isNullOrBlank()) showPreview(url = url)
            },
            onCloseTab = { id ->
                val next = browserTabs.close(id)
                when {
                    next == null -> hidePreview()
                    !next.appId.isNullOrBlank() -> openAppById(next.appId)
                    !next.url.isNullOrBlank() -> showPreview(url = next.url)
                }
            },
            onChanged = {
                applyWebToolsPrefs()
                if (previewContainer.isVisible) webView.reload()
            }
        )
    }

    private fun openAppById(id: String) {
        val app = miniApps.get(id)
        if (app != null) showApp(app)
        else Toast.makeText(this, "ไม่พบแอพนี้", Toast.LENGTH_SHORT).show()
    }

    private fun openLibrary(editAppId: String? = null) {
        LibrarySheet.show(this, miniApps, editAppId) { app -> showApp(app) }
    }

    private fun setupRecentApps() {
        val host = findViewById<FrameLayout>(R.id.recent_apps_host) ?: return
        val row = RecentAppsRow(this)
        host.addView(
            row,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        )
        recentAppsRow = row
        refreshRecentApps()
    }

    private fun refreshRecentApps() {
        if (!::miniApps.isInitialized) return
        val row = recentAppsRow ?: return
        val host = findViewById<FrameLayout>(R.id.recent_apps_host) ?: return
        host.visibility = if (miniApps.list().isEmpty()) View.GONE else View.VISIBLE
        row.bind(miniApps, { app -> showApp(app) }, { openLibrary() })
    }

    private fun setupChatExtras() {
        val inputRow = input.parent as? LinearLayout ?: return
        val chatCol = inputRow.parent as? LinearLayout ?: return

        val webRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        fun webChip(icon: String, label: String, action: () -> Unit): TextView =
            TextView(this).apply {
                text = FaIcons.labeled(this@MainActivity, icon, label)
                textSize = 12f
                setTextColor(getColor(R.color.label_on_dark))
                gravity = Gravity.CENTER
                setPadding(dp(11), dp(7), dp(11), dp(7))
                background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { action() }
            }
        webRow.addView(
            webChip("table-columns", "แท็บ") { openBrowserMenu() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        )
        webRow.addView(
            webChip("wand-magic-sparkles", "ผู้ช่วย") { openPageAssistant() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        )
        webRow.addView(
            webChip("magnifying-glass", "ค้นหา") { toggleFindBar() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        webToolsRow = webRow

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        editChip = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.white))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            visibility = View.GONE
            setOnClickListener {
                editMode = !editMode
                updateEditChip()
            }
        }
        attachChip = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.white))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            visibility = View.GONE
            setOnClickListener { setPendingImage(null) }
        }
        chipRow.addView(
            editChip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        )
        chipRow.addView(
            attachChip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        chatCol.addView(webRow, chatCol.indexOfChild(inputRow))
        chatCol.addView(chipRow, chatCol.indexOfChild(inputRow))

        micChip = TextView(this).apply {
            gravity = Gravity.CENTER
            alpha = 0.5f
            contentDescription = "โหมดสนทนาด้วยเสียง"
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(48))
            FaIcons.setIcon(this, "microphone", 18f, getColor(R.color.label_on_dark))
            setOnClickListener { toggleHandsFree() }
        }
        inputRow.addView(micChip, inputRow.indexOfChild(input))
        updateEditChip()
        updateWebToolsVisibility()
    }

    /** Page tools live inside the chat panel and only appear while a page is open. */
    private fun updateWebToolsVisibility() {
        val show = previewContainer.isVisible && !isInPictureInPictureMode
        webToolsRow?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toggleHandsFree() {
        handsFree = !handsFree
        micChip?.alpha = if (handsFree) 1f else 0.5f
        if (handsFree) {
            stopVoiceListening()
            voiceMode.startListening()
            Toast.makeText(this, "โหมดสนทนาด้วยเสียง: เปิด", Toast.LENGTH_SHORT).show()
        } else {
            voiceMode.stopListening()
            Toast.makeText(this, "โหมดสนทนาด้วยเสียง: ปิด", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderConversation(conv: Conversation) {
        messagesContainer.removeAllViews()
        history.clear()
        conv.messages.forEach { m ->
            val cleaned = if (m.role == "assistant") visiblePart(m.text) else m.text
            val label = when {
                !m.imageDataUrl.isNullOrBlank() -> "[รูปภาพ] ${m.text}"
                m.role == "assistant" && cleaned.isBlank() -> "ดำเนินการแล้ว"
                else -> cleaned
            }
            addMessage(you = m.role == "user", text = label)
            history += m.role to label
        }
        greeted = true
        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startNewChat() {
        chatStore.newConversation()
        messagesContainer.removeAllViews()
        history.clear()
        greeted = true
        addMessage(you = false, text = getString(R.string.greeting))
    }

    private fun openChatHistory() {
        ChatHistorySheet.show(
            this,
            chatStore,
            onPick = { conv ->
                chatStore.select(conv.id)
                renderConversation(chatStore.current())
                openSheet()
            },
            onNew = {
                startNewChat()
                openSheet()
            },
            onDismiss = null
        )
    }

    private fun updateEditChip() {
        val chip = editChip ?: return
        if (currentAppId == null) editMode = false
        chip.visibility = if (currentAppId != null) View.VISIBLE else View.GONE
        chip.text = if (editMode) FaIcons.labeled(this, "pen", "กำลังแก้แอพนี้ · แตะเพื่อปิด")
        else FaIcons.labeled(this, "pen", "แตะเพื่อแก้แอพนี้ด้วยแชท")
        chip.background = roundedBg(
            if (editMode) 0xFF5F6CF5.toInt() else 0xFF23232B.toInt(),
            dp(14).toFloat()
        )
    }

    private fun setPendingImage(dataUrl: String?) {
        pendingImage = dataUrl
        val chip = attachChip ?: return
        if (dataUrl.isNullOrBlank()) {
            chip.text = ""
            chip.visibility = View.GONE
        } else {
            chip.text = FaIcons.labeled(this, "image", "แนบรูปแล้ว · แตะเพื่อลบ")
            chip.background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
            chip.visibility = View.VISIBLE
        }
    }

    private fun showExtras() {
        ExtrasMenu.show(this) { key ->
            when (key) {
                "history" -> openChatHistory()
                "library" -> openLibrary()
                "tabs" -> openBrowserMenu()
                "find" -> toggleFindBar()
                "automations" -> {
                    ensureNotificationPermission()
                    AutomationSheet.show(this)
                }
                "appearance" -> Appearance.showSheet(this)
                "voice" -> {
                    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val on = !prefs.getBoolean("voice_tts", false)
                    prefs.edit().putBoolean("voice_tts", on).apply()
                    voiceMode.speakReplies = on
                    Toast.makeText(
                        this,
                        if (on) "อ่านคำตอบเป็นเสียง: เปิด" else "อ่านคำตอบเป็นเสียง: ปิด",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                "lock" -> toggleAppLock()
                "update" -> Updater.check(this, silent = false)
                "about" -> showAbout()
            }
        }
    }

    /** Automation reminders need the Android 13+ notification runtime permission. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun toggleAppLock() {
        val on = AppLock.isEnabled(this)
        val dialog = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
            .setTitle("ล็อคแอพ")
            .setMessage(
                if (on) "ปิดการล็อคด้วยไบโอเมตริก?"
                else "เปิดล็อค Any ด้วยลายนิ้วมือ/ใบหน้า/รหัสเครื่อง?"
            )
            .setPositiveButton(if (on) "ปิด" else "เปิด") { _, _ ->
                AppLock.setEnabled(this, !on)
                appUnlocked = true
                Toast.makeText(
                    this,
                    if (on) "ปิดการล็อคแล้ว" else "เปิดการล็อคแล้ว",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        AnyChrome.dialog(dialog)
    }

    private fun showAbout() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        val dialog = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
            .setTitle("Any")
            .setMessage(
                "เวอร์ชัน $version\nแอพแชทที่สร้างแอพให้คุณได้\n\n" +
                    "github.com/Suidvck/Any\n\n" +
                    "ไอคอน: Font Awesome Free 6 (CC BY 4.0 · SIL OFL 1.1)\nfontawesome.com"
            )
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
        AnyChrome.dialog(dialog)
    }

    private fun roundedBg(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
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

        val providerSpinner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.provider)
        }
        val providerValue = TextView(this).apply {
            text = providers[savedProvider].name
            setTextColor(getColor(R.color.white))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        providerSpinner.addView(
            providerValue,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        providerSpinner.addView(
            FaIcons.make(this, "chevron-down", 12f, getColor(R.color.icon_muted)),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        )

        val baseInput = EditText(this).apply {
            hint = getString(R.string.base_url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(prefs.getString("base", AiClient.DEFAULT_BASE))
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
        }
        val modelInput = EditText(this).apply {
            hint = getString(R.string.model)
            setSingleLine()
            setText(prefs.getString("model", AiClient.DEFAULT_MODEL))
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
        }
        val keyInput = EditText(this).apply {
            hint = getString(R.string.api_key)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setText(prefs.getString("key", ""))
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
        }
        val maxCtxInput = EditText(this).apply {
            hint = getString(R.string.max_context)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("maxContext", DEFAULT_MAX_CONTEXT).toString())
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
        }
        val adBlockCheck = CheckBox(this).apply {
            setText(R.string.adblock_label)
            isChecked = prefs.getBoolean("adblock", true)
            setTextColor(getColor(R.color.white))
            buttonTintList = android.content.res.ColorStateList.valueOf(
                getColor(R.color.brand)
            )
        }
        val webSearchCheck = CheckBox(this).apply {
            setText(R.string.web_search_label)
            isChecked = prefs.getBoolean("web_search", true)
            setTextColor(getColor(R.color.white))
            buttonTintList = android.content.res.ColorStateList.valueOf(
                getColor(R.color.brand)
            )
        }

        var lastProvider = savedProvider
        providerSpinner.setOnClickListener {
            ChoiceSheet.show(
                this,
                getString(R.string.provider),
                null,
                providers.map { it.name },
                lastProvider
            ) { position ->
                if (position != lastProvider) {
                    lastProvider = position
                    providerValue.text = providers[position].name
                    val p = providers[position]
                    if (p.base.isNotEmpty()) {
                        baseInput.setText(p.base)
                        modelInput.setText(p.model)
                    }
                }
            }
        }

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
            TextView(this).apply {
                setText(R.string.search)
                setTextColor(getColor(R.color.white))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    roundedBg(0xFF2C2C35.toInt(), dp(14).toFloat()),
                    null
                )
                isClickable = true
                isFocusable = true
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
        col.addView(webSearchCheck, lp())

        val settingsDialog = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
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
                    .putBoolean("web_search", webSearchCheck.isChecked)
                    .apply()
                adBlockEnabled = adBlockCheck.isChecked
                addMessage(you = false, text = getString(R.string.saved))
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        settingsDialog.show()
        AnyChrome.dialog(settingsDialog)
    }

    private fun showModelPicker(baseUrl: String, apiKey: String, onPick: (String) -> Unit) {
        if (baseUrl.isBlank()) {
            val dialog = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
                .setTitle(R.string.model_search)
                .setMessage(R.string.models_need_base)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            dialog.show()
            AnyChrome.dialog(dialog)
            return
        }

        val loading = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
            .setMessage(R.string.models_loading)
            .setCancelable(false)
            .create()
        loading.show()
        AnyChrome.dialog(loading)

        AiClient.listModels(baseUrl, apiKey) { result ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                loading.dismiss()
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        val empty = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
                            .setTitle(R.string.model_search)
                            .setMessage(R.string.models_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .create()
                        empty.show()
                        AnyChrome.dialog(empty)
                        return@onSuccess
                    }

                    val filter = EditText(this).apply {
                        hint = getString(R.string.models_filter_hint)
                        setSingleLine()
                        inputType = InputType.TYPE_CLASS_TEXT
                        setTextColor(getColor(R.color.white))
                        setHintTextColor(getColor(R.color.input_hint))
                        textSize = 14f
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = roundedBg(0xFF23232B.toInt(), dp(14).toFloat())
                    }
                    val list = ListView(this).apply {
                        dividerHeight = 0
                        setSelector(android.R.color.transparent)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                    }
                    val adapter = object : ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_list_item_1,
                        ArrayList(models)
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getView(position, convertView, parent)
                            (view as? TextView)?.apply {
                                setTextColor(getColor(R.color.white))
                                textSize = 14f
                                setPadding(dp(14), dp(13), dp(14), dp(13))
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            return view
                        }
                    }
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
                    col.addView(
                        list,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(320)
                        ).apply { topMargin = dp(10) }
                    )

                    picker = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
                        .setTitle(getString(R.string.model_search) + " (" + models.size + ")")
                        .setView(col)
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                    picker.show()
                    AnyChrome.dialog(picker)
                }.onFailure { e ->
                    val error = MaterialAlertDialogBuilder(this, R.style.AnyDialogTheme)
                        .setTitle(R.string.model_search)
                        .setMessage(getString(R.string.error_prefix) + (e.message ?: "unknown error"))
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                    error.show()
                    AnyChrome.dialog(error)
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

        /**
         * Largest saved HTML document that may be sent back for an edit. The edit prompt cannot
         * carry more than this without silently truncating the app and overwriting it with an
         * incomplete reconstruction, so oversized apps are refused with an explanation instead.
         */
        private const val MAX_EDIT_HTML = 60_000

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
