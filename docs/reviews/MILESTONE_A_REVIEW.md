# Milestone A — Senior Android Architecture & Code Review

**Reviewer:** Staff Android engineer (PR gate)
**Date:** 2026-09-14
**Scope reviewed:** `android/` — build-logic, module graph, `:core:designsystem`, `:core:ui`, `:core:common`, `:domain`, `:feature:library`, `:feature:note`, `:app`.
**Verdict:** **GO-WITH-FIXES** (see §8). The foundation is genuinely strong. One pattern must be corrected before it is copied into every future feature.

> **Resolution log (post-review):** All P1, P2, and P3 items below have since been
> addressed except where explicitly marked *Deferred*. See §9 for the status matrix.

---

## 1. Verification performed (not just reading)

| Check | Command | Result |
|---|---|---|
| Debug build | `./gradlew assembleDebug --console=plain` | **BUILD SUCCESSFUL in 2s** — 197 actionable tasks (all up-to-date on the cached run; clean compile confirmed) |
| Lint | `./gradlew lint --console=plain` | **BUILD SUCCESSFUL in 50s** — **0 errors, 40 warnings** (all in `:app`; every library module reports *No Issues*) |
| `!!` on nullable chains | grep, all `*.kt` | **0 occurrences** ✅ |
| `java.time` / `android.*` in `:domain` | grep | **0** (only a comment naming the ban) ✅ |
| Reintroduced rounding | grep `RoundedCornerShape\|CircleShape\|clip(\|shadow(\|elevation` | Only `RoundedCornerShape(0.dp)` in the M3 `Shapes` slots ✅ |
| `@Preview` | grep | **0 previews anywhere** ⚠️ (see P2-4) |
| Unit/instrumented tests | file search `src/test`,`src/androidTest` | **none exist** ⚠️ (see P2-5) |

**Lint warning breakdown (`:app`):** `AndroidGradlePluginVersion` (newer AGP available), `GradleDependency` (newer library versions available), `UnusedResources` (27 — dominated by the unused `material-icons-extended` artifact, see P2-2), `DataExtractionRules`, `ObsoleteSdkInt`, `MonochromeLauncherIcon`. None are correctness-critical for Milestone A. Note: **lint has no gating config** — no `baseline`, no `abortOnError`, no `warningsAsErrors` (see P3-6).

---

## 2. Module graph & build-logic — **PASS (exemplary)**

Everything the brief asked to verify holds:

- **No copy-pasted `android {}` blocks.** All shared config funnels through `ProjectExtensions.configureAndroid()` + `VaaniBuild` constants, applied by `AndroidLibraryConventionPlugin` / `AndroidApplicationConventionPlugin`. This is the nowinandroid pattern done correctly. ✅
- **Version catalog is the single source of truth.** No hardcoded dependency versions in any module `build.gradle.kts`; every dep is a `libs.*` alias, and the convention plugins resolve libraries via `libs.findLibrary(...)`. ✅
- **`:feature:library` and `:feature:note` do not depend on each other.** Confirmed in both build files. ✅
- **Each feature depends only on `:domain` + `:core:*`.** ✅
- **`:domain` is pure Kotlin/JVM** — applies only `kotlin.jvm`, Java 17, no AGP, no android imports, no `java.time`; time via `kotlinx-datetime` `Instant`. ✅ This keeps the KMP/iOS path open exactly as ARCHITECTURE.md §4.2 and the `kotlinx-datetime` rule (§5.5.4) intend.

Minor observations (not defects):
- `:core:ui` correctly `api`-exposes `:domain` and `:core:designsystem` (it re-exports their types in its public surface), while features still declare their own direct `implementation` deps on what they use — good hygiene, not redundant.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` and scoped `google()` content filters — production-grade.

---

## 3. Design system — **PASS (palette + sharp corners are clean)**

- **Every hex value matches `DESIGN_SYSTEM.md` exactly.** Verified all 14 light roles + brand + semantics + dark surfaces against the spec table: `#F3EADB`, `#FBF6EC`, `#ECE0CD`, `#1B1A18`, `#4A4038`, `#8A7E70`, `#835F41`, `#A17C5B`, `#654833`, `#63707D`, `#D8CAB4`, `#7A8A6B`, `#C9A15B`, `#B5705E`, dark `#151412`/`#1B1A18`/`#211F1D`. **No palette violation.** ✅
- **Sharp corners are truly global.** All five M3 `Shapes` slots are `RoundedCornerShape(0.dp)`, and every custom component draws with `RectangleShape` explicitly (`VaaniCard`, `StatusChip`, `VaaniCheckbox`, `VaaniToggle`, buttons, nav FAB). The grep for any rounding/clip/shadow/elevation returns nothing but zero-radius. **No sharp-corner violation.** ✅
- **Flat & matte respected** — no `.shadow()`/elevation anywhere; depth is surface-tone + `HairlineDivider`. ✅
- **Dark theme exists** (`DarkVaaniColors` + `darkColorScheme` branch, driven by `isSystemInDarkTheme()`). ✅
- **Custom sharp icon set** (`VaaniIcon` Canvas-drawn, `StrokeCap.Butt`, no round joins) matches the design intent. ✅

