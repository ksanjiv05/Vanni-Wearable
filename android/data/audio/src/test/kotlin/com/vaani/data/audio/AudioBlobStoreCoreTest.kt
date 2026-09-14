package com.vaani.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class AudioBlobStoreCoreTest {

    private lateinit var baseDir: File
    private lateinit var core: AudioBlobStoreCore

    @Before
    fun setUp() {
        baseDir = Files.createTempDirectory("audio-blob-test").toFile()
        core = AudioBlobStoreCore(baseDir)
    }

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    @Test
    fun `import returns stable sha and byte count`() {
        val data = "hello audio".toByteArray()
        val blob = core.import(ByteArrayInputStream(data), "m4a")

        // Known SHA-256 of "hello audio".
        assertEquals(64, blob.sha256.length)
        assertEquals(data.size.toLong(), blob.bytes)
        assertTrue(blob.absolutePath.endsWith("${blob.sha256}.m4a"))
        assertTrue(File(blob.absolutePath).exists())
    }

    @Test
    fun `importing same bytes twice is idempotent and does not duplicate`() {
        val data = bytes(1, 2, 3, 4, 5)
        val first = core.import(ByteArrayInputStream(data), "wav")
        val second = core.import(ByteArrayInputStream(data), "wav")

        assertEquals(first.sha256, second.sha256)
        assertEquals(first.absolutePath, second.absolutePath)

        // Only one stored file for that content address (temp .part files cleaned up).
        val stored = baseDir.listFiles()?.filter { it.name.startsWith(first.sha256) } ?: emptyList()
        assertEquals(1, stored.size)
    }

    @Test
    fun `same content different extension does not duplicate`() {
        val data = bytes(9, 8, 7)
        val first = core.import(ByteArrayInputStream(data), "m4a")
        val second = core.import(ByteArrayInputStream(data), "ogg")

        assertEquals(first.sha256, second.sha256)
        // Idempotent: returns the already-stored file regardless of new ext.
        assertEquals(first.absolutePath, second.absolutePath)
        val stored = baseDir.listFiles()?.filter { it.name.startsWith(first.sha256) } ?: emptyList()
        assertEquals(1, stored.size)
    }

    @Test
    fun `different bytes yield different sha`() {
        val a = core.import(ByteArrayInputStream(bytes(1, 1, 1)), "wav")
        val b = core.import(ByteArrayInputStream(bytes(2, 2, 2)), "wav")
        assertNotEquals(a.sha256, b.sha256)
    }

    @Test
    fun `exists and uriFor reflect presence`() {
        val blob = core.import(ByteArrayInputStream(bytes(42)), "m4a")
        assertTrue(core.exists(blob.sha256))
        assertEquals(blob.absolutePath, core.uriFor(blob.sha256))

        assertFalse(core.exists("deadbeef"))
        assertNull(core.uriFor("deadbeef"))
    }

    @Test
    fun `delete removes the blob`() {
        val blob = core.import(ByteArrayInputStream(bytes(5, 6, 7)), "wav")
        assertTrue(core.exists(blob.sha256))

        assertTrue(core.delete(blob.sha256))
        assertFalse(core.exists(blob.sha256))
        // Deleting again is a no-op returning false.
        assertFalse(core.delete(blob.sha256))
    }

    @Test
    fun `import from file streams correctly`() {
        val src = File(baseDir.parentFile, "source.bin")
        src.writeBytes(ByteArray(20_000) { (it % 256).toByte() })
        val blob = core.import(src, "raw")
        assertEquals(20_000L, blob.bytes)
        assertTrue(core.exists(blob.sha256))
        src.delete()
    }
}
