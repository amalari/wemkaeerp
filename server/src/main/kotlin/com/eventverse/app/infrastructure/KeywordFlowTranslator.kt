package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.FlowTranslationDraft
import com.eventverse.app.domain.prospect.FlowTranslator
import com.eventverse.app.domain.prospect.RawCapabilityRequirement

/**
 * Reads a narrative by **matching keywords**, not by understanding it.
 *
 * Named for what it does, like [LexicalEmbeddingProvider]. It will find "jahit" and map it to the
 * sewing slot; it will not work out that "kami rakit potongan jadi baju" means the same thing. It
 * cannot follow negation ("kami *tidak* potong sendiri"), cannot tell a plan from a fact, and will
 * miss any need the client phrased in words nobody anticipated.
 *
 * It is still the right first implementation:
 *
 *  - no API key, no per-request cost, no network — the whole path is testable today, and an
 *    unauthenticated public endpoint cannot become someone else's bill;
 *  - it is deterministic, so tests assert on behaviour rather than on a model's mood;
 *  - its failures are *visible*: a missed need becomes a missing slot a reviewer notices, not a
 *    confident wrong answer. And since every translation with warnings is parked for review, and
 *    every unpriceable gap withholds the range, a weak reading degrades into a phone call rather
 *    than into a bad quote.
 *
 * Replacing it is one implementation of [FlowTranslator]. `translator_ref` is recorded on every row,
 * so translations made by this stub stay distinguishable from a real model's afterwards.
 */
