package com.vaani.data.asr.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * MediaCodec/MediaExtractor decoder: any supported container (opus/m4a/aac/wav…)
 * → 16 kHz mono float PCM for sherpa-onnx (ADR-001 §7). The DSP is delegated to
 * [PcmMath] (pure/tested); this file owns only the Android codec plumbing.
 *
 * STREAMING by design: a 60-minute recording is ~57M float samples (~228 MB as
 * a FloatArray, and ~1.4 GB if ever boxed) — far past the app heap. So decode
 * emits fixed-size chunks via [decodeStreamingMono16k] and never holds the whole
 * PCM in memory. Whisper's 30 s window maps naturally onto these chunks.
 */
internal object AudioDecoder {

    /** 30 s at 16 kHz — one Whisper window. */
    const val DEFAULT_CHUNK_SAMPLES = 30 * 16_000

    /**
     * Decode [file] to 16 kHz mono float PCM, invoking [onChunk] for each window
     * of ~[chunkSamples] samples (the final chunk may be shorter). [onChunk]'s
     * second arg is a 0..1 progress fraction based on the source duration.
     * Returns the total number of mono samples emitted (→ real duration).
     *
     * Throws on unreadable/unsupported input so the engine maps it to a typed
     * InferenceFailed rather than fabricating silence.
     */
    fun decodeStreamingMono16k(
        file: File,
        chunkSamples: Int = DEFAULT_CHUNK_SAMPLES,
        onChunk: (pcm: FloatArray, fraction: Float) -> Unit,
    ): Long {
        require(file.exists()) { "audio file not found: ${file.path}" }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("no audio track in ${file.name}")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val expectedTotal = if (durationUs > 0) (durationUs / 1_000_000.0 * 16_000).toLong() else 0L

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            // Growable primitive buffer for the current chunk — no boxing.
            var buffer = FloatArray(chunkSamples)
            var filled = 0
            var emitted = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            fun emit(len: Int) {
                if (len <= 0) return
                val out = if (len == buffer.size) buffer.copyOf() else buffer.copyOf(len)
                emitted += len
                val frac = if (expectedTotal > 0) (emitted.toFloat() / expectedTotal).coerceIn(0f, 1f) else 0f
                onChunk(out, frac)
            }

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(chunk, 0, bufferInfo.size)
                        outBuf.clear()
                        val mono16k = PcmMath.toMono16k(chunk, chunk.size, channels, sampleRate)
                        var srcPos = 0
                        while (srcPos < mono16k.size) {
                            val space = buffer.size - filled
                            val take = minOf(space, mono16k.size - srcPos)
                            System.arraycopy(mono16k, srcPos, buffer, filled, take)
                            filled += take
                            srcPos += take
                            if (filled == buffer.size) {
                                emit(buffer.size)
                                filled = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }
            emit(filled) // trailing partial chunk
            return emitted
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private const val TIMEOUT_US = 10_000L
}
