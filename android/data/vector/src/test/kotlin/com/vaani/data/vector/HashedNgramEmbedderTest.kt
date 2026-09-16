package com.vaani.data.vector

import com.vaani.domain.ai.Embedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashedNgramEmbedderTest {

    private val embedder = HashedNgramEmbedder()

    @Test
    fun vectorsAreL2Normalized() {
        val v = embedder.embed("mayor convened the council meeting")
        var norm = 0f
        for (x in v) norm += x * x
        assertEquals(1f, norm, 0.01f) // unit length (empty stays 0)
    }

    @Test
    fun emptyTextYieldsZeroVector() {
        val v = embedder.embed("   ")
        assertTrue(v.all { it == 0f })
    }

    @Test
    fun similarTextScoresHigherThanUnrelated() {
        val q = embedder.embed("council meeting")
        val related = embedder.embed("Mayor convened the council meeting today")
        val unrelated = embedder.embed("banana smoothie recipe")
        val simRelated = Embedder.cosine(q, related)
        val simUnrelated = Embedder.cosine(q, unrelated)
        assertTrue("related=$simRelated should beat unrelated=$simUnrelated", simRelated > simUnrelated)
        assertTrue("related should be a meaningful match", simRelated > 0.3f)
    }

    @Test
    fun toleratesTypoViaCharNgrams() {
        val q = embedder.embed("meetng")   // typo: missing 'i'
        val doc = embedder.embed("weekly team meeting notes")
        assertTrue(Embedder.cosine(q, doc) > 0.15f)
    }

    @Test
    fun identicalTextIsNearlyOne() {
        val a = embedder.embed("quarterly budget review")
        val b = embedder.embed("quarterly budget review")
        assertEquals(1f, Embedder.cosine(a, b), 0.001f)
    }
}
