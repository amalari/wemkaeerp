package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.Ratio

/**
 * Hasil resolusi [QuickEstimateTuning] beserta jejak asal tiap kunci.
 *
 * [provenance] ikut dikembalikan karena alasan yang sama dengan
 * [ResolvedCostingParameters.provenance]: ketika angka penawaran dipersoalkan klien berbulan-bulan
 * kemudian, pertanyaan pertamanya selalu "ini pakai setelan pabrik atau default sistem?".
 */
data class ResolvedQuickEstimateTuning(
    val tuning: QuickEstimateTuning,
    val provenance: Map<String, CostingParameterSource> = emptyMap(),
    val warnings: List<String> = emptyList()
) {
    /** Kunci yang masih memakai default sistem — dasar badge "belum disetel" di UI. */
    val keysOnSystemDefault: Set<String>
        get() = provenance.filterValues { it == CostingParameterSource.HARDCODED_DEFAULT }.keys
}

/**
 * Membaca koefisien estimator dari `customFormulaParameters` node `COSTING_HPP` milik tenant.
 *
 * ## Daftar kunci
 * | Kunci | Arti | Contoh |
 * |---|---|---|
 * | `estimatorYarnWastePercent` | Susut benang, persen | `8` |
 * | `estimatorGramsPerMinute` | Produktivitas mesin saat arsip tanpa menit riil | `5` |
 * | `estimatorSmallRunTiers` | Tier penalti run kecil, `qty:faktor` dipisah koma | `24:1.35,50:1.2,100:1.1` |
 * | `estimatorDefaultMarginPercent` | Margin saat rate card tidak menyebutnya | `20` |
 * | `estimatorSpreadHighPercent` / `Medium` / `Low` | Lebar rentang per keyakinan | `10` / `18` / `30` |
 * | `estimatorCategoryWeight` / `GaugeWeight` / `YarnWeight` | Bobot skor kemiripan | `0.45` |
 * | `estimatorMinSimilarity` | Ambang kandidat dipakai | `0.35` |
 * | `estimatorMaxMatches` / `estimatorCandidateLimit` | Batas kandidat | `5` / `25` |
 * | `estimatorVisionTrustThreshold` | Ambang keyakinan AI gambar | `0.4` |
 * | `estimatorArchiveFallbackMinSamples` | Minimal sampel arsip untuk gramasi baku | `3` |
 * | `estimatorFallbackGrams` | Gramasi baku, `KATEGORI:gram` dipisah koma | `CARDIGAN:640,VEST:380` |
 *
 * ## Kenapa nilai tak terbaca tidak menggagalkan estimasi
 * Salah ketik di konfigurasi pipeline tidak boleh membuat CS gagal memberi harga ke klien yang
 * sedang menunggu di telepon. Nilai yang tidak terbaca dicatat sebagai peringatan, kuncinya
 * jatuh kembali ke default sistem, dan estimasi tetap keluar.
 */
object QuickEstimateTuningCodec {

    const val KEY_YARN_WASTE_PERCENT = "estimatorYarnWastePercent"
    const val KEY_GRAMS_PER_MINUTE = "estimatorGramsPerMinute"
    const val KEY_SMALL_RUN_TIERS = "estimatorSmallRunTiers"
    const val KEY_DEFAULT_MARGIN_PERCENT = "estimatorDefaultMarginPercent"
    const val KEY_SPREAD_HIGH_PERCENT = "estimatorSpreadHighPercent"
    const val KEY_SPREAD_MEDIUM_PERCENT = "estimatorSpreadMediumPercent"
    const val KEY_SPREAD_LOW_PERCENT = "estimatorSpreadLowPercent"
    const val KEY_CATEGORY_WEIGHT = "estimatorCategoryWeight"
    const val KEY_GAUGE_WEIGHT = "estimatorGaugeWeight"
    const val KEY_YARN_WEIGHT = "estimatorYarnWeight"
    const val KEY_MIN_SIMILARITY = "estimatorMinSimilarity"
    const val KEY_MAX_MATCHES = "estimatorMaxMatches"
    const val KEY_CANDIDATE_LIMIT = "estimatorCandidateLimit"
    const val KEY_VISION_TRUST_THRESHOLD = "estimatorVisionTrustThreshold"
    const val KEY_ARCHIVE_FALLBACK_MIN_SAMPLES = "estimatorArchiveFallbackMinSamples"
    const val KEY_FALLBACK_GRAMS = "estimatorFallbackGrams"

