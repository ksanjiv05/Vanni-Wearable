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
