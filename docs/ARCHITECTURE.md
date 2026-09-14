# Vaani — ESP32 Voice Recorder + Android AI Notes App
## Production Architecture & Delivery Plan

> **Status:** Design v1.0 · **Date:** 2026-08-31 · *"Vaani" is a placeholder codename.*
>
> **Scope:** A battery-powered ESP32 wearable records audio and stores it locally. When it comes
> near the paired Android phone, all recordings transfer to the app. The app transcribes them via
> Sarvam AI, generates notes / summaries / to-dos, indexes everything into an on-device vector
> database, and lets the user chat with their notes through RAG. The user supplies their own
> Sarvam API key.

---

## 0. Executive Summary

### 0.1 The shape of the system

```
┌───────────────────────────┐        ┌────────────────────────────────────┐      ┌──────────────────┐
│      ESP32-S3 DEVICE      │        │           ANDROID APP              │      │   SARVAM CLOUD   │
│                           │        │                                    │      │                  │
│  I2S MEMS mic (16 kHz)    │        │  ┌──────────────────────────────┐  │      │  /speech-to-text │
│         ↓                 │        │  │  Transport (BLE + Wi-Fi)     │  │      │  /job/v1 (batch) │
│  VAD → segment            │        │  └──────────────┬───────────────┘  │      │  /v1/chat/       │
│         ↓                 │  BLE   │                 ↓                  │ HTTPS│    completions   │
│  Opus encode 24 kbps      │ ◄────► │  ┌──────────────────────────────┐  │◄────►│  /translate      │
│         ↓                 │  ctrl  │  │  Durable Ingest Pipeline     │  │      │                  │
│  AES-GCM → microSD        │        │  │  (Room-backed state machine) │  │      │  Key: user's own │
│         ↓                 │  Wi-Fi │  └──────────────┬───────────────┘  │      └──────────────────┘
│  Chunked resumable HTTP   │ ◄────► │                 ↓                  │
│                           │  bulk  │  Room (SQLCipher) + ObjectBox HNSW │      ┌──────────────────┐
│  Deep sleep when idle     │        │  + FTS5      ← ALL ON DEVICE       │      │  On-device ONNX  │
└───────────────────────────┘        │                 ↓                  │      │  embedding model │
                                     │  Hybrid RAG → Chat with citations  │ ◄──► │  (no cloud)      │
                                     └────────────────────────────────────┘      └──────────────────┘
```

### 0.2 Five findings from the Sarvam API that shape the design

These were verified against live docs on 2026-08-31. Each one forces a specific architectural decision.

| # | Finding | Consequence for this design |
|---|---|---|
| 1 | **Sync STT caps at 30 seconds.** The Batch API handles up to **2 hours/file, 20 files/job**. | You cannot just POST a 40-minute recording. A **dual-path ASR router** (§5.5.2) is mandatory, not an optimization. |
| 2 | **Sarvam has no text-embeddings endpoint.** The API families are STT, TTS, translate, transliterate, LID, chat, vision. | RAG embeddings **must be generated on-device** (ONNX). This is a constraint, but it turns into a feature: search works fully offline and costs nothing. §6.3 |
| 3 | **Batch results are delivered by webhook or polling.** Webhooks need a public URL. | A phone has no public URL → the app **must poll**. WorkManager's 15-min periodic floor is too slow, so use chained expedited one-shot workers. §5.7 |
| 4 | **Chat completions support strict `json_schema` structured output + tool calling + `reasoning_effort`.** | Note/summary/to-do extraction becomes **schema-guaranteed** rather than prompt-and-pray. No fragile regex parsing. §5.5.4 |
| 5 | **Rate limits are per *account*, not per key**, token-bucket refilled, and Sarvam-105B is only **40 req/min on Starter**. | Needs a **client-side token-bucket limiter** shared across all workers, plus a budget cap. Naive parallel fan-out will 429 immediately. §5.5.5 |

### 0.3 The one correction worth making up front: **MCP is not for the Android app**

You mentioned "Sarvam AI API/MCP". These are not interchangeable here:

- The **official Sarvam MCP server** (`github.com/sarvamai/sarvam-mcp`) is a **Python package** run via `uvx sarvam-mcp`, speaking stdio to a desktop MCP client (Claude Code, Cursor, Zed).
- **It cannot run inside an Android app.** There is no Python runtime, no stdio transport, and MCP would add a pointless hop in front of a REST API the app can call directly.

**Decision:** the Android app calls the **Sarvam REST API directly** over HTTPS. Use the MCP server as a **developer-time tool** on your laptop — to prototype prompts, eyeball ASR quality on sample audio, and tune the extraction schema before you write a line of Kotlin. That is genuinely valuable, just at a different layer. §7 covers the workflow.

### 0.4 Verified API reference (the contract this design is built on)

**Base URL:** `https://api.sarvam.ai` · **Auth:** `api-subscription-key: <KEY>` *(also accepts `Authorization: Bearer <KEY>`)*

| Capability | Endpoint | Model | Hard limits |
|---|---|---|---|
| STT sync | `POST /speech-to-text` | `saaras:v3` | **≤ 30 s**; 60 req/min (Starter) |
| STT batch | `POST /speech-to-text/job/v1`<br>`GET /speech-to-text/job/v1/{job_id}/status`<br>`POST /speech-to-text/job/v1/download-files` | `saaras:v3` | **≤ 2 h/file, ≤ 20 files/job**; 20 req/min (Starter); Azure blob storage |
| STT realtime | WebSocket | `saaras:v3-realtime` | WAV/PCM only, 16 k or 8 k; 20 concurrent (Starter) |
| LLM | `POST /v1/chat/completions` | `sarvam-105b` (128 K ctx)<br>`sarvam-105b-conversations` | 40 req/min (Starter) |
| Translate | `POST /translate` | — | 60 req/min (Starter) |
| Language ID | `POST /text-lid` | — | — |

**Saaras v3 `mode` values:** `transcribe` · `translate` (→English) · `verbatim` (keeps fillers) · `translit` (Roman script) · `codemix` (**Hinglish — the one you want by default in India**)

**Batch extras:** `with_diarization=true`, `num_speakers` (≤ 20), returns `diarized_transcript[]` with `transcript`, `start_time_seconds`, `end_time_seconds`, `speaker_id`. Timestamps are **chunk-level, not word-level**.

**Audio formats accepted:** MP3, WAV, AAC, AIFF, OGG, FLAC, MP4, AMR, WMA, WEBM, PCM. Raw PCM needs `input_audio_codec` = `pcm_s16le` | `pcm_l16` | `pcm_raw` and **must be 16 kHz**.

**Errors:** `429` with `rate_limit_exceeded_error`; WebSocket rejection = close code `1003`. Exponential backoff required.

> ℹ️ **`saaras:v4` has since shipped** alongside `saaras:v3` on the REST, WebSocket and Batch
> endpoints. Keep the ASR model name a **remote-configurable string**, never a hardcoded constant,
> and A/B it against v3 on your WER set (§10.4) before switching the default.

> ⚠️ **`sarvam-m` (24B) is deprecated** and removed from chat completions. Do not build against it. Use `sarvam-105b`.

> ⚠️ **One thing to probe before committing:** OGG is on the accepted-format list, but it is not documented whether that means Ogg **Opus** specifically. §2.3 makes the firmware emit Opus; §5.4 keeps a transcode stage that is a no-op if Opus uploads work. **Run this probe in week 1** — it decides whether the phone needs to burn CPU transcoding. See §12 Phase 0.

---

## 1. Product Scope

### 1.1 Core user journeys

| # | Journey | Success criterion |
|---|---|---|
| J1 | **Pair** — unbox device, pair over BLE, enter Sarvam API key | < 90 s, key validated with a live probe call |
| J2 | **Capture** — press button, record a meeting, walk away | Device records ≥ 12 h on a charge; LED shows recording state |
| J3 | **Sync** — walk near phone, recordings transfer automatically | 1 h of audio transfers in < 30 s over Wi-Fi; zero user action |
| J4 | **Process** — audio → transcript → note + summary + to-dos | Survives app kill / reboot / network loss and resumes exactly where it stopped |
| J5 | **Browse** — see notes, play audio, tick off to-dos | Tap any transcript line → audio seeks to that moment |
| J6 | **Chat** — "what did I commit to in Tuesday's standup?" | Answer with tappable citations that deep-link into note + audio timestamp |
| J7 | **Search** — semantic + keyword, in Hindi, English, or Hinglish | Works **fully offline**; results < 200 ms |

### 1.2 Explicit non-goals for v1

Naming these now prevents scope creep later.

- ❌ No backend server, no user accounts, no cloud sync between devices. *(Single phone, single device. This removes an entire tier of infrastructure, auth, and liability. Revisit at v2.)*
- ❌ No live streaming from device to phone during recording. *(Store-and-forward only. The realtime WebSocket API is reserved for phone-mic recording in v2.)*
- ❌ No speaker **identification** (naming who is who). Diarization gives you "Speaker 1 / Speaker 2"; mapping those to real people is a v2 feature.
- ❌ No multi-device / multi-user. One device ↔ one phone.

### 1.3 Quality bars (make these testable, track them in CI)

| Metric | Target |
|---|---|
| Audio→note end-to-end latency (10-min recording, Wi-Fi) | < 3 min p50 |
| Sync throughput (Wi-Fi bulk) | > 1.5 MB/s sustained |
| Device battery, continuous recording | ≥ 12 h |
| Device battery, idle + advertising | ≥ 30 days |
| Data loss rate (recorded → persisted on phone) | **0** — enforced by two-phase commit (§3.4) |
| RAG Recall@5 on golden set | ≥ 0.85 |
| Semantic search latency, 100 k chunks | < 200 ms p95 |
| Crash-free sessions | > 99.5 % |

---

## 2. Part A — ESP32 Device

### 2.1 Hardware selection

**Use the ESP32-S3, not the classic ESP32.** The four reasons that matter:

1. **PSRAM (8 MB)** — room for audio ring buffers and Opus encoder state. Classic ESP32's 520 KB SRAM makes buffering painful.
2. **Native USB-OTG** — a wired fallback for large backlogs and a rescue path for firmware bricks.
3. **Vector/DSP instructions** — makes on-device Opus encoding and VAD comfortably real-time.
4. **BLE 5.0** — 2M PHY and Data Length Extension, which the transfer path (§3) depends on.

| Component | Part | Why |
|---|---|---|
| MCU | **ESP32-S3-WROOM-1-N16R8** (16 MB flash, 8 MB PSRAM) | See above |
| Microphone | **ICS-43434** I2S MEMS (or INMP441 for lower cost) | Digital I2S output — no analog noise, no ADC calibration. 65 dB SNR. |
| Storage | **microSD, SDMMC 4-bit mode** | 4-bit SDMMC gives ~10–20 MB/s vs ~1 MB/s over SPI. Do not wire it as SPI. |
| Charging | MCP73831 or TP4056 + **MAX17048 fuel gauge** | The fuel gauge matters: users need a real battery % , not a voltage guess. |
| Battery | 500–1000 mAh Li-Po | 1000 mAh ≈ 20 h recording (§2.5) |
| RTC | Sync from phone over BLE + track drift | **Skip the DS3231.** Timestamps are what make notes findable, but a BLE time-sync on every connect plus a drift model is accurate enough and saves BOM cost + board space. |
| UI | 1 tactical button + RGB LED | Button: short = start/stop, long = mark moment, double = privacy mute. LED must be **visible while recording** — this is a legal requirement in many places, not decoration (§8.4). |

