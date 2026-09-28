package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition

/**
 * Urutan tahap yang benar-benar dilewati kartu sampling satu desain.
 *
 * [SamplingPipelineStage.nextStage] menjawab "tahap apa sesudah ini di kerangka pabrik"; rute ini
 * menjawab "ke meja mana kartu **ini** diserahkan". Keduanya berbeda begitu desain men-× tag
 * Sampling pada Cuci atau Setrika — kartunya melompati meja itu dan tidak pernah muncul di
 * antriannya.
 */
data class SamplingRoute(
    val skipped: Set<StageCode> = emptySet(),
    /**
     * Kerangka tahap yang dijalani rute ini, berurutan. Hari ini selalu kerangka rajut
     * ([DEFAULT_FRAME]); di Tahap 3 TRD-FLOW-001 diisi dari `TenantStageFlow` milik tenant.
     */
    val frame: List<StageCode> = DEFAULT_FRAME
) {

    val stageCodes: List<StageCode>
        get() = frame.filterNot { it in skipped }

    operator fun contains(code: StageCode): Boolean = code in frame && code !in skipped

    /** Tahap berikutnya pada rute sesudah [code] — dihitung juga bila [code] sendiri dilompati. */
    fun nextAfter(code: StageCode): StageCode? {
        val index = frame.indexOf(code)
        if (index < 0) return null
        return frame.drop(index + 1).firstOrNull { it !in skipped }
    }

    // ── Jembatan TRD-FLOW-001 Tahap 2 untuk pemanggil yang masih memegang enum ──────────────

    val stages: List<SamplingPipelineStage>
        get() = stageCodes.mapNotNull { it.toSamplingStageOrNull() }

    operator fun contains(stage: SamplingPipelineStage): Boolean = stage.toStageCode() in this

    fun nextAfter(stage: SamplingPipelineStage): SamplingPipelineStage? =
        nextAfter(stage.toStageCode())?.toSamplingStageOrNull()

    companion object {
        /** Kerangka rajut, identik dengan urutan enum lama (dijaga `IndustryStageTemplatesTest`). */
        val DEFAULT_STAGES: List<StageDefinition> = IndustryStageTemplates.stagesOf(IndustryTemplateCode.KNIT_SWEATER)
        val DEFAULT_FRAME: List<StageCode> = DEFAULT_STAGES.map { it.code }

        val FULL = SamplingRoute()

        fun from(tags: StagePhaseTags, frame: List<StageCode> = DEFAULT_FRAME): SamplingRoute =
            SamplingRoute(tags.skippedSamplingStages, frame)
    }
}

/**
 * Rute kartu sampling ini. Tag `null` (belum dibekukan) jatuh ke rute penuh — tag template
 * tenant dibekukan ke order saat SPK masuk Program CAM, sebelum kartu menyentuh meja mana pun.
 */
val SamplingOrder.samplingRoute: SamplingRoute
    get() = SamplingRoute.from(stagePhaseTags ?: StagePhaseTags.DEFAULT, stageFrame.map { it.code })

/** Tag efektif: milik desain bila sudah ada (atau beku), kalau tidak template pabrik. */
fun SamplingOrder.effectivePhaseTags(tenantDefault: StagePhaseTags): StagePhaseTags =
    stagePhaseTags ?: tenantDefault

/** Rute efektif untuk tampilan & penurunan leg sebelum tag dibekukan. */
fun SamplingOrder.routeWith(tenantDefault: StagePhaseTags): SamplingRoute =
    SamplingRoute.from(effectivePhaseTags(tenantDefault), stageFrame.map { it.code })
