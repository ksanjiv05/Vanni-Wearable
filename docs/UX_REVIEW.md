# Vaani — UI/UX Design Review (v1 mockups)

Senior design crit of the 8 static mockups in `docs/screens/` (1080×2340).
Grid reference: base unit **8px**, side padding **PAD = 64px**, canvas 1080 wide →
content column is **952px** (1080 − 64 − 64). All findings respect the locked
constraints: **sharp corners (radius 0)**, **flat/matte (no gradients/shadows)**,
**cream primary theme**, **charcoal/coffee/cream/slate palette**.

Priority key: **P1 must-fix** (broken/blocking/ships-embarrassing) ·
**P2 should-fix** (clear quality gap) · **P3 polish**.

---

## CROSS-CUTTING (read first)

The single biggest issue spans every screen: **there is not one real icon in the
product.** Bottom-nav tabs, the search magnifier, the audio play button, note-detail
overflow, the sync-banner device glyph, the onboarding privacy lock, and the offline
badge are all crude filled squares (□). This reads as "unfinished wireframe," not a
premium engineer-calm app. Icons are the highest-leverage fix and are called out
per-screen below with exact glyph specs. Use a single-weight, geometric, **sharp-cornered
line icon set** (e.g. Lucide/Phosphor "regular" drawn on the 8px grid, 1.5–2px stroke,
coffee `#835F41` active / muted `#8A7E70` inactive) so it matches the flat aesthetic.

Second: **bottom-nav active state and slot mapping are broken/ambiguous** — the Device
screen shows "Library" active though there is no Device tab, and the 4 non-REC slots are
visually identical, so you cannot tell which tab you're on without reading the label.

---

## 01 — Onboarding

**What works:** Strong first impression — big `Vaani` wordmark (display 72) over the
coffee hero waveform is confident and on-brand. Three numbered setup steps map cleanly to
the real setup (pair · API key · consent). Cream field, generous top breathing room.

Fixes:
1. **[P1] Privacy-strip glyph is a placeholder □.** Replace with a **lock (shield-lock)
   icon**, 24px, slate `#63707D`, baseline-aligned to the caption text with 12px gap.
2. **[P1] Step-number badges vs. text baselines.** The circular/square step badges should
   be true squares (radius 0) sized 48×48, coffee fill, cream numeral (title 34), with the
   step title vertically centered to the badge — currently the title sits slightly high of
   badge center. Lock a 24px gap badge→text.
3. **[P2] Page/progress indicator (top-right 4 squares) is placeholder-ish.** Either make
   it a real step indicator (3 squares to match 3 steps, active = coffee fill, inactive =
   sunken `#ECE0CD`) or remove it. Four squares for three steps is a mismatch.
4. **[P2] Hero waveform lacks a caption anchor.** Add a single overline (20, muted,
   tracked, uppercase) tagline under the wordmark to fill the gap between wordmark and
   first step; currently that vertical space is uneven vs. the tight step stack below.
5. **[P2] Primary CTA height.** Ensure the "Continue/Get started" button is ≥96px tall
   (12×8) with 32px internal vertical padding and cream label (body 26) — verify it's not
   cramped to text height.
6. **[P3] Consent step needs an inline checkbox**, not just a chevron — consent is an
   explicit action. Use the square checkbox component (coffee fill + drawn check when set).

---

## 02 — Library

**What works:** Day-grouped list with overline headers (TODAY/YESTERDAY) is the right
IA. Coffee left-accent bar on note cards + hairline bottom divider is a clean, flat depth
cue. Status chips (sage Ready / coffee Processing / slate Syncing) are legible and on-token.

Fixes:
1. **[P1] Bottom-nav icons are all identical □ squares.** Assign real glyphs:
   **Library = stacked-lines / horizontal-list**, **Search = magnifier**,
   **center = mic (REC, coffee square FAB — keep the fill, add a mic glyph over the "REC"
   or replace text with mic)**, **Tasks = check-square**, **Settings = sliders**.
   Active tab coffee `#835F41`, inactive muted `#8A7E70`, label caption 20 under each.
2. **[P1] Sync-banner device glyph is a placeholder □.** Replace with a small
   **device/chip icon** (the wearable), 24px, coffee; keep the coffee left-accent bar and
   the progress track (sunken fill, coffee progress).
