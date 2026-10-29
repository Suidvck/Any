<div align="center">

# Any

**Type what you want. Get a working app. Instantly.**

A Thai-first Android app-builder — describe an idea in chat and the built-in AI writes a complete mini-app and runs it full-screen, or opens the web in a built-in browser with an ad blocker, voice control, Picture-in-Picture, and background playback.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24%2B-3DDC84)
![Release](https://img.shields.io/github/v/release/Suidvck/Any?label=release&color=blue)
![Build](https://github.com/Suidvck/Any/actions/workflows/android.yml/badge.svg)
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
- **Saved app library** — every mini-app is saved automatically. Reopen it from the recent-apps rail on the home screen or the full library, and delete what you no longer need.
- **Edit by chat** — open a saved app and ask for a change. The AI receives the app's current HTML and updates the same app in place instead of creating a duplicate.
- **Streaming replies** — answers render token by token as the model writes, instead of waiting for the full response.
- **Image attachments** — attach a photo or screenshot to a prompt for vision-capable models.
- **Conversation history** — chats are stored locally. Start a new chat, switch between conversations, and continue exactly where you left off.
- **Overlay panels** — ask for something floating (a clock, notes, extra buttons) and the AI returns an `` ```html-overlay `` block that opens as a small draggable panel on top of the current screen. It stays visible even when a video goes fullscreen.
- `OPEN_URL:` protocol - ask for a site and the built-in browser opens it (real site URLs, including search URLs).
- **Live web search** - when a question needs current facts, the AI asks the app to search the web, receives the top results and answers with sources. No search API key is required; toggle it under AI settings. Queries go directly to Bing (DuckDuckGo fallback) from the device.
- Bring your own AI key: **Gemini (free tier)**, **OpenAI**, **OpenRouter**, **Groq**, or any OpenAI-compatible endpoint via the Custom option (base URL + model).
- Configurable context window, per-provider base URL and model, `/clear` to reset the conversation.

### Built-in browser
- Full-screen WebView with full HTML5 video support — including true fullscreen (landscape) playback.
- **Tabs, history and bookmarks** — keep several sites open, revisit recent pages, and save favourites.
- **Find in page** — search the current page with a compact find bar.
- **Downloads** — files are handed to Android's DownloadManager and saved to Downloads.
- **Desktop mode and force dark** — request desktop sites or apply a dark rendering pass to bright pages.
- **Page assistant** — summarise, explain or ask questions about the page you are reading, without leaving it.
- **Ad blocker** — host-based blocking of major ad and tracker networks, plus a built-in YouTube ad skipper that prunes ad placements from player responses, skips ad players, and hides promoted feed cards. Toggleable in settings.
- **Deep-link guard** — pages cannot bounce you into other apps (`intent://`, `market://`, `youtube://`, and similar schemes are blocked and kept inside Any).
- **Picture-in-Picture** — leave the app while a video is playing and it continues in a floating window that shows only the video, like a native player.
- **Draggable chat bubble** — drag the chat button anywhere over the page; its position is remembered between sessions.
- **Background playback** — audio and video keep playing with the screen off via a media-session keep-alive.

### Voice & commands
- Long-press the floating chat ring to give a voice command using in-app speech recognition (Thai).
- **Read replies aloud** with Android TTS, and use hands-free voice mode: Any listens again automatically after it finishes speaking.
- `/clear` resets the chat · `/setting` opens configuration (provider, model, API key, context size, ad blocker, live web search).

### Automation & privacy
- **Scheduled automations** - set a daily reminder that opens a URL or a saved mini-app, or prefills a prompt for you. Jobs run through WorkManager and are stored only on the device. They keep running after the app is closed and survive a reboot; a manual force-stop or aggressive battery saver may delay them until the app is opened again.
- **Biometric lock** — optionally protect Any with fingerprint, face or device credentials.
- **In-app updater** — checks the signed GitHub Releases feed for a newer version and opens the download.
- **Home-screen widget** — jump straight into a new chat from the launcher.

### Personalization
- **Accent colours** and **clock styles** — monochrome, violet, green, amber, blue, plus pixel Doto, monospace and bold sans clock faces, in light or dark theme.
- **Customisable dock** — the three home shortcuts can each be changed to a preset (Instagram, Discord, TikTok, YouTube, GitHub, Google, ChatGPT) or any custom URL with a long-press.

### Design
- Edge-to-edge and truly full-screen — the status bar is always hidden.
- Floating chat sheet with a home button: light AI bubbles, rose user bubbles, pill input with a circular send button, plus an edit chip that shows which app your next message will update.
- **All page tools live in the chat panel** — browser menu, page assistant and find-in-page sit in a compact row above the input, so nothing floats over the page itself. The row hides when no page is open and in Picture-in-Picture.
- **A single icon language** — the whole UI uses Font Awesome Free 6 solid icons instead of emoji, and every saved mini-app keeps a Font Awesome icon you can change from the library.
- Home screen with a stacked pixel clock, soft corner blobs (light and dark palettes), media controls, a recent-apps rail, and the three-slot dock.
- Panels open over a fully transparent scrim — the home screen stays visible and clean behind them, and tapping outside still dismisses the panel.
- Swipe right on the home screen to jump back to the last page you opened (AI mini-app or site); the home button in the chat sheet takes you back home.
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

# Run the unit tests
./gradlew testDebugUnitTest

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Every push and pull request runs the same tests and builds the debug APK on GitHub Actions (`.github/workflows/android.yml`).

Works with Android Studio (any recent version) or a JDK 17+ toolchain and the Android SDK. `minSdk 24` (Android 7.0+), `targetSdk 37`.

### First run

1. Open the app and tap the chat ring.
2. Open `/setting` and paste an API key — the default provider is Gemini's free OpenAI-compatible endpoint (a free key from Google AI Studio is enough to start).
3. Try it out: *"Make a snake game"*, *"create a to-do app"*, *"open YouTube"*.

## Privacy & your API key

- No account, no analytics, no telemetry, no ads of its own.
- Your API key is stored only on your device, in the app's private settings. It is never bundled in this repository or in the APK.
- Chat requests go directly from your device to whichever AI provider you configure. Attached images and page text are sent only when you explicitly ask the assistant about them.
- Saved apps, conversations, browser data and automations live in the app's private storage and are never uploaded anywhere.

## Tech stack

- 100% Kotlin, single Activity, fully programmatic UI (no XML-driven screens beyond the shell).
- Font Awesome Free 6 (solid + regular) bundled as font assets and rendered by a small `FaIcons` helper — no icon library, no runtime download.
- AndroidX WebKit `addDocumentStartJavaScript` for keep-alive and ad-blocking script injection.
- AppCompat + Material components, `androidx.webkit` 1.12.1, WorkManager 2.10.1, Biometric 1.1.0.
- Theming: full-screen light/dark UI with custom drawing for the chat sheet, pixel clock, blobs and logo.

## Known limitations

- Generated mini-apps are web apps only — they run inside a WebView sandbox and cannot call native Android APIs.
- YouTube ad skipping depends on the current player response format; if YouTube changes it, the skip logic may need an update.
- Automations use WorkManager, so aggressive battery savers (MIUI, One UI, etc.) can delay a reminder; allow Any to run in the background for best results.
- The app interface is in Thai; the AI supports both Thai and English.

## License

MIT — see [LICENSE](LICENSE).

Any bundles the Font Awesome Free 6 icon fonts. Font Awesome icons are
CC BY 4.0 and the font files are SIL OFL 1.1 — see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the full attribution and
license text.

---

<div align="center">

Made with ❤️ in Thailand

</div>
