# Vaani — Android app (Milestone A)

Native Kotlin + Jetpack Compose (Material 3) implementation of the Vaani notes
app. This milestone delivers the **scalable modular foundation** plus the
design system, domain models, app navigation shell, and the first two feature
screens (Library, Note detail) wired to fake sample data.

See `../docs/ARCHITECTURE.md` (§4) and `../docs/DESIGN_SYSTEM.md` for the full
spec this is built against.

## Build

```bash
source ../env.sh          # sets JAVA_HOME (JDK17) + ANDROID_HOME
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.11.1 · Compose BOM 2024.10.01 ·
Hilt 2.52 (via KSP) · compileSdk/targetSdk 36 · minSdk 29 · Java 17.

## Module graph

```
:app  ──────────────────────────────────────────────┐
   depends on features + core + domain               │
:feature:library      :feature:note                  │  (features never depend
   (Compose UI + ViewModel + Hilt)                   │   on each other)
        │                                             │
        ▼                                             ▼
:core:ui        (shared Compose helpers: SectionHeader, chip mapping)
:core:designsystem  (VaaniTheme, colors, type, spacing, icons, components)
:core:common    (formatting utils)
        │
        ▼
:domain   (pure Kotlin: models, enums, Outcome<T>, NotesRepository + FakeNotesRepository)
```

Dependency rules enforced:
- `:feature:*` depend only on `:domain` + `:core:*` — never on each other.
- `:app` depends on features; features never depend on `:app`.
- `:domain` is pure-Kotlin (no Android), time via `kotlinx-datetime`.

## Build logic (convention plugins)

`build-logic/` is an included composite build exposing four convention plugins
so no module copy-pastes an `android {}` block:

| Plugin id | Applies |
|---|---|
| `vaani.android.application` | AGP application + Kotlin + shared SDK/Java config |
| `vaani.android.library` | AGP library + Kotlin + shared config |
| `vaani.android.compose` | Compose compiler plugin + BOM + Compose deps |
| `vaani.android.hilt` | KSP + Hilt + compiler |

Versions are centralized in `gradle/libs.versions.toml` (version catalog).

## Design system (`:core:designsystem`)

- **Sharp corners everywhere** — M3 `Shapes` are all zero-radius; components use
  `RectangleShape`. This is the defining visual rule.
- `VaaniColors` light (cream) + dark (charcoal) from the documented roles.
- `VaaniTypography` + monospace style for timestamps/technical values.
- `VaaniSpacing` (8dp base).
- Components: `VaaniButton`, `VaaniOutlineButton`, `StatusChip`, `VaaniCard`
  (coffee left-accent + hairline), `VaaniTopBar` + `SyncedPill`,
  `VaaniBottomNav` (5 slots incl. center REC FAB), `VaaniCheckbox`, `VaaniToggle`.
- `VaaniIconView` — Canvas-drawn sharp line-icon set matching
  `docs/icon-sheet.png`.

## Screens

- **Library** (`:feature:library`) — large title + synced pill, charcoal
  pending-sync banner with progress, TODAY/YESTERDAY day groups, note cards
  (coffee accent, title, snippet, meta, status chip + to-do count). State via
  `LibraryViewModel` exposing `StateFlow<LibraryUiState>`.
- **Note detail** (`:feature:note`) — back/kebab bar, title + meta + tag chips,
  SUMMARY card, KEY POINTS (slate square bullet + mono seek), TO-DOS (48dp sharp
  checkboxes), TRANSCRIPT (color-cycled S1/S2/S3 speaker tags + mono times),
  sticky charcoal audio player. State via `NoteDetailViewModel`.

Both screens read from `FakeNotesRepository` (in `:domain`), provided by Hilt in
`:feature:library`'s `FakeDataModule`. No real data layer yet.

## Deferred to later milestones

- `:data:*`, `:core:crypto`, `:core:testing`, remaining `:feature:*`
  (onboarding, device, chat, tasks, settings), Room/SQLCipher, ObjectBox, ONNX
  RAG, Sarvam client, durable pipeline — per ARCHITECTURE.md §4.2 / §12.
- Real audio playback (Media3), real sync, real icons for launcher (placeholder
  adaptive icon shipped).
