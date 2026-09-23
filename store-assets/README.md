# Vaani — Play Store Console Assets

All images here are generated on-brand (cream `#F3EADB`, coffee `#835F41`, sharp corners) by
`tools/gen_store_assets.py`. Re-run that script after changing screen mockups in `docs/screens/`.

## Graphic assets (upload in Play Console → Store listing → Graphics)

| File | Size | Play Console slot | Required |
|---|---|---|---|
| `icon-512.png` | 512×512 (RGBA) | **App icon** | ✅ Yes |
| `feature-graphic.png` | 1024×500 | **Feature graphic** | ✅ Yes |
| `phone/1.png … 5.png` | 1080×2340 | **Phone screenshots** (need 2–8) | ✅ Yes (min 2) |
| `promo-banner.png` | 1024×500 | Marketing / alt feature graphic | optional |
| `tv-banner.png` | 1280×720 | Android TV banner / large promo | optional |
| `icon-1024.png` | 1024×1024 | Hi-res icon (other stores / press) | optional |

Notes:
- The app icon is 32-bit PNG **with alpha**, as Play requires.
- Screenshots are portrait 9:19.5 (Nothing Phone native res), well within Play's 320–3840 px bounds.
- Play Console also asks for a 512×512 icon at the *app* level — reuse `icon-512.png`.

## Suggested listing copy

**App name (30 chars max):**
`Vaani — Voice Notes On-Device`

**Short description (80 chars max):**
`Private voice notes. Wearable wake-word capture. Transcribed on-device, no cloud.`

**Full description:**
```
Vaani turns spoken thoughts into organized, searchable notes — entirely on your device.

Pair the optional Vaani Link wearable, say “Hi ESP”, and it records hands-free, auto-stops
when you go quiet, and syncs to your phone the moment you connect. Every recording is
transcribed and enriched into a title, summary, key points and to-dos, right on your phone.
No account. No cloud upload. No listening in.

• Hands-free capture with a wake word on the wearable companion
• On-device transcription (Whisper) — works offline
• Smart notes: summaries, key points and to-dos extracted automatically
• Play back the original audio anytime
• Search everything you ever said
• Chat with your own notes (on-device RAG with citations)
• Private by design — your voice never leaves your device unless you choose

Built for people who think out loud and want their words kept — privately.
```

**Category:** Productivity
**Tags:** voice notes, transcription, privacy, offline, dictation

## Regenerate

```bash
python3 tools/gen_store_assets.py
```
