# Vaani Link — Wearable Deep Dive

Everything about the ESP32-S3 wearable and the phone↔wearable link. Firmware source:
`firmware/vaani_link/vaani_link.ino` (mirror of `~/Arduino/vaani_link/`). Android side: `:data:device`.

---

## 1. Hardware

- **Board:** ESP32-S3 **N16R8** — 16MB flash, 8MB OPI PSRAM, dual-core 240MHz, WiFi + BT5 LE.
  Native USB-JTAG (`303a:1001`) at `/dev/ttyACM0`. MAC `d4:05:92:7b:08:30`.
- **Display:** ST7735 128×160 SPI TFT (BLACKTAB), landscape 160×128.
- **Storage:** microSD SPI module on the shared SPI bus.

### Confirmed wiring (device-verified)
| Signal | GPIO | | Signal | GPIO |
|---|---|---|---|---|
| Shared SCLK | 12 | | TFT CS | 10 |
| Shared MOSI | 11 | | TFT DC (A0) | 17 |
| SD MISO | 13 | | TFT RST | 18 |
| SD CS | 14 | | TFT LED (backlight) | 21 |
| SD VCC | 5V | | TFT VCC | 3.3V |

Init: `SPIClass spiBus(HSPI); spiBus.begin(12,13,11,-1); SD.begin(14, spiBus, 4000000);`
TFT: `Adafruit_ST7735(&spiBus,10,17,18); tft.initR(INITR_BLACKTAB); setRotation(1)`.

### Free pins reserved for voice (planned)
Mic (INMP441, I2S0): SCK=4, WS=5, SD=6. Speaker (MAX98357A, I2S1): BCLK=7, LRC=15, DIN=16.
None conflict with TFT/SD/USB(19,20)/octal-PSRAM(33–37). See `docs/vaani-voice-command-architecture.html`.

---

## 2. Toolchain

- `arduino-cli` 1.5.1 standalone at `~/.local/bin` (no sudo). Core `esp32:esp32` 3.3.11.
  Libs: Adafruit ST7735/ST7789, GFX, BusIO, SD.
- **FQBN:** `esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,USBMode=hwcdc,CDCOnBoot=cdc`
- **Port is `root:dialout`** and the agent's shell may lack the group → wrap port ops in
  `sg dialout -c '...'` (re-`export PATH` inside). `sudo` needs an interactive password here.
- Read serial: `sg dialout -c 'stty -F /dev/ttyACM0 115200 raw -echo; timeout 8 cat /dev/ttyACM0'`.
  Loop prints `alive heap=… wifi=N ble=N reqs=N pend=N todos=N`.
- Reset to re-advertise: `esptool --chip esp32s3 -p /dev/ttyACM0 --after hard_reset chip_id`.

---

## 3. Transport model

**WiFi primary for bulk audio, BLE for control + fallback. Everything app-driven — the host never
joins the ESP32 AP.**

- **BLE GATT** service `6e40fda0-b5a3-f393-e0a9-e50e24dcca9e`:
  - `INFO` (fda1, read) · `CMD` (fda2, write) · `DATA` (fda3, notify) · `STAT` (fda4, notify)
  - All characteristics are **encryption-required** (bonded link only).
- **WiFi SoftAP** `Vaani-XXXX` (per-device MAC suffix) @ `192.168.4.1`, HTTP REST server.
  The app joins via `WifiNetworkSpecifier` + `requestNetwork` (one-time system dialog), and routes
  ONLY wearable HTTP through `Network.socketFactory` — the phone keeps its normal network.

### BLE command protocol (write UTF-8 to CMD)
| Command | Effect |
|---|---|
| `INFO` | device+SD JSON on DATA |
| `CRED` | `{ssid,psk,token}` (only reachable when bonded/encrypted) |
| `LIST <dir>` | tab-lines `name\tsize\tD|F` |
| `RN <path> <off> <len>` | block read: `OP_SZ(total)` + `OP_DATA…` + `OP_END` |
| `WRITE <path>` then `D:`+bytes… then `WEND` | write session (bytes may contain NULs) |
| `DEL <path>` | delete file |
| `DISP <conn> <pending>\t<todo1>\t<todo2>…` | push dashboard to TFT |

**DATA framing:** every notification is `[1-byte opcode][payload]` — `OP_DATA=0x01`, `OP_END=0x02`,
`OP_SZ=0x03`, `OP_ERR=0x04`. Structural, so raw audio can never collide with a control marker.

### REST endpoints (all require `Authorization: Bearer <token>`; 401 otherwise)
`GET /api/info` · `GET /api/files?path=` · `GET /api/file?path=` ·
`POST /api/upload?path=` (streaming, auth checked at file-start) · `GET /api/delete?path=`

---

## 4. Security model (v4)

The **encrypted BLE link is the root of trust**; WiFi + REST secrets derive from it.

- **Per-device secrets in NVS.** First boot generates a random 16-char WiFi PSK + 32-char REST token
  (`esp_random`), persisted in NVS namespace `vaani` (keys `psk`/`tok`). Never printed; survive
  reflash. To re-provision: erase NVS/full-erase flash + remove the phone's bond.
- **BLE bonding + encryption.** `BLESecurity` (SC + bonding, `ESP_IO_CAP_NONE` = Just Works) +
  per-char `ESP_GATT_PERM_*_ENCRYPTED`. Android auto-bonds on first encrypted access.
- **CRED provisioning.** Phone sends `CRED` over the bonded link → gets `{ssid,psk,token}`.
- **REST token auth.** Every `/api/*` needs the bearer token; upload validates at `UPLOAD_FILE_START`
  so an unauthorized body never touches SD.

