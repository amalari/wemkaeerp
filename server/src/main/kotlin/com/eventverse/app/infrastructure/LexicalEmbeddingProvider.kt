package com.eventverse.app.infrastructure

import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.EmbeddingVector
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Retrieval by **word overlap**, not by meaning.
 *
 * Named for what it does rather than what a placeholder usually pretends to do. It maps words into
 * a fixed set of buckets and weights them, so "foto cacat kain" lands near "rekam foto cacat" — but
 * it will not connect "foto cacat" to "gambar reject", because it has no notion that those mean the
 * same thing. A real embedding model would.
 *
 * It is nonetheless the right thing to run first:
 *
 *  - it needs no API key, no network call, and no per-request cost, so the whole estimation path
 *    can be exercised and tested end to end today;
 *  - lexical overlap is a respectable retrieval baseline, and with a corpus in the tens of rows the
 *    limiting factor is the corpus, not the retriever;
 *  - the similarity gate means poor neighbours produce a refusal rather than a bad price, so its
 *    weakness fails safe.
 *
 * **Swap it before the corpus grows past a few dozen builds.** `embedding_model` is recorded on
 * every row, so once a real model is wired in, old vectors are recognisably from a different space
 * and are skipped rather than silently compared against new ones.
 */
class LexicalEmbeddingProvider(
    override val model: String = MODEL_NAME,
    private val dimension: Int = DEFAULT_DIMENSION
) : EmbeddingProvider {

    override suspend fun embed(text: String): EmbeddingVector {
        val counts = HashMap<Int, Double>()

        tokenize(text).forEach { token ->
            val bucket = abs(token.hashCode()) % dimension
            counts[bucket] = (counts[bucket] ?: 0.0) + 1.0
        }

        val buckets = DoubleArray(dimension)
        counts.forEach { (bucket, count) ->
            // Sub-linear term weighting: a word repeated ten times matters more than one used once,
            // but not ten times more. Without it, a requirement that says "kain" repeatedly would
            // be judged similar to anything else that does, regardless of what either asks for.
            buckets[bucket] = 1.0 + ln(count)
        }

        val magnitude = sqrt(buckets.sumOf { it * it })
        // An empty or stopword-only text has no direction; give it a single non-zero component so
        // similarity comes back as a real low number rather than as "incomparable".
        if (magnitude == 0.0) {
            buckets[0] = 1.0
            return EmbeddingVector(buckets.toList(), model = model)
        }

        return EmbeddingVector(buckets.map { it / magnitude }, model = model)
    }

    /**
     * Splits on non-letters and drops very short and very common Indonesian words.
     *
     * The stopword list is short on purpose: an aggressive one strips domain words that happen to
     * look generic ("potong", "jalan"), and in this corpus those carry most of the signal.
     */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(*NON_WORD)
            .map { it.trim() }
            .filter { it.length > 2 && it !in STOPWORDS }

    companion object {
        const val MODEL_NAME = "lexical-bucket-v1"
        const val DEFAULT_DIMENSION = 512

        private val NON_WORD = arrayOf(
            " ", ",", ".", ";", ":", "!", "?", "\n", "\r", "\t",
            "/", "\\", "(", ")", "[", "]", "{", "}", "-", "_", "\"", "'"
        )

        private val STOPWORDS = setOf(
            "yang", "untuk", "dari", "dengan", "pada", "dan", "atau", "itu", "ini",
            "kami", "kita", "saya", "bisa", "tidak", "sudah", "akan", "agar", "juga",
            "the", "for", "with", "and", "that", "this"
        )
    }
}
