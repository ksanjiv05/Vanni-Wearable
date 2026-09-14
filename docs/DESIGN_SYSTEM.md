# Vaani — UI Design System (v1)

Locked with Ankit, design-first, before implementation. All mockups live in
`docs/screens/` (1080×2340, phone).

## Principles
- **Sharp corners only — border-radius 0 everywhere.** No exceptions (cards,
  chips, buttons, toggles, inputs, avatars). This is the defining visual rule.
- **Flat & matte.** No gradients, no shadows/gloss. Depth comes from surface
  tone steps + hairline dividers only.
- **Cream light theme is primary.** A charcoal dark theme mirrors it (same tokens).
- Warm-neutral, editorial, engineer-calm.

## Palette (charcoal · coffee · cream · slate)
Light theme roles:
| Role | Hex |
|---|---|
| background | `#F3EADB` (cream) |
| surface / raised | `#FBF6EC` |
| sunken / track | `#ECE0CD` |
| ink text | `#1B1A18` (charcoal) |
| secondary text | `#4A4038` |
| muted text | `#8A7E70` |
| **primary (coffee)** | `#835F41` |
| primary hi/lo | `#A17C5B` / `#654833` |
| **secondary (slate)** | `#63707D` |
| hairline | `#D8CAB4` |
| success (sage) | `#7A8A6B` |
| warning (honey) | `#C9A15B` |
| danger (clay) | `#B5705E` |

Dark theme swaps background→`#151412`, surface→`#1B1A18`/`#211F1D`, text→cream;
coffee/slate/semantics unchanged.

## Type
- UI: **Inter / Geist** (Noto Sans used as stand-in in mockups).
- Technical bits (timestamps, key id, budget figures): **monospace** (JetBrains
  Mono / DejaVu Mono in mockups). Mono = the "engineer" signal.
- Scale (px @1080w): display 72 · h1 58 · h2 48 · title 34 · body 26 · label 22 ·
  caption 20 · overline 20 (tracked, MUTED, uppercase).

## Components
- **Cards**: surface fill, optional 8px coffee left-accent bar, hairline bottom divider.
- **Status chips**: solid fill, sharp. sage=Ready, coffee=Processing, slate=Syncing, sunken=Queued.
- **Buttons**: primary = coffee fill + cream label; secondary = surface + hairline stroke; danger = surface + clay stroke.
- **Toggles**: square track + square knob (coffee on / sunken off). No rounding.
- **Checkboxes**: square; done = coffee fill + drawn checkmark.
- **Bottom nav**: 5 slots — Library · Search · [REC square FAB, coffee] · Tasks · Settings.
- **Speaker tags**: solid square, S1/S2/S3, color-cycled coffee/slate/coffee-dk.
- **Citation chips**: sunken tile + color left-bar + square [C1] badge + ▶ mono timestamp → deep-links to audio.
- **Timestamps everywhere are tap-to-seek** and rendered in mono.

## Screens (all in docs/screens/)
1. `01-onboarding` — brand, hero waveform, 3-step setup, privacy strip
2. `02-library` — day-grouped notes, sync banner + progress, status chips
3. `03-note-detail` — summary, key points, to-dos, speaker transcript, sticky audio player
4. `04-chat` — RAG answer with source citation chips, follow-up chips, input bar
5. `05-search` — hybrid search, offline badge, filter chips, ranked results w/ score
6. `06-tasks` — to-dos across notes, priority bars, due groups, source back-links
7. `07-device` — charcoal hero (battery/storage), backlog, firmware/OTA, identify, unpair
8. `08-settings` — API key vault, budget cap + usage, processing toggles, export/delete

## Icons
Custom sharp-cornered **line-icon set** (`design/generators/vaani_icons.py`),
single-weight stroke, Lucide/Feather style. Coffee active / muted inactive.
Set: library, search, mic (REC), check-square, sliders, play, chevrons, arrows,
more (kebab), close, bluetooth, key, shield, battery, wifi, clock, trash,
download, refresh, zap, waveform. See `docs/icon-sheet.png`.

## Notes for Compose implementation
- `RectangleShape` as the global default shape override in the M3 theme (kills all rounding).
- Elevation tokens map to surface-tone steps, not shadows.
- One `VaaniColors`/`VaaniType` object in `:core:designsystem`; light + dark from same roles.
