package com.vaani.data.vector

import com.vaani.domain.ai.Embedder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Fully-offline lexical vector embedder: hashes overlapping character n-grams
 * (3- and 4-grams) of each token into a fixed [dim] space, TF-weighted, then
 * L2-normalizes. Cosine similarity of two such vectors captures word-overlap AND
 * sub-word overlap, so it tolerates typos, morphology and partial words far
 * better than exact substring matching — and needs no model download, no network.
 *
 * This is honest "lexical vector search", not neural semantics (it won't match
 * synonyms with no shared characters). A neural ONNX sentence embedder can drop
 * in behind [Embedder] later; nothing else changes.
 */
@Singleton
class HashedNgramEmbedder @Inject constructor() : Embedder {

    override val dim: Int = DIM

    override fun embed(text: String): FloatArray {
        val vec = FloatArray(DIM)
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return vec
        for (token in tokens) {
            // Whole-token feature (weighted higher) + character n-grams.
            addFeature(vec, "w:$token", WEIGHT_TOKEN)
            val padded = "^$token$"
            for (n in 3..4) {
                if (padded.length < n) continue
                for (i in 0..padded.length - n) {
                    addFeature(vec, padded.substring(i, i + n), 1f)
                }
            }
        }
        l2Normalize(vec)
        return vec
    }

    private fun addFeature(vec: FloatArray, feature: String, weight: Float) {
        val h = feature.hashCode()
        val idx = (h and 0x7fffffff) % DIM
        // Signed hashing reduces collision bias.
        val sign = if (h < 0) -1f else 1f
        vec[idx] += sign * weight
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 2 }

    private fun l2Normalize(vec: FloatArray) {
        var norm = 0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
    }

    private companion object {
        const val DIM = 512
        const val WEIGHT_TOKEN = 2f
    }
}
