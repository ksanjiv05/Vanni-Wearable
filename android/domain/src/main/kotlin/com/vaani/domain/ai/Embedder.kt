package com.vaani.domain.ai

/**
 * Produces a fixed-length, L2-normalized vector for a piece of text so notes and
 * queries can be compared by cosine similarity (vector search). A `:domain` port
 * so features/search depend on the contract, not the implementation.
 *
 * The default implementation ([com.vaani.data.vector.HashedNgramEmbedder]) is a
 * fully-offline lexical vector (hashed character n-grams) — real vector math, no
 * model download, tolerant of typos/partial words/morphology. A neural sentence
 * embedder (ONNX E5/MiniLM) can drop in behind this same interface later for true
 * semantic matching without touching callers.
 */
interface Embedder {
    /** Dimensionality of the produced vectors. */
    val dim: Int

    /** Embed [text] into an L2-normalized FloatArray of length [dim]. */
    fun embed(text: String): FloatArray

    companion object {
        /** Cosine similarity of two same-length L2-normalized vectors → dot product. */
        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }
    }
}