### 2.2 Firmware architecture (ESP-IDF + FreeRTOS)

Five tasks, pinned deliberately across the two cores:

```
CORE 0 (protocol/IO)                    CORE 1 (DSP, latency-sensitive)
┌────────────────────────┐              ┌────────────────────────────┐
│ T4: BLE / Wi-Fi        │              │ T1: I2S Capture   prio 24  │
│     transport  prio 10 │              │   DMA → PSRAM ring buffer  │
├────────────────────────┤              │   16 kHz mono s16le        │
│ T5: Housekeeping       │              ├────────────────────────────┤
│   battery, sleep,      │              │ T2: VAD + Segmenter prio 20│
│   LED, watchdog prio 5 │              │   drops silence            │
└────────────────────────┘              ├────────────────────────────┤
         ▲                              │ T3: Opus Encode +          │
         │                              │     AES-GCM + SD  prio 15  │
         └──────── FreeRTOS queues ─────┴────────────────────────────┘
```

**Why this split:** T1 must never miss an I2S DMA deadline or you get audible dropouts, so it gets the highest priority on a core that Wi-Fi/BLE never touches. The ESP32 Wi-Fi stack is bursty and latency-hostile — keeping it on core 0 isolates the audio path from it entirely.

### 2.3 Audio capture, VAD, and encoding

**Capture at 16 kHz mono, 16-bit** — not 48 kHz. Sarvam's models want 16 kHz, so recording higher means you pay 3× in storage, battery, and transfer time only to downsample later.

**VAD is the highest-leverage optimization in the whole device.** A wearable recording a workday captures mostly silence. Running WebRTC VAD (aggressiveness 2) with a 300 ms hangover typically drops **40–70 %** of stored bytes.

- Keep **500 ms of pre-roll** in the ring buffer so you never clip the first syllable of a sentence. This is the single most common bug in VAD-gated recorders.
- A silence gap > 3 s closes the current segment and starts a new one. Segments become natural RAG chunk boundaries later (§6.2) — the DSP layer is quietly doing work for the retrieval layer.

**Codec: Opus @ 16 kHz, 24 kbps VBR, complexity 3.** The storage math is decisive:

| Format | Bytes/sec | Per hour | 8-h day (with VAD @ 50 %) |
|---|---|---|---|
| Raw PCM s16le | 32,000 | **115 MB** | 460 MB |
| IMA-ADPCM (4:1) | 8,000 | 28.8 MB | 115 MB |
| **Opus 24 kbps** | **3,000** | **10.8 MB** | **43 MB** |
| Opus 16 kbps | 2,000 | 7.2 MB | 29 MB |

Opus is ~11× smaller than PCM, which converts directly into 11× faster sync and 11× less SD wear.

> **Fallback plan:** if Opus encoding measures worse than ~15 mA average or cannot sustain real-time at complexity 3, drop to **IMA-ADPCM**. It is trivially cheap (a few instructions per sample), still gives 4:1, and the phone can transcode it to WAV easily. **Benchmark this in Phase 0 before committing** — it is the single biggest firmware unknown.

### 2.4 On-device storage format (crash-safe by construction)

The device will lose power mid-write. Design for it rather than hoping.

Each recording session is a directory:

```
/sd/rec/<session_ulid>/
   ├── meta.json      # written FIRST (start time, codec, rate, device id, schema ver)
   ├── audio.ogg      # append-only, framed
   └── state          # single byte: OPEN | SEALED | SYNCED  (atomic 1-byte write)
```

**Framing inside `audio.ogg`** — every frame is self-describing so a truncated file is still recoverable:

```
[u32 magic 'VFRM'][u32 seq][u64 t_offset_ms][u16 payload_len][u8 payload[]][u32 crc32]
```

On boot, the device scans for `state == OPEN` sessions, walks frames until CRC fails or magic is missing, truncates there, and seals. **You lose at most the final frame (20–60 ms), never the session.** This is the same principle as a write-ahead log, and it is why append-only framing beats writing a proper Ogg container live.

**Encrypt at rest.** AES-256-GCM per frame, key derived from a device secret in encrypted NVS. A stolen or desoldered SD card must be worthless — this device follows people into private conversations, and physical loss is a realistic threat.

**Wear & capacity:** 32 GB SD ≈ 740 h of Opus audio. Sync will drain it long before it fills, but implement a ring-buffer eviction policy (oldest `SYNCED` session deleted first) plus a hard "stop recording at 95 % full and alert via BLE" guard.

### 2.5 Power budget

| State | Current | Notes |
|---|---|---|
| Deep sleep (idle, RTC only) | ~15 µA | Months of standby |
| BLE advertising, 1 s interval | ~2 mA avg | Waiting for the phone |
| Recording (I2S + VAD + Opus + periodic SD flush) | **~45–55 mA** | Dominant state |
| Wi-Fi TX burst during sync | 150–250 mA | Short, infrequent — this is why Wi-Fi stays off except during transfer |

**1000 mAh → ~18–20 h continuous recording.** Batch SD writes into 32–64 KB blocks; per-frame writes murder both battery and flash endurance.

### 2.6 Device security

- **Secure Boot v2 + Flash Encryption** burned into eFuse for production units. Non-negotiable for a device that stores private conversations.
- **Per-device Ed25519 identity keypair**, generated on first boot, private key in encrypted NVS, never leaves the chip. Used for pairing attestation (§3.5).
- **Signed OTA with A/B partitions and automatic rollback.** An unbootable image after a bad OTA means an RMA; the rollback path is the cheapest insurance you will ever write.
- **Privacy mute** = double-press cuts mic power via a load switch (hardware, not a software flag) and turns the LED amber. Users must be able to trust it physically.

---

## 3. Part B — Device ↔ Phone Transport

### 3.1 Why a hybrid BLE + Wi-Fi transport

The instinct is "it's a wearable, use BLE for everything." Run the numbers and that falls apart:

| Transport | Realistic throughput | Time to move 1 h of Opus audio (10.8 MB) |
|---|---|---|
| BLE GATT notifications (MTU 247) | ~30–80 kbps | **18–48 min** ❌ |
| BLE L2CAP CoC + 2M PHY + DLE | ~150–500 kbps | **3–10 min** ⚠️ |
| **Wi-Fi SoftAP + HTTP** | **2–5 MB/s** | **2–5 s** ✅ |
| USB CDC (wired) | ~5–10 MB/s | ~1–2 s ✅ |

A user who records a 3-hour conference does not want to stand next to their phone for 30 minutes. But Wi-Fi costs 150–250 mA, so leaving it on all day is equally unacceptable.

**Decision — use each radio for what it is good at:**

- **BLE is always-on.** Discovery, control, status (battery, free space, pending bytes), time sync, small metadata. Costs ~2 mA.
- **Wi-Fi is spun up on demand,** only when there is a real backlog to move, then torn down immediately.
- **USB is the escape hatch** for huge backlogs and for users who left the device in a drawer for a month.

**The handshake:**

```
1. Device advertises over BLE (1 s interval, ~2 mA)
2. Phone connects → reads Status characteristic → sees pending_bytes = 47 MB
3. pending_bytes > 2 MB threshold?
      NO  → stream it over BLE L2CAP right now, done. (Small syncs stay cheap.)
      YES → continue:
4. Phone writes a 32-byte random WPA2 password to the WifiHandoff characteristic
   (the BLE link is already encrypted + bonded, so this is safe)
5. Device brings up SoftAP "vaani-<id>" with that password, replies with IP + port
6. Phone joins via WifiNetworkSpecifier  ← see §3.3, this API detail is critical
7. HTTP bulk transfer with Range resume
8. Phone sends per-file SHA-256 ACKs; device deletes only after ACK (§3.4)
9. Device tears down SoftAP, returns to BLE-only
```

### 3.2 BLE GATT service specification

Custom 128-bit service `0000A100-...`. Write this spec down before either side is coded — it is the contract between two teams / two toolchains.

| Characteristic | UUID suffix | Props | Payload |
|---|---|---|---|
| `DeviceInfo` | A101 | Read | fw_version, hw_rev, device_id, protocol_ver |
| `Status` | A102 | Read, **Notify** | battery_pct, charging, sd_free_bytes, **pending_files**, **pending_bytes**, recording_state, unix_time |
| `TimeSync` | A103 | Write | phone's epoch_ms + tz_offset; device computes and stores drift |
| `Manifest` | A104 | Read, Notify | Paged list of sealed sessions: `{session_id, start_ms, duration_ms, bytes, sha256, state}` |
| `Control` | A105 | Write | `START_SYNC`, `ACK_SESSION(id)`, `DELETE_SESSION(id)`, `WIFI_UP`, `WIFI_DOWN`, `FACTORY_RESET`, `IDENTIFY` (blink LED) |
| `WifiHandoff` | A106 | Write, Read | ← SSID+PSK ; → assigned IP + port + transfer token |
| `L2capPsm` | A107 | Read | PSM for the CoC channel used on the small-sync path |

**Required BLE tuning** — defaults will make this crawl:

- Request **MTU 517** and **Data Length Extension** (PDU 251).
- Request **2M PHY** (`BluetoothGatt.setPreferredPhy`).
- Connection interval: **7.5–15 ms during transfer**, relax to 200–500 ms when idle. Android only *requests* intervals via `requestConnectionPriority(CONNECTION_PRIORITY_HIGH)` — the peripheral must accept them.
- Use **L2CAP CoC** (`BluetoothDevice.createL2capChannel`, API 29+) for bulk, never a firehose of GATT notifications. CoC gives you a real credit-based flow-controlled stream socket.

### 3.3 The Wi-Fi handoff detail that breaks naive implementations

> **This is the single most common bug in ESP32↔Android projects.**

When the phone joins the ESP32's SoftAP, that network has **no internet**. If you join it the old way (`WifiManager.enableNetwork`), Android routes *all* traffic to it, the OS notices there is no internet, and then either (a) silently switches back to cellular and your socket dies, or (b) keeps it and every other network call in the app fails — including the Sarvam uploads you are about to make.

**The correct approach on Android 10+ (API 29):**

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid("vaani-a3f2")
    .setWpa2Passphrase(pskFromBle)     // random, delivered over encrypted BLE
    .build()

val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)  // ← tell Android we know
    .setNetworkSpecifier(specifier)
    .build()

connectivityManager.requestNetwork(request, object : NetworkCallback() {
    override fun onAvailable(network: Network) {
        // CRITICAL: bind the OkHttp socket factory to THIS network only.
        // Cellular stays alive for Sarvam API calls at the same time.
        val client = okHttpBuilder.socketFactory(network.socketFactory).build()
        downloadSessions(client)
    }
})
```

Two things this buys you: the device transfer is pinned to Wi-Fi, and **cellular stays available**, so the app can transfer from the device and upload to Sarvam concurrently. On Android 10+ this also shows a system dialog the user approves once.

**Alternative worth considering:** if the user's home Wi-Fi credentials are provisioned onto the device, have the ESP32 **join the existing LAN** instead of hosting an AP. Then both are on the same network, mDNS (`_vaani._tcp`) handles discovery, no dialog, no network switching at all. Best UX at home; SoftAP remains the fallback everywhere else. **Support both.**

### 3.4 Sync protocol — durable and idempotent

**HTTP endpoints served by the ESP32:**

```
GET  /v1/manifest                     → JSON list of sealed sessions
GET  /v1/session/{id}/meta            → session metadata
GET  /v1/session/{id}/audio           → audio bytes; supports Range: bytes=N-
POST /v1/session/{id}/ack             → body: {"sha256": "..."}  → device marks SYNCED
DELETE /v1/session/{id}                → only permitted after ACK
GET  /v1/health                       → liveness + free space
```

**Two-phase commit — the rule that guarantees zero data loss:**

```
Phase 1  Phone downloads bytes → writes to app-private storage → fsync
         → computes SHA-256 → compares to manifest hash
         → inserts recording row in Room with state = PERSISTED   ← durable NOW