3. **[P2] Card internal padding is tight on the right.** The status chip and the mono
   timestamp crowd the right edge — pull them in to respect PAD=64 and add 16px between
   chip and timestamp so they don't read as one blob.
4. **[P2] Chip padding.** Status chips look vertically cramped; set 8px vertical / 16px
   horizontal internal padding, label 22, so text isn't hugging the fill edges.
5. **[P2] Vertical rhythm between group headers and first card.** Standardize overline→
   first-card gap to 16px and card→card to the hairline + 0 (dividers do the work); the
   TODAY block currently sits closer to its cards than YESTERDAY does.
6. **[P3] Empty state missing.** Design a "no notes yet — press REC or sync your device"
   empty state (waveform ghost + one line) for first run.
7. **[P3] Snippet line length.** Cap note snippet to 1 line with a mono time trailing;
   two-line snippets on some cards make row heights ragged.

---

## 03 — Note detail

**What works:** Good editorial stack — summary → key points → to-dos → speaker transcript.
Speaker square tags (S1/S2) color-cycled coffee/slate is a smart, flat identity cue.
Sticky bottom audio player is the right pattern for tap-to-seek.

Fixes:
1. **[P1] Audio-player play control is a placeholder □.** Replace with a **▶ play / ❚❚
   pause triangle-and-bars icon**, 32px, cream on coffee square button (≥64×64). This is
   the primary action on the screen and currently looks broken.
2. **[P1] Top-right overflow is a placeholder □.** Replace with a **kebab (3-dot
   vertical) or share icon**, 24px, charcoal; add a **back arrow (‹ chevron-left / arrow-
   left)** top-left at ≥48×48 tap target if not already a real glyph.
3. **[P1] To-do checkboxes must be ≥48dp tap targets.** Verify the square checkboxes are
   48×48 hit area (visual box can be 32) — transcript-adjacent checkboxes tend to render
   at ~28px, below the 48dp minimum.
4. **[P2] Transcript speaker-tag alignment.** Left-align every S-tag to a single vertical
   rail at PAD (64) and start the utterance text at a consistent 96px (64 + 24 tag + gap),
   so speaker turns form a clean two-column grid rather than a ragged left edge.
5. **[P2] Timestamps affordance.** Mono timestamps are tap-to-seek but read as static
   text — give them the coffee color (or a subtle ▶ prefix) so users know they're
   interactive; match the citation-chip timestamp treatment for consistency.
6. **[P2] Sticky player scrubber.** Track should be sunken `#ECE0CD`, played portion
   coffee, playhead a 4px coffee square; ensure 24px vertical padding inside the player bar
   so the control + time + scrubber aren't vertically cramped.
7. **[P3] Section labels.** KEY POINTS / TO-DOS / TRANSCRIPT should all be overline (20,
   muted, tracked) with identical 24px space-above; confirm they're consistent.

---

## 04 — Chat  ← worst offender

**What works:** RAG answer in a surface card with a SOURCES row of citation chips is
exactly right, and follow-up suggestion chips are a good affordance.

Fixes:
1. **[P1] Citation chips overflow the content column and collide with the FOLLOW-UP
   label.** The C1/C2 source chips extend past the card's right edge (past PAD=64) and the
   next section's "FOLLOW-UP" overline overlaps the last chip. This is a visible layout
   bug. Constrain chips to the 952px column; wrap to a second row with 8px gaps rather than
   overflowing, and add ≥24px clear space before the FOLLOW-UP overline.
2. **[P1] Citation chip anatomy is incomplete.** Per the design system each chip =
   sunken tile + color left-bar + square **[C1]** badge + **▶ mono timestamp**. Verify the
   ▶ is a real play glyph (not □) and the [C1] badge is a filled coffee square with cream
   mono numeral; add 12px internal padding so the badge/time aren't touching.
3. **[P1] Input-bar send control is a placeholder □.** Replace with a **send / arrow-up
   (paper-plane or ↑) icon**, cream on coffee square ≥56×56. Ensure the text field is
   ≥96px tall with 24px internal padding and a mic icon (voice-to-chat) if in scope.
4. **[P2] Follow-up chips look like citation chips.** Differentiate: follow-ups are
   actions (surface + hairline stroke, no color bar, no timestamp), citations are
   references (sunken + color bar + timestamp). Right now they're visually confusable.
