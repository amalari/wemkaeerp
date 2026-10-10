package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import kotlinx.datetime.Instant

/*
 * Kustomisasi alur per desain: proses sisipan dan tag fase Cuci/Setrika.
 *
 * Keduanya satu tema — "desain ini menyimpang dari template pabrik" — dan keduanya hanya boleh
 * berubah selama SPK belum masuk Program CAM. Sesudah itu kartunya sudah punya rute di lantai.
 */

/** Tahap mulai dari mana alur desain sudah final dan tidak boleh diubah lagi. */
val SamplingOrder.isFlowLocked: Boolean
    get() = pipelineStage.order >= SamplingPipelineStage.CAM_PROGRAMMING.order

/**
 * Menyesuaikan alur proses khusus untuk desain ini.
 * Mengesampingkan alur default tenant.
 */
fun SamplingOrder.customizeProcessFlow(processes: List<TenantOptionalProcess>, updatedAt: Instant): SamplingOrder = copy(
    customFlowProcesses = processes,
    isCustomFlow = true,
    updatedAt = updatedAt
)

/**
 * Mengembalikan alur proses desain ini ke alur default tenant (pabrik) — termasuk tag fasenya.
 */
fun SamplingOrder.resetProcessFlowToDefault(updatedAt: Instant): SamplingOrder = copy(
    customFlowProcesses = null,
    isCustomFlow = false,
    stagePhaseTags = null,
    updatedAt = updatedAt
)

/** Tag fase khusus desain ini (× Sampling pada Cuci, dst.). */
fun SamplingOrder.withPhaseTags(tags: StagePhaseTags, updatedAt: Instant): SamplingOrder {
    require(!isFlowLocked) { "Alur sudah final sejak Program CAM - tag fase tidak bisa diubah lagi" }
    return copy(stagePhaseTags = tags.normalized, updatedAt = updatedAt)
}

/**
 * Membekukan tag template pabrik ke order bila desain belum punya tag sendiri.
 *
 * Dipanggil saat SPK masuk Program CAM: sejak itu kartu menuju lantai, dan mengubah template
 * pabrik tidak boleh me-rute ulang kartu yang sudah berjalan. Pembekuan juga membuat transisi
 * otomatis di dalam agregat (setoran linking penuh, makloon kembali) selalu tahu rutenya tanpa
 * membaca repository.
 */
fun SamplingOrder.freezePhaseTags(tenantDefault: StagePhaseTags): SamplingOrder =
    if (stagePhaseTags != null) this else copy(stagePhaseTags = tenantDefault.normalized)