Phase 2  Phone POSTs /ack with the hash
         → device flips state to SYNCED
         → device deletes only during a later garbage-collection pass
```

**The device never deletes audio it has not been told is safely on the phone.** If the phone dies between phases, the next sync re-downloads; the phone sees the content hash already exists in Room and no-ops. The whole protocol is idempotent because everything is keyed by content hash.

**Resume:** `Range` requests plus a per-session partial-download record mean a sync interrupted at 90 % resumes at 90 %, not 0 %.

**Garbage collection on device:** delete `SYNCED` sessions oldest-first when free space < 20 %. Never delete `OPEN` or `SEALED`.

### 3.5 Pairing and key management

```
FIRST PAIR
  1. User long-presses button → device enters pairing mode (LED pulses blue, 60 s window)
  2. BLE LESC pairing with NUMERIC COMPARISON — 6 digits shown in app, confirmed on device
     ⚠️ NOT "Just Works": that is vulnerable to active MITM during pairing, and this device
        carries private conversations. The numeric compare is worth the extra tap.
  3. Bond stored on both sides
  4. Device sends Ed25519 public key; app pins it (trust-on-first-use)
  5. App generates a 32-byte pairing secret → sends over the encrypted link → device stores in NVS
  6. Per-session transfer keys = HKDF(pairing_secret, session_nonce)

EVERY RECONNECT
  - Bonded LE Secure Connections re-establish automatically
  - App verifies the pinned Ed25519 key still matches → mismatch = hard fail + warn the user
```

### 3.6 Build a device simulator in week 1

**This is the highest-ROI item in the entire plan.**

Write a ~300-line Python or Kotlin program that implements the exact HTTP endpoints in §3.4 and serves synthetic Opus sessions over plain TCP.

Why it pays for itself many times over:
- Android development is **completely unblocked by firmware schedule**. Two workstreams run in parallel from day one.
- You can generate pathological cases on demand that are near-impossible to produce on real hardware: corrupt CRCs, mid-transfer disconnects, a 200-file backlog, a 2-hour recording, clock skew, duplicate session IDs.
- CI can run full end-to-end sync tests with no hardware in the loop.
- New contributors can build the app without owning a device.

Ship the simulator as a `:tools:device-sim` Gradle module and a `docker run` one-liner.

---

## 4. Part C — Android Application

### 4.1 Technology stack

| Concern | Choice | Rationale |
|---|---|---|
| Language / UI | **Kotlin 2.x + Jetpack Compose + Material 3** | Standard; Compose keeps the state-heavy sync/processing UI manageable |
| Min SDK | **29 (Android 10)** | Hard floor: `BluetoothDevice.createL2capChannel` and `WifiNetworkSpecifier` both need 29. Going lower means writing two transports. |
| Target SDK | Latest (36) | Play requirement |
| Architecture | **MVVM + UDF**, Clean layering | Repositories expose `Flow`, ViewModels expose immutable `UiState` |
| DI | **Hilt** | First-party, works with WorkManager via `HiltWorker` |
| Async | **Coroutines + Flow** | `Flow` from Room makes the UI reactive to pipeline progress for free |
| Relational DB | **Room + SQLCipher** | Encrypted at rest; schema export enables migration tests |
| Vector DB | **ObjectBox** (HNSW) | §6.4 |
| Full-text | **SQLite FTS5** | Built in, BM25 included |
| Embeddings | **ONNX Runtime Mobile** (XNNPACK) | §6.3 |
| Background | **WorkManager + Foreground Service** | §5.7 |
| Network | **Retrofit + OkHttp + kotlinx.serialization** | SSE for streaming chat |
| Audio | **Media3/ExoPlayer** (playback), **MediaCodec** (Opus decode) | Opus decode is native — no bundled libopus needed |
| Prefs | **DataStore (Proto)** | |
| Testing | JUnit5, Turbine, Robolectric, MockWebServer, Compose UI test | §10 |

### 4.2 Module graph

Multi-module from day one. Retrofitting module boundaries later is expensive, and here it also enforces a genuinely useful rule: **`:feature:*` modules cannot see the Sarvam client or the crypto layer.**

```
:app  ────────────────────────────────────────────────┐
                                                      │
:feature:onboarding :feature:device  :feature:library │
:feature:note       :feature:chat    :feature:tasks   │
:feature:settings                                     │
        │                                             │
        ▼                                             ▼
:domain  (use cases, pure Kotlin models, no Android)
        │
        ▼
:data:device   ── BLE, Wi-Fi, sync protocol, pairing
:data:audio    ── blob store, transcode, playback
:data:notes    ── Room, repositories, FTS5
:data:vector   ── embedder, ObjectBox index, retrieval
:data:sarvam   ── API client, key vault, limiter, budget
:data:pipeline ── the durable job engine
        │
        ▼
:core:common  :core:crypto  :core:designsystem  :core:ui  :core:testing
:tools:device-sim   (§3.6)
```

### 4.3 Data model (Room)

```sql
-- ============ DEVICE & AUDIO ============
device(id PK, name, ed25519_pubkey, pairing_secret_alias, last_seen_at,
       fw_version, battery_pct, sd_free_bytes)

recording(id PK, device_id FK, session_ulid UNIQUE,
          started_at_utc, tz_offset_min, duration_ms,
          codec, sample_rate, sha256 UNIQUE,          -- content address
          bytes, storage_uri, dek_alias,               -- per-file encryption key
          sync_state,        -- DISCOVERED|DOWNLOADING|PERSISTED|ACKED
          pipeline_state)    -- see §5.2

-- ============ TRANSCRIPTION ============
transcript(id PK, recording_id FK, provider, model, mode, language_code,
           full_text, created_at, cost_micros, sarvam_job_id)

transcript_segment(id PK, transcript_id FK, idx,
                   start_ms, end_ms, speaker_id, text, confidence)
  INDEX(transcript_id, start_ms)

-- ============ DERIVED KNOWLEDGE ============
note(id PK, recording_id FK, title, summary_short, summary_long,
     language_code, created_at, updated_at, pinned, archived,
     user_edited)        -- true once the user edits; never overwrite on re-run

note_key_point(id PK, note_id FK, idx, text, source_segment_id FK)

todo(id PK, note_id FK, text, due_at, priority, status,
     assignee, source_segment_id FK, completed_at)
  INDEX(status, due_at)

tag(id PK, name UNIQUE)              note_tag(note_id, tag_id)
entity(id PK, type, name)            note_entity(note_id, entity_id, mention_count)
  -- entity.type ∈ {PERSON, ORG, PLACE, PRODUCT}

-- ============ RAG ============
chunk(id PK, note_id FK, kind, idx, text, lang,
      start_ms, end_ms, token_count, embed_model_id, embedded_at)
  -- kind ∈ {TRANSCRIPT, SUMMARY, KEY_POINT, TODO, TITLE, TRANSLATION}
  INDEX(note_id, idx)

chunk_fts USING fts5(text, content='chunk', content_rowid='id',
                     tokenize='trigram')   -- trigram handles Devanagari + Latin

-- vectors live in ObjectBox, keyed by chunk.id (§6.4)

-- ============ ENGINE & OPS ============
job(id PK, entity_type, entity_id, stage, state, attempt,
    next_run_at, last_error, params_hash, created_at, updated_at)
  INDEX(state, next_run_at)
  UNIQUE(entity_type, entity_id, stage, params_hash)   -- idempotency

api_usage(id PK, ts, endpoint, model, audio_seconds,
          tokens_in, tokens_out, cost_micros, http_status)

chat_session(id PK, title, created_at)
chat_message(id PK, session_id FK, role, content, citations_json, ts)
```

**Two schema decisions worth calling out:**

1. **`recording.sha256` is UNIQUE.** The content hash *is* the identity. Re-downloading the same audio can never create a duplicate note — the whole pipeline inherits idempotency from this one constraint.
2. **`note.user_edited`.** Once a user fixes a title or edits a summary, re-running the LLM stage (after a model upgrade, say) must never silently clobber their work. Merge into new fields and surface a diff instead.

### 4.4 Screens

| Screen | Contents |
|---|---|
| **Onboarding** | Permissions → BLE pair (numeric compare) → API key entry + live validation → consent/legal notice (§8.4) |
| **Home / Library** | Notes list grouped by day; per-item pipeline status chips (`Syncing 34 %`, `Transcribing`, `Ready`); pull-to-sync |
| **Device** | Battery, storage, pending backlog, last sync, firmware + OTA, Identify (blink), unpair |
| **Note detail** | Title · summary · key points · to-dos · tags · **transcript with speaker labels, tap-to-seek** · audio player · edit · export · delete |
| **Chat** | Streaming answers, source chips under each answer → tap = open note at the cited timestamp |
| **Search** | Unified semantic + keyword, filter chips (date, tag, person, has-todo); **works offline** |
| **Tasks** | All to-dos across notes; group by due date; swipe complete; back-link to source moment |
| **Settings** | API key mgmt, processing quality (Fast/Best), budget cap + usage graph, language defaults, local-only mode, export all, delete all |

**One UX principle that matters more than it sounds:** every note must be traceable back to the audio. Summary → key point → transcript line → audio timestamp. When an LLM gets something subtly wrong, the user needs a one-tap path to the ground truth. This is what makes the product trustworthy rather than merely impressive.

---

## 5. Part D — Ingest Pipeline & Sarvam Integration

### 5.1 Pipeline overview

```
 recording (PERSISTED)
      │
      ▼
 ① TRANSCODE ──────► normalized 16 kHz mono audio  (no-op if Opus uploads directly)
      │
      ▼
 ② ASR ────────────► transcript + segments + speaker_ids
      │              router: sync ≤30 s │ batch ≤2 h        (§5.5.2)
      ▼
 ③ TRANSLATE ──────► English mirror of the transcript      (cheap, big RAG win — §5.4.3)
      │
      ▼
 ④ ENRICH ─────────► title, summaries, key points, to-dos, tags, entities
      │              strict json_schema on sarvam-105b      (§5.5.4)
      ▼
 ⑤ CHUNK ──────────► RAG chunks from transcript + derived artifacts (§6.2)
      │
      ▼
 ⑥ EMBED ──────────► on-device ONNX vectors → ObjectBox + FTS5 (§6.3)
      │
      ▼
   READY  ✅
```

### 5.2 The durable job engine (the part that makes this production-ready)

A naive implementation chains coroutines and loses everything when the process dies. On Android the process **will** die — the OS kills it, the battery dies, the user swipes the app away, OEM battery managers are aggressive. A 2-hour recording that gets 90 % through transcription and then restarts from scratch is both a terrible experience and real money burned.

**Every stage transition is a row in the `job` table, committed before the work starts.**

```kotlin
enum class Stage { TRANSCODE, ASR, TRANSLATE, ENRICH, CHUNK, EMBED }
enum class JobState { PENDING, RUNNING, WAITING_REMOTE, SUCCEEDED, FAILED, DEAD_LETTER }

interface PipelineStage {
    val stage: Stage
    /** MUST be idempotent: safe to call twice with the same input. */
    suspend fun execute(ctx: JobContext): StageResult
}

