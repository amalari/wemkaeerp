package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.stageflow.TenantStageFlow
import kotlinx.datetime.Instant

/**
 * Kerangka tahap yang dijalani satu SPK — sumber bagi setiap pembaca yang perlu tahu "tahap ini
 * perannya apa" tanpa menyebut nama tahap rajut (TRD-FLOW-001 Tahap 2, R1).
 *
 * Beku per SPK, dibekukan bersama tag fase saat kartu masuk lantai ([freezeStageFlow]): admin
 * yang mengubah kerangka pabrik tidak boleh me-rute ulang kartu yang sedang dikerjakan. Sebelum
 * beku, kerangka jatuh ke template rajut — satu-satunya kerangka yang ada hari ini.
 */
val SamplingOrder.stageFrame: List<StageDefinition>
    get() = frozenStageFlow ?: DEFAULT_STAGE_FRAME

private val DEFAULT_STAGE_FRAME: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES

/** Definisi tahap tempat SPK ini berada sekarang. */
val SamplingOrder.currentStage: StageDefinition
    get() = stageDefinition(stageCode)

fun SamplingOrder.stageDefinition(code: StageCode): StageDefinition =
    checkNotNull(stageFrame.firstOrNull { it.code == code }) {
        "Tahap ${code.value} tidak ada di kerangka SPK ${spkNumber.value}"
    }

/** Tahap kerja pertama yang mengisi [archetype], mis. meja jahit = `SEWING`. */
fun SamplingOrder.firstStageWith(archetype: ModuleArchetype): StageDefinition? =
    stageFrame.firstOrNull { it.kind == StageKind.WORK && it.archetype == archetype }

fun SamplingOrder.stagesWith(trait: StageTrait): List<StageDefinition> = stageFrame.filter { it.has(trait) }

/** Posisi [code] di kerangka (0-based), atau -1 — pengganti `SamplingPipelineStage.order`. */
fun SamplingOrder.positionOf(code: StageCode): Int = stageFrame.indexOfFirst { it.code == code }

/** Membekukan kerangka tenant ke SPK bila belum beku. Idempoten. */
fun SamplingOrder.freezeStageFlow(tenantFlow: TenantStageFlow): SamplingOrder =
    if (frozenStageFlow != null) this else copy(frozenStageFlow = tenantFlow.stages)

// ── Peran tahap: pengganti nama tahap rajut di aturan domain ────────────────────────────────
// Rajut: SEWING = Linking, QUALITY_CONTROL = QC Finishing, FULFILLMENT (kerja) = Pengemasan —
// dijaga `SamplingOrderStageCodeTest`. Kerangka tanpa peran itu gagal keras, bukan menebak.

/** Meja perakitan — tempat vendor makloon mengembalikan barang dan setoran finishing dihitung. */
internal val SamplingOrder.assemblyStage: StageCode get() = requireRole(ModuleArchetype.SEWING)

/** Meja pemeriksaan akhir yang boleh meloloskan SPK ke pengemasan. */
internal val SamplingOrder.finalQcStage: StageCode get() = requireRole(ModuleArchetype.QUALITY_CONTROL)

/** Meja pengemasan — tujuan SPK yang lolos QC akhir. */
internal val SamplingOrder.packingStage: StageCode get() = requireRole(ModuleArchetype.FULFILLMENT)

private fun SamplingOrder.requireRole(archetype: ModuleArchetype): StageCode =
    checkNotNull(firstStageWith(archetype)?.code) {
        "Kerangka SPK ${spkNumber.value} tidak punya tahap kerja ${archetype.displayName}"
    }

/**
 * Kode tahap jangkar keluar. Ada di setiap template industri (TRD-FLOW-001 FR-1) karena kustodi
 * penyimpanan, pengiriman ke buyer, dan ACC bergantung padanya — bukan proses produksi.
 */
object ExitStages {
    val STORAGE: StageCode = StageCode("STORAGE_HOLDING")
    val DELIVERY: StageCode = StageCode("IN_DELIVERY")
    val APPROVED: StageCode = StageCode("ACC_APPROVED")
}

// ── Jembatan enum lembar input tahap, untuk pemanggil yang belum pindah (TRD-FLOW-001 R3) ────

fun SamplingOrder.fillStageInput(stage: SamplingPipelineStage, sections: List<StageInputSection>, updatedAt: Instant) =
    fillStageInput(stage.toStageCode(), sections, updatedAt)

fun SamplingOrder.stageInputFor(stage: SamplingPipelineStage): StageWorkInput? = stageInputFor(stage.toStageCode())