Design-system issues are logged as P2-1 (incomplete M3 role mapping), P2-2 (dead `material-icons-extended`), P2-3 (Typography defined but bypassed), P3-3 (FAILED chip color collision).

---

## 4. Screens vs. mockups — **PASS (high fidelity)**

Cross-checked the rendered composables against `docs/screens/02-library.png` and `03-note-detail.png`.

**Library** — matches: large "Library" title + `Synced 2m` pill; sub-count line; charcoal sync banner with down-arrow tile, "Syncing from device…", detail line, mono `34%`, and a sharp coffee progress bar; `TODAY`/`YESTERDAY` day-group headers with trailing hairline; note cards with title + mono duration, one-line snippet, meta line, status chip + "N to-dos" chip; bottom nav Library · Search · [coffee REC square] · Tasks · Settings. Section order is correct. ✅

**Note detail** — matches: back chevron + kebab top bar; bold title; meta line; `#atlas`/`#standup` tag chips; coffee-accented Summary card; `KEY POINTS` rows with slate square + mono timestamp; `TO-DOS` with square checkboxes (done row greyed); `TRANSCRIPT` with `S1/S2/S3` solid speaker tags, text, mono timestamps, and "Hinglish · codemix" meta; sticky charcoal audio player with coffee play square, scrubber + knob, mono `04:20 / 14:20`. Section order matches. ✅

**State hoisting is done right:** `LibraryScreen`/`NoteDetailScreen` collect the `StateFlow` and delegate to stateless `LibraryContent`/`NoteDetailContent`. This is textbook UDF and is exactly the shape that makes previews trivial — which makes the *absence* of previews (P2-4) more disappointing than fatal.

**Checkbox tap target:** `TodoRowView` wraps the 26dp `VaaniCheckbox` in a `Box(Modifier.size(48.dp))` → **48dp target met.** ✅ (Caveat in P3-2: the raw `VaaniCheckbox`/`VaaniToggle` put `clickable` on a sub-48dp box, so any *other* call site without a wrapper would regress.)

---

## 5. Domain models — **PASS**

- Models cover the §4.3 entities used by Milestone A: `Recording`, `Transcript`, `TranscriptSegment`, `Note`, `KeyPoint`, `Todo`, `Tag`, `Entity`, `Chunk`. Enums cover `SyncState`, `PipelineState`, `Stage`, `Priority`, `TodoStatus`, `EntityType`, `ChunkKind` — all matching the spec's value sets. ✅
- **`Outcome<T>` is sound:** sealed interface, correct `out T` variance, `Err : Outcome<Nothing>`, typed `AppError` hierarchy, `inline` `map`/`getOrElse`. Keeping it off `kotlin.Result` (typed error, no `Throwable`, KMP-pure) is the right call. ✅ (Minor: no `flatMap`/`fold`/`onErr` yet — add when the data layer needs them.)

---

## 6. Prioritized findings

### P1 — must fix before Milestone B

