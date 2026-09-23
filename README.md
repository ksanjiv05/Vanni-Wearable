# Vaani — Wearable Voice Notes

**Privacy-first, on-device voice notes.** A wrist/pocket **ESP32-S3 wearable ("Vaani Link")** captures speech hands-free with a wake word, stores it on a microSD, and syncs it to an **Android app** that transcribes and enriches every recording into searchable notes, to-dos, summaries, and a RAG chat — **entirely on-device, no mandatory cloud.**

> **Repo:** https://github.com/ksanjiv05/Vanni-Wearable
> **Status:** ✅ End-to-end working & device-verified — wearable records hands-free → app auto-syncs → transcribes → playable notes in the Library.

<p align="center">
  <em>📐 Wearable wiring diagram → <a href="docs/wearable-wiring-diagram.html">docs/wearable-wiring-diagram.html</a> (open in a browser)</em>
</p>

---

## Table of contents
- [What it is](#what-it-is)
- [How it works (end to end)](#how-it-works-end-to-end)
- [The wearable (Vaani Link)](#the-wearable-vaani-link)
  - [Hardware & wiring](#hardware--wiring)
  - [Firmware features](#firmware-features)
  - [Build & flash](#build--flash-the-firmware)
- [The Android app](#the-android-app)
  - [Feature map](#feature-map)
  - [Build & install](#build--install-the-app)
- [Repository layout](#repository-layout)
- [Security model](#security-model)
- [Documentation index](#documentation-index)
- [Hard rules](#hard-rules-learned-the-hard-way)

---

## What it is

Two halves that talk over an **encrypted BLE link** (primary) with an on-demand **Wi-Fi SoftAP** for faster bulk audio transfer:

| Half | Stack | What it does |
|---|---|---|
| **Vaani Link** (wearable) | ESP32-S3 N16R8, Arduino/ESP-IDF, ESP-SR | Wake-word listening, hands-free recording with silence auto-stop, microSD storage, ST7735 TFT dashboard, BLE GATT + Wi-Fi REST server. |
| **Vaani app** (phone) | Android, Kotlin, Jetpack Compose, Hilt, multi-module | Pairs with the wearable, auto-pulls recordings, runs a durable transcribe → enrich → note pipeline (sherpa-onnx Whisper + MediaPipe LLM + optional Sarvam API), RAG chat, search, tasks. |

---

## How it works (end to end)

```
   ┌──────────────────────── WEARABLE (Vaani Link) ─────────────────────────┐
   │  🎙  "Hi ESP"  ──▶  record to /vaani/note_*.wav  ──▶  silence auto-stop  │
   │       (ESP-SR wake word)      (INMP441 I²S mic)         (~2s quiet)      │
   │            │                                                 │          │
   │            ▼                        TFT shows:               ▼          │
   │   Listening → SPEAK NOW → RECORDING → SAVED      Notes-to-sync + todos   │
   └────────────────────────────────┬───────────────────────────────────────┘
                                     │  encrypted BLE (bonded) · Wi-Fi on demand
                                     ▼
   ┌──────────────────────────── PHONE (Vaani app) ─────────────────────────┐
   │  Connect  ──▶  AUTO-SYNC pulls new notes  ──▶  transcribe (Whisper)      │
   │                                                    │                     │
   │                                                    ▼                     │
   │              enrich (on-device LLM / heuristic)  ──▶  READY note         │
   │                                                    │                     │
   │      Library (play audio) · Search · Tasks · RAG chat over your notes    │
   └─────────────────────────────────────────────────────────────────────────┘
```

1. **Speak.** Say **"Hi ESP"** near the wearable. The screen flips **Listening → SPEAK NOW**, records what you say, and **auto-stops after ~2 seconds of silence** (or 30s max). The note is written as a real WAV to the SD card's `/vaani` folder.
2. **Sync.** Open the app and connect to the wearable. It **auto-syncs** — pulling any new recordings over the encrypted BLE link (Wi-Fi optional for speed) into a durable WorkManager pipeline. No button to remember.
3. **Understand.** Each recording is transcribed on-device (sherpa-onnx Whisper), then enriched into a title, summary, key points, and to-dos (on-device MediaPipe LLM, with a dependency-free heuristic fallback). The result lands in your **Library** as a READY note.
4. **Use.** Play the original audio, search across everything, manage extracted to-dos, or **chat with your notes** via on-device RAG.

---

## The wearable (Vaani Link)

### Hardware & wiring

**Board:** ESP32-S3 **N16R8** (16 MB flash, 8 MB OPI PSRAM, dual-core 240 MHz, Wi-Fi + BLE 5). Connects via native USB-JTAG at `/dev/ttyACM0`.

**Peripherals:** INMP441 I²S MEMS microphone · ST7735 128×160 SPI TFT · microSD SPI reader.

> 🎨 **A beautiful, color-coded wiring diagram is in [`docs/wearable-wiring-diagram.html`](docs/wearable-wiring-diagram.html)** — open it in any browser. The pin tables below are the same source-of-truth from the firmware.

**INMP441 microphone (I²S0):**

| Mic pin | → ESP32-S3 | Note |
|---|---|---|
| VDD | **3V3** | ⚠️ 3.3V only — **not** 5V |
| GND | GND | |
| SCK | GPIO 4 | I²S bit clock |
| WS | GPIO 5 | I²S word select |
| SD | GPIO 6 | data out |
| L/R | **GND** | ties mic to LEFT channel — must not float |

**ST7735 TFT (HSPI):**

| TFT pin | → ESP32-S3 |
|---|---|
| VCC | 3V3 |
| GND | GND |
| SCK / SCL | GPIO 12 |
| SDA / MOSI | GPIO 11 |
| CS | GPIO 10 |
| DC / A0 | GPIO 17 |
| RST | GPIO 18 |
| LED / BL | GPIO 21 |

**microSD (HSPI — shares SCK/MOSI with the TFT):**

| SD pin | → ESP32-S3 | Note |
|---|---|---|
| VCC | **5V** | this module is 5V-regulated |
| GND | GND | |
| CS | GPIO 14 | unique (not the TFT's 10) |
| SCK | GPIO 12 | shared with TFT |
| MOSI | GPIO 11 | shared with TFT |
| MISO | GPIO 13 | SD only |

**Wiring gotchas (verified the hard way):**
- The **mic is on I²S** (GPIO 4/5/6), a separate peripheral from the SPI bus — no conflict.
- TFT + SD **share the HSPI bus**; you **must** pass MISO=13 to `spiBus.begin()` or the TFT initializes white even though the SD works.
- If the SD won't mount, check its power rail (5V here). A firmware **SPI mutex** guards SD vs TFT access since they run on different cores.

### Firmware features

The single firmware `firmware/vaani_link/vaani_link.ino` (**v5**) runs everything at once, device-verified:

- **🎙 Wake word** — on-device **ESP-SR** WakeNet listens for **"Hi ESP"** (no cloud, no internet).
- **⏺ Hands-free recording** — after the wake word, records to `/vaani/note_<ms>.wav` (16 kHz mono PCM WAV) and **auto-stops after ~2s of continuous silence** (min 1.5s, max 30s; tunable).
- **📺 TFT dashboard** — big voice-state banner (**Listening → SPEAK NOW → RECORDING → SAVED**) plus a glanceable status page: app-link state, count of notes pending sync, and today's top todos (pushed from the app).
- **📶 App link** — encrypted **BLE GATT** server (bonded, per-device secrets) is the primary transport; a **Wi-Fi SoftAP + REST** server can be brought up on demand for faster bulk pulls.
- **💾 Storage** — all Vaani media lives under a single `/vaani` folder; the app never browses the whole card.
- **🔒 Security** — per-device Wi-Fi PSK + REST bearer token generated in NVS at first boot (never hardcoded, never printed), handed to the phone only over the encrypted BLE link.

### Build & flash the firmware

```bash
export PATH="$HOME/.local/bin:$PATH"
# ESP-SR needs the model partition — use the esp_sr_16 scheme:
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,USBMode=hwcdc,CDCOnBoot=cdc,PartitionScheme=esp_sr_16"

arduino-cli compile -b "$FQBN" firmware/vaani_link

# The port is root:dialout — wrap the upload in `sg dialout`:
sg dialout -c "export PATH=\$HOME/.local/bin:\$PATH; arduino-cli upload -b '$FQBN' -p /dev/ttyACM0 firmware/vaani_link"

# Watch serial:
sg dialout -c 'stty -F /dev/ttyACM0 115200 raw -echo; timeout 8 cat /dev/ttyACM0'
```

Requires `arduino-cli`, the `esp32:esp32` core, and the Adafruit ST7735 / GFX / BusIO / SD libraries. Full board details, protocol reference, and troubleshooting: **[firmware/README.md](firmware/README.md)** and **[docs/WEARABLE.md](docs/WEARABLE.md)**.

---

## The Android app

Native Android — **Kotlin + Jetpack Compose**, multi-module, Hilt DI. Offline-first: on-device transcription and enrichment, with the paid Sarvam API strictly opt-in.

### Feature map

| Area | What it does |
|---|---|
| **Library** | All notes, grouped by day; a PROCESSING section for in-flight/failed recordings with retry + delete. |
| **Note detail** | Full transcript, summary, key points, to-dos, and a real **audio player** to play the original recording. |
| **Device** | Pair with the wearable, browse `/vaani`, **auto-sync** on connect, manual sync, delete files, per-device dashboard push. |
| **Search** | Keyword + lexical-vector ("fuzzy") search across titles, summaries, key points, to-dos, tags. |
| **Tasks** | Every extracted to-do, completable and persisted. |
| **Chat** | RAG chat grounded in your own notes (retrieval + on-device LLM), with citations. |
| **Settings** | Choose on-device models, engine routing, credentials (Keystore-encrypted). |

**On-device AI stack:**
- **ASR:** sherpa-onnx **Whisper** (Tiny/Small), streaming/chunked for long audio.
- **Enrichment:** **MediaPipe** LLM (Gemma / Qwen `.task` models) with a dependency-free extractive **heuristic fallback**, so a note always reaches READY even with no LLM installed.
- **Optional cloud:** **Sarvam** API (India-focused ASR/LLM) — opt-in, never a silent fallback.
- **Pipeline:** durable **WorkManager** jobs, serialized so only one heavy inference runs at a time (bounds memory); fail-closed on model/integrity errors.

### Build & install the app

```bash
source env.sh                 # puts JDK 17 + Android SDK on PATH
cd android
./gradlew assembleDebug       # build
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd uimode night no # force the light (cream) theme
```

---

## Repository layout

```
Vanni/
├── README.md                       ← you are here
├── env.sh                          ← source before any gradle/adb (JDK17 + Android SDK)
├── android/                        ← the Android app (multi-module Gradle)
│   ├── app/                        ← nav shell + app-wide DI
│   ├── core/                       ← designsystem (theme, icons), common utils
│   ├── domain/                     ← PURE Kotlin: models + port interfaces (no Android deps)
│   ├── data/                       ← ai, asr-local, audio, database, device, notes,
│   │                                 pipeline, sarvam, vector, work
│   └── feature/                    ← Compose screens: library, note, chat, search,
│                                     tasks, device, onboarding, settings
├── firmware/
│   ├── README.md                   ← board, wiring, arduino-cli build/flash, protocol
│   ├── vaani_link/vaani_link.ino   ← THE wearable firmware (v5: link + wake word + record)
│   ├── vaani_voice/                ← standalone voice sketch (dev reference)
│   └── mic_test/                   ← INMP441 bring-up test sketch
└── docs/
    ├── wearable-wiring-diagram.html ← 🎨 color-coded wiring diagram (open in browser)
    ├── WEARABLE.md                 ← wearable deep-dive: protocol, security, sync, gotchas
    ├── PROJECT_STATUS.md           ← what's built + device-verified
    ├── RESUME_HERE.md              ← where we stopped + how to restart
    ├── ARCHITECTURE.md             ← original design spec (the plan)
    ├── vaani-voice-command-architecture.html ← voice-command design + wiring
    ├── DESIGN_SYSTEM.md · UX_REVIEW.md · ADR-001-local-ai-engines.md
    └── screens/ · reviews/
```

---

## Security model

- **BLE is the encrypted root of trust.** The wearable bonds with the phone (LE Secure Connections, Just Works). Its per-device Wi-Fi PSK and REST bearer token are generated randomly in NVS at first boot, **never hardcoded and never printed**, and are handed to the phone only over the bonded/encrypted BLE link.
- **REST is token-gated.** Every `/api/*` call requires the per-device bearer token; without it the Wi-Fi AP is useless to a stranger.
- **App secrets are Keystore-encrypted.** The Hugging Face token and Sarvam key live in Keystore-backed `EncryptedSharedPreferences`, never in source or plaintext prefs.
- **No secrets in the repo.** `.gitignore` excludes build artifacts, APKs, and credential files; a pre-push scan keeps them out.

---

## Documentation index

- **Catch up on what exists →** [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)
- **Wearable internals (protocol, security, sync) →** [docs/WEARABLE.md](docs/WEARABLE.md)
- **Wiring diagram →** [docs/wearable-wiring-diagram.html](docs/wearable-wiring-diagram.html)
- **Restart / next steps →** [docs/RESUME_HERE.md](docs/RESUME_HERE.md)
- **Original design spec →** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Firmware build & protocol →** [firmware/README.md](firmware/README.md)

---

## Hard rules (learned the hard way)

- **All device testing goes through the phone app** — never join the ESP32 SoftAP from the dev host (it knocks the host off its network). The app joins the AP itself via `WifiNetworkSpecifier`.
- **Never commit secrets.** Runtime credentials live in on-device encrypted storage; the wearable's Wi-Fi PSK + REST token are per-device, generated in NVS, never in source.
- **`:domain` stays pure Kotlin.** `feature` modules depend only on domain seams, never on `data`.
- **Never fabricate AI output.** ASR routing is strict (no silent on-device → cloud fallback); the pipeline fails closed on model/integrity errors and never invents a transcript or summary.
- **Sharp corners, cream light theme** are global design invariants (see [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)).
