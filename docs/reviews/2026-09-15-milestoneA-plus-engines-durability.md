# Vaani — Milestone A+ Review (pluggable AI engines → Sarvam + durability + decode)

**Date:** 2026-09-15
**Reviewer:** senior-engineer pass (self-review at each phase boundary)
**Verdict:** GO — build green, 101 unit tests pass, 0 failures.

## Scope delivered this pass
1. **P1 correctness (foundation):** `EngineRouter` now returns `Outcome` (never throws, never silently substitutes a backend). `IngestPipeline` resolves each engine before advancing state → a missing/unsupported engine fails cleanly to `FAILED` instead of crashing or stranding a recording in `ENRICHING`. Silent LOCAL→API fallback removed (privacy/cost): absent LOCAL → `ModelUnavailable`, absent API → `Unsupported`.
2. **P2 hardening:** `@AiPreferences` qualifier + `ReplaceFileCorruptionHandler` on the AI DataStore; `DataStoreAiEngineSettings.observe()` `.catch`es to defaults; `AsrDeviceGating` wired into `LocalAsrEngine.transcribe()`; `ModelDownloader` fails closed on unpinned SHA; `Media3AudioPlayer.release()` no longer races its own scope-cancel (player released synchronously, guarded by `playerCreated`).
3. **`:data:sarvam`** — real Retrofit/OkHttp/kotlinx.serialization stack. `ResilientSarvamClient`: key-gated auth (`KeyMissing` when unset), shared account-wide token-bucket `RateLimiter`, bounded exponential-backoff retry on 429/5xx/IO, typed error mapping, `flowOn(IO)` streaming. `SarvamAsrEngine` (Saaras STT, diarization+codemix caps) and `SarvamEnricher` (strict-JSON extraction + grounded chat stream), contributed via `@IntoMap`. App now assembles with BOTH backends in the Hilt graph — proves the seam.
4. **`:data:work`** — Hilt `@HiltWorker TranscriptionWorker` wrapping the pipeline; `WorkManagerPipelineEnqueuer` (unique work per recording, exponential backoff); app `Configuration.Provider` + default WM initializer disabled. `RecordingStore` seam + Room impl to resolve `recordingId → AudioRef`.
5. **Real MediaCodec decode** — `AudioDecoder` (any container → 16 kHz mono float) + pure/tested `PcmMath` (PCM16→float, downmix, linear resample). Replaces the `FloatArray(0)` stub.

## Issues found in review and fixed same-pass
- Hilt could not provide `kotlinx.datetime.Clock` for `IngestPipeline` (Kotlin default arg invisible to Dagger) — surfaced only when the worker injected the pipeline. Added `PipelineModule`.
- `chatStream` did blocking OkHttp reads inside `flow{}` with no dispatcher → ANR risk on Main. Added `.flowOn(Dispatchers.IO)`.
- `AiError` has no `Network` variant (it's `AppError.Network`); corrected the Sarvam error mapper's return type and retry predicate.

## Remaining (honest)
- **sherpa-onnx native decode** (`SherpaAsr.recognize`) still returns null — externally blocked on the AAR/model; engine degrades to `ModelUnavailable` by design (ADR §7). Decode + gating + download + mapping around it are all real and tested.
- **API-key entry UI** and **device→phone sync layer** not built (sync is a separate milestone; key store seam exists via `SarvamCredentials`/DataStore).
- SQLCipher at-rest encryption + Room schema export still deferred (pre-GA).
