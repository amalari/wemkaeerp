package com.eventverse.app.domain.moduledev

import kotlin.math.sqrt

/**
 * A semantic embedding of a requirement's prose, tagged with the model that produced it.
 *
 * [model] is not bookkeeping. Distances are only meaningful within one embedding space, so
 * comparing a vector from one model against a vector from another yields numbers that look
 * perfectly reasonable and mean nothing. The failure is silent — slightly poor neighbours, never
 * an error — so the guard has to live in the type rather than in a reviewer's memory.
 */
data class EmbeddingVector(
    val values: List<Double>,
    val model: String,
    val version: String = "1"
) {
    init {
        require(values.isNotEmpty()) { "EmbeddingVector cannot be empty" }
        require(values.all { it.isFinite() }) { "EmbeddingVector must contain finite values" }
        require(model.isNotBlank()) { "EmbeddingVector must record the model that produced it" }
    }

    val dimension: Int get() = values.size

    /** Whether [other] was produced in the same space and can therefore be compared. */
    fun isComparableTo(other: EmbeddingVector): Boolean =
        model == other.model && version == other.version && dimension == other.dimension

    /**
     * Cosine similarity in `[-1.0, 1.0]`, or `null` when the two vectors are not comparable.
     *
     * Null rather than an exception: a corpus mixing embedding generations is a migration
     * problem, and retrieval should skip the incomparable rows and still return its best
     * neighbours rather than failing the whole estimate.
     */
    fun cosineSimilarityTo(other: EmbeddingVector): Double? {
        if (!isComparableTo(other)) return null

        var dot = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0
        for (index in values.indices) {
            val left = values[index]
            val right = other.values[index]
            dot += left * right
            leftMagnitude += left * left
            rightMagnitude += right * right
        }

        val denominator = sqrt(leftMagnitude) * sqrt(rightMagnitude)
        // A zero vector has no direction, so similarity is undefined rather than zero.
        if (denominator == 0.0) return null
        return dot / denominator
    }
}
