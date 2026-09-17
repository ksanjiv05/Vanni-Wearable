# Vaani — Wearable Voice Notes

Privacy-first, on-device voice-notes system: an **ESP32-S3 wearable** ("Vaani Link") captures/stores
audio and syncs it to an **Android app**, which transcribes and enriches it into searchable notes,
to-dos, and a RAG chat — all on-device, no mandatory cloud.

> **Repo:** https://github.com/ksanjiv05/Vanni-Wearable
> **Status:** Wearable link + app integration complete & device-verified. Voice-command capture on the
> wearable is designed but not yet built. See **[docs/RESUME_HERE.md](docs/RESUME_HERE.md)**.

---

## What this is

Two halves that talk over an **encrypted BLE link** (primary) with a **Wi-Fi SoftAP** fallback for
bulk audio:

| Half | What it does |
|---|---|
| **Vaani Link** (ESP32-S3 firmware) | Wi-Fi SoftAP + BLE GATT server + microSD + ST7735 TFT. Stores recordings under `/vaani`, serves them to the app, shows a glanceable dashboard (app-link status, pending count, today's todos). |
| **Vaani app** (Android, Kotlin/Compose) | Pairs with the wearable, pulls recordings into a durable transcribe→enrich→note pipeline (sherpa-onnx Whisper + MediaPipe LLM + optional Sarvam API), real RAG chat, tasks, search. |

## Repository layout

```
Vanni/
├── README.md                  ← you are here
├── env.sh                     ← `source` before any gradle/adb (JDK17 + Android SDK paths)
├── android/                   ← the Android app (multi-module Gradle)
│   ├── app/                   ← nav shell, DI wiring
│   ├── core/                  ← designsystem (theme, icons), common utils
│   ├── domain/                ← PURE Kotlin: models + port interfaces (no Android deps)
│   ├── data/                  ← implementations: ai, asr-local, audio, database, device, notes,
│   │                            pipeline, sarvam, vector, work
│   └── feature/               ← Compose screens: library, note, chat, search, tasks, device,
│                                onboarding, settings
├── firmware/
│   ├── README.md              ← board, wiring, arduino-cli build/flash, protocol, security
│   └── vaani_link/vaani_link.ino   ← the ESP32-S3 firmware (mirror of ~/Arduino/vaani_link/)
└── docs/
    ├── PROJECT_STATUS.md      ← ✅ what's built + device-verified (start here to catch up)
    ├── WEARABLE.md            ← wearable deep-dive: protocol, security, dashboard, sync, gotchas
    ├── RESUME_HERE.md         ← where we stopped + how to restart (voice commands next)
    ├── ARCHITECTURE.md        ← original design spec v1.0 (the plan; predates the build)
    ├── vaani-voice-command-architecture.html  ← voice-command design + wiring (open in browser)
    ├── DESIGN_SYSTEM.md · UX_REVIEW.md · ADR-001-local-ai-engines.md
    └── screens/ · reviews/
```

## Quick start

### Android app
```bash
source env.sh                 # JDK17 + Android SDK on PATH
cd android
./gradlew assembleDebug       # build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Firmware (ESP32-S3 N16R8)
```bash
export PATH="$HOME/.local/bin:$PATH"
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,USBMode=hwcdc,CDCOnBoot=cdc"
arduino-cli compile -b "$FQBN" firmware/vaani_link
# port is root:dialout — wrap upload in `sg dialout`:
sg dialout -c "export PATH=\$HOME/.local/bin:\$PATH; arduino-cli upload -b '$FQBN' -p /dev/ttyACM0 firmware/vaani_link"
```
Full board/wiring/protocol details: **[firmware/README.md](firmware/README.md)**.

## Where to read next
- **Catching up on what exists →** [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)
- **Wearable internals →** [docs/WEARABLE.md](docs/WEARABLE.md)
- **Restarting work (voice commands) →** [docs/RESUME_HERE.md](docs/RESUME_HERE.md)

## Hard rules (learned the hard way)
- **All device testing goes through the phone app**, never by joining the ESP32 AP from the dev host
  (it knocks the host off its network). The app joins the AP itself via `WifiNetworkSpecifier`.
- **Never commit secrets.** Runtime credentials (HF token, Sarvam key) live in Keystore-backed
  encrypted storage on-device; the wearable's Wi-Fi PSK + REST token are per-device, generated in
  NVS, never in source.
- **`:domain` stays pure Kotlin**; `feature` depends only on domain seams, never on `data`.
- **Never fabricate AI output**; fail-closed on model/integrity errors.
