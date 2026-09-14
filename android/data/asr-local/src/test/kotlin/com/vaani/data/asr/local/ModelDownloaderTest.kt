package com.vaani.data.asr.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun downloadsVerifiesAndPromotes() {
        val payload = "on-device-whisper-weights".toByteArray()
        val spec = ModelSpec("m1", "http://x", sha256(payload), payload.size.toLong(), "m1.bin")
        val dl = ModelDownloader(tmp.root)

        val result = dl.download(spec, openRange = { off -> ByteArrayInputStream(payload, off.toInt(), payload.size - off.toInt()) })

        assertTrue(result is DownloadResult.Done)
        assertTrue(dl.isPresent(spec))
        assertFalse(java.io.File(tmp.root, "m1.bin.part").exists()) // part promoted
    }

    @Test
    fun resumesFromPartialPart() {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        val spec = ModelSpec("m2", "http://x", sha256(payload), payload.size.toLong(), "m2.bin")
        val dl = ModelDownloader(tmp.root)

        // Pre-seed a half-written .part file.
        val part = java.io.File(tmp.root, "m2.bin.part").apply { writeBytes(payload.copyOfRange(0, 4_000)) }
        var requestedOffset = -1L

        val result = dl.download(spec, openRange = { off ->
            requestedOffset = off
            ByteArrayInputStream(payload, off.toInt(), payload.size - off.toInt())
        })

        assertEquals(4_000L, requestedOffset) // resumed, did not restart at 0
        assertTrue(result is DownloadResult.Done)
        assertTrue(dl.isPresent(spec))
    }

    @Test
    fun rejectsCorruptDownload() {
        val payload = "good".toByteArray()
        val spec = ModelSpec("m3", "http://x", sha256("expected".toByteArray()), payload.size.toLong(), "m3.bin")
        val dl = ModelDownloader(tmp.root)

        val result = dl.download(spec, openRange = { ByteArrayInputStream(payload) })

        assertTrue(result is DownloadResult.Failed)
        assertFalse(dl.isPresent(spec))
        assertFalse(java.io.File(tmp.root, "m3.bin").exists()) // not promoted
    }
}
