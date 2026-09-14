# ADR-001 — Pluggable AI Engines: Local (on-device) as a peer to Sarvam (API)

- **Status:** Accepted
- **Date:** 2026-09-14
- **Supersedes:** nothing (extends ARCHITECTURE.md §5.5, §6.3)
- **Owner:** Ankit

---

## 1. Context

`ARCHITECTURE.md` is deliberately **API-first**: STT and note/summary/to-do enrichment go
to **Sarvam** over HTTPS; only the RAG **embedder** runs on-device (ONNX Runtime, §6.3),
because Sarvam has no embeddings endpoint.

Product decision: give the user **two selectable AI backends** for the two cloud stages —

| Stage | API backend (exists in design) | New local backend (this ADR) |
|---|---|---|
| Speech-to-text | Sarvam `saaras:v3` (sync / batch / realtime) | `sherpa-onnx` (Whisper-first, IndicConformer later) |
| Enrichment LLM | Sarvam `sarvam-105b`, schema-strict JSON | `llama.cpp` + GBNF grammar (Gemma-3-1B / Sarvam-1 2B) |
| Embeddings | — (none) | unchanged: `multilingual-e5-small` int8, ONNX Runtime |

The user picks **Local** (free, fully offline, private) or **API** (higher quality, needs key + network)
per stage, from Settings.

## 2. Decision

**Do not fork the pipeline.** Introduce two engine interfaces in `:domain` and bind an
implementation per user setting. The durable pipeline, retrieval, and all ViewModels depend
only on the interface — identical to how `VectorIndex` (§6.4) is already abstracted.

```kotlin
// :domain — pure Kotlin, no Android, no vendor types
enum class AiBackend { LOCAL, SARVAM }

interface AsrEngine {
    val id: AiBackend
    /** Transcribe one recording. Emits progress; returns a diarized transcript. */
    suspend fun transcribe(audio: AudioRef, opts: AsrOptions): Outcome<DiarizedTranscript>
    fun capabilities(): AsrCapabilities   // diarization?, codemix?, maxDurationMs, langs
}

interface Enricher {
    val id: AiBackend
    /** Transcript -> schema-guaranteed Note (title, summary, key points, to-dos). */
    suspend fun enrich(transcript: DiarizedTranscript, opts: EnrichOptions): Outcome<NoteExtraction>
    fun chatStream(req: ChatRequest): Flow<ChatDelta>
}
```

`SarvamAsrEngine` / `SarvamEnricher` wrap the existing `ResilientSarvamClient` (§5.5.1).
`LocalAsrEngine` / `LocalEnricher` live in new leaf modules. A Hilt `@Provides` reads the
persisted `AiBackend` from DataStore and returns the right binding via a small
`EngineRouter` (so the choice can differ per stage and change at runtime without DI graph rebuilds).

### Module graph delta

```
:data:sarvam     (existing plan)  ── Sarvam impls of AsrEngine/Enricher
:data:asr-local  (NEW)            ── sherpa-onnx: VAD, Whisper/IndicConformer, diarization
:data:llm-local  (NEW)            ── llama.cpp JNI: GGUF load, GBNF-constrained decode
:data:ai         (NEW, thin)      ── EngineRouter + Hilt wiring that reads the setting
```

`:feature:*` still never sees a vendor type — only `AsrEngine`/`Enricher` through `:domain`.

## 3. Library choices (all commercial-safe)

| Concern | Library | Coordinate / integration | License |
|---|---|---|---|
| Local STT + VAD + diarization | **sherpa-onnx** (k2-fsa) | official Android AAR / jitpack | Apache-2.0 |
| Local LLM w/ grammar-constrained JSON | **llama.cpp** | vendored JNI (submodule + CMake `externalNativeBuild`); no Maven artifact | MIT |
| Embeddings + (optional ONNX ASR) | **ONNX Runtime Mobile** | `com.microsoft.onnxruntime:onnxruntime-android` | MIT |
| Vector store | **ObjectBox** | `io.objectbox:objectbox-android` + gradle plugin | binding Apache-2.0; core free for mobile* |
| API STT/LLM | Retrofit/OkHttp/kotlinx.serialization (existing) | existing stack | Apache-2.0 |

\* ObjectBox core is closed-source but free for mobile distribution — **confirm against the
distribution model before GA** (already flagged in §6.4). `sqlite-vec` is the fully-open fallback.

