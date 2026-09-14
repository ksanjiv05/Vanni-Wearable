package com.vaani.domain.model

/**
 * A minimal, allocation-free Result type for the domain layer.
 * Kept separate from kotlin.Result so failures carry a typed [AppError]
 * rather than a bare Throwable, and so :domain stays pure Kotlin.
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T> Outcome<T>.getOrElse(fallback: (AppError) -> T): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> fallback(error)
}

/** Typed error surface for the domain. Expand as data/pipeline layers land. */
sealed interface AppError {
    data class Network(val message: String) : AppError
    data class NotFound(val id: String) : AppError
    data class Unknown(val message: String) : AppError
}

/**
 * AI-stage failures (ADR-001), mapped from vendor/native errors at the module
 * edge. Lives here (not in the ai package) because [AppError] is sealed and
 * Kotlin requires sealed subtypes in the same package.
 */
sealed interface AiError : AppError {
    /** The chosen local model is not downloaded/available yet. */
    data class ModelUnavailable(val backend: com.vaani.domain.ai.AiBackend, val modelId: String) : AiError

    /** Device cannot run this backend (RAM/ABI/thermal gating, ADR-001 §6). */
    data class Unsupported(val backend: com.vaani.domain.ai.AiBackend, val reason: String) : AiError

    /** API backend needs a key that is missing or invalid. */
    data class KeyMissing(val backend: com.vaani.domain.ai.AiBackend) : AiError

    /** Inference started but failed (native crash, OOM, bad audio, timeout). */
    data class InferenceFailed(val backend: com.vaani.domain.ai.AiBackend, val message: String) : AiError
}