5. **[P2] Answer card left-accent.** Give the assistant answer card the same 8px coffee
   left-accent bar used on Library/Device banners for cross-screen consistency; the user
   turn (if any) should be visually distinct (slate accent or right-aligned).
6. **[P3] Empty state.** Add a first-run chat empty state ("Ask across your notes —
   answers cite the exact moment") with 2–3 example prompt chips.

---

## 05 — Search

**What works:** Hybrid intent is communicated (offline badge + filter chips + scored
results). Score badges in mono is the right "engineer signal."

Fixes:
1. **[P1] Magnifier is a placeholder □.** Replace with a real **magnifier icon** inside
   the search field, 24px, muted `#8A7E70`, 16px from the left inner edge; text baseline
   aligned.
2. **[P1] Offline badge glyph is a placeholder □.** Replace with a **cloud-off / wifi-off
   icon**, 20px, paired with an "OFFLINE" overline; use slate (informational) not a warning
   color unless it's an error.
3. **[P1] Bottom-nav icons** — same fix as Library (magnifier / list / mic / check-square
   / sliders). All four are identical squares today.
4. **[P2] Search field height & padding.** Field should be ≥96px tall, sunken fill,
   hairline stroke, 24px horizontal inner padding; icon + placeholder text (body 26, muted)
   vertically centered.
5. **[P2] Filter chips selected-state.** Selected chip = coffee fill + cream label;
   unselected = surface + hairline stroke. Keep 8/16 internal padding and 8px gaps; ensure
   they align to PAD on the left and don't ragged-wrap.
6. **[P2] Result row grid.** Align score badge to a right rail, snippet to the same left
   rail as the title; keyword highlight should be coffee text or sunken highlight, not a
   different hue. Standardize row height so results scan cleanly.
7. **[P3] Empty & no-results states.** "No matches — try fewer words" and an initial
   "recent searches / suggested" state before typing.

---

## 06 — Tasks

**What works:** The strongest screen. Priority left-bars (clay overdue / honey med /
slate low) + due-group overlines (OVERDUE/TODAY/DONE TODAY) + segmented Open/All/Done tabs
is clear and well-balanced. Completed task correctly shows coffee-filled checkbox + drawn
check + muted/struck styling. Mono times read well.

Fixes:
1. **[P1] Checkbox tap targets.** The square checkboxes look ~32px; ensure a 48×48 hit
   area. This is the primary interaction on this screen.
2. **[P2] The ▶ before mono times renders inconsistently** — the "Confirm budget" row shows
   a bare "▶ —" with an em-dash where others show a time. Use a consistent placeholder
   ("▶ --:--" muted) when no timestamp exists rather than a lone dash that looks like a
   cut-off glyph.
3. **[P2] Priority pill vs. priority bar redundancy.** HIGH/MED/LOW pills repeat the
   left-bar color. Keep both but ensure pill padding is 8/16 and the pill sits on a shared
   right rail aligned with the mono time above it (currently slightly ragged right).
4. **[P2] Segmented control.** Selected segment = coffee fill + cream; unselected =
   surface + hairline. Confirm the divider between segments is a single hairline and the
   control is ≥88px tall.
5. **[P3] "from …" source back-link is not obviously tappable.** Give the source note name
   coffee color (link affordance) with a small ↳ / chevron so users know it deep-links.
6. **[P3] Empty state** for "no open tasks — you're clear" per tab.

---

## 07 — Device

**What works:** The charcoal hero is a lovely tonal break from the cream field and shows
off the dark theme tokens — battery (sage bar) + storage (coffee bar) read instantly.
List rows (Firmware/Identify/Recording indicator/Time sync) are consistent. Unpair uses the
correct clay-stroke danger button. Real chevrons here (good — not placeholders).

Fixes:
1. **[P1] Bottom nav shows "Library" active while on the Device screen, and there is no
   Device slot.** This is a navigation-model inconsistency. Either (a) add Device to nav,
   or (b) if Device is a push from Library/Settings, show a back arrow in the top bar and
   don't highlight any tab (or keep the originating tab highlighted but add the back
   affordance). Right now the active state lies about where you are.
