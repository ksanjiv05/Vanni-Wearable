package com.vaani.data.asr.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmMathTest {

    private fun pcm16(vararg samples: Int): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bb.putShort(it.toShort()) }
        return bb.array()
    }

    @Test
    fun pcm16ToFloat_normalizesToUnitRange() {
        val floats = PcmMath.pcm16ToFloat(pcm16(0, 32767, -32768, 16384))
        assertEquals(0f, floats[0], 1e-6f)
        assertTrue(floats[1] in 0.999f..1.0f)
        assertEquals(-1f, floats[2], 1e-6f)
        assertEquals(0.5f, floats[3], 1e-3f)
    }

    @Test
    fun downmix_stereoAveragesChannels() {
        // L,R interleaved: (1,-1) → 0 ; (0.5,0.5) → 0.5
        val mono = PcmMath.downmixToMono(floatArrayOf(1f, -1f, 0.5f, 0.5f), channels = 2)
        assertEquals(2, mono.size)
        assertEquals(0f, mono[0], 1e-6f)
        assertEquals(0.5f, mono[1], 1e-6f)
    }

    @Test
    fun downmix_monoPassthrough() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)
        assertTrue(PcmMath.downmixToMono(input, 1) === input)
    }

    @Test
    fun resample_downsamplesLengthByRatio() {
        val src = FloatArray(32_000) { if (it % 2 == 0) 0.5f else -0.5f }
        val out = PcmMath.resampleLinear(src, srcRate = 32_000, dstRate = 16_000)
        assertEquals(16_000, out.size)
    }

    @Test
    fun resample_sameRateIsPassthrough() {
        val src = floatArrayOf(0.1f, 0.2f)
        assertTrue(PcmMath.resampleLinear(src, 16_000, 16_000) === src)
    }

    @Test
    fun toMono16k_fullChain_stereo48kToMono16k() {
        // 48 kHz stereo, 480 frames → expect ~160 mono samples at 16 kHz.
        val frames = 480
        val bb = ByteBuffer.allocate(frames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { bb.putShort(16384); bb.putShort(16384) }
        val out = PcmMath.toMono16k(bb.array(), bb.array().size, channels = 2, sampleRate = 48_000)
        assertEquals(160, out.size)
        assertTrue("expected ~0.5, got ${out[10]}", out[10] in 0.45f..0.55f)
    }
}