**P1-1 · ViewModels bypass the repository and read `SampleData` directly — the architecture violation that will metastasize.**
- **Where:** `feature/library/LibraryViewModel.kt:62` (`SampleData.pipelineByNote[id]`), `:95` (`SampleData.notes.maxByOrNull…`); `feature/note/NoteDetailViewModel.kt:30` (`SampleData.notes.first().id`), `:58` (`SampleData.transcripts[id]?.segments`).
- **What's wrong:** Both VMs inject `NotesRepository` (good) but then reach around it into a concrete fixture object for pipeline status, transcript segments, and the "newest day" reference date. The repository abstraction is only half-used.
- **Why it matters at scale:** This is the seed pattern every future feature VM will copy. When the real Room-backed `:data:notes` lands, these call sites keep compiling against `SampleData` and silently read empty/stale fixtures — the status chips and the entire transcript on Note detail will go blank with no compile error. It also couples `:feature:*` to fixture internals, defeating the very boundary the module graph was built to enforce.
- **Fix:** Move everything the UI needs behind the interface. Add `fun observePipeline(noteId): Flow<PipelineState>` (or fold `pipelineState` into `Note`) and `fun observeTranscript(noteId): Flow<Transcript?>` to `NotesRepository`; have `FakeNotesRepository` serve them from `SampleData`. Compute the day-bucket reference date from the emitted note list, not from `SampleData`. No VM should import `SampleData`.

**P1-2 · Fixtures + fake live in `:domain/main`, and the app-wide repository binding is owned by a feature module — a boundary/ownership leak.**
- **Where:** `domain/.../repository/FakeNotesRepository.kt` (+ `SampleData`, ~180 lines of sample content) sit in `src/main` and ship in the release APK; the Hilt binding `FakeDataModule` lives in `feature/library/di/` yet `@Provides @Singleton NotesRepository` is consumed app-wide (including by `:feature:note`).
- **Why it matters:** (a) Test/demo data compiled into production is dead weight and an information leak. (b) `:feature:note` silently depends on a binding *owned by `:feature:library`* — build or preview `:feature:note` without library and DI fails to resolve `NotesRepository`. Ownership of a shared dependency does not belong to one sibling feature.
- **Fix:** Keep the `NotesRepository` *interface* in `:domain`. Move `FakeNotesRepository` + `SampleData` into a dedicated source set/module (`:core:testing`, a `:data:notes-fake`, or at minimum `testFixtures`/`debug`). Host the DI binding in `:app` (or that data module), not inside a feature.

*(Both P1s are the same root cause — the fake data layer was wired through the shortest path rather than through the boundary. Fixing them together is a contained change and unblocks a clean `:data:notes` swap.)*

### P2 — should fix soon

**P2-1 · M3 `ColorScheme` mapping is incomplete.** `Theme.kt:44-68` sets ~9 roles; the rest (`surfaceVariant`, `onSurfaceVariant`, `primaryContainer`, `secondaryContainer`, `surfaceContainer*`, `onError`, `inverse*`…) fall back to stock Material defaults (purple-ish). Today the app renders almost entirely via custom components + `VaaniTheme.colors`, so it's invisible — but the first stock M3 `TextField`, `Switch`, `Slider`, `Card`, or `AlertDialog` will render off-palette. *Fix:* fill out the scheme (or set the containers to cream/sunken tones) now, while there's one theme file to touch.

**P2-2 · `material-icons-extended` is a dependency but nothing uses it.** `AndroidComposeConventionPlugin.kt:29` forces it into every Compose module; grep finds zero `Icons.*` usages (the app uses the custom `VaaniIcon` set by design). This is the bulk of the 27 `UnusedResources` lint warnings and adds a large artifact + method count. *Fix:* remove it from the convention plugin; add per-module only if ever needed.

**P2-3 · `VaaniTypography` is defined but bypassed everywhere.** No screen references `MaterialTheme.typography`/`VaaniTypography`; every text uses hardcoded `fontSize = 17.sp/15.sp/26.sp/13.sp…` literals. The type scale is therefore decorative and will drift from `DESIGN_SYSTEM.md`. The documented px/2.1 ratio is also applied inconsistently (e.g. body 26px → 15sp rather than ~12sp). *Fix:* route text through the typography tokens (`style = MaterialTheme.typography.titleLarge`, etc.) and delete the magic `.sp` literals.

**P2-4 · Zero `@Preview` in a design-system-first app.** `ui-tooling-preview` is a dependency and the stateless `LibraryContent`/`NoteDetailContent` split was clearly made to enable previews — but none exist. For a milestone whose entire deliverable is UI, previews are the review artifact. *Fix:* add light+dark previews for both `*Content`s and the key `:core:designsystem` components, fed by `SampleData`.