2. **[P1] Device thumbnail is a placeholder** (coffee vertical bar in a square outline).
   Replace with a real product illustration/silhouette of the wearable, flat, 2-tone.
3. **[P2] Storage bar scale.** 6.1/32 GB ≈ 19% but the coffee fill reads closer to ~45%.
   Fix the fill ratio to be truthful; add a "Manage storage ›" affordance.
4. **[P2] Add small metric icons** to BATTERY and STORAGE overlines (battery glyph, HDD/
   chip glyph), 20px, muted-on-charcoal, for fast scanning.
5. **[P2] "Connected" chip** has a sage square dot — good; make sure it's the same status-
   chip component (8/16 padding, sharp) used in Library so components stay consistent.
6. **[P3] Row value alignment.** "Check ›", "Blink ›", "On", "±12 ms" should share one
   right rail at PAD=64; verify none are ragged.

---

## 08 — Settings

**What works:** Excellent information design. API-key row masks the secret (`sk_live ····
4c9a`) with mono + a sage "validated" affordance; budget card shows spend, cap, %, and an
Adjust cap button; square toggles (coffee on / sunken off) are perfectly on-brand; Export
vs. Delete correctly differentiated (delete = clay stroke danger). This screen sells the
"engineer-calm" thesis.

Fixes:
1. **[P2] Budget usage bar needs a threshold marker.** At 67% used / 86% of spend on ASR,
   add a subtle honey (warning) tick at the cap-approach point so the bar communicates risk,
   not just progress. Keep fill coffee, track sunken.
2. **[P2] Export row has a lone chevron ›**; give it a leading action affordance or align
   it to the same right rail as the toggles so the DATA group reads as one grid.
3. **[P2] Section overlines (PROCESSING / DATA)** should have identical 24px space-above
   and the same tracked-muted overline style as other screens; verify rhythm matches
   Library/Tasks.
4. **[P2] Toggle tap targets & label gap.** Toggle track should be ≥88×48 with a 48dp hit
   area; keep 24px between the row's caption and the toggle so "Skip silence with on-device
   VAD" doesn't crowd the control.
5. **[P3] Add small leading glyphs** to Manage (key icon), Budget (₹/gauge), Export
   (download), Delete (trash) — optional but improves scan; keep them flat and sharp.
6. **[P3] "Billed to your account"** micro-label under the key is easy to miss — align its
   sage dot to the same rail as the mono key text.

---

## HIGHEST-IMPACT CHANGES (do these first)

1. **Ship a real sharp-cornered line-icon set** and replace every □ placeholder app-wide
   (bottom nav ×5, search magnifier, audio play, note-detail overflow, sync-banner device,
   onboarding privacy lock, offline badge, chat send). Biggest perceived-quality jump.
2. **Fix the Chat citation-chip overflow + FOLLOW-UP overlap (P1 layout bug)** — constrain
   chips to the 952px column and wrap; add ≥24px clearance before the next section.
3. **Give the 5 bottom-nav slots distinct icons + a correct active state**, and resolve the
   Device screen showing "Library" active with no Device tab.
4. **Make the audio play button and chat send button real, ≥56–64px coffee squares with
   cream glyphs** — they're the primary actions and currently look broken.
5. **Enforce 48dp minimum tap targets** on all checkboxes (Note detail to-dos, Tasks) and
   toggles (Settings).
6. **Respect PAD=64 and a single right rail everywhere** — pull in Library chip/timestamp,
   Tasks pills, Device/Settings row values so no element rags past the content column.
7. **Standardize the chip system**: status chips, citation chips, follow-up chips, and
   filter chips each need a distinct, consistent anatomy (fill vs. stroke, color-bar vs.
   none, timestamp vs. none) with uniform 8/16 internal padding. Today citations and
   follow-ups are confusable.
8. **Add the missing empty/first-run states** for Library, Search, Chat, and Tasks — an
   engineer-premium app should never show a blank list with no guidance.
9. **Truth-check data-driven bars** (Device storage fill ratio, Settings budget bar) and
   add a warning-threshold marker to the budget bar.
10. **Lock vertical rhythm to the 8px grid** — group-overline→first-item = 16px, section
    gaps = 24px, card internal padding = 24px — to remove the ragged inter-block spacing
    seen on Library, Note detail, and Settings.
