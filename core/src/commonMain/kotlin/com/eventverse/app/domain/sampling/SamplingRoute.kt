package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.StagePhaseTags

/**
 * Urutan tahap yang benar-benar dilewati kartu sampling satu desain.
 *
 * [SamplingPipelineStage.nextStage] menjawab "tahap apa sesudah ini di kerangka pabrik"; rute ini
 * menjawab "ke meja mana kartu **ini** diserahkan". Keduanya berbeda begitu desain men-× tag
 * Sampling pada Cuci atau Setrika — kartunya melompati meja itu dan tidak pernah muncul di
 * antriannya.
 */
data class SamplingRoute(val skipped: Set<SamplingPipelineStage> = emptySet()) {

    val stages: List<SamplingPipelineStage>
        get() = SamplingPipelineStage.entries.filterNot { it in skipped }

    operator fun contains(stage: SamplingPipelineStage): Boolean = stage !in skipped

    /** Tahap berikutnya pada rute sesudah [stage] — dihitung juga bila [stage] sendiri dilompati. */
    fun nextAfter(stage: SamplingPipelineStage): SamplingPipelineStage? =
        SamplingPipelineStage.entries.firstOrNull { it.order > stage.order && it !in skipped }

    companion object {
        val FULL = SamplingRoute()

        fun from(tags: StagePhaseTags): SamplingRoute = SamplingRoute(tags.skippedSamplingStages)
    }
}

/**
 * Rute kartu sampling ini. Tag `null` (belum dibekukan) jatuh ke rute penuh — tag template
 * tenant dibekukan ke order saat SPK masuk Program CAM, sebelum kartu menyentuh meja mana pun.
 */
val SamplingOrder.samplingRoute: SamplingRoute
    get() = SamplingRoute.from(stagePhaseTags ?: StagePhaseTags.DEFAULT)

/** Tag efektif: milik desain bila sudah ada (atau beku), kalau tidak template pabrik. */
fun SamplingOrder.effectivePhaseTags(tenantDefault: StagePhaseTags): StagePhaseTags =
    stagePhaseTags ?: tenantDefault

/** Rute efektif untuk tampilan & penurunan leg sebelum tag dibekukan. */
fun SamplingOrder.routeWith(tenantDefault: StagePhaseTags): SamplingRoute =
    SamplingRoute.from(effectivePhaseTags(tenantDefault))