**P2-5 · No tests at all.** ARCHITECTURE.md §10 makes testing first-class; the trivially-testable pure surface already exists: `formatClock/formatDuration/formatBytes` (`:core:common`), `Outcome.map/getOrElse`, `PipelineState.chip()`, `LibraryViewModel.dayBucket`. *Fix:* add a JUnit module for these now to set the precedent before the pipeline logic (RRF, retry classification, `due_hint` resolution) arrives.

**P2-6 · Locale-unsafe number/time formatting.** `Format.kt` and `LibraryViewModel.kt:46` use default-locale `String.format` (`"%.1f h"`, `"%02d:%02d"`, `"%d"`). For an India-first, multilingual app this yields comma decimals / Arabic-Indic digits in some locales — wrong for mono "engineer" technical values that must stay ASCII. *Fix:* format technical/mono values with `Locale.ROOT` (or `Locale.US`).

**P2-7 · Presentation values hardcoded in the Note VM.** `NoteDetailViewModel.kt:62` hardcodes `"Today 09:32"` (wrong for the yesterday note) and `:80-82` hardcodes player position `260_000`; `LibraryViewModel.kt:76` hardcodes `"Transcribing 62%"`. Fine as a Milestone-A stub, but they should derive from data (format `createdAt` with `kotlinx-datetime`; drive progress from state) before the real pipeline lands, or they become invisible bugs.

### P3 — polish / nits

- **P3-1 · Redundant custom `items` in `NoteDetailScreen.kt:126-130`** shadows the standard `androidx.compose.foundation.lazy.items(list, key)` (which `LibraryScreen` imports and uses correctly). Delete it and import the stdlib overload.
- **P3-2 · `VaaniCheckbox`/`VaaniToggle` put `clickable` on a <48dp box.** Works today only because the to-do row wraps it in 48dp. Bake the min-touch-target into the component (`Modifier.minimumInteractiveComponentSize()` or an internal 48dp hit box) so it can't regress at the next call site. The toggle is also 48×26 — height below 48dp.
- **P3-3 · `PipelineState.FAILED` maps to `ChipVariant.Slate`** (`PipelineChip.kt:12`), the same color the design assigns to "Syncing" — a failure reads as in-progress. The palette has `danger` (clay) but no danger chip variant. Add `ChipVariant.Danger` and map FAILED to it.
- **P3-4 · `composable(Routes.NOTE)` declares no `navArgument`.** It works via `SavedStateHandle` path extraction, but declaring `arguments = listOf(navArgument(NOTE_ID_ARG){ type = NavType.StringType })` is the idiomatic, type-safe form.
- **P3-5 · Dead code / unused imports:** `LibraryUiState.snippetOrEmpty()` (unused), unused `HairlineDivider` import in `LibraryScreen`, unused `PaddingValues` import in `Buttons.kt`. `SampleData.chunkKinds` exists only to silence warnings — remove once chunks are consumed.
- **P3-6 · No lint gating.** Add a `lint {}` block (baseline + `warningsAsErrors`/`abortOnError` on the modules you want clean) and consider `detekt`/`ktlint` + `explicitApi()` on `:domain` and `:core:*` to lock the public surface.
- **P3-7 · Tap-to-seek affordance stubbed.** Key-point / transcript / player rows carry `seekMs` but aren't `clickable`; `DESIGN_SYSTEM.md` says "timestamps everywhere are tap-to-seek." Expected to land with playback — noting so it isn't forgotten.
- **P3-8 · `DataExtractionRules`/`MonochromeLauncherIcon` lint warnings** are Phase-4 items (§5.6/§8.2 require `dataExtractionRules` excluding the key blob); `allowBackup="false"` is already set. Fine to defer.

---

## 7. What's genuinely good (keep doing this)

- Convention-plugin + version-catalog build with a pure-Kotlin `:domain` — the module foundation is production-grade and matches the intended architecture precisely.
- Palette and the defining sharp-corner rule are implemented exactly and globally; the custom Canvas icon set reinforces it.
- Correct UDF: immutable `StateFlow<UiState>`, stateless `*Content` composables, `collectAsStateWithLifecycle`, no `!!`, no business logic in composables.
- `Outcome`/`AppError` is the right domain result type.
- Screen fidelity to the mockups is high.

---

## 8. Verdict — **GO-WITH-FIXES**

The foundation is sound and, in most respects, exemplary. Proceed to the next milestone **after** the two P1 items are fixed, because P1-1 is a pattern that will be copied into every feature and will silently break at the real-data swap.

