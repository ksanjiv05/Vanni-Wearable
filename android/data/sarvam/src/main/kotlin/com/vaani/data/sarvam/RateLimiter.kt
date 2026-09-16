package com.vaani.data.sarvam

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-side token-bucket limiter (ADR-001: Sarvam rate limits are per-ACCOUNT,
 * not per-key, token-bucket; a naive parallel fan-out 429s immediately). A single
 * shared limiter across every Sarvam call smooths bursts below the account cap.
 *
 * [refillPerSec] tokens are added continuously up to [capacity]; [acquire]
 * suspends until a token is available. Pure time math via an injectable clock so
 * it is deterministic under test.
 */
class RateLimiter(
    private val capacity: Double,
    private val refillPerSec: Double,
    private val nowNanos: () -> Long = System::nanoTime,
    private val sleep: suspend (Long) -> Unit = { ms -> delay(ms) },
) {
    private val mutex = Mutex()
    private var tokens: Double = capacity
    private var lastRefillNanos: Long = nowNanos()

    /** Suspend until one token is available, then consume it. */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                refill()
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    0L
                } else {
                    // ms until the next whole token accrues.
                    (((1.0 - tokens) / refillPerSec) * 1000.0).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMs == 0L) return
            sleep(waitMs)
        }
    }

    private fun refill() {
        val now = nowNanos()
        val elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0
        if (elapsedSec <= 0) return
        tokens = (tokens + elapsedSec * refillPerSec).coerceAtMost(capacity)
        lastRefillNanos = now
    }
}
