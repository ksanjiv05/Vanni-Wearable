package com.vaani.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeTest {

    @Test
    fun map_transformsOk() {
        val result = Outcome.Ok(21).map { it * 2 }
        assertEquals(Outcome.Ok(42), result)
    }

    @Test
    fun map_passesThroughErr() {
        val err: Outcome<Int> = Outcome.Err(AppError.NotFound("x"))
        val mapped = err.map { it * 2 }
        assertSame(err, mapped)
    }

    @Test
    fun getOrElse_returnsValueOnOk() {
        assertEquals(7, Outcome.Ok(7).getOrElse { -1 })
    }

    @Test
    fun getOrElse_returnsFallbackOnErr() {
        val err: Outcome<Int> = Outcome.Err(AppError.Network("down"))
        assertEquals(-1, err.getOrElse { -1 })
    }

    @Test
    fun getOrElse_fallbackReceivesTheError() {
        val err: Outcome<Int> = Outcome.Err(AppError.NotFound("note-1"))
        val seen = err.getOrElse { e -> if (e is AppError.NotFound) e.id.length else 0 }
        assertEquals("note-1".length, seen)
    }

    @Test
    fun errIsNothingTyped_soItComposes() {
        val chained: Outcome<String> = Outcome.Err(AppError.Unknown("boom"))
            .map { it.toString() }
        assertTrue(chained is Outcome.Err)
    }
}