sealed interface StageResult {
    data class Success(val output: Any?) : StageResult
    data class Retry(val after: Duration, val cause: Throwable) : StageResult
    /** Batch job submitted; park until the poller says it's done. */
    data class WaitRemote(val remoteId: String, val pollAfter: Duration) : StageResult
    data class Fatal(val cause: Throwable) : StageResult      // → DEAD_LETTER
}
```

**The five properties that make it safe:**

| Property | How |
|---|---|
| **Crash-safe** | State persisted *before* work begins. On boot, a reconciler sweeps `RUNNING` rows older than a lease timeout back to `PENDING`. |
| **Idempotent** | `UNIQUE(entity_type, entity_id, stage, params_hash)`. Re-running a completed stage is a cheap no-op. |
| **Resumable** | `WAITING_REMOTE` parks a Sarvam batch job with zero held resources. Survives reboot. |
| **Observable** | The `job` table is the progress UI's data source. `Flow<List<Job>>` → status chips. No separate progress tracking. |
| **Bounded** | Exponential backoff with jitter; after N attempts → `DEAD_LETTER` with the error surfaced to the user, plus a Retry button. |

**`params_hash` is what makes model upgrades sane.** It hashes `(model, mode, prompt_version, schema_version)`. Bump the prompt and only the ENRICH stage re-runs — expensive ASR results are reused. This turns "we improved the summarizer" from a full reprocess into a cheap targeted backfill.

### 5.3 Stage ① — Transcode

Only runs if the Opus probe (§0.4) fails. `MediaCodec` decodes Opus → PCM; write a 16 kHz mono s16le WAV header. Native decoder, no bundled libopus, ~20× realtime on a mid-range phone.

If the probe succeeds, upload the Opus bytes untouched — it is ~11× smaller than WAV, so **every upload gets 11× faster and the user's mobile data bill drops accordingly.** Worth the week-1 probe.

**Also in this stage:** split at VAD boundaries if the sync path is chosen, and normalize loudness (simple RMS gain) — quiet far-field speech transcribes measurably worse.

### 5.4 Stages ② and ③ — ASR and the translation trick

#### 5.4.1 Mode selection

Default to **`codemix`** for Indian users. Real speech in India is Hinglish, and `transcribe` mode on code-mixed audio produces awkward output. Expose in Settings:

| Setting | `mode` | For |
|---|---|---|
| Auto (default) | `codemix` | Mixed Hindi-English speech |
| Faithful | `verbatim` | Interviews, legal, research — keeps fillers |
| Clean | `transcribe` | Single-language dictation |
| Roman script | `translit` | Users who cannot read Devanagari |

#### 5.4.2 Diarization is batch-only — and that drives the routing policy

`with_diarization` exists on the **Batch API only**. Speaker labels are what turn a transcript into a *meeting* note, so this is not a minor feature. Therefore:

> **If the recording is multi-speaker, always use Batch — even for a 25-second clip.**

#### 5.4.3 The translation trick — and the pricing trap inside it

After transcription, produce an **English mirror** of the transcript, stored as `kind = TRANSLATION`
chunks alongside the original.

**Why:** the user speaks Hindi but often *searches in English* ("what did we decide about the
budget?"). Multilingual embedders handle cross-lingual matching only moderately well. Indexing an
English mirror means the English query matches English text directly — a large recall win (§6.2).

**How to produce it is where real money is at stake.** Sarvam's translate API is priced *per
character* (₹20 / 10 K chars), while the LLM is priced per *token*. For one hour of speech
(≈ 45 000 characters) the three routes are wildly different:

| Route | Unit price | Cost per hour of audio | vs. best |
|---|---|---|---|
| `POST /translate` (Mayura) | ₹20 / 10 K chars | **₹90** | 60× |
| Second ASR pass in `translate` mode | ₹30 / hour | **₹30** | 20× |
| **Fold it into the existing `sarvam-105b` enrichment call** | ₹29.28 in / ₹73.2 out per 1 M tok | **≈ ₹1.50** | ✅ **1×** |

> **Decision: emit the English mirror as an extra field in the ENRICH schema (§5.5.4).** The
> transcript is already in that prompt, so you pay only for the additional output tokens. This is
> roughly **60× cheaper than the dedicated translate endpoint** for the same job.
>
> Character-priced APIs and token-priced APIs are not comparable at a glance, and the intuitive
> choice ("use the translation API for translation") is the expensive one here. Keep `/translate`
> for *user-facing, on-demand* translation of a single note, where quality justifies the price and
> volume is tiny — not for bulk index-building.

**Never re-run ASR just to get translated text** unless you also need it as a quality cross-check;
a second audio pass costs 20× the LLM route.

### 5.5 The Sarvam integration layer

#### 5.5.1 Client design

```kotlin
interface SarvamClient {
    suspend fun transcribeSync(audio: File, opts: AsrOptions): SyncTranscript      // ≤30 s
    suspend fun createBatchJob(files: List<File>, opts: AsrOptions): BatchJobRef   // ≤20 files
    suspend fun batchStatus(jobId: String): BatchStatus
    suspend fun downloadBatchResults(jobId: String): List<DiarizedTranscript>
    suspend fun translate(text: String, from: String, to: String): String
    fun  chatStream(req: ChatRequest): Flow<ChatDelta>                             // SSE
    suspend fun <T> chatStructured(req: ChatRequest, schema: JsonSchema, out: KClass<T>): T
    suspend fun validateKey(): KeyValidation
}
```

Wrap it in **`ResilientSarvamClient`** which composes, in order: key injection → token-bucket limiter → budget guard → retry/backoff → circuit breaker → usage recorder. Each is independently testable, and the stages never think about any of it.

#### 5.5.2 The ASR routing policy

```kotlin
fun route(rec: Recording, quality: Quality): AsrRoute = when {
    // Diarization is batch-only, so multi-speaker forces batch at any length
    rec.expectsMultipleSpeakers          -> AsrRoute.Batch(diarize = true)

    rec.durationMs <= 30_000             -> AsrRoute.Sync

    // Short-to-medium + user wants speed: parallel 29 s windows with 2 s overlap.
    // Costs more requests against the 60/min budget but returns in seconds.
    rec.durationMs <= 5 * 60_000 &&
        quality == Quality.FAST          -> AsrRoute.ChunkedSync(
                                                windowMs = 29_000, overlapMs = 2_000)

    rec.durationMs <= 2 * 3600_000       -> AsrRoute.Batch(diarize = false)

    // Beyond the 2 h/file ceiling: split on VAD silence, submit as multiple files
    else                                 -> AsrRoute.BatchSplit(maxFileMs = 110 * 60_000)
}
```

**Chunked-sync stitching:** the 2 s overlap must be de-duplicated or you get repeated words at every boundary. Align the tail of window *N* against the head of window *N+1* by normalized token sequence (longest common subsequence over the overlap region) and cut at the best match. Without this, every 29 seconds produces a visible glitch. *Note that chunked-sync also loses diarization and slightly hurts accuracy at boundaries — which is why `Quality.BEST` always prefers Batch.*

**Batch packing:** a job takes up to 20 files. Accumulate pending recordings and submit them together — 20 recordings become **1** job creation call instead of 20, which matters against the 20 req/min batch limit and reduces polling load 20×.

#### 5.5.3 Batch job lifecycle on a phone

The API supports webhook callbacks, but **a phone has no public URL**, so poll. WorkManager's periodic minimum is 15 minutes — far too coarse. Use a self-rescheduling chain of expedited one-shot workers:

```
submit → store job_id, state = WAITING_REMOTE
       → schedule BatchPollWorker
           poll cadence: 10 s → 20 s → 40 s → 60 s → 60 s … (cap 60 s, +jitter)
           on Completed → download results → parse diarized_transcript[] → advance
           on Failed    → classify → retry or DEAD_LETTER
           on timeout (> 2× expected)  → surface to user, keep polling slowly
```

While a sync is actively running, hold a **foreground service** with a progress notification so the OS does not kill polling mid-flight. When the app is backgrounded with jobs parked, fall back to WorkManager with `setBackoffCriteria` — a parked `WAITING_REMOTE` row holds no resources, so a delayed poll costs nothing but latency.

#### 5.5.4 Stage ④ — Enrichment with schema-guaranteed output

Sarvam supports `response_format: {type: "json_schema", json_schema: {..., strict: true}}`. **Use it.** The alternative — asking for JSON in the prompt and parsing whatever comes back — is the single largest source of flaky bugs in LLM apps.

```json
{
  "name": "note_extraction",
  "strict": true,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["title","summary_short","summary_long","key_points",
                 "todos","tags","entities","language_code","english_mirror"],
    "properties": {
      "title":         { "type": "string", "maxLength": 80 },
      "summary_short": { "type": "string", "maxLength": 280 },
      "summary_long":  { "type": "string" },
      "key_points": {
        "type": "array", "maxItems": 12,
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["text","source_start_ms"],
          "properties": {
            "text": { "type": "string" },
            "source_start_ms": { "type": "integer" }
          }
        }
      },
      "todos": {
        "type": "array", "maxItems": 20,
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["text","assignee","due_hint","priority","source_start_ms"],
          "properties": {
            "text":     { "type": "string" },
            "assignee": { "type": ["string","null"] },
            "due_hint": { "type": ["string","null"],
                          "description": "Verbatim time phrase, e.g. 'by Friday'. Do NOT resolve to a date." },
            "priority": { "enum": ["LOW","MEDIUM","HIGH"] },
            "source_start_ms": { "type": "integer" }
          }
        }
      },
      "tags":     { "type": "array", "maxItems": 8, "items": { "type": "string" } },
      "entities": {
        "type": "array",
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["name","type"],
          "properties": {
            "name": { "type": "string" },
            "type": { "enum": ["PERSON","ORG","PLACE","PRODUCT"] }
          }
        }
      },
      "language_code": { "type": "string" },
      "english_mirror": {
        "type": "array",
        "description": "English translation of each transcript segment, same order and count as the input segments. Used only for cross-lingual search indexing (§5.4.3).",
        "items": {
          "type": "object", "additionalProperties": false,
          "required": ["start_ms","text_en"],
          "properties": {
            "start_ms": { "type": "integer" },
            "text_en":  { "type": "string" }
          }
        }
      }
    }
  }
}
```

**Four deliberate choices in that schema:**

1. **`source_start_ms` on every key point and to-do.** This is what makes every extracted claim traceable back to the exact audio moment. Prompt the model with timestamped segments (`[00:04:12] speaker_1: ...`) so it can fill this in accurately. It is also a cheap hallucination detector: a `source_start_ms` outside the recording's duration means the item was invented — drop it.
2. **`due_hint` is a verbatim string, not a date.** LLMs are unreliable at resolving "next Tuesday" — they do not reliably know today's date or the user's timezone. Capture the phrase, then resolve it **in Kotlin** against the recording's actual timestamp and timezone. Deterministic, testable, correct.

> **Implemented with `kotlinx-datetime`, not `java.time`.** `:domain` is a pure-Kotlin module precisely so the iOS path stays open (CLAUDE.md rule 3), and `java.time` is JVM-only — it would have made `:domain` unportable at the first date. `kotlinx-datetime` covers everything the resolver needs. One trap when using it: `Month` is a typealias for `java.time.Month` on the JVM, so `.value` compiles here and fails everywhere else. Use the `number` extension.
3. **`additionalProperties: false` and `maxItems` everywhere.** Strict mode plus bounded arrays prevents both schema drift and a runaway model generating 400 to-dos from a rambling recording.
4. **`english_mirror` rides along in the same call.** Per §5.4.3 this is ~60× cheaper than the translate endpoint, because the transcript is already in the prompt and you pay only for output tokens. If the note is already in English, instruct the model to return an empty array — don't pay to translate English into English.

**Long recordings exceed a comfortable prompt** even at 128 K context. For transcripts over ~40 K tokens, map-reduce: summarize per ~10-minute window, then reduce the window summaries into the final note. Keep `reasoning_effort` at `low` for map steps and `medium` for the reduce step — that is where the quality actually shows up.

#### 5.5.5 Rate limiting, retries, and cost control

Rate limits are **per account, not per key**, and refill token-bucket style. Mirror that model client-side:

```kotlin
// One shared limiter per endpoint family. Tiers configurable in Settings.
val limiters = mapOf(
    STT_SYNC   to TokenBucket(perMinute = 60),
    STT_BATCH  to TokenBucket(perMinute = 20),
    CHAT       to TokenBucket(perMinute = 40),   // sarvam-105b Starter
    TRANSLATE  to TokenBucket(perMinute = 60),
)
val inFlight = Semaphore(4)   // separate concurrency cap; don't rely on the limiter alone
```

**Retry policy — classify before you retry:**

| Condition | Action |
|---|---|
| `429 rate_limit_exceeded_error` | Honour `Retry-After` if present, else exponential backoff 2ⁿ + jitter, **max 6 attempts**. Do **not** count against the job's own attempt budget — it is not the job's fault. |
| `401 / 403` | **Never retry.** Invalidate key, notify user, pause the whole pipeline. Retrying a bad key just burns battery. |
| `413` payload too large | Fatal for this route → re-route to `BatchSplit` |
| `5xx`, timeouts, connection reset | Retry with backoff, max 4 |
| `400` schema/validation | `DEAD_LETTER` — a bug, not a transient fault. Log the request (key redacted) for diagnosis. |

Add a **circuit breaker**: 5 consecutive failures on an endpoint family → open for 60 s. Prevents a Sarvam outage from draining the battery via a retry storm.

**Cost control — mandatory, since this is the user's own money:**
- Record every call in `api_usage` (audio seconds, tokens, estimated cost).
- User-configurable **monthly budget cap**; at 80 % warn, at 100 % pause the pipeline and require explicit confirmation to continue.
- Show a live usage graph in Settings. Sarvam gives ₹100 free credits, so a new user should be able to see exactly where those went.
- **Pre-flight estimate:** before a large backlog is processed, show "≈ 4.2 h of audio → ≈ ₹X. Process now / Wi-Fi only / Later."

### 5.6 API key vault

The user pastes a key that bills their account. Treat it accordingly.

```
Entry screen  →  FLAG_SECURE on the window (blocks screenshots + screen recording)
              →  disable autofill; clear clipboard after paste
              →  immediate validation via a cheap live call (validateKey())
                 ↓