App flow: BLE connect → bond → `CRED` → join WiFi with provisioned PSK → bearer token on all REST.

### ⚠️ Critical BLE-bonding gotchas (cost real debugging time)
1. **Firmware MUST `BLEDevice::setSecurityCallbacks(new SecCb())`** or Bluedroid silently ignores the
   pairing request → `SMP_RSP_TIMEOUT`, bond fails. `BLESecurity` alone is NOT enough. `SecCb`
   returns true from onConfirmPIN/onSecurityRequest/onAuthorizationRequest, 0 from onPassKeyRequest.
   Do **not** override `onAuthenticationComplete(esp_ble_auth_cmpl_t)` — type won't resolve in the
   `.ino` on core 3.3.11. `BLEDevice::setEncryptionLevel()`/`ESP_BLE_SEC_ENCRYPT` don't exist in 3.3.11.
2. **Android: on reconnect to a bonded device, wait ~1.5s for auto-encryption to settle BEFORE**
   discoverServices/MTU/notifications, else `smp_link_encrypted: SMP state machine busy` → encrypted
   chars fail → "No device info after pairing". `BleGattClient.connect()` does `ensureBonded()` +
   delay before discovery.
3. **Just Works still shows a "Tap to pair" consent** on Android (not silent). Deterministic path:
   pair once from Android Settings → Bluetooth → Pair new device → Vaani-XXXX. Then the app's
   `ensureBonded()` sees BOND_BONDED and goes straight to CRED. adb-scripted taps on that dialog are
   flaky; a manual tap is the reliable way in test.
4. If the phone shows the device but won't scan-find it, **cycle phone Bluetooth**
   (`adb shell cmd bluetooth_manager disable/enable`) — clears a stale scanner cache.

---

## 5. Sync flow (wearable → app → pipeline)

`DeviceLink.syncRecordings(deleteAfterSync)` → `WearableSyncManager.sync()`:
1. `listRecordings()` = `listFiles("/vaani")`, filter audio.
2. For each: `pullRecording()` (WiFi `readFile` if WiFi-active — near-instant; else BLE block-read
   `readBinary` with re-request on dropped blocks).
3. `AudioBlobStore.import()` (content-addressed, SHA-256) → `RecordingWriter.upsert(deviceId="wearable",
   QUEUED)` → `PipelineEnqueuer.enqueue`.
4. If `deleteAfterSync`: `deleteFile()` **only after** bytes are durably persisted (no data loss).

Idempotent: recording id = `rec-<sha.take(12)>`; re-syncing an identical file dedupes.
Verified E2E: recording pulled → DB `pipelineState=READY` + real note.

### BLE bulk-transfer lessons
- One-shot streamed reads DROP notifications on 100KB+ files. Fix = **block protocol** (`RN` →
  `OP_SZ` + blocks + `OP_END`); re-request any block whose received size ≠ requested. **480B blocks +
  8 retries** work (4096B still dropped).
- **CMD writes use WRITE_TYPE_NO_RESPONSE** + 35ms drain; the response callback congests behind the
  notification stream and times out.
- `notifyChunked()` sends ≤180B chunks with `delay()`; one-notify-per-item drops all but the first.

---

## 6. Wearable dashboard (informative without the phone)

The app pushes `DISP <conn> <pending>\t<todos…>` on connect and every **15s (heartbeat)** while
connected; firmware stores it and redraws the TFT (on the loop core; BLE-core sets a `dispDirty` flag
because the SPI bus is shared).

TFT shows:
- **Banner:** `APP LINKED` (green) if a push arrived <30s ago, else `WAITING APP` / `CONNECTING`.
  (Heartbeat < stale window keeps it green.)
- **`Notes to sync: N`** — pending recordings (orange if >0).
- **`TODAY`** — top 5 open todos, HIGH-priority first (app sorts `priority.ordinal` desc). The **first
  2** long todos wrap onto an indented 2nd line; #3–5 are single-line truncated with `~`.

App gathers this in `DeviceViewModel.gatherDashboard()`: pending from `NotesRepository.observeProcessing()`,
todos from `observeNotes().todos` filtered OPEN. Cap 48 chars/todo on the wire.

---

## 7. SPI-bus safety
SD runs on the BLE-callback core, TFT on the loop core, sharing HSPI. A single FreeRTOS `spiMutex`
guards **every** SD + TFT access. TFT draws never happen in BLE callbacks (loop-core only, via
`dispDirty`). Streaming `/api/upload` writes to SD as bytes arrive (no ~150KB RAM buffer). File
handles closed on every path; overlapping WRITE sessions rejected.

---

## 8. Files (Android `:data:device`)
- `domain/device/DeviceLink.kt` — port (scan/connect/info/list/read/write/delete/listRecordings/
  pullRecording/pushDisplay/syncRecordings) + `SyncProgress`.
- `ble/BleGattClient.kt` — scan/connect (stopScan+settle+retry, ensureBonded+encryption-settle),
  opcode-framed command/readBinary/writeSession, `@Volatile collector`, stale-gatt guard.
- `wifi/WifiApConnector.kt` — `WifiNetworkSpecifier`+`requestNetwork`, returns `Network` (no
  process-wide bind), onNetworkLost callback.
- `wifi/WifiHttpClient.kt` — OkHttp on `Network.socketFactory`, bearer token on every call.
- `DeviceLinkImpl.kt` — BLE-first bootstrap → CRED → WiFi upgrade; pushDisplay builds DISP.
- `WearableSyncManager.kt` — pull+import+optional-delete; `VAANI_DIR="/vaani"`.
- `di/DeviceModule.kt` — Hilt binding (in data module, correct).
