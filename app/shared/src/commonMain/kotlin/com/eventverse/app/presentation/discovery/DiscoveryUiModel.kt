package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.shared.discovery.ScreenProposalCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.InteractiveScreenCodec

/**
 * Model tampilan draf discovery (Fase D): hasil parse ringkasan `GET /api/discovery/drafts/{id}`.
 * Murni data tampilan — validasi domain tetap milik server; klien hanya menggambar.
 */
data class DiscoverySectionUi(
    val code: String,
    val displayName: String,
    val order: Int,
    val colorHex: Long,
    val tintHex: Long
)

data class DiscoveryModuleUi(
    val id: String,
    val displayName: String,
    val section: String,
    val kind: String,
    val iconKey: String? = null,
    val slot: String?,
    val slotInput: String?,
    val slotOutput: String?,
    val active: Boolean
)

data class DiscoveryScreenUi(
    val screenId: String,
    val moduleId: String,
    val title: String,
    val widget: String,
    val sampleRows: List<Map<String, String>>,
    /** Versi bisa dimainkan (TRD-PLAT-003); null = gambar statis dari [sampleRows]. */
    val interactive: InteractiveScreen? = null,
    /** Usulan layar tervalidasi (kontrak ScreenProposal); null untuk draf lama tanpa proposal. */
    val proposal: ScreenProposal? = null,
    /** Alasan pemilihan jenis tampilan bahasa pemilik usaha ("Dipilih karena …"). */
    val rationale: String? = null,
    /** Asal usulan (Pack / Deterministik / Agent). */
    val source: ProposalSource? = null
)

data class DiscoveryDraftUi(
    val id: String,
    val status: String,
    /** Narasi asli (E1/E2) — dipulihkan server dari buku demand; null untuk draf sebelum V80. */
    val narrative: String?,
    val packCode: String,
    val packDisplayName: String,
    val blueprintCode: String,
    val blueprintDescription: String,
    val modules: List<DiscoveryModuleUi>,
    val sectionsMetadata: List<DiscoverySectionUi> = emptyList(),
    val activeModuleCodes: List<String>,
    val screens: List<DiscoveryScreenUi>,
    /**
     * Kosakata label dari pack draf itu sendiri (`summaryObj`: `portLabels`/`slotLabels`).
     * Sumber utama label — bekerja juga untuk draf pra-handoff yang pack-nya belum terdaftar
     * di [com.eventverse.app.domain.pack.DomainPackRegistry]; tanpa entri = kode, bukan pack lain.
     */
    val portLabels: Map<String, String> = emptyMap(),
    val slotLabels: Map<String, String> = emptyMap()
) {
    val activeModules: List<DiscoveryModuleUi> get() = modules.filter { it.active }
    val sections: List<String> get() = if (sectionsMetadata.isNotEmpty()) {
        sectionsMetadata.sortedBy { it.order }.map { it.code }
    } else {
        modules.map { it.section }.distinct()
    }

    companion object {
        fun fromJson(o: JsonValue.Obj): DiscoveryDraftUi {
            fun arr(key: String): List<JsonValue> = (o[key] as? JsonValue.Arr)?.items ?: emptyList()
            fun arr2(src: JsonValue.Obj, key: String): List<Map<String, String>> =
                ((src[key] as? JsonValue.Arr)?.items ?: emptyList())
                    .mapNotNull { it as? JsonValue.Obj }
                    .map { row -> row.entries.mapValues { (_, v) -> (v as? JsonValue.Str)?.value.orEmpty() } }
            fun stringMap(key: String): Map<String, String> =
                (o[key] as? JsonValue.Obj)?.entries
                    ?.mapValues { (_, v) -> (v as? JsonValue.Str)?.value.orEmpty() }
                    ?.filterValues { it.isNotEmpty() }
                    ?: emptyMap()
            return DiscoveryDraftUi(
                id = o.string("id").orEmpty(),
                status = o.string("status").orEmpty(),
                narrative = o.string("narrative"),
                packCode = o.string("packCode").orEmpty(),
                packDisplayName = o.string("packDisplayName").orEmpty(),
                blueprintCode = o.string("blueprintCode").orEmpty(),
                blueprintDescription = o.string("blueprintDescription").orEmpty(),
                modules = arr("modules").mapNotNull { it as? JsonValue.Obj }.map { m ->
                    DiscoveryModuleUi(
                        id = m.string("id").orEmpty(),
                        displayName = m.string("displayName").orEmpty(),
                        section = m.string("section").orEmpty(),
                        kind = m.string("kind").orEmpty(),
                        iconKey = m.string("iconKey"),
                        slot = m.string("slot"),
                        slotInput = m.string("slotInput"),
                        slotOutput = m.string("slotOutput"),
                        active = m.string("id") in (arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value })
                    )
                },
                sectionsMetadata = arr("sections").mapNotNull { it as? JsonValue.Obj }.map { s ->
                    DiscoverySectionUi(
                        code = s.string("code").orEmpty(),
                        displayName = s.string("displayName").orEmpty(),
                        order = s.int("order") ?: 0,
                        colorHex = s.long("colorHex") ?: 0L,
                        tintHex = s.long("tintHex") ?: 0L
                    )
                },
                activeModuleCodes = arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value },
                screens = arr("screens").mapNotNull { it as? JsonValue.Obj }.map { s ->
                    val proposal = s.obj("proposal")?.let { raw ->
                        runCatching { ScreenProposalCodec.decode(raw, "$.screens.proposal") }.getOrNull()
                    }
                    val source = s.obj("source")?.let { raw ->
                        runCatching { ScreenProposalCodec.decodeSource(raw, "$.screens.source") }.getOrNull()
                    } ?: s.string("source")?.let { str ->
                        when {
                            str.equals("PACK", ignoreCase = true) -> ProposalSource.Pack
                            str.equals("DETERMINISTIC", ignoreCase = true) -> ProposalSource.Deterministic
                            str.startsWith("AGENT", ignoreCase = true) -> {
                                val ref = str.substringAfter(":", "").ifBlank { str }
                                ProposalSource.Agent(ref)
                            }
                            else -> null
                        }
                    }
                    val rationale = s.string("rationale") ?: proposal?.rationale

                    DiscoveryScreenUi(
                        screenId = s.string("screenId").orEmpty(),
                        moduleId = s.string("moduleId").orEmpty(),
                        title = s.string("title").orEmpty(),
                        widget = s.string("widget").orEmpty(),
                        sampleRows = arr2(s, "sampleRows"),
                        interactive = s.obj("interactive")?.let { runCatching { InteractiveScreenCodec.decode(it) }.getOrNull() },
                        proposal = proposal,
                        rationale = rationale,
                        source = source
                    )
                },
                portLabels = stringMap("portLabels"),
                slotLabels = stringMap("slotLabels")
            )
        }
    }
}

/** Format label tampilan sumber untuk lencana UI. */
val ProposalSource.displayName: String
    get() = when (this) {
        is ProposalSource.Pack -> "Pack"
        is ProposalSource.Deterministic -> "Deterministik"
        is ProposalSource.Agent -> {
            val modelName = agentRef.substringAfterLast('/').ifBlank { agentRef }
            "Agent · $modelName"
        }
    }

