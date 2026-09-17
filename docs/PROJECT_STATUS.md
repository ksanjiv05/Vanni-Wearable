# Vaani — Project Status

**Last updated:** 2026-09-17 · **Branch:** `main` · **Latest commit:** `561d262`

This is the authoritative "what exists and works" reference. For the original design intent see
[ARCHITECTURE.md](ARCHITECTURE.md); for how to resume see [RESUME_HERE.md](RESUME_HERE.md).

Legend: ✅ built & **device-verified** · 🟡 built, not fully verified on hardware · ⚪ designed only.

---

## 1. Feature status

### Android app
| Area | Status | Notes |
|---|---|---|
| Multi-module Compose scaffold (build-logic, version catalog) | ✅ | `assembleDebug` green; 708 tasks |
| Design system (cream/coffee, sharp corners, custom icons) | ✅ | `:core:designsystem`; light theme forced |
| Domain seam (pure Kotlin models + ports) | ✅ | `:domain`; `feature`→`data` forbidden |
| 8 screens (library, note, chat, search, tasks, device, onboarding, settings) | ✅ | `:feature:*` |
| Room persistence + audio module | ✅ | `:data:database`, `:data:audio` |
| Durable ingest pipeline (WorkManager transcribe→enrich→note) | ✅ | `:data:pipeline`, `:data:work` |
| Engine seam + router (local / Sarvam per stage) | ✅ | `:data:ai`; ASR strict, enrich auto-selects |
| On-device ASR (sherpa-onnx Whisper AAR + real SherpaAsr) | ✅ | `:data:asr-local` (48MB AAR vendored) |
| On-device LLM enrichment (MediaPipe GenAI 0.10.35) | ✅ | Qwen/TinyLlama/Gemma downloads |
| Sarvam API backend (STT/enrich/translate) | ✅ | `:data:sarvam`; user's own key |
| Real RAG chat over notes | ✅ | keyword + lexical-vector fusion |
| Lexical vector search (`:data:vector`) | ✅ | 512-dim hashed n-gram embedder; typo/variant match. **Not** neural-semantic (onnxruntime Java API absent from sherpa AAR) — clean drop-in behind `Embedder` port later |
| Todo persistence (Room-backed) | ✅ | survives relaunch |
| Credentials encrypted (Keystore EncryptedSharedPreferences) | ✅ | migrated from plaintext DataStore |
| 121 unit tests | ✅ | pass; debug+release green |

### Wearable link (the recent workstream — see [WEARABLE.md](WEARABLE.md))
| Area | Status | Notes |
|---|---|---|
| Firmware: Wi-Fi SoftAP + HTTP REST | ✅ | `/api/info /files /file /upload /delete` |
| Firmware: BLE GATT server | ✅ | svc `6e40fda0-…`, chars INFO/CMD/DATA/STAT |
| Firmware: microSD + ST7735 TFT | ✅ | shared SPI bus, FreeRTOS mutex |
| Android `:data:device` (BLE + Wi-Fi transports) | ✅ | `DeviceLink` port, BleGattClient, WifiHttpClient, WifiApConnector |
| Encrypted BLE bond (Just Works) as root of trust | ✅ | SMP responder callbacks required (see gotchas) |
| Per-device Wi-Fi PSK + REST token in NVS | ✅ | no hardcoded secrets; provisioned via `CRED` over encrypted BLE |
| REST bearer-token auth (401 without token) | ✅ | upload gated at file-start |
| Wi-Fi-preferred bulk sync + BLE block-protocol fallback | ✅ | app joins AP itself, no host |
| Sync recordings → ingest pipeline → note | ✅ | content-addressed dedup; DB row `READY` + real transcript |
| Delete SD files (manual + auto-after-sync) | ✅ | trash icon + confirm dialog; auto-delete only after safe persist |
| Single `/vaani` folder (app shows only Vaani files) | ✅ | migrates legacy `/recordings` at boot |
| Wearable dashboard (app-link status, pending count, today's todos) | ✅ | `DISP` push + 15s heartbeat; long-todo 2-line wrap for first 2 |
| **On-wearable voice commands (mic capture)** | ⚪ | **designed only** — ESP-SR chosen; needs INMP441 mic + partition/IDF work. See voice HTML doc + RESUME_HERE |
| On-wearable mic recording to SD | ⚪ | wearable currently stores/serves files but can't capture its own audio yet |

---

## 2. Module map (Android)

```
domain/                 pure Kotlin — models (Note, Todo, Recording, DeviceInfo…), ports:
                          NotesRepository, NotesWriter, RecordingStore/Writer, DeviceLink,
                          PipelineEnqueuer, Embedder
data/ai                 EngineRouter, EncryptedSecrets, DataStore*Credentials (encrypted)
data/asr-local          sherpa-onnx Whisper AAR + SherpaAsr
data/audio              AudioBlobStore (content-addressed), decode
data/database           Room entities/DAOs, SQLite
data/device             BleGattClient, WifiHttpClient, WifiApConnector, DeviceLinkImpl,
                          WearableSyncManager, DeviceModule (Hilt)
data/notes              NotesRepository impl
data/pipeline           IngestPipeline (transcribe→enrich→note state machine)
data/sarvam             Sarvam API client + DTOs
data/vector             HashedNgramEmbedder (lexical), VectorModule
data/work               WorkManager workers
feature/*               Compose screens + ViewModels (device is the wearable UI)
app/                    nav shell, Hilt wiring (binds fakes/impls; injects WearableSyncManager)
```

**Dependency rule:** `feature` → `domain` only. `data` implements `domain` ports, bound in `:app`.
`:domain` has **no** Android dependencies.

---

## 3. Key decisions (and why)

- **WiFi primary for bulk audio, BLE for control + fallback, everything app-driven.** After a user
  correction (host must never join the ESP32 AP), the phone joins the AP itself via
  `WifiNetworkSpecifier`; only app sockets route to it (via `Network.socketFactory`, **not**
  `bindProcessToNetwork` — that leaked the whole app's internet).
- **Encrypted BLE is the root of trust.** WiFi PSK + REST token are per-device secrets handed to the
  phone only over the bonded/encrypted BLE `CRED` command. No hardcoded `vaani12345` anymore.
- **Opcode-framed BLE DATA channel.** Every notification is `[1-byte opcode][payload]`
  (OP_DATA/OP_END/OP_SZ/OP_ERR) so raw audio bytes can never be mistaken for a control marker.
- **Lexical vector search, not neural** — honest labeling; onnxruntime Java API isn't in the sherpa
  AAR. Neural E5 is a clean drop-in behind the `Embedder` port later.
- **Single `/vaani` SD folder** so the app browser shows only app data, never the whole card.
- **ESP-SR for future voice** (over Edge Impulse / TFLite) — see the voice HTML doc.

---

## 4. Test / build facts
- **App:** `source env.sh && cd android && ./gradlew assembleDebug` → green. 121 unit tests pass.
- **Firmware:** compiles at ~95% of the default partition (voice work will need a custom partition).
- **Devices:** Nothing Phone A063 (`com.vaani.app`); ESP32-S3 N16R8 on `/dev/ttyACM0` (`303a:1001`).

See [WEARABLE.md §gotchas](WEARABLE.md) for the hard-won BLE/flash/permission lessons, and
[RESUME_HERE.md](RESUME_HERE.md) for exact restart procedures.
