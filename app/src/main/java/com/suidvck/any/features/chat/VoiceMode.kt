package com.suidvck.any.features.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.suidvck.any.R
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Thai voice mode for the chat sheet: speaks assistant replies with the system
 * TTS engine and captures speech with a dedicated [SpeechRecognizer].
 *
 * This instance is independent from MainActivity's long-press recognizer, so the
 * host must not run both at the same time (stop this one before starting the
 * long-press voice command, and vice versa).
 */
class VoiceMode(
    activity: Activity,
    private val onTranscript: (String) -> Unit
) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 4108

        private const val PREFS = "settings"
        private const val KEY_TTS = "voice_tts"
        private const val UTTERANCE_ID = "any_voice_reply"
        private const val MAX_SPEECH_CHARS = 600
    }

    private val activityRef = WeakReference(activity)
    private val appContext: Context = activity.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsInitializing = false
    private var ttsUnavailable = false
    private var pendingSpeech: String? = null
    private var released = false

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /**
     * Optional extension used for hands-free mode: invoked on the main thread
     * after a reply finishes playing (not on TTS errors).
     */
    var onSpeechDone: (() -> Unit)? = null

    /** Persisted in `settings` under `voice_tts`. Defaults to off. */
    var speakReplies: Boolean
        get() = prefs.getBoolean(KEY_TTS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TTS, value).apply()
        }

    // ---------- TTS ----------

    /**
     * Speaks [text] in Thai, stripping markdown/code/URLs first. No-op while
     * [speakReplies] is off, so callers may invoke it unconditionally.
     */
    fun speak(text: String) {
        if (!speakReplies) return
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return
        runOnMain {
            if (released) return@runOnMain
            ensureTts()
            if (tts == null || ttsInitializing) {
                pendingSpeech = cleaned
                return@runOnMain
            }
            speakNow(cleaned)
        }
    }

    private fun ensureTts() {
        if (released || tts != null || ttsInitializing || ttsUnavailable) return
        ttsInitializing = true
        try {
            tts = TextToSpeech(appContext) { status ->
                // Post so the constructor has finished assigning `tts`.
                main.post {
                    ttsInitializing = false
                    if (released) return@post
                    val engine = tts
                    if (status != TextToSpeech.SUCCESS || engine == null) {
                        ttsUnavailable = true
                        tts = null
                        pendingSpeech = null
                        return@post
                    }
                    configureThai(engine)
                    val queued = pendingSpeech
                    pendingSpeech = null
                    if (!queued.isNullOrBlank()) speakNow(queued)
                }
            }
        } catch (_: Throwable) {
            ttsInitializing = false
            ttsUnavailable = true
            tts = null
        }
    }

    private fun configureThai(engine: TextToSpeech) {
        try {
            val result = engine.setLanguage(Locale("th", "TH"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // No Thai voice installed: fall back to the device language
                // rather than crashing or staying silent forever.
                engine.setLanguage(Locale.getDefault())
            }
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    main.post {
                        if (!released) onSpeechDone?.invoke()
                    }
                }

                override fun onError(utteranceId: String?) {}

                override fun onError(utteranceId: String?, errorCode: Int) {}
            })
        } catch (_: Throwable) {
            // A broken engine must never crash the chat; speak() simply no-ops.
            ttsUnavailable = true
        }
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        try {
            engine.stop()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (_: Throwable) {
        }
    }

    /** Removes markdown, code fences, HTML tags and URLs, then caps at ~600 chars. */
    private fun cleanForSpeech(raw: String): String {
        var text = raw
        text = text.replace(Regex("```[\\s\\S]*?(```|$)"), " ")
        text = text.replace(Regex("`([^`]*)`"), "$1")
        text = text.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
        text = text.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("(https?://|www\\.)\\S+"), " ")
        text = text.replace(Regex("</?[a-zA-Z][^>]*>"), " ")
        text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*[-*+•]\\s+"), "")
        text = text.replace("**", "").replace("__", "").replace("~~", "")
        text = text.replace("*", "").replace("|", " ")
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n\\s*\\n+"), "\n").trim()
        if (text.length > MAX_SPEECH_CHARS) {
            val space = text.lastIndexOf(' ', MAX_SPEECH_CHARS)
            val cut = if (space > MAX_SPEECH_CHARS - 120) space else MAX_SPEECH_CHARS
            text = text.substring(0, cut).trimEnd() + "…"
        }
        return text
    }

    // ---------- Speech recognition ----------

    /**
     * Starts a Thai listening session. If RECORD_AUDIO is missing, requests it
     * (code [PERMISSION_REQUEST_CODE]) and returns without listening; the host
     * should call [startListening] again when the permission is granted.
     */
    fun startListening() {
        runOnMain {
            if (released) return@runOnMain
            val activity = activityRef.get() ?: return@runOnMain
            if (!SpeechRecognizer.isRecognitionAvailable(activity)) return@runOnMain
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_CODE
                )
                return@runOnMain
            }
            beginListening(activity)
        }
    }

    /** Cancels the current session. Safe to call when not listening. */
    fun stopListening() {
        runOnMain {
            listening = false
            destroyRecognizer()
        }
    }

    /** Stops recognition and shuts TTS down. Call from `onDestroy`. */
    fun release() {
        runOnMain {
            released = true
            listening = false
            destroyRecognizer()
            pendingSpeech = null
            onSpeechDone = null
            val engine = tts
            tts = null
            ttsInitializing = false
            if (engine != null) {
                try {
                    engine.stop()
                } catch (_: Throwable) {
                }
                try {
                    engine.shutdown()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun beginListening(activity: Activity) {
        destroyRecognizer()
        val engine = try {
            SpeechRecognizer.createSpeechRecognizer(activity)
        } catch (_: Throwable) {
            return
        }
        recognizer = engine
        listening = true
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            // Partial results are intentionally ignored: the chat only reacts
            // to a final sentence.
            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                if (!listening) return
                listening = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                destroyRecognizer()
                if (heard.isNotEmpty()) deliver(heard)
            }

            override fun onError(error: Int) {
                if (!listening) return
                listening = false
                destroyRecognizer()
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    val activity = activityRef.get() ?: return
                    toast(activity.getString(R.string.voice_need_permission))
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
        }
        try {
            engine.startListening(intent)
        } catch (_: Throwable) {
            listening = false
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        val current = recognizer ?: return
        recognizer = null
        try {
            current.cancel()
        } catch (_: Throwable) {
        }
        try {
            current.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun deliver(text: String) {
        main.post {
            if (!released) onTranscript(text)
        }
    }

    private fun toast(message: String) {
        try {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post { block() }
        }
    }
}
