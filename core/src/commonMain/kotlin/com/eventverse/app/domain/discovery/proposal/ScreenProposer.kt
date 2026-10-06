package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleDefinition

/**
 * Port pembuat usulan layar (plan §2.4). Implementasi: `PackScreenProposer` (acuan dari pack, [ProposalSource.Pack]),
 * pembuat deterministik (peran → tampilan, [ProposalSource.Deterministic]); agent LLM menghasilkan usulan di dalam
 * dokumen drafnya sendiri, bukan lewat port ini.
 *
 * Hasil kosong = pembuat ini **tidak mengusulkan** apa pun untuk modul itu (bukan galat, dan tidak ada tebakan).
 * Galat nyata (usulan tak bisa dibentuk) → `Result.failure` bermesej. Keluaran **belum divalidasi**: pemanggil
 * menjalankan [ScreenProposalValidator] seperti untuk pembuat mana pun.
 */
interface ScreenProposer {
    suspend fun propose(pack: DomainPack, module: ModuleDefinition, narrative: String?): Result<List<ScreenProposal>>
}
