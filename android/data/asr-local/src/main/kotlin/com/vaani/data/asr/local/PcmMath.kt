package com.vaani.data.asr.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure PCM/DSP helpers for turning decoded audio into the 16 kHz mono float
 * stream sherpa-onnx expects (ADR-001). No Android types, so every step is
 * unit-testable on the JVM; [AudioDecoder] supplies the MediaCodec bytes.
 */
internal object PcmMath {

    /** Little-endian 16-bit signed PCM bytes → normalized float [-1, 1]. */
    fun pcm16ToFloat(bytes: ByteArray, sizeBytes: Int = bytes.size): FloatArray {
        val samples = sizeBytes / 2
        val out = FloatArray(samples)
        val bb = ByteBuffer.wrap(bytes, 0, sizeBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            out[i] = (bb.short.toInt() / 32768f).coerceIn(-1f, 1f)
        }
        return out
    }

    /** Downmix interleaved [channels]-channel float PCM to mono by averaging. */
    fun downmixToMono(interleaved: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += interleaved[f * channels + c]
            out[f] = sum / channels
        }
        return out
    }

    /**
     * Linear-interpolation resample of mono float PCM from [srcRate] to
     * [dstRate]. Good enough for ASR feature extraction; cheap and allocation-lean.
     */
    fun resampleLinear(mono: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || mono.isEmpty()) return mono
        val ratio = dstRate.toDouble() / srcRate
        val outLen = (mono.size * ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt()
            val i1 = (i0 + 1).coerceAtMost(mono.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = mono[i0] * (1 - frac) + mono[i1] * frac
        }
        return out
    }

    /** Full chain: 16-bit interleaved PCM → 16 kHz mono float. */
    fun toMono16k(
        pcm16le: ByteArray,
        sizeBytes: Int,
        channels: Int,
        sampleRate: Int,
    ): FloatArray {
        val floats = pcm16ToFloat(pcm16le, sizeBytes)
        val mono = downmixToMono(floats, channels)
        return resampleLinear(mono, sampleRate, TARGET_SAMPLE_RATE)
    }

    const val TARGET_SAMPLE_RATE = 16_000
}