Storage       →  AES-256-GCM DEK generated inside AndroidKeyStore (StrongBox when available)
              →  key material is non-exportable — it never exists in app memory as raw bytes
              →  ciphertext + IV stored in Proto DataStore
                 ↓
Use           →  decrypt just-in-time in an OkHttp Interceptor; never hold in a field or singleton
                 ↓
Hygiene       →  OkHttp logging interceptor redacts `api-subscription-key` and `Authorization`
              →  android:allowBackup="false" + dataExtractionRules exclude the key blob
              →  crash reporter scrubs headers; never log request headers in release
              →  optional: setUserAuthenticationRequired(true) → biometric gate before each session
              →  "Delete key" wipes ciphertext AND the Keystore entry
```

> ⚠️ **Do not use `EncryptedSharedPreferences` / Jetpack Security Crypto** — it is deprecated and unmaintained. Write a ~100-line AndroidKeyStore wrapper, or use Google Tink directly.

**Honest threat-model note for the docs:** on a rooted or malware-compromised device, a determined attacker with the app's UID can invoke the Keystore to decrypt. Keystore prevents *key extraction*, not *key use* by the app's own process. This is the correct and standard protection level for a client-held API key — but say so plainly in your privacy policy rather than implying it is unbreakable.

### 5.7 Background execution (and surviving Android OEMs)

| Work | Mechanism |
|---|---|
| BLE scan for the paired device | `PendingIntent`-based scan filter — OS-level, no running process needed |
| Active sync (device → phone) | **Foreground service**, type `dataSync` + `connectedDevice`, with progress notification |
| Pipeline stages | `CoroutineWorker` chains; constraints: network for ASR, **battery-not-low + charging** for EMBED |
| Batch polling | Expedited self-rescheduling workers (§5.5.3) |
| Reconciliation on boot | `BOOT_COMPLETED` receiver → sweep stale `RUNNING` jobs → requeue |

**The OEM problem is real and will generate your worst bug reports.** Xiaomi, Oppo, Vivo, OnePlus, Realme, Samsung, Huawei and Honor all ship aggressive background killers — and these are exactly the phones your Indian user base is on. Mitigations:

1. On first sync, detect the manufacturer and show a **one-time, dismissible** guide to whitelisting the app (deep-link to the OEM's autostart settings via the known intents from the *dontkillmyapp.com* project).
2. Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — with a clear justification screen, since Play scrutinizes this permission.
3. **Design so it does not matter:** every stage is resumable, so being killed costs latency, never data. Test this explicitly with `adb shell am kill` mid-pipeline (§10).
4. Never rely on a background process for correctness — reconcile on next launch.

---

## 6. Part E — On-Device RAG & Vector Database

### 6.1 The constraint that defines this layer

**Sarvam has no embeddings endpoint.** The API surface is STT, TTS, translate, transliterate, language-ID, chat, and vision — no `/embeddings`.

That rules out the obvious design (call a cloud embedder) and forces embeddings on-device. This turns out to be the better architecture anyway:

| | On-device embeddings (forced) | Cloud embeddings (unavailable) |
|---|---|---|
| Privacy | Note text **never leaves the phone** for indexing | Every note shipped to a third party |
| Offline | Semantic search works on a plane | Dead without network |
| Cost | ₹0, forever | Per-token, forever |
| Latency | ~30–80 ms/chunk locally | Network round-trip per chunk |
| Cost of a re-index | Free — run it overnight while charging | Re-pay for the entire corpus |

The only real price is a ~35 MB model download and some background CPU. Worth it.

**Resulting privacy story, which is a genuine selling point:** audio goes to Sarvam for transcription and enrichment (unavoidable, and disclosed). Everything after that — indexing, embedding, search, ranking — happens on the phone. Only the final chat *answer* needs the network, and even then only the retrieved snippets are sent, not the corpus.

### 6.2 Chunking strategy

Generic fixed-size chunking wastes the structure you already have. Saaras v3 batch returns `diarized_transcript[]` with per-chunk timestamps and `speaker_id` — real semantic boundaries, free.

**Index six chunk kinds, not one:**

| `kind` | Source | Size | Why it earns its place |
|---|---|---|---|
| `TRANSCRIPT` | Merged ASR segments | 200–400 tok, 15 % overlap | The detail layer — verbatim facts |
| `TRANSLATION` | English mirror (§5.4.3) | same | Cross-lingual recall for English queries over Hindi notes |
| `SUMMARY` | `summary_short` + `summary_long` | 1–2 chunks | **Wins "what was that meeting about?" queries** — a gist question matches a gist chunk, never a transcript fragment |
| `KEY_POINT` | Each key point | 1 chunk each | Dense, high-signal, pre-distilled |
| `TODO` | Each to-do | 1 chunk each | Makes "what do I owe Ravi?" a direct hit |
| `TITLE` | Note title + date | 1 chunk | Cheap high-precision anchor |

**This multi-representation approach is the highest-leverage RAG decision in the document.** Different question types have different natural answer granularities; indexing only raw transcript makes abstractive questions fail no matter how good your embedder is.

**Building `TRANSCRIPT` chunks:**

```
1. Start from ASR segments (already speaker- and pause-delimited)
2. Greedily merge adjacent segments until ~300 tokens
3. Force a break when: speaker changes AND current ≥ 150 tokens
                    OR silence gap > 3 s AND current ≥ 150 tokens
