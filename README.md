<div align="center">

# Any

**Type what you want. Get a working app. Instantly.**

A Thai-first Android app-builder — describe an idea in chat and the built-in AI writes a complete mini-app and runs it full-screen, or opens the web in a built-in browser with an ad blocker, voice control, Picture-in-Picture, and background playback.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24%2B-3DDC84)
![Release](https://img.shields.io/github/v/release/Suidvck/Any?label=release&color=blue)
![License](https://img.shields.io/github/license/Suidvck/Any?color=green)

</div>

---

## What is Any?

Any turns plain language into working software. Ask for a game, a tool, a calculator — the AI answers with a complete, self-contained HTML/CSS/JS app that runs immediately in a full-screen WebView, fully isolated from the rest of the device. No install, no store, no code.

Ask for a website instead, and Any opens it in its own built-in browser — a full-screen WebView with an ad blocker, real fullscreen video, Picture-in-Picture, and screen-off audio playback.

The interface is Thai-first, and the AI understands and answers in both Thai and English.

## Download

Grab the latest APK from [**Releases**](https://github.com/Suidvck/Any/releases/latest), install it, and add your API key in `/setting` — the free Gemini default is enough to start.

## Features

### AI app builder
- Describe anything — game, tool, page, calculator — in Thai or English, and the AI returns one complete single-file app that starts instantly, full-screen.
- Fenced `` ```html `` replies are detected and rendered; the AI is instructed to produce self-contained apps (all CSS/JS inline, no external resources), mobile-first and touch-friendly.
- **Overlay panels** — ask for something floating (a clock, notes, extra buttons) and the AI returns an `` ```html-overlay `` block that opens as a small draggable panel on top of the current screen.
- `OPEN_URL:` protocol — ask for a site and the built-in browser opens it (real site URLs, including search URLs).
- Bring your own AI key: **Gemini (free tier)**, **OpenAI**, **OpenRouter**, **Groq**, or any OpenAI-compatible endpoint via the Custom option (base URL + model).
- Configurable context window, per-provider base URL and model, `/clear` to reset the conversation.

### Built-in browser
- Full-screen WebView with full HTML5 video support — including true fullscreen (landscape) playback.
- **Ad blocker** — host-based blocking of major ad and tracker networks, plus a built-in YouTube ad skipper that prunes ad placements from player responses, skips ad players, and hides promoted feed cards. Toggleable in settings.
- **Deep-link guard** — pages cannot bounce you into other apps (`intent://`, `market://`, `youtube://`, and similar schemes are blocked and kept inside Any).
- **Picture-in-Picture** — leave the app while a video is playing and it continues in a floating window.
- **Background playback** — audio and video keep playing with the screen off via a media-session keep-alive.

### Voice & commands
- Long-press the floating chat ring to give a voice command using in-app speech recognition (Thai).
- `/clear` resets the chat · `/setting` opens configuration (provider, model, API key, context size, ad blocker).

### Design
- Edge-to-edge and truly full-screen — the status bar is always hidden.
- Floating dark chat sheet: light AI bubbles, rose user bubbles, pill input with a circular send button.
- Pixel-art home screen with a clock, running-tasks list, media controls, and a quick-launch dock.
- The signature two-ring logo (app icon and floating button).

## How it works

````
  You ──chat──►  Any  ──HTTPS──►  AI provider
                  │               (Gemini · OpenAI · OpenRouter · Groq · Custom)
                  │◄───────────────  reply: ```html … ```  or  OPEN_URL: https://…
                  ▼
        ┌───────────────────────────────────────────┐
        │  Full-screen WebView                      │
        │   • renders the generated mini-app        │
        │   • or browses the open web               │
        │   • ad blocker · keep-alive · PiP         │
        └───────────────────────────────────────────┘
````

- The assistant reply is parsed on-device: a fenced `` ```html `` block is rendered in the app WebView; an `` ```html-overlay `` block opens in a draggable floating panel on top of the screen; an `OPEN_URL:` line navigates the browser WebView. The AI is instructed to never mix them.
- Chat uses any OpenAI-compatible `POST {base}/chat/completions` endpoint with a bearer key, so providers are interchangeable.
- The ad blocker works at the WebView layer: requests are checked against a host blocklist before they leave the device, and a document-start script on YouTube prunes ad placements from player API responses and skips ad playback.
- Mini-apps run sandboxed in a plain WebView: HTML/CSS/JS only, no native Android access.

## Build & run

Standard Gradle Android project — no special setup.

```bash
# Clone
git clone https://github.com/Suidvck/Any.git
cd Any

# Build the debug APK
./gradlew assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Works with Android Studio (any recent version) or a JDK 17+ toolchain and the Android SDK. `minSdk 24` (Android 7.0+), `targetSdk 37`.

### First run

1. Open the app and tap the chat ring.
2. Open `/setting` and paste an API key — the default provider is Gemini's free OpenAI-compatible endpoint (a free key from Google AI Studio is enough to start).
3. Try it out: *"Make a snake game"*, *"create a to-do app"*, *"open YouTube"*.

## Privacy & your API key

- No account, no analytics, no telemetry, no ads of its own.
- Your API key is stored only on your device, in the app's private settings. It is never bundled in this repository or in the APK.
- Chat requests go directly from your device to whichever AI provider you configure.

## Tech stack

- 100% Kotlin, single Activity, fully programmatic UI (no XML-driven screens beyond the shell).
- AndroidX WebKit `addDocumentStartJavaScript` for keep-alive and ad-blocking script injection.
- AppCompat + Material components, `androidx.webkit` 1.12.1.
- Theming: full-screen dark UI with custom drawing for the chat sheet, pixel clock, and logo.

## Known limitations

- Generated mini-apps are web apps only — they run inside a WebView sandbox and cannot call native Android APIs.
- YouTube ad skipping depends on the current player response format; if YouTube changes it, the skip logic may need an update.
- The app interface is in Thai; the AI supports both Thai and English.

## License

MIT — see [LICENSE](LICENSE).

---

<div align="center">

Made with ❤️ in Thailand

</div>
