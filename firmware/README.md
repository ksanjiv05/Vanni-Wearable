# Vaani Wearable — ESP32-S3 Firmware

`vaani_link` — the companion firmware for the Vaani voice-notes app. The wearable records audio to
a microSD card; the phone app pulls recordings off it (BLE primary / Wi-Fi preferred for bulk) and
feeds them into the on-device transcription pipeline.

## Hardware

- **Board:** ESP32-S3 **N16R8** (16 MB flash, 8 MB OPI PSRAM), connects via the S3's native USB-JTAG.
- **Display:** 1.8" **ST7735** 128×160 SPI TFT (BLACKTAB variant).
- **Storage:** microSD SPI module (shares the SPI bus with the TFT).

### Wiring (device-verified)

| Peripheral | Signal | GPIO |
|---|---|---|
| Shared SPI | SCLK | 12 |
| Shared SPI | MOSI | 11 |
| SD | MISO | 13 |
| SD | CS | 14 |
| SD | VCC | 5V |
| TFT | CS | 10 |
| TFT | DC (A0) | 17 |
| TFT | RST | 18 |
| TFT | SCLK / MOSI | 12 / 11 (shared) |
| TFT | LED (backlight) | 21 |
| TFT | VCC | 3.3V |

## Build & flash (arduino-cli)

```bash
# ESP32 core + libraries (once)
arduino-cli core install esp32:esp32
arduino-cli lib install "Adafruit ST7735 and ST7789 Library"   # pulls GFX + BusIO + SD

FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,USBMode=hwcdc,CDCOnBoot=cdc"
arduino-cli compile -b "$FQBN" firmware/vaani_link
arduino-cli upload  -b "$FQBN" -p /dev/ttyACM0 firmware/vaani_link
```

- `PSRAM=opi` (the R8 is OPI PSRAM), `FlashSize=16M` (the N16), `USBMode=hwcdc`+`CDCOnBoot=cdc` so
  `Serial` works over the native USB port.

## What it exposes

On boot it starts a Wi-Fi SoftAP `Vaani-XXXX` (WPA2) with an HTTP REST server at `192.168.4.1`
**and** a BLE GATT server; both serve the same microSD.

### Wi-Fi REST

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/info` | GET | device + SD JSON status |
| `/api/files?path=` | GET | list a directory |
| `/api/file?path=` | GET | read a file (octet-stream) |
| `/api/upload?path=` | POST | streaming file upload |
| `/api/delete?path=` | GET | delete a file |

### BLE GATT

- Service `6e40fda0-b5a3-f393-e0a9-e50e24dcca9e`
- `INFO`(read) / `CMD`(write) / `DATA`(notify) / `STAT`(notify)
- Commands (write UTF-8 to `CMD`): `INFO`, `LIST <dir>`, `RN <path> <off> <len>` (block read),
  `WRITE <path>` → `D:`+bytes… → `WEND`, `DEL <path>`.
- **Framing:** every `DATA` notification is `[1-byte opcode][payload]` (`OP_DATA=0x01`, `OP_END=0x02`,
  `OP_SZ=0x03`, `OP_ERR=0x04`) — structural, so raw binary audio can never collide with a control
  marker. Bulk reads are re-requestable by offset (lossless over flaky BLE).

## Notes / hardening

- Binary is handled via raw ATT byte buffers (`getData()`/`getLength()`), never NUL-terminated
  strings — WAV/PCM is full of `0x00` and would otherwise truncate.
- A single FreeRTOS mutex serialises all SD + TFT access (SD runs on the BLE-callback core, TFT on
  the loop core; the shared SPI bus would corrupt without it).
- Per-device SSID suffix from the SoftAP MAC.

## Security (v4)

The encrypted BLE link is the **root of trust**; Wi-Fi is derived from it.

- **Per-device secrets, never hardcoded.** On first boot the firmware generates a random 16-char
  Wi-Fi PSK and a 32-char REST token from the hardware RNG and persists them in NVS (`Preferences`).
  They are never printed to serial or shown on the TFT.
- **BLE bonding + encryption.** All characteristics are `ENCRYPTED`-permission; the server uses
  Secure-Connections bonding with Just-Works pairing (no I/O for a passkey). The phone bonds on
  first connect; keys persist so re-pairing is automatic.
- **CRED provisioning.** Over the bonded/encrypted link the phone sends `CRED` and receives
  `{ssid, psk, token}`. Reaching this handler at all proves the peer is encrypted.
- **REST token auth.** Every `/api/*` call must present `Authorization: Bearer <token>` (or
  `?token=`); without it the firmware returns `401`. The upload handler checks auth at
  `UPLOAD_FILE_START` so an unauthorized body never touches the SD card.

App flow: BLE connect → bond → `CRED` → join Wi-Fi with the provisioned PSK → all REST calls carry
the bearer token.

### To reset a device's identity (re-provision)
Erase NVS (`nvs_flash_erase` / full flash erase) — the next boot generates fresh secrets. Also
remove the old bond from the phone's Bluetooth settings.