4. Carry ~15 % overlap from the previous chunk (preserves cross-boundary context)
5. Record start_ms / end_ms  ← what makes citations tappable
```

**Prepend a context header to every chunk before embedding** — this is contextual retrieval, and it is a large, cheap accuracy win:

```
[2026-08-28 · Standup with Ravi and Priya · #project-atlas]
Speaker 1: ...actual chunk text...
```

A bare chunk saying "yes, let's push it to next sprint" is unretrievable in isolation. With the header it matches "what did we decide in the Atlas standup?" Costs ~20 tokens per chunk and meaningfully lifts recall.

### 6.3 Embedding model

| Model | Dim | Size (int8) | Multilingual / Indic | Verdict |
|---|---|---|---|---|
| **multilingual-e5-small** | **384** | **~35 MB** | 100+ langs, solid Hindi | ✅ **Recommended** — best size/quality/Indic balance |
| paraphrase-multilingual-MiniLM-L12-v2 | 384 | ~35 MB | 50 langs | Good fallback, slightly weaker retrieval |
| multilingual-e5-base | 768 | ~110 MB | Better | Upgrade path if quality falls short; 2× storage & latency |
| LaBSE | 768 | ~470 MB | Excellent | ❌ Too large for a phone APK/download |
| all-MiniLM-L6-v2 | 384 | ~23 MB | ❌ English-only | ❌ Disqualified — Hindi is a core requirement |

**Choice: `multilingual-e5-small`, int8-quantized, ONNX Runtime Mobile with the XNNPACK execution provider.**

Implementation notes that matter:

- **E5 requires prefixes.** Documents must be embedded as `"passage: <text>"` and queries as `"query: <text>"`. Skipping this silently degrades retrieval — the model was trained with them and it is an easy, invisible bug.
- **Download the model on first run**, don't ship it in the APK. Verify SHA-256, store in app-private storage, and make it resumable.
- **Tokenizer:** HuggingFace `tokenizers` via JNI, or a Kotlin SentencePiece implementation. Must match the model's vocab exactly — a mismatched tokenizer produces plausible-looking garbage vectors, which is a miserable bug to track down.
- **Normalize to unit length** at write time, then cosine similarity reduces to a dot product.
- **Batch of 8–16 chunks** per inference call; roughly 3–5× the throughput of one-at-a-time.
- **Run under `charging + battery-not-low` constraints** for backlogs. A single new note (~8 chunks) is under a second and can run immediately; a 500-note first-time import should wait for the charger.
- **Try NNAPI, but measure.** On many mid-range devices NNAPI is *slower* than XNNPACK for small transformers and has correctness quirks across vendors. Default to XNNPACK; make NNAPI an opt-in flag validated per-device.

### 6.4 Vector store

| Option | Index | Pros | Cons |
|---|---|---|---|
| **ObjectBox** | **HNSW, native** | Mature Android/Kotlin API, ACID, HNSW built in, filtered vector search, fast CRUD | Core is closed-source (free for mobile — **check the licence against your distribution model**) |
| sqlite-vec | Brute force + partitions | Fully open (MIT/Apache-2.0), tiny, rides your existing SQLite | Must build/bundle the extension for Android ABIs; brute-force degrades past ~100 k vectors |
| Room + JNI hnswlib | HNSW | Total control | You own index persistence, crash recovery, and concurrency — significant work |
| ChromaDB / FAISS / pgvector | — | — | ❌ Server-side. Not applicable on-device. |

**Choice: ObjectBox**, behind an interface so it stays replaceable:

```kotlin
interface VectorIndex {
    suspend fun upsert(items: List<VectorItem>)
    suspend fun search(query: FloatArray, k: Int, filter: VectorFilter? = null): List<ScoredId>
    suspend fun delete(chunkIds: List<Long>)
    suspend fun rebuild(progress: (Float) -> Unit)
    val modelId: String; val dim: Int
}
```

If the ObjectBox licence conflicts with how you ship, swapping in a `SqliteVecIndex` becomes a contained change rather than a rewrite. Given this is a fully offline, single-user app, the mobile-free tier fits — **but confirm it before you build on it.**

**Sizing math (10 000 notes, ~8 chunks each = 80 000 vectors):**

```
fp32   384 × 4 B × 80 000  = 123 MB     ← unnecessary
int8   384 × 1 B × 80 000  =  31 MB     ← use this (scalar quantization)
HNSW graph, M = 16         ≈  10 MB
chunk text (~1.2 KB each)  ≈  96 MB
                             ───────
                            ~137 MB total
```

Comfortable. Search latency at this scale is single-digit milliseconds — the on-device query embedding (~30–80 ms) dominates, not the search.

### 6.5 Hybrid retrieval

Pure vector search fails on exact terms — invoice numbers, unusual proper nouns, acronyms. Pure keyword search fails on paraphrase. **Run both and fuse.**

```
                       user query
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
   ① QUERY UNDERSTANDING          (parallel branches)
      → semantic query
      → keyword terms                     │
      → structured filters ───────────────┤ (applied to both)
              │                           │
      ┌───────┴────────┐         ┌────────┴────────┐
      ▼                ▼         ▼                 ▼
 ② VECTOR (HNSW)             ③ LEXICAL (FTS5 BM25)
    top-50                        top-50
      └───────┬────────────────────────┘
              ▼
   ④ RECIPROCAL RANK FUSION  →  top-20
              ▼
   ⑤ RERANK (optional cross-encoder)  →  top-8
              ▼
   ⑥ CONTEXT ASSEMBLY (neighbour expansion, dedupe, token budget)
              ▼
   ⑦ GENERATE — sarvam-105b, streaming, citations required
```

**Reciprocal Rank Fusion** — pick this over score normalization. Vector cosine scores and BM25 scores live on incompatible scales, and normalizing them is fiddly and corpus-dependent. RRF uses only *ranks*, needs no tuning, and is robust:

```kotlin
fun rrf(rankings: List<List<Long>>, k: Int = 60, weights: List<Double>): Map<Long, Double> =
    buildMap {
        rankings.forEachIndexed { listIdx, ranked ->
            ranked.forEachIndexed { rank, id ->
                merge(id, weights[listIdx] / (k + rank + 1.0), Double::plus)
            }
        }
    }
// Start at weights = [1.0 vector, 1.0 lexical]; tune against the golden set (§10.4)
```

**FTS5 tokenizer choice:** use `trigram`. The default `unicode61` tokenizer handles Latin scripts but segments Devanagari poorly, and `porter` stemming is English-only. Trigram is script-agnostic, handles Hindi and Hinglish, and tolerates the spelling variation that transliterated text produces. It costs more index space — accept that.

**Filter before ANN when the filter is selective.** "Notes from last week tagged #atlas" might be 40 chunks out of 80 000; brute-forcing 40 vectors beats an HNSW traversal and is exact. Rule of thumb: if the filter yields < 1 000 candidates, skip the ANN index entirely.

### 6.6 Query understanding

Turn natural language into a structured query with one cheap `sarvam-105b` call using strict JSON schema (`reasoning_effort: low`):

```json
{
  "semantic_query": "decisions about the marketing budget",
  "keyword_terms": ["budget", "marketing"],
  "filters": {
    "date_from": "2026-08-24", "date_to": "2026-08-31",
    "people": ["Ravi"], "tags": [], "chunk_kinds": ["SUMMARY","KEY_POINT"],
    "todo_status": null
  },
  "intent": "SEARCH"     // SEARCH | SUMMARIZE | LIST_TODOS | TIMELINE | COMPARE
}
```

Two important details:

- **Resolve relative dates in Kotlin, not in the LLM.** The model returns `"last week"`; `kotlinx-datetime` converts it against the device clock and timezone. Same reasoning as `due_hint` in §5.5.4 — deterministic beats plausible.
- **Rewrite follow-ups into standalone queries.** In a chat, "what about the other one?" is meaningless to a retriever. Feed the last 2–3 turns into the rewriter so it emits a self-contained query. Skipping this is why multi-turn RAG chats degrade after turn two.

**Offline fallback:** no network → skip the LLM, use regex date parsing plus the raw query for both branches. Search still works; only the generated answer needs connectivity.

### 6.7 Context assembly and citations

```
1. Take fused top-8
2. Neighbour expansion ("small-to-big"): for each TRANSCRIPT hit, pull chunk idx±1
   → retrieval precision stays high, but the LLM sees enough context to answer
3. Dedupe: cap at 3 chunks per note so one verbose meeting can't crowd out the rest
4. Sort chronologically (not by score) — temporal order helps the model reason about sequence
5. Render each with a citation handle:
      [C3 | 2026-08-28 14:32 | Standup with Ravi | 00:14:20–00:15:05]
      Speaker 1: ...
6. Budget: cap context at ~8 K tokens. 128 K is available, but more context means
   slower, costlier, and — past a point — *less* accurate answers.
```

**System prompt requires citation of the handles:**

```
You answer strictly from the user's notes provided below.
Cite every factual claim with its handle, e.g. [C3].
If the notes do not contain the answer, say so plainly — do not use outside knowledge.
Answer in the same language the user asked in.
```

The app parses `[C3]` markers, maps them to `chunk_id`, and renders tappable chips. **Tapping a chip opens the note and seeks the audio player to `start_ms`.** This closes the loop from AI answer → source claim → the actual recorded voice, and it is the feature that makes users trust the product.

### 6.8 Grounding guard and offline behaviour

**Refuse rather than hallucinate.** If the top fused score is below a calibrated threshold, do not call the LLM at all:

> "I couldn't find anything about that in your notes. Want me to search more broadly?"

An honest miss costs far less trust than a confident fabrication about the user's own life. Always render retrieved sources even when the answer is uncertain, so the user can judge for themselves.

**Offline matrix:**

| Capability | Offline? |
|---|---|
| Semantic + keyword search | ✅ Fully — on-device embeddings and index |
| Browse notes, play audio, edit to-dos | ✅ |
| Chat answer generation | ❌ Needs `/v1/chat/completions` |
| Transcription of new audio | ❌ Queued; runs automatically on reconnect |

Offline chat degrades gracefully: show the ranked retrieved chunks as an extractive result set with a "Generate answer when online" action.

### 6.9 Re-indexing and model migration

Embedding models will be upgraded. Design for it now, because retrofitting is painful:

- Every vector stores `embed_model_id` and `dim`.
- Vectors from different models are **never compared** — that produces silently wrong results, the worst failure mode there is.
- A model change enqueues a `ReindexWorker`: builds the new index in a **shadow** ObjectBox entity, swaps atomically on completion, then drops the old one. Search stays available on the old index throughout.
- Reindexing 80 k chunks ≈ 40–90 min on a mid-range phone. Charging-only, resumable, with a progress notification.
- Chunk **text** is preserved separately from vectors, so re-embedding never requires re-transcribing. This is exactly why `chunk.text` is stored in Room rather than only in the vector store.

---

## 7. Where the Sarvam MCP Server Actually Fits

Restating §0.3 with the practical workflow, because this is the most likely thing to be misunderstood.

**MCP does not go in the Android app.** `sarvam-mcp` is a Python package (`uvx sarvam-mcp`) that speaks stdio to a desktop MCP client. Android has no Python runtime, and even if it did, MCP would be a needless proxy in front of a REST API the app calls directly.

**MCP goes on your laptop, and it is genuinely useful there:**

```jsonc
// .mcp.json  — Claude Code / Cursor / Zed
{
  "mcpServers": {
    "sarvam": {
      "command": "uvx",
      "args": ["sarvam-mcp"],
      "env": { "SARVAM_API_KEY": "${SARVAM_API_KEY}" }
    }
  }
}
```

| Development task | Why MCP beats writing throwaway scripts |
|---|---|
| Compare `codemix` vs `transcribe` vs `verbatim` on your own recordings | Iterate conversationally in seconds; no script-edit-rerun loop |
| Tune the extraction prompt + JSON schema (§5.5.4) | Get the schema right *before* it is embedded in Kotlin |
| Build the ASR golden set (§10.4) | Transcribe reference clips, hand-correct, commit as fixtures |
| Sanity-check diarization on real multi-speaker audio | Confirm speaker counts and `num_speakers` behaviour |
| Estimate real token costs on real transcripts | Feeds the §11 cost model with measured, not guessed, numbers |
| Validate `saaras:v4` vs `v3` before switching defaults | A/B two models without touching app code |

**Rule of thumb:** MCP for *exploration and prompt engineering*; REST for *the shipped product*. The artifacts that cross from one to the other are the prompt text, the JSON schema, and the test fixtures — all of which live in version control.

---

## 8. Security, Privacy & Compliance

### 8.1 Data flow and where trust boundaries sit

```
Audio on device      → AES-256-GCM at rest on SD                (§2.4)
Device → phone       → BLE LESC encrypted / WPA2 + app-layer AEAD (§3.5)
Audio on phone       → AES-256-GCM, DEK in AndroidKeyStore
Phone → Sarvam       → TLS 1.3.  ⚠️ AUDIO LEAVES THE DEVICE HERE — disclose prominently
Notes/transcripts    → SQLCipher-encrypted Room DB
Embeddings + search  → 100 % on-device, never transmitted        (§6.1)
Chat                 → only retrieved snippets are sent, never the whole corpus
```

### 8.2 Controls checklist

| Layer | Control |
|---|---|
| Device | Secure Boot v2, flash encryption, per-device Ed25519 identity, hardware mic-mute switch |
| Pairing | LESC **numeric comparison** (not Just Works), bonding, TOFU public-key pinning |
| Phone at rest | SQLCipher DB, per-file AEAD for audio, Keystore/StrongBox-held keys |
| API key | §5.6 — Keystore-wrapped, `FLAG_SECURE`, redacted logs, optional biometric gate |
| App manifest | `allowBackup="false"`, `dataExtractionRules` excludes audio + DB + key blob |
| Network | TLS 1.3; certificate pinning on `api.sarvam.ai` with a **backup pin and a kill switch** |
| Logging | No transcript/note content in release logs; crash reporter scrubs headers and bodies |
| Screens | `FLAG_SECURE` on key entry always; user-toggleable app-wide |
| Export/Delete | Full JSON + audio export; "Delete everything" wipes DB, blobs, vectors, and Keystore entries |

> **On certificate pinning:** pin, but ship a backup pin and a remote kill switch. A pin that outlives its certificate bricks the app for every user simultaneously, and shipping a fix through Play review takes days.

### 8.3 Threat model — stated honestly

| Threat | Mitigation | Residual risk |
|---|---|---|
| Lost device / desoldered SD | Frame-level AES-GCM, key in encrypted NVS | Low |
| BLE eavesdropping / MITM at pairing | LESC numeric comparison + bonding | Low |
| Lost/stolen phone | SQLCipher + Keystore + device lock | Low **if** the user has a screen lock — prompt if none |
| **Rooted or malware-infected phone** | Keystore prevents key *extraction*, not key *use* by the app's own UID | **Accepted — state this plainly in the privacy policy** |
| Malicious app reading the DB | App-private storage + SQLCipher | Low |
| Sarvam-side handling of audio | Their terms; user's own key and account | **Accepted — must be disclosed** |
| Backup/ADB extraction | `allowBackup=false`, extraction rules | Low |

### 8.4 Recording consent — a real compliance requirement, not a footnote

**This is the highest legal risk in the product and it is not a technical problem.** A wearable that records ambient audio captures people who never consented.

- **Consent law varies by jurisdiction.** Many places require *all-party* consent for recording a conversation. India's telegraph/IT rules, the EU (GDPR + national laws), and US two-party-consent states (California, Florida, Illinois, Pennsylvania and others) all differ.
- **Required product measures:**
  1. A clear, unavoidable onboarding screen explaining the user's legal responsibility to obtain consent — with an explicit acknowledgement, logged.
  2. A **visible recording indicator on the hardware** (LED) that cannot be disabled in software.
  3. An optional periodic audible chime while recording (default on in strict jurisdictions).
  4. A hardware privacy mute (§2.6).
  5. Easy per-recording delete, plus "delete everything".
- **Get actual legal review before launch** in every market you ship to. This is not a "figure it out later" item — it can be the difference between a product and a lawsuit.

### 8.5 Data protection regime

- **India DPDP Act 2023** applies, and Sarvam being an Indian provider keeps processing in-country — a genuine advantage worth stating in marketing.
- **GDPR** if you ship to the EU: lawful basis, data minimization, right to erasure (your "delete everything" satisfies this), and note that third parties captured in recordings are also data subjects.
- **Google Play:** the Data Safety form must accurately declare that audio is sent to a third party for processing. Recording permissions get elevated scrutiny — expect review friction and prepare a demo video.
- **No analytics on content.** Aggregate, content-free telemetry only (§9), and make it opt-in.

---

## 9. Observability

You cannot debug what you cannot see, and this pipeline spans two processors and a cloud API.

**On-device (app):**
- A **Pipeline Inspector** debug screen: every `job` row, its stage, attempt count, last error, timings. This will save more engineering hours than any other single screen.
- Structured local logs with a ring buffer, exportable as a redacted bundle from Settings → Help. Users can send you a real diagnostic instead of "it didn't work".
- Per-stage latency histograms kept locally; surfaced in the inspector.

**Telemetry (opt-in, content-free):**
- Stage success/failure rates, retry counts, error taxonomy.
- Sync throughput by transport (BLE vs Wi-Fi), transfer failure rate.
- ASR route distribution (sync vs chunked vs batch) and time-to-note.
- Crash-free rate, ANR rate, cold-start time.
- **Never** transcript text, note content, queries, or the API key.

**Device side:**
- A rolling diagnostic buffer in NVS: boot reason, brownouts, SD write errors, VAD ratio, encoder underruns, last N sync results — uploaded over BLE on connect, shown in the Device screen.
- Watchdog with a panic handler that records the crash reason and reboots into a safe state.

---

## 10. Testing & Evaluation

### 10.1 Test pyramid

| Level | Coverage |
|---|---|
| **Unit** (JVM, fast) | Chunk merging, overlap de-dup/stitching, RRF, retry classification, `due_hint` date resolution, frame CRC, token bucket, cost math |
| **Integration** | Room migrations (schema-export based), WorkManager chains, `SarvamClient` against MockWebServer incl. 429/401/5xx/malformed-JSON |
| **End-to-end (no hardware)** | `:tools:device-sim` (§3.6) → full sync → pipeline → note → RAG answer |
| **Instrumented** | Compose UI tests, permission flows, foreground-service behaviour |
| **Hardware-in-loop** | Nightly: real ESP32 + Raspberry Pi driving power cycles and audio playback |
| **Firmware unit** | ESP-IDF host tests for framing, CRC recovery, VAD, ring buffer |

### 10.2 Chaos tests — run these in CI, they are where real bugs live

```
□ Kill the app mid-transfer      (adb shell am kill)      → resumes from byte offset
□ Kill the app mid-ASR-batch                              → re-polls, no duplicate job
□ Airplane mode mid-upload                                → retries, no data loss
□ Power-cut the device mid-write                          → last frame truncated, session recovers
□ Corrupt an SD frame CRC                                 → truncate at corruption, keep prior audio
□ Wi-Fi drops during handoff                              → falls back to BLE, no wedge
□ Fill the disk during download                           → clean error, no partial commit
□ Revoke the API key mid-pipeline                         → pauses, prompts, no retry storm
□ Clock skew ±48 h between device and phone               → timestamps corrected via TimeSync
□ Sync 200 pending sessions at once                       → batch packing holds; no OOM, no rate-limit storm
□ Same recording delivered twice                          → sha256 unique constraint no-ops it
```

### 10.3 The 2-hour recording test

A single dedicated test, because it exercises every limit simultaneously: batch file ceiling, `BatchSplit` routing, map-reduce enrichment, chunk-count scaling, embedding backlog, and UI progress over a long duration. Run it every release.

### 10.4 Evaluation harness (treat AI quality as a test, not a vibe)

**ASR quality**
- 30–50 hand-corrected reference clips: clean speech, noisy café, multi-speaker, heavy Hinglish, soft/far-field.
- Track **WER / CER** per bucket. Gate model changes (`saaras:v3` → `v4`) on it.

**RAG quality — the golden set**
- 80–120 `(query, expected_note_ids)` pairs covering: factual lookup, gist/abstractive, to-do retrieval, person-scoped, date-scoped, cross-lingual (English query → Hindi note), and **negatives that should return nothing**.
- Metrics: **Recall@5**, **MRR@10**, and answer **faithfulness** (does every claim trace to a cited chunk?).
- **Run this in CI on every change to chunking, embedding, or fusion weights.** Retrieval quality regressions are silent — nothing crashes, results just quietly get worse. This harness is the only thing that catches them.
- Include the negative set explicitly: it is what verifies the grounding guard (§6.8) refuses instead of inventing.

**Extraction quality**
- 40 transcripts with hand-written expected to-dos and key points.
- Track precision/recall on to-do extraction, and validate that every `source_start_ms` falls inside the recording.

---

## 11. Cost Model

> **These figures are enforced by tests.** `SarvamPricing` in `:data:sarvam` holds the same rates,
> and `MonthlyCostModelTest` recomputes every line of the table below from them — so this document
> cannot drift away from what the app actually charges. It caught a real error on its first run:
> the chat line had been costed with output tokens priced at the *input* rate, understating it by
> ~9%.

**Verified Sarvam rates (₹, as of 2026-08-31 — re-check before launch, these moved on 2026-08-05):**

| Service | Rate |
|---|---|
| STT standard | **₹30 / hour** of audio (billed per second, rounded up per request) |
| STT + diarization | **₹45 / hour** (+50 %) |
| `sarvam-105b` input | ₹29.28 / 1M tokens |
| `sarvam-105b` **cached** input | ₹10.98 / 1M tokens (~62 % cheaper) |
| `sarvam-105b` output | ₹73.2 / 1M tokens |
| Translate (Mayura) | ₹20 / 10K characters — **expensive per §5.4.3** |
| Free credits | ₹100 for new accounts |

**Modelled user: 1 hour of *speech* per day** (VAD already stripped silence, so ~2–3 h of wall-clock wearing).

| Item | Per day | Per month |
|---|---|---|
| STT with diarization (1 h @ ₹45) | ₹45.00 | ₹1,350 |
| Enrichment: ~25 K in + 6 K out | ₹1.17 | ₹35 |
| English mirror (extra output, §5.4.3) | ₹0.81 | ₹24 |
| Chat: ~20 queries × (8 K in + 500 out) | ₹5.42 | ₹163 |
| Embeddings (on-device) | **₹0** | **₹0** |
| Search / retrieval (on-device) | **₹0** | **₹0** |
| **Total** | **≈ ₹52.39** | **≈ ₹1,572** |

**ASR is ~86 % of the bill. Every meaningful cost lever is on that line:**

| Lever | Saving | Trade-off |
|---|---|---|
| **VAD on device** (§2.3) | **40–70 %** | None — this is the single biggest lever, and it is already in the design |
| Diarization only when multi-speaker | 33 % on those files | Needs a speaker-count heuristic; default off for solo dictation |
| User-set "process only starred recordings" | Large | Manual curation |
| Skip enrichment for < 60 s clips | Small | Voice memos get a transcript but no summary |

**LLM-side levers:**
- **Prompt caching** cuts input cost 62 %. The system prompt + schema is identical on every enrichment call — structure requests so that prefix is cacheable. Meaningful at volume.
- Keep `reasoning_effort: low` for map steps, `medium` only for the final reduce.
- Cap retrieved context at ~8 K tokens (§6.7) — bigger context is slower, costlier, *and* often less accurate.

**Product implications, given that the user pays:**
1. Show a **cost estimate before processing a backlog** — "4.2 h of audio ≈ ₹189. Process now / Wi-Fi only / Later."
2. Ship the **monthly budget cap** (§5.5.5) enabled by default with a sensible starting value.
3. Make VAD aggressiveness a user setting, framed as **"Battery & Cost saver"**, not as a DSP parameter.
4. ₹100 free credits ≈ **2.2 hours** of diarized audio. Onboarding should make that concrete so the first session doesn't silently exhaust it.

---

## 12. Delivery Roadmap

Estimates assume **1 firmware engineer + 1–2 Android engineers**. Adjust to your team.

### Phase 0 — De-risk (1–2 weeks) · *do this before committing to anything else*

The entire purpose of this phase is to kill unknowns early, while changing course is still cheap.

| # | Spike | Question it answers | If it fails |
|---|---|---|---|
| 1 | **Opus upload probe** | Does `/speech-to-text` accept Ogg Opus, or must the phone transcode? | Keep the transcode stage (§5.3); uploads get ~11× bigger |
| 2 | **ESP32-S3 Opus encode benchmark** | Real-time at 24 kbps? What is the mA cost? | Fall back to IMA-ADPCM (4:1 instead of 11:1) |
| 3 | **Sarvam quality on your real audio** | Is far-field wearable audio (not clean mic input) usable? WER by mode? | Reconsider mic placement, add AGC/beamforming, or a second mic |
| 4 | **Wi-Fi handoff on 3 real phones** | Does `WifiNetworkSpecifier` + bound socket work on Xiaomi/Samsung/Pixel? | Fall back to BLE L2CAP; re-plan sync UX around slower transfers |
| 5 | **Embedding model on a mid-range phone** | ms/chunk and MB for `multilingual-e5-small` int8; Hindi retrieval sanity | Try MiniLM-L12, or e5-base if quality is the problem |
| 6 | **Batch API end-to-end** | Real latency for a 30-min file; diarization accuracy | Adjust UX expectations for time-to-note |

**Deliverables:** a written findings doc, plus the **device simulator (§3.6)** — which unblocks Android work permanently.

> Spikes 1, 2 and 4 can invalidate significant parts of this design. That is exactly why they run first, and why they are cheap.

### Phase 1 — Thin end-to-end slice (3–4 weeks)

The goal is one complete vertical path, not breadth. **Audio recorded on the device becomes a note on the phone.**

- Firmware: I2S capture → PCM → SD, framed + CRC. *No VAD, no Opus yet.*
- Transport: BLE GATT service + L2CAP bulk transfer only.
- Android: pairing, API key vault (§5.6), sync, sync-path ASR (≤30 s clips), a note list.
- **Ship it internally.** Everything after this is improvement on a working system.

### Phase 2 — Make it real (4–5 weeks)

- Firmware: VAD + segmentation, Opus encoding, power tuning, deep sleep.
- Transport: Wi-Fi handoff + resumable HTTP + two-phase commit.
- Android: **the durable job engine (§5.2)** — this is the phase's centrepiece; batch ASR routing + polling; enrichment with strict JSON schema; to-dos and tags; note detail with tap-to-seek.
- **Gate:** all chaos tests in §10.2 pass.

### Phase 3 — RAG (3–4 weeks)

- Chunking across all six kinds (§6.2), on-device ONNX embedder, ObjectBox HNSW, FTS5.
- Hybrid retrieval + RRF, query understanding, citation chips with audio deep-link.
- **The golden-set eval harness (§10.4) is built in this phase, not after it.** Without it you cannot tell whether retrieval changes help or hurt.

### Phase 4 — Production hardening (3–4 weeks)

- Secure boot + flash encryption, signed OTA with rollback.
- SQLCipher, cert pinning + kill switch, full export/delete.
- OEM battery-killer handling, budget caps, cost estimates.
- Consent/legal flows (§8.4) — **with actual legal review**.
- Play Store listing, Data Safety declaration, demo video for review.
- Accessibility pass, dark theme, string extraction for localization (hi/en at minimum).

### Phase 5 — Beyond v1

Speaker identification (name the diarized speakers) · phone-mic recording via the realtime WebSocket API · Google Tasks / Calendar export · home-screen widgets · Wear OS companion · multi-device · agentic RAG using Sarvam tool-calling ("find every commitment I made to Ravi and draft a status update") · optional encrypted cloud backup.

**Total to a hardened v1: roughly 14–19 weeks.**

---

## 13. Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Far-field wearable audio transcribes poorly | **High** | **High** | Phase 0 spike 3. Better mic (ICS-43434), AGC, VAD tuning, possible dual-mic. *This is the top product risk — bad ASR makes every downstream feature worthless.* |
| R2 | OEM battery managers kill background sync | **High** | Medium | Foreground service, whitelist guidance, and — crucially — a design where being killed costs only latency (§5.7) |
| R3 | Opus encoding too heavy for ESP32-S3 | Medium | Medium | Phase 0 spike 2; ADPCM fallback |
| R4 | Wi-Fi handoff unreliable across Android OEMs | Medium | **High** | Phase 0 spike 4; BLE L2CAP fallback; LAN-join mode as the preferred path at home |
| R5 | User's Sarvam costs surprise them | Medium | **High** | Budget caps on by default, pre-flight estimates, VAD, transparent usage UI (§11) |
| R6 | Sarvam pricing/API changes | Medium | Medium | Rates already rose ~7× on 2026-08-05. Keep model names and rates **remote-configurable**; abstract behind `SarvamClient` |
| R7 | Recording consent / legal exposure | Medium | **Critical** | §8.4 — hardware indicator, consent flow, legal review per market |
| R8 | ObjectBox licence conflicts with distribution | Low | Medium | `VectorIndex` interface (§6.4) makes sqlite-vec a contained swap — **verify the licence in Phase 0** |
| R9 | Battery life below expectations | Medium | Medium | Aggressive deep sleep, VAD gating, batched SD writes; larger cell if needed |
| R10 | RAG quality disappoints on real notes | Medium | **High** | Multi-representation chunking (§6.2), hybrid retrieval, and the golden set to actually measure it |
| R11 | Data loss between device and phone | Low | **Critical** | Two-phase commit (§3.4); the device never deletes unacknowledged audio |
| R12 | Play Store rejection over audio recording | Medium | Medium | Accurate Data Safety form, clear disclosure, demo video, prominent in-app disclosure |

---

## 14. Decisions You Need to Make

These are genuine forks where your context matters more than a default. Everything else in this document has a recommendation you can simply take.

| # | Decision | Options | Default if you don't decide |
|---|---|---|---|
| D1 | **Form factor** | Pendant · clip-on · desk puck · pocket recorder | Drives mic placement, battery size, and R1 |
| D2 | **Always-on vs. push-to-record** | Always-on (better capture, worse battery + legal exposure) vs. button (safer, misses moments) | **Push-to-record for v1** — much lower legal and battery risk |
| D3 | **Primary market** | India-only · India + diaspora · global | Determines consent-law scope (§8.4) and language priorities |
| D4 | **Hardware plan** | Off-the-shelf devkit · custom PCB · white-label | Devkit for Phases 0–2; custom PCB is a 12+ week parallel track |
| D5 | **Whose API key** | User's own (as specified) · your key with a backend · hybrid | User's own — as designed here. *Note it caps the mass-market audience: most consumers won't create a Sarvam account.* Revisit for v2. |
| D6 | **Open-source the firmware?** | Yes / no | Affects secure-boot key handling and the ObjectBox licence question (R8) |
| D7 | **Minimum Android version** | 29 (as designed) · 26 with a degraded transport | 29 — going lower means writing a second transport |

> **D5 deserves the most thought.** "User brings their own API key" is excellent for privacy, cost, and shipping speed — there is no backend to build, secure, or pay for, which is why this design has none. But it is a real adoption ceiling: asking a consumer to sign up at dashboard.sarvam.ai and paste a key will lose most of them. If this is a personal/prosumer tool, it is the right call. If it is a consumer product, plan a v2 backend that proxies Sarvam with your key and bills through the app — and know that this adds auth, abuse prevention, and per-user cost accounting to your scope.

---

## 15. Appendix

### 15.1 Suggested repository layout

```
vaani/
├── firmware/                      # ESP-IDF project
│   ├── main/
│   │   ├── audio_capture.c        # T1: I2S DMA
│   │   ├── vad_segmenter.c        # T2
│   │   ├── encoder_store.c        # T3: Opus + AES-GCM + SD
│   │   ├── ble_service.c          # T4: GATT (§3.2)
│   │   ├── wifi_bulk.c            # T4: SoftAP + HTTP (§3.4)
│   │   └── power.c                # T5
│   ├── components/                # libopus, webrtc_vad
│   └── test/                      # host tests
├── android/
│   ├── app/
│   ├── core/{common,crypto,designsystem,ui,testing}/
│   ├── data/{device,audio,notes,vector,sarvam,pipeline}/
│   ├── domain/
│   ├── feature/{onboarding,device,library,note,chat,tasks,settings}/
│   └── tools/device-sim/          # §3.6 — build this first
├── eval/
│   ├── asr_golden/                # reference clips + corrected transcripts
│   ├── rag_golden/                # (query, expected_note_ids) pairs
│   └── run_eval.py                # CI-invoked
├── docs/
│   ├── ARCHITECTURE.md            # this document
│   ├── PROTOCOL.md                # BLE + HTTP wire spec (§3.2, §3.4)
│   └── PROMPTS.md                 # versioned prompts + JSON schemas
└── .mcp.json                      # §7 — dev-time Sarvam MCP
```

### 15.2 Sarvam call examples

```bash
# Sync STT — files ≤ 30 s only
curl -X POST https://api.sarvam.ai/speech-to-text \
  -H "api-subscription-key: $SARVAM_API_KEY" \
  -F "file=@clip.wav" \
  -F "model=saaras:v3" \
  -F "mode=codemix" \
  -F "language_code=hi-IN"

# Batch job — up to 20 files, 2 h each, diarization available here only
curl -X POST https://api.sarvam.ai/speech-to-text/job/v1 \
  -H "api-subscription-key: $SARVAM_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"saaras:v3","mode":"codemix","language_code":"hi-IN",
       "with_diarization":true,"num_speakers":3}'

