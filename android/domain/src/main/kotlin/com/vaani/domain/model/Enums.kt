package com.vaani.domain.model

/** Two-phase-commit sync lifecycle of a recording (§3.4 / §4.3). */
enum class SyncState { DISCOVERED, DOWNLOADING, PERSISTED, ACKED }

/** Coarse pipeline state surfaced to the UI (§5.2). */
enum class PipelineState { QUEUED, TRANSCRIBING, ENRICHING, READY, FAILED }

/** Durable job-engine stages (§5.2). */
enum class Stage { TRANSCODE, ASR, TRANSLATE, ENRICH, CHUNK, EMBED }

/** To-do priority (§5.5.4). */
enum class Priority { LOW, MEDIUM, HIGH }

/** To-do completion status. */
enum class TodoStatus { OPEN, DONE }

/** Entity taxonomy (§4.3). */
enum class EntityType { PERSON, ORG, PLACE, PRODUCT }

/** RAG chunk kinds (§6.2). */
enum class ChunkKind { TRANSCRIPT, TRANSLATION, SUMMARY, KEY_POINT, TODO, TITLE }