    /** Node pipeline mana yang menyimpan setelan ini. */
    const val COSTING_MODULE_CODE = "costing_hpp"

    fun resolve(
        nodeParams: Map<String, String>,
        base: QuickEstimateTuning = QuickEstimateTuning.SYSTEM_DEFAULT
    ): ResolvedQuickEstimateTuning {
        val warnings = mutableListOf<String>()
        val provenance = mutableMapOf<String, CostingParameterSource>()

        fun <T> pick(key: String, baseValue: T, parsed: T?): T =
            if (parsed != null) {
                provenance[key] = CostingParameterSource.PIPELINE_NODE
                parsed
            } else {
                provenance[key] = CostingParameterSource.HARDCODED_DEFAULT
                baseValue
            }

        fun percent(key: String): Double? = nodeParams.readDouble(key, warnings)?.let { it / 100.0 }
        fun ratio(key: String): Double? = nodeParams.readDouble(key, warnings)

        val candidate = QuickEstimateTuning(
            yarnWasteRatio = pick(KEY_YARN_WASTE_PERCENT, base.yarnWasteRatio, percent(KEY_YARN_WASTE_PERCENT)),
            defaultGramsPerMinute = pick(
                KEY_GRAMS_PER_MINUTE, base.defaultGramsPerMinute, ratio(KEY_GRAMS_PER_MINUTE)
            ),
            smallRunTiers = pick(
                KEY_SMALL_RUN_TIERS, base.smallRunTiers, parseTiers(nodeParams[KEY_SMALL_RUN_TIERS], warnings)
            ),
            defaultMargin = pick(
                KEY_DEFAULT_MARGIN_PERCENT,
                base.defaultMargin,
                nodeParams.readDouble(KEY_DEFAULT_MARGIN_PERCENT, warnings)?.let { Ratio.percent(it) }
            ),
            spreadHigh = pick(KEY_SPREAD_HIGH_PERCENT, base.spreadHigh, percent(KEY_SPREAD_HIGH_PERCENT)),
            spreadMedium = pick(KEY_SPREAD_MEDIUM_PERCENT, base.spreadMedium, percent(KEY_SPREAD_MEDIUM_PERCENT)),
            spreadLow = pick(KEY_SPREAD_LOW_PERCENT, base.spreadLow, percent(KEY_SPREAD_LOW_PERCENT)),
            categoryWeight = pick(KEY_CATEGORY_WEIGHT, base.categoryWeight, ratio(KEY_CATEGORY_WEIGHT)),
            gaugeWeight = pick(KEY_GAUGE_WEIGHT, base.gaugeWeight, ratio(KEY_GAUGE_WEIGHT)),
            yarnWeight = pick(KEY_YARN_WEIGHT, base.yarnWeight, ratio(KEY_YARN_WEIGHT)),
            minSimilarity = pick(KEY_MIN_SIMILARITY, base.minSimilarity, ratio(KEY_MIN_SIMILARITY)),
            maxMatches = pick(KEY_MAX_MATCHES, base.maxMatches, nodeParams.readInt(KEY_MAX_MATCHES, warnings)),
            candidateLimit = pick(
                KEY_CANDIDATE_LIMIT, base.candidateLimit, nodeParams.readInt(KEY_CANDIDATE_LIMIT, warnings)
            ),
            visionTrustThreshold = pick(
                KEY_VISION_TRUST_THRESHOLD, base.visionTrustThreshold, ratio(KEY_VISION_TRUST_THRESHOLD)
            ),
            archiveFallbackMinSamples = pick(
                KEY_ARCHIVE_FALLBACK_MIN_SAMPLES,
                base.archiveFallbackMinSamples,
                nodeParams.readInt(KEY_ARCHIVE_FALLBACK_MIN_SAMPLES, warnings)
            ),
            fallbackWeightGramsByCategory = pick(
                KEY_FALLBACK_GRAMS,
                base.fallbackWeightGramsByCategory,
                parseFallbackGrams(nodeParams[KEY_FALLBACK_GRAMS], base.fallbackWeightGramsByCategory, warnings)
            )
        )

        return ResolvedQuickEstimateTuning(candidate, provenance, warnings)
    }

