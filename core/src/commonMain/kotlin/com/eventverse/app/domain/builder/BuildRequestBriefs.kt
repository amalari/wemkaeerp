package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.brief.BriefCoverage
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.BriefRevision
import com.eventverse.app.domain.discovery.brief.RequirementsBriefAssembler
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.pack.BriefCodec
import kotlinx.datetime.Clock

/**
 * Membekukan brief developer untuk **satu modul** yang diminta dibangun (opsi B). Dipanggil saat deploy pack kustom, ketika
 * draf juga dikunci: isi = modul itu (layar, entitas, field), konteks chat yang relevan (cerita, tanya-jawab, keputusan
 * terapan, **belum jelas**), dan satu baris cakupan "perlu dibangun" (harga belum dihitung — penawaran datang kemudian lewat
 * `quoteId`). Murni terhadap draf + riwayat chat; tidak mengakses katalog harga.
 */
class BuildRequestBriefs(
    private val chats: BuilderChatRepository,
    private val clock: Clock = Clock.System
) {
    /**
     * [previous] = permintaan belum selesai untuk modul yang sama yang digantikan oleh yang baru: brief baru memuat bagian
     * **Revisi brief** (versi, id yang digantikan, status sebelumnya, dan selisih isi) supaya developer tahu apa yang berubah.
     */
    suspend operator fun invoke(tenantId: TenantId, draft: DiscoveryDraft, moduleId: String, previous: BuildRequest? = null): BriefSnapshot {
        val included = setOf(moduleId)
        val name = draft.pack.modules.firstOrNull { it.id.value == moduleId }?.displayName ?: moduleId
        val coverage = listOf(BriefCoverage(moduleId, name, covered = false, monthlyIdr = null, gapLowIdr = null, gapHighIdr = null))
        val context = briefContextOf(chats.messages(chats.conversationFor(tenantId).id), included)
        val first = RequirementsBriefAssembler.assemble(draft, included, emptyList(), coverage, context)
        val previousBrief = previous?.brief
        val brief = if (previous == null || previousBrief == null) first else {
            val (added, removed) = BriefRenderer.diff(previousBrief.markdown, BriefRenderer.markdown(first))
            first.copy(revision = BriefRevision(previous.briefVersion + 1, previous.id.value, previous.status.name, added, removed))
        }
        return BriefSnapshot(BriefRenderer.markdown(brief), BriefCodec.encode(brief).encode(), clock.now())
    }
}