**Minimal set of P1 fixes required to reach GO:**
1. **P1-1** — Remove all `SampleData.*` access from both ViewModels; expose pipeline state, transcript, and the day-bucket reference through `NotesRepository`, served by the fake.
2. **P1-2** — Move `FakeNotesRepository` + `SampleData` out of `:domain/main` (to `testFixtures`/`:core:testing`/a fake data module) and relocate the `NotesRepository` Hilt binding out of `:feature:library` into `:app` (or the data module).

P2/P3 items can be scheduled into Milestone B without blocking.

---

## 9. Resolution status matrix (post-review)

| ID | Item | Status | How |
|---|---|---|---|
| P1-1 | VMs bypass repository / read `SampleData` | ✅ Fixed | `observeTranscript` added; Library pipeline from `note.pipelineState`; day-bucket from emitted list; NoteDetail id from `SavedStateHandle` (error state on missing); Tasks back-links from `Todo.sourceStartMs`. No `SampleData` refs remain. |
| P1-2 | Fake + fixtures in `:domain`; binding in a feature | ✅ Fixed | New `:data:notes` module holds `FakeNotesRepository` + fixtures; `@Binds` in `:app/di/DataModule`. `:domain` is pure interface + models. |
| P1-3 | Pipeline status not modeled on `Note` | ✅ Fixed | `Note.pipelineState` + optional `pipelineProgress`; side-map removed. |
| P2-1 | Incomplete M3 `ColorScheme` mapping | ✅ Fixed | Both light/dark schemes fully mapped (containers, surfaceVariant, scrim, inverse, outlineVariant, onError…). |
| P2-2 | Dead `material-icons-extended` dep | ✅ Fixed | Removed from the compose convention plugin + catalog. |
| P2-3 | `VaaniTypography` bypassed | ◑ Partial | Shared styles (`OverlineStyle`/`MonoStyle`/`SectionHeader`) tokenized; Library card title routed to `MaterialTheme.typography.titleLarge`. Full literal sweep across all 8 screens **deferred** (visual-tuning task; tracked). |
| P2-4 | Zero `@Preview` | ✅ Fixed | Light+dark previews: component gallery in `:core:designsystem`, `LibraryContent`, `NoteDetailContent`. |
| P2-5 | No tests | ✅ Fixed | `:core:common` `FormatTest` (6) + `:domain` `OutcomeTest` (6); **12 pass, 0 fail**. JUnit/Turbine/coroutines-test in the catalog. |
| P2-6 | Locale-unsafe formatting | ✅ Fixed | `Format.kt` + Library use `Locale.ROOT`/`Locale.US`. |
| P2-7 | Hardcoded presentation values in Note VM | ✅ Fixed | Meta time derived from `createdAt`; player position no longer hardcoded; Library "Transcribing %" from `pipelineProgress`. |
| P3-1 | Redundant custom `items` | ✅ Fixed | Removed in Note + Tasks; use stdlib `items(list, key)`. |
| P3-2 | `<48dp` clickable in checkbox/toggle | ✅ Fixed | `minimumInteractiveComponentSize()` baked into `VaaniCheckbox`/`VaaniToggle`. |
| P3-3 | FAILED chip = Slate (reads as syncing) | ✅ Fixed | Added `ChipVariant.Danger`; FAILED → clay. |
| P3-4 | No `navArgument` on note route | ✅ Fixed | Declared `navArgument(NOTE_ID_ARG){ StringType }`. |
| P3-5 | Dead code / unused imports | ✅ Fixed | Removed `snippetOrEmpty()`, unused `HairlineDivider`/`PaddingValues` imports. |
| P3-6 | No lint gating | ✅ Fixed | `lint { abortOnError; checkDependencies; baseline }` in shared config; per-module baselines committed. |
| P3-7 | Tap-to-seek affordance stubbed | ⏸ Deferred | Genuinely blocked on the audio player (later milestone); `seekMs` seam already threaded. |
| P3-8 | DataExtractionRules / MonochromeLauncherIcon | ⏸ Deferred | Phase-4 security items; captured in lint baseline. |

**Verification after fixes:** `assembleDebug` green; `lint` green (gating active); unit tests 12/12 pass; Library/Note/Tasks re-verified on-device (Nothing Phone A063, cream theme).