    /**
     * Membangun [ResolvedQuickEstimateTuning] yang aman: kalau kombinasi setelan tenant melanggar
     * invariant [QuickEstimateTuning], kembalikan default sistem dengan peringatan yang menyebut
     * penyebabnya — bukan melempar ke pemanggil.
     */
    fun resolveSafely(
        nodeParams: Map<String, String>,
        base: QuickEstimateTuning = QuickEstimateTuning.SYSTEM_DEFAULT
    ): ResolvedQuickEstimateTuning = runCatching { resolve(nodeParams, base) }.getOrElse { error ->
        ResolvedQuickEstimateTuning(
            tuning = base,
            provenance = emptyMap(),
            warnings = listOf(
                "Setelan estimator tenant tidak konsisten (${error.message}); memakai default sistem."
            )
        )
    }

    /** `"24:1.35,50:1.2,100:1.1"` */
    private fun parseTiers(raw: String?, warnings: MutableList<String>): List<SmallRunTier>? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        val tiers = text.split(',').mapNotNull { part ->
            val pieces = part.split(':')
            if (pieces.size != 2) {
                warnings += "Tier run kecil '$part' diabaikan: formatnya harus 'kuantitas:faktor'."
                return@mapNotNull null
            }
            val qty = pieces[0].trim().toLongOrNull()
            val factor = pieces[1].trim().toDoubleOrNull()
            if (qty == null || factor == null) {
                warnings += "Tier run kecil '$part' diabaikan: angkanya tidak terbaca."
                return@mapNotNull null
            }
            runCatching { SmallRunTier(qty, factor) }.getOrElse {
                warnings += "Tier run kecil '$part' diabaikan: ${it.message}"
                null
            }
        }
        return tiers.ifEmpty {
            warnings += "'$KEY_SMALL_RUN_TIERS' tidak memuat satu pun tier yang sah; memakai default."
            null
        }
    }

    /** `"CARDIGAN:640,VEST:380"` — kategori yang tidak disebut tetap memakai nilai [base]. */
    private fun parseFallbackGrams(
        raw: String?,
        base: Map<KnitCategory, Double>,
        warnings: MutableList<String>
    ): Map<KnitCategory, Double>? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        val overrides = mutableMapOf<KnitCategory, Double>()
        text.split(',').forEach { part ->
            val pieces = part.split(':')
            if (pieces.size != 2) {
                warnings += "Gramasi baku '$part' diabaikan: formatnya harus 'KATEGORI:gram'."
                return@forEach
            }
            val category = KnitCategory.entries.firstOrNull { it.name.equals(pieces[0].trim(), ignoreCase = true) }
            val grams = pieces[1].trim().toDoubleOrNull()
            when {
                category == null -> warnings += "Kategori '${pieces[0].trim()}' tidak dikenal; baris diabaikan."
                grams == null || grams <= 0.0 || grams >= 10_000.0 ->
                    warnings += "Gramasi baku untuk ${pieces[0].trim()} di luar rentang wajar; baris diabaikan."
                else -> overrides[category] = grams
            }
        }
        return if (overrides.isEmpty()) null else base + overrides
    }

    private fun Map<String, String>.readDouble(key: String, warnings: MutableList<String>): Double? {
        val raw = this[key]?.takeIf { it.isNotBlank() } ?: return null
        return raw.trim().toDoubleOrNull()
            ?: run { warnings += "Nilai '$key' = '$raw' bukan angka; memakai default."; null }
    }

    private fun Map<String, String>.readInt(key: String, warnings: MutableList<String>): Int? {
        val raw = this[key]?.takeIf { it.isNotBlank() } ?: return null
        return raw.trim().toIntOrNull()
            ?: run { warnings += "Nilai '$key' = '$raw' bukan bilangan bulat; memakai default."; null }
    }
}
