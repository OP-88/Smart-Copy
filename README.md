# Smart Copy

> **Privacy-first · 100% Offline · Zero Ads · Open Source**

A precision Android OCR utility — draw a circle or box over any text on screen and instantly copy it, even from locked PDFs, videos, or images. Zero network permissions. Zero telemetry.

---

## Features

| Feature | Details |
|---|---|
| **Circle-to-Extract OCR** | Draw a selection over any screen content → instant offline text recognition |
| **Sub-pixel Magnifier** | 2×–5× precision loupe for dense code strings, math formulas, serial keys |
| **Magnetic Snap** | Selection handles snap to detected text baselines automatically |
| **Table → TSV** | Detects grid layouts, outputs Tab-Separated Values for direct paste into Excel/Sheets |
| **Clipboard TTL** | Auto-wipes clipboard after configurable timeout (15s / 30s / 60s / 5min) |
| **Quick Settings Tile** | One-tap trigger from notification shade |
| **Edge Bubble** | Optional floating trigger docked to screen edge |

## Privacy Architecture

```
AndroidManifest.xml
  └── ❌ android.permission.INTERNET    → ABSENT
  └── ✅ SYSTEM_ALERT_WINDOW            → Overlay
  └── ✅ FOREGROUND_SERVICE             → Screen capture
  └── ✅ FOREGROUND_SERVICE_MEDIA_PROJECTION
  └── ✅ POST_NOTIFICATIONS             → Android 13+
  └── ✅ VIBRATE                        → Haptic feedback
```

No data ever leaves the device. Verified by the CI [zero-network audit](.github/workflows/ci.yml) on every push.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Build | Gradle KTS + Version Catalog |
| UI | Jetpack Compose + Material3 |
| OCR | ML Kit Text Recognition (bundled, offline) |
| Screen Capture | MediaProjection API |
| Magnifier | `android.widget.Magnifier` (API 28+) |
| Preferences | DataStore |
| Clipboard TTL | WorkManager |
| Min SDK | 28 (Android 9) |

## Project Structure

```
app/src/main/kotlin/com/github/op88/smartcopy/
├── MainActivity.kt
├── overlay/
│   ├── OverlayService.kt        ← Central foreground service
│   ├── FreezeOverlayView.kt     ← Screen freeze + selection drawing
│   ├── EdgeBubbleView.kt        ← Dockable edge trigger
│   └── ActionBarView.kt         ← Copy / TSV / Dismiss bar
├── capture/
│   ├── ScreenCaptureManager.kt  ← MediaProjection + ImageReader
│   └── FrameBuffer.kt           ← Thread-safe bitmap store
├── ocr/
│   ├── OcrEngine.kt             ← ML Kit wrapper (suspending)
│   ├── TableParser.kt           ← Grid → TSV extraction
│   └── SelectionInferencer.kt   ← Sentence boundary expansion
├── snap/
│   └── MagneticSnapHelper.kt    ← Edge-detection snap assist
├── clipboard/
│   ├── SmartClipboardManager.kt ← Copy + TTL scheduling
│   └── ClipboardWipeWorker.kt   ← WorkManager wipe job
├── qs/
│   └── SmartCopyTileService.kt  ← Quick Settings tile
├── settings/
│   ├── SettingsActivity.kt
│   └── Preferences.kt           ← DataStore wrapper
└── ui/theme/
    ├── Theme.kt                  ← Material3 dark scheme
    └── Type.kt                   ← Typography scale
```

## Getting Started

1. Open the `SmartCopy/` folder in **Android Studio Hedgehog** (2023.1.1) or newer.
2. Let Gradle sync complete — all dependencies are pulled automatically.
3. Run on a device or emulator with **API 28+**.
4. Grant "Display over other apps" when prompted.

## Build

```bash
# Lint
./gradlew lint

# Unit tests (TableParser, SelectionInferencer — no device required)
./gradlew testDebugUnitTest

# Debug APK
./gradlew assembleDebug
```

## Distribution

| Channel | Status |
|---|---|
| Google Play Store | Planned |
| F-Droid | Planned (bundled ML Kit — no Play Services dependency) |
| GitHub Releases | APK attached to each tagged release |

## Supporters Hub

If you find Smart Copy useful, visit **[smartcopy.vercel.app](https://smartcopy.vercel.app)** — project docs, roadmap, and optional tip jar (Ko-fi / GitHub Sponsors).

---

## License

```
GNU
Copyright (c) 2026 OP-88
```