### Why llama.cpp over MediaPipe LLM Inference for the LLM

The enrichment stage's whole value (§5.5.4) is **schema-guaranteed JSON** for notes/to-dos.
llama.cpp supports **GBNF grammars** → we enforce the same JSON schema locally that Sarvam's
`strict: true` gives us over the API. MediaPipe's LLM Inference API does not expose grammar
constraints, so it would drag us back to prompt-and-pray parsing. MediaPipe stays a possible
future fast-path for plain chat, not for extraction.

## 4. Models (download-on-first-run, SHA-256 verified, resumable — never in the APK)

| Model | Role | Size (quant) | License | Status |
|---|---|---|---|---|
| Whisper-small (multilingual) ONNX | **local ASR v1** | ~250 MB int8 | MIT | ✅ proven in sherpa-onnx |
| IndicConformer-600M | local ASR, Indic-accurate | ~600 MB | MIT | ⚠️ ONNX export WIP — add once verified |
| Silero VAD + diarization models | segmentation / speakers | ~30–70 MB | MIT/Apache | ✅ |
| Sarvam-1 2B GGUF (Q4_K_M) | local LLM, Indic-strong | ~1.4 GB | check Sarvam license | phase 2 |
| Gemma-3-1B-it GGUF (Q4_K_M) | local LLM, lighter | ~750 MB | Gemma (commercial-ok) | phase 2 |
| multilingual-e5-small int8 ONNX + tokenizer | embeddings | ~35 MB | MIT | ✅ (already speced §6.3) |

## 5. Resolved decisions (defaults chosen; revisit anytime)

1. **Phasing:** **local STT this milestone; local LLM scaffolded behind `Enricher` but Sarvam-only until the next.**
   The 1–2B local LLM needs its own quality/thermal tuning pass and must not gate the engine seam.
2. **Indic ASR risk:** **ship Whisper-multilingual for local v1**, add IndicConformer once its ONNX
   export is production-verified. De-risks the milestone; Whisper is known-good in sherpa-onnx.
3. **Local LLM default (phase 2):** **Sarvam-1 2B on ≥6 GB-RAM arm64 devices, Gemma-3-1B otherwise**, auto-selected.

## 6. Consequences / honest limits of Local vs Sarvam

- **Codemix/Hinglish:** Sarvam `codemix` is bespoke and better. Local transcribes per-language;
  no true Hinglish. Surface this in the engine picker copy.
- **Extraction quality:** GBNF makes local JSON *valid*, not as *good* as sarvam-105b. Expect weaker summaries/to-dos.
- **Diarization:** local needs a separate sherpa-onnx model + CPU; not free like Sarvam batch.
- **Thermals/throughput:** 2B LLM ≈ 3–8 tok/s mid-range + heat → gate to **charging + ≥6 GB RAM + arm64**, low-priority background.
- **Footprint:** local adds ~0.4–1.4 GB of downloads. Same download policy as the embedder.
- **Upside:** Local = zero API cost, works on airplane mode, nothing leaves the device. Real product differentiator.

## 7. Build checklist

**Seam (this milestone):**
- [ ] `AiBackend`, `AsrEngine`, `Enricher`, `AsrCapabilities`, `NoteExtraction` in `:domain` (pure Kotlin)
- [ ] `AiBackend` persisted in DataStore; Settings "AI engine" picker (Local / Sarvam) per stage
- [ ] `:data:ai` `EngineRouter` + Hilt wiring reads the setting
- [ ] `SarvamAsrEngine`/`SarvamEnricher` adapters over `ResilientSarvamClient`

**Local STT (this milestone):**
- [ ] `:data:asr-local` module; sherpa-onnx AAR; Whisper-small ONNX; Silero VAD
- [ ] Model manager: resumable download, SHA-256, app-private storage, progress UI
- [ ] `LocalAsrEngine` implements `transcribe()`; long audio chunked on VAD silence
- [ ] Device gating (arm64, RAM, charging for backlogs); on-device latency measured
- [ ] Diarization model wired (or capability reports `diarization=false` for v1)

**Local LLM (next milestone):**
- [ ] `:data:llm-local` JNI module (llama.cpp submodule + CMake); GGUF via `MappedByteBuffer`
- [ ] GBNF grammar generated from the note-extraction JSON schema
- [ ] `LocalEnricher`; model auto-select by RAM; thermal/charging gating
