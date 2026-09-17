# Resume Here

Pick-up guide for when work restarts. Read [PROJECT_STATUS.md](PROJECT_STATUS.md) first for the full
"what exists" picture; this file is specifically **where we stopped and how to continue**.

---

## Current state (2026-09-17)

Everything through the **wearable dashboard** is built, device-verified, committed, and pushed to
`main`. The repo is clean and in sync. The firmware in `firmware/vaani_link/` matches what's flashed.

**Last completed:** wearable TFT dashboard (app-link status, pending count, today's top-5 todos with
2-line wrap for the first two) + a 15s heartbeat that keeps the "APP LINKED" banner fresh.

**Next planned workstream:** **on-wearable voice commands** (wake word + Vaani actions). Designed but
not started — full design in `docs/vaani-voice-command-architecture.html`.

---

## Environment refresher

```bash
source /home/sanjiv/projects/Vanni/env.sh     # JDK17 + Android SDK
# Android:
cd /home/sanjiv/projects/Vanni/android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Firmware (port is root:dialout → sg dialout wrapper):
export PATH="$HOME/.local/bin:$PATH"
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,USBMode=hwcdc,CDCOnBoot=cdc"
arduino-cli compile -b "$FQBN" firmware/vaani_link
sg dialout -c "export PATH=\$HOME/.local/bin:\$PATH; arduino-cli upload -b '$FQBN' -p /dev/ttyACM0 firmware/vaani_link"
```

**After editing firmware:** copy it into the repo so it stays version-controlled:
```bash
cp ~/Arduino/vaani_link/vaani_link.ino firmware/vaani_link/vaani_link.ino
```
(The working copy lives at `~/Arduino/vaani_link/`; the repo copy is a mirror — keep them in sync.)

### Machine-specific gotchas (host)
- **Chrome Remote Desktop quirk:** logging in physically can say "session already running for sanjiv".
  Fix: gracefully terminate the CRD session — `loginctl terminate-session <id>` (find it via
  `loginctl list-sessions`; it's the `chrome-remote-desktop` one) — then log in at the screen.
- **USB/adb permission:** a remote (seatless) session can't access plugged devices (logind gives them
  to `gdm`). Log in physically OR add the user to `plugdev` and use `sg plugdev`. The ESP32 port needs
  `sg dialout`.
- Devices are often unplugged between sessions — always check `adb devices` and `ls /dev/ttyACM0`.

---

## NEXT: On-wearable voice commands

**Goal:** "Hey Vaani"-style offline wake word + a few spoken Vaani actions (start/stop recording, sync
notes, how many pending, delete last). Full design + wiring: open
`docs/vaani-voice-command-architecture.html` in a browser.

### Decisions already made
- **Library: ESP-SR** (Espressif official) — WakeNet (wake word) + MultiNet (~200 commands) + AFE.
  Chosen over Edge Impulse (few keywords, self-trained) and TFLite micro_speech (toy-level). The
  N16R8 (16MB/8MB) is exactly what ESP-SR needs.
- **Hardware: INMP441** I2S MEMS mic (user has one, not yet wired) on I2S0 (GPIO 4/5/6); optional
  **MAX98357A** amp+speaker on I2S1 (GPIO 7/15/16) for audio feedback. Pins verified conflict-free.
- **Command set:** wake word + Start recording / Stop recording / Sync notes / How many pending /
  Delete last.

### Blockers to resolve first
1. **Flash/partition:** firmware is at ~95% of the default partition. ESP-SR models need a dedicated
   multi-MB `model` partition → **custom partition table** (16MB has room) or a separate "voice"
   firmware image.
2. **Framework:** ESP-SR is officially **ESP-IDF**. arduino-esp32 3.x has an `ESP_SR` wrapper but it's
   stricter than the current Arduino flow → likely move the voice build to ESP-IDF (or IDF-as-component).
3. **Custom wake word:** a true "Hi Vaani" needs an Espressif-trained model; v1 uses a prebuilt wake
   word ("Hi ESP" etc.). The 5 commands are fully custom via MultiNet (free-form English).

### Suggested phasing
- **P1 — Mic bring-up:** wire INMP441, capture raw I2S audio, dump to serial/SD, confirm levels.
  (Standalone sketch; doesn't touch the main firmware yet.)
- **P2 — Wake word:** ESP-SR WakeNet → on detection, TFT shows "Listening…". Requires the partition/IDF
  decision from blocker 1–2.
- **P3 — Commands:** MultiNet with the 5 phrases → a **command dispatcher** that calls the existing
  subsystems (start/stop SD recording, trigger sync, report pending, delete last).
- **P4 (optional) — Audio out:** MAX98357A chime on wake / spoken confirmations.

### First concrete step when resuming
Wire the INMP441 per the HTML doc, then build a **P1 mic-test sketch** (separate from `vaani_link.ino`)
that reads I2S and prints RMS/levels — prove the audio path before adding any ML. Only after P1 passes
do we tackle the partition + IDF move for P2.

---

## Other backlog ideas (not yet scoped)
- On-wearable mic **recording to SD** (independent of voice commands — lets the wearable capture audio
  at all; currently it only stores/serves files pushed to it).
- Auto-sync on connect (pull without tapping Sync).
- Neural (E5) embedder to replace the lexical vector search (drop-in behind the `Embedder` port).
- Battery level on the dashboard.

---

## Quick verification checklist (to confirm nothing regressed)
1. `cd android && ./gradlew assembleDebug` → BUILD SUCCESSFUL.
2. Flash firmware, `adb install` the app.
3. Pair once via Android Settings → Bluetooth → Vaani-XXXX (Just Works).
4. In-app: Device → Scan → connect → expect Wi-Fi or BLE badge + device info.
5. Wearable TFT: `APP LINKED`, `Notes to sync: N`, `TODAY` + todos.
6. Sync recordings → a DB row reaches `pipelineState=READY` with a real transcript.
7. Delete a file (trash icon → confirm) → gone from `/vaani`, list refreshes.
