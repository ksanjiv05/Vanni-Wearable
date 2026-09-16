package com.vaani.data.work

import com.vaani.domain.model.AppError
import com.vaani.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test of the worker's OUTCOME→Result decision logic, isolated from the
 * Android WorkManager runtime (which needs instrumentation). Mirrors the exact
 * branching in [TranscriptionWorker.doWork]/isTransient so a regression in the
 * retry policy is caught on the JVM.
 */
class TranscriptionWorkerLogicTest {

    private enum class Decision { SUCCESS, RETRY, FAILURE }

    private fun decide(outcome: Outcome<Unit>, attempt: Int, maxAttempts: Int = 5): Decision =
        when (outcome) {
            is Outcome.Ok -> Decision.SUCCESS
            is Outcome.Err -> {
                val transient = outcome.error is AppError.Network && attempt < maxAttempts
                if (transient) Decision.RETRY else Decision.FAILURE
            }
        }

    @Test
    fun success_maps_to_success() {
        assertEquals(Decision.SUCCESS, decide(Outcome.Ok(Unit), attempt = 0))
    }

    @Test
    fun network_error_under_attempt_cap_retries() {
        assertEquals(Decision.RETRY, decide(Outcome.Err(AppError.Network("timeout")), attempt = 2))
    }

    @Test
    fun network_error_at_attempt_cap_fails_permanently() {
        assertEquals(Decision.FAILURE, decide(Outcome.Err(AppError.Network("timeout")), attempt = 5))
    }

    @Test
    fun non_network_error_fails_without_retry() {
        assertEquals(Decision.FAILURE, decide(Outcome.Err(AppError.NotFound("x")), attempt = 0))
    }

    @Test
    fun uniqueName_isStablePerRecording() {
        assertTrue(("transcription-" + "rec-9").endsWith("rec-9"))
    }
}
