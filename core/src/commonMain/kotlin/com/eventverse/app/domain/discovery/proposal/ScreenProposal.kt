package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.FieldType

/**
 * Usulan satu layar prototype — **kontrak tunggal** antara semua pembuat layar (manusia di pack, agent
 * deterministik, agent LLM) dan satu validator ([ScreenProposalValidator]).
 *
 * Bentuknya **ringkas** (bukan `InteractiveScreen` penuh) supaya ramah LLM dan murah token; konversi ke
 * `InteractiveScreen` dilakukan kode ([toInteractiveScreen]), bukan model.
 *
 * Kode-vs-data (Uji Variabilitas): isi usulan (entitas, field, status, label, kartu) adalah **data** yang
 * berbeda per tenant/industri. Yang tinggal di kode hanya kosakata **tertutup milik sistem** — [WidgetKind],
 * [FieldType], `CardStyle` — karena renderer harus bisa menggambar tiap anggotanya di semua vertikal.
 *
 * Tipe ini sengaja **tidak melempar di konstruktor** untuk aturan bisnis: pelanggaran dikumpulkan
 * [ScreenProposalValidator] sebagai galat berpath yang bisa dikembalikan ke LLM untuk koreksi. Template vs
 * salinan: proposal adalah dokumen; ia dibekukan bersama draf saat draf `LOCKED`.
 */
data class ScreenProposal(
    val screenId: String,
    val moduleId: ModuleId,
    val title: String,
    val widget: WidgetKind,
    /** Alasan bahasa pemilik usaha ("Dipilih karena …"), ≤ [ProposalLimits.TEXT] karakter. */
    val rationale: String,
    /**
     * Wajib untuk widget data (KANBAN, TABLE, FORM, CHECKLIST); null untuk DASHBOARD dan CUSTOM_SCREEN;
     * opsional untuk PRINT (wajib bila `ViewProposal.Print.fields` tak kosong — field-nya merujuk entitas ini).
     */
    val entity: EntityProposal?,
    val view: ViewProposal,
    /** ≤ [ProposalLimits.SEED_ROWS] baris, divalidasi terhadap skema [entity]. */
    val seed: List<Map<String, String>> = emptyList(),
    val binding: DataBinding = DataBinding.Memory
)

/**
 * Jenis benda yang dikelola layar. [statusField] (bila ada) wajib field ENUM; [transitions] membatasi
 * perpindahan status — kosong = bebas pindah (sama dengan petunjuk papan di pack).
 */
data class EntityProposal(
    val id: String,
    val label: String,
    val fields: List<FieldProposal>,
    val statusField: String? = null,
    val transitions: Map<String, List<String>> = emptyMap()
)

data class FieldProposal(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /** Wajib untuk [FieldType.ENUM]; kosong untuk tipe lain. */
    val options: List<String> = emptyList()
)

/**
 * Asal usulan (provenance), tampil ke prospek. Enum sah di bagian "peran platform": tiga pembuat memang
 * dimiliki sistem. [Agent] membawa `agentRef` (mis. `koog/<model>/draft-v1`) agar setiap layar bisa dilacak.
 */
sealed interface ProposalSource {
    data object Pack : ProposalSource
    data object Deterministic : ProposalSource
    data class Agent(val agentRef: String) : ProposalSource {
        init { require(agentRef.isNotBlank()) { "ProposalSource.Agent.agentRef kosong" } }
    }
}

/** Batas anti-bengkak & anti-penyalahgunaan (plan §2.2) — satu tempat, dipakai validator dan prompt. */
object ProposalLimits {
    const val FIELDS = 12
    const val OPTIONS = 8
    const val STATUSES = 8
    const val SEED_ROWS = 8
    const val TEXT = 200
    const val TILES = 8
    val KEY = Regex("[a-z][a-z0-9_]{0,40}")
}