curl -H "api-subscription-key: $SARVAM_API_KEY" \
  https://api.sarvam.ai/speech-to-text/job/v1/$JOB_ID/status

# Enrichment with strict structured output (§5.5.4)
curl -X POST https://api.sarvam.ai/v1/chat/completions \
  -H "api-subscription-key: $SARVAM_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"sarvam-105b",
       "messages":[{"role":"system","content":"..."},{"role":"user","content":"<transcript>"}],
       "reasoning_effort":"low",
       "response_format":{"type":"json_schema","json_schema":{"name":"note_extraction","strict":true,"schema":{...}}}}'
```

### 15.3 The retrieval path, end to end

```kotlin
suspend fun answer(query: String, history: List<ChatMessage>): Answer {
    // 1. Understand (§6.6) — rewrite follow-ups into standalone queries
    val q = queryUnderstanding.parse(query, history)      // falls back to regex offline

    // 2. Retrieve, both branches in parallel (§6.5)
    val (vec, lex) = coroutineScope {
        val v = async { vectorIndex.search(embedder.embedQuery(q.semanticQuery), k = 50, q.filters) }
        val l = async { fts.search(q.keywordTerms, k = 50, q.filters) }
        v.await() to l.await()
    }

    // 3. Fuse by rank, not score
    val fused = rrf(listOf(vec.ids(), lex.ids()), weights = listOf(1.0, 1.0))
        .entries.sortedByDescending { it.value }.take(20)

    // 4. Grounding guard (§6.8) — refuse rather than invent
    if (fused.firstOrNull()?.value ?: 0.0 < GROUNDING_THRESHOLD)
        return Answer.NotFound(suggestions = fused.take(3).toChunks())

    // 5. Assemble with neighbour expansion + citation handles (§6.7)
    val ctx = contextAssembler.build(fused.take(8), tokenBudget = 8_000)

    // 6. Generate, streaming, citations required
    return sarvam.chatStream(ChatRequest(
        model = "sarvam-105b",
        messages = systemPrompt(ctx) + history.takeLast(6) + user(query),
        reasoningEffort = "low",
    )).parseCitations(ctx)     // [C3] → tappable chip → note + audio seek
}
```

### 15.4 Reference links

- Sarvam docs — https://docs.sarvam.ai/
- STT overview (sync vs batch vs realtime) — https://docs.sarvam.ai/api-reference-docs/api-guides-tutorials/speech-to-text/overview
- Batch STT API — https://docs.sarvam.ai/api-reference-docs/api-guides-tutorials/speech-to-text/batch-api
- Chat completions — https://docs.sarvam.ai/api-reference-docs/api-guides-tutorials/chat-completion/overview
- Rate limits — https://docs.sarvam.ai/api/getting-started/ratelimits
- Pricing — https://docs.sarvam.ai/api-reference-docs/pricing
- Official Sarvam MCP server — https://github.com/sarvamai/sarvam-mcp
- ObjectBox on-device vector search — https://docs.objectbox.io/on-device-vector-search
- OEM background-kill reference — https://dontkillmyapp.com

---

### Document summary

| Layer | Decision |
|---|---|
| **Device** | ESP32-S3 · I2S MEMS mic @16 kHz · VAD + Opus 24 kbps · AES-GCM on microSD · framed append-only log |
| **Transport** | BLE always-on for control + Wi-Fi on demand for bulk · two-phase commit · zero data loss |
| **App** | Kotlin/Compose · minSdk 29 · multi-module · Room+SQLCipher · durable job engine |
| **ASR** | `saaras:v3` (watch v4) · sync ≤30 s / batch ≤2 h · diarization is batch-only · `codemix` default |
| **Enrichment** | `sarvam-105b` with strict `json_schema` · English mirror folded in (~60× cheaper than `/translate`) |
| **RAG** | On-device `multilingual-e5-small` int8 · ObjectBox HNSW + FTS5 · RRF hybrid · citations that seek the audio |
| **MCP** | Developer tool on your laptop — **not** in the app |
| **Cost** | ≈ ₹1,572/month at 1 h speech/day; ASR is 86 % of it; VAD is the biggest lever |

**Start with Phase 0.** Six spikes, one to two weeks, and they can invalidate parts of this document while that is still cheap.

*End of document.*
