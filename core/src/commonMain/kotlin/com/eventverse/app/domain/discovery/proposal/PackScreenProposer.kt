package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ScreenSuggestion

/**
 * Acuan emas: mengekspresikan usulan layar **yang ditulis manusia di pack** (`DomainPack.screenSuggestions`)
 * sebagai [ScreenProposal] ber-[ProposalSource.Pack]. Tidak khas satu industri — pack mana pun yang punya
 * `screenSuggestions` mendapat proposalnya; garment hanya pack pertama yang punya isinya.
 *
 * Penerjemahannya **tanpa kehilangan**: `toInteractiveScreen(Pack)` setara dengan `WidgetRegistry.interactiveFor`
 * dari suggestion yang sama (dijaga tes paritas yang mengiterasi daftar suggestion, tenant-variability Kontrak 8).
 * Pack bersumber manusia, jadi kunci field boleh sama dengan label baris contohnya ([ProposalLimits.PACK_KEY]).
 *
 * Narasi diabaikan: pembuat ini deterministik terhadap pack. Suggestion tanpa `rationale` **ditolak** — alasan tidak
 * pernah dikarang mesin. Modul tanpa suggestion → daftar kosong.
 */
object PackScreenProposer : ScreenProposer {

    override suspend fun propose(pack: DomainPack, module: ModuleDefinition, narrative: String?): Result<List<ScreenProposal>> =
        proposalsFor(pack, module)

    fun proposalsFor(pack: DomainPack, module: ModuleDefinition): Result<List<ScreenProposal>> =
        proposalsFor(pack.screenSuggestions.filter { it.moduleId == module.id })

    /** Semua usulan pack, urutan suggestion dipertahankan; gagal pada suggestion pertama yang tak bisa diterjemahkan. */
    fun proposalsForAll(pack: DomainPack): Result<List<ScreenProposal>> = proposalsFor(pack.screenSuggestions)

    private fun proposalsFor(suggestions: List<ScreenSuggestion>): Result<List<ScreenProposal>> = runCatching {
        suggestions.flatMap { PackSuggestionMapping.map(it) }
    }
}
