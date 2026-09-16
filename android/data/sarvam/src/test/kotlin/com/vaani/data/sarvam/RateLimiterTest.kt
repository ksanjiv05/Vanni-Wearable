package com.vaani.data.sarvam

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun allowsUpToCapacityImmediately_thenThrottles() = runBlocking {
        var virtualNanos = 0L
        val slept = mutableListOf<Long>()
        val limiter = RateLimiter(
            capacity = 3.0,
            refillPerSec = 1.0,
            nowNanos = { virtualNanos },
            sleep = { ms ->
                slept += ms
                virtualNanos += ms * 1_000_000 // advance virtual clock by the wait
            },
        )
        // First 3 acquisitions consume the full bucket without sleeping.
        repeat(3) { limiter.acquire() }
        assertTrue(slept.isEmpty())

        // 4th must wait ~1s for a token to refill at 1/sec.
        limiter.acquire()
        assertEquals(1, slept.size)
        assertTrue("waited ${slept[0]}ms", slept[0] in 900..1100)
    }
}