class KeywordFlowTranslator(
    override val translatorRef: String = TRANSLATOR_REF
) : FlowTranslator {

    override suspend fun translate(narrative: String): Result<FlowTranslationDraft> = runCatching {
        val text = narrative.lowercase()

        val requirements = SLOT_RULES.mapNotNull { rule ->
            val hit = rule.keywords.firstOrNull { it in text } ?: return@mapNotNull null
            RawCapabilityRequirement(
                archetypeCode = rule.archetype.code,
                title = rule.title,
                description = rule.description,
                sourceQuote = sentenceContaining(narrative, hit),
                features = rule.features
            )
        }.toMutableList()

        // Every factory takes work from somewhere, even when the narrative only describes the
        // production floor. Adding it is more useful than reporting a gap the prospect never has —
        // but it is added without a source quote, which flags it for review on its own.
        if (requirements.none { it.archetypeCode == GarmentSlots.ORDER_INGESTION.code }) {
            requirements += RawCapabilityRequirement(
                archetypeCode = GarmentSlots.ORDER_INGESTION.code,
                title = "Penerimaan pesanan",
                description = "Diasumsikan; tidak disebut eksplisit dalam narasi.",
                sourceQuote = "",
                features = BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2)
            )
        }

        FlowTranslationDraft(
            translatorRef = translatorRef,
            detectedPresetCode = detectPreset(text),
            requirements = requirements,
            openQuestions = openQuestionsFor(text, requirements)
        )
    }

    /**
     * Returns null rather than guessing.
     *
     * A narrative that does not say who owns the fabric genuinely does not determine the business
     * model, and filing it as full-package would quote the prospect for buying materials they may
     * never buy.
     */
    private fun detectPreset(text: String): String? = when {
        PRESET_CMT_HINTS.any { it in text } -> GarmentBlueprints.CMT_MAKLOON.code.value
        PRESET_D2C_HINTS.any { it in text } -> GarmentBlueprints.BRAND_D2C.code.value
        PRESET_FOB_HINTS.any { it in text } -> GarmentBlueprints.FOB_FULL_PACKAGE.code.value
        else -> null
    }

    /**
     * Things a salesperson would ask next.
     *
     * Cheap to generate and disproportionately useful: a question answered before work starts costs
     * far less than two rounds of revision, and a prospect who answers them has already engaged.
     */
    private fun openQuestionsFor(
        text: String,
        requirements: List<RawCapabilityRequirement>
    ): List<String> = buildList {
        if (PRESET_CMT_HINTS.none { it in text } && PRESET_FOB_HINTS.none { it in text }) {
            add("Kain dibeli sendiri atau dikirim oleh buyer?")
        }
        if (requirements.none { it.archetypeCode == GarmentSlots.QUALITY_CONTROL.code }) {
            add("Bagaimana proses pemeriksaan kualitas sebelum barang dikirim?")
        }
        if ("sablon" in text || "bordir" in text) {
            add("Sablon/bordir dikerjakan sendiri atau disubkontrakkan?")
        }
        if (requirements.none { it.archetypeCode == GarmentSlots.FULFILLMENT.code }) {
            add("Barang jadi dikirim per karton, per lusin, atau langsung ke pembeli akhir?")
        }
    }

    /** The sentence a keyword appeared in, so a reviewer can check the reading against the source. */
    private fun sentenceContaining(narrative: String, keyword: String): String {
        val sentences = narrative.split('.', '\n', ';')
        return sentences
            .firstOrNull { keyword in it.lowercase() }
            ?.trim()
            ?.take(MAX_QUOTE_LENGTH)
            .orEmpty()
    }

    private data class SlotRule(
        val archetype: ModuleArchetype,
        val title: String,
        val description: String,
        val keywords: List<String>,
        val features: BuildFeatureVector
    )

    companion object {
        const val TRANSLATOR_REF = "keyword/v1"
        private const val MAX_QUOTE_LENGTH = 200

        private val PRESET_CMT_HINTS = listOf(
            "makloon", "cmt", "kain dari buyer", "kain titipan", "jasa jahit", "cuma jahit"
        )
        private val PRESET_D2C_HINTS = listOf(
            "brand sendiri", "d2c", "marketplace", "shopee", "tokopedia", "tiktok", "toko online"
        )
        private val PRESET_FOB_HINTS = listOf(
            "fob", "full package", "beli kain sendiri", "kami yang beli kain", "ekspor penuh"
        )

        /**
         * Keyword rules, most specific slot first.
         *
         * The feature vectors are rough sizes for a typical module in each slot. They are the
         * translator's *guess* at scope and exist so the estimator has something to scale; a
         * reviewer is expected to correct them, which is why they are visible in the admin view.
         */
        private val SLOT_RULES = listOf(
            SlotRule(
                GarmentSlots.ORDER_INGESTION, "Penerimaan pesanan & SPK",
                "Pencatatan order masuk dari buyer atau pelanggan.",
                listOf("order", "pesanan", "spk", "po ", "buyer", "pelanggan", "klien"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 4)
            ),
            SlotRule(
                GarmentSlots.RAW_MATERIAL, "Bahan baku & stok kain",
                "Penerimaan dan pencatatan kain, benang, dan aksesoris.",
                listOf("gudang", "stok", "beli kain", "bahan baku", "roll", "benang", "kancing"),
                BuildFeatureVector(entityCount = 3, useCaseCount = 4, screenCount = 2, apiEndpointCount = 5, dbTableCount = 2)
            ),
            SlotRule(
                GarmentSlots.COSTING_HPP, "Perhitungan HPP",
                "Kalkulasi biaya produksi dan penetapan harga.",
                listOf("hpp", "harga pokok", "biaya produksi", "ongkos", "costing", "margin"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 3, requiresCustomFormula = true)
            ),
            SlotRule(
                GarmentSlots.CUTTING, "Pemotongan & SPK potong",
                "Penjadwalan dan pencatatan proses potong.",
                listOf("potong", "cutting", "marker", "spreading", "pola"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 4)
            ),
            SlotRule(
                GarmentSlots.SEWING, "Penjahitan",
                "Pencatatan output jahit per operator atau per lini.",
                listOf("jahit", "sewing", "operator", "lini", "borongan"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 4, screenCount = 3, apiEndpointCount = 5, targetPlatformCount = 3)
            ),
            SlotRule(
                GarmentSlots.FINISHING, "Finishing",
                "Setrika, cuci, trimming, dan pelabelan.",
                listOf("finishing", "setrika", "gosok", "cuci", "washing", "trimming", "label"),
                BuildFeatureVector(entityCount = 1, useCaseCount = 2, screenCount = 1, apiEndpointCount = 3)
            ),
            SlotRule(
                GarmentSlots.QUALITY_CONTROL, "Inspeksi QC",
                "Pemeriksaan mutu, pencatatan cacat, dan grading.",
                listOf("qc", "aql", "inspeksi", "cacat", "reject", "kualitas", "mutu"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 4, reportCount = 1)
            ),
            SlotRule(
                GarmentSlots.FULFILLMENT, "Packing & pengiriman",
                "Pengepakan, surat jalan, dan serah terima ekspedisi.",
                listOf("packing", "surat jalan", "kirim", "ekspedisi", "karton", "pengiriman"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 4, reportCount = 1)
            ),
            SlotRule(
                GarmentSlots.CUSTOM_EXTENSION, "Sablon / bordir",
                "Proses dekorasi yang tidak tercakup slot bawaan.",
                listOf("sablon", "bordir", "print", "dtf", "embroidery"),
                BuildFeatureVector(entityCount = 2, useCaseCount = 3, screenCount = 2, apiEndpointCount = 4, requiresCustomFormula = true)
            )
        )
    }
}
