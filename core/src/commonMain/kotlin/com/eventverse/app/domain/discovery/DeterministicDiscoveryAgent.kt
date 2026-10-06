package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability

/**
 * Fallback deterministik (plan §2 A3, D4): narasi → kata kunci → draf, **tanpa jaringan dan tanpa biaya**.
 * Dua jalur:
 *
 * 1. narasi konveksi → draf memakai pack garment bawaan + salah satu starter B4 (FOB/CMT/D2C). Dokumen pack
 *    **identik** dengan yang dikirim platform — validator menolak penulisan ulang pack bawaan;
 * 2. narasi lain → pack baru berprefiks kode vertikal (aturan identitas global B7), satu fase, satu seksi,
 *    dan modul generik per kata kunci kemampuan. Modul selalu berprefiks `<kode pack>_`, jadi tidak mungkin
 *    merebut id platform (risiko plan §7).
 *
 * Ini juga baseline evals A9: skor agent LLM dibandingkan hasil deterministik yang sama narasinya.
 */
class DeterministicDiscoveryAgent : DiscoveryAgent {

    override val agentRef: String = "deterministic/keyword-v1"

    override suspend fun draft(request: DiscoveryRequest): Result<DiscoveryDraft> = runCatching {
        garmentStarter(request.narrative)?.let { starter ->
            DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = starter)
        } ?: customPackDraft(request)
    }

    private fun garmentStarter(narrative: String): Blueprint? {
        val text = narrative.lowercase()
        if (GARMENT_WORDS.none { text.contains(it) }) return null
        return when {
            CMT_WORDS.any { text.contains(it) } -> GarmentBlueprints.CMT_MAKLOON
            D2C_WORDS.any { text.contains(it) } -> GarmentBlueprints.BRAND_D2C
            else -> GarmentBlueprints.FOB_FULL_PACKAGE
        }
    }

    private fun customPackDraft(request: DiscoveryRequest): DiscoveryDraft {
        val code = packCode(request)
        val displayName = request.displayName?.takeIf { it.isNotBlank() }
            ?: code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        val section = ModuleSection(ModuleSectionCode("UTAMA"), "Operasional", 1, 0xFF2563EB, 0xFFEFF6FF)
        val phase = PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur kerja harian", 0xFF2563EB)
        val capabilities = resolveCapabilities(request.narrative)
        // A4: pack yang lahir dari narasi langsung berbicara bahasa vertikalnya ("klinik", "Kunjungan"),
        // bukan kata netral — dan tidak pernah kata konveksi.
        val terms = INDUSTRY_TERMS[industryWord(request).orEmpty()].orEmpty()

        val slots = capabilities.map { cap ->
            SlotDefinition(
                SlotCode("${code}_${cap.suffix}"), cap.name, PhaseCode("OPERASI"),
                PortType("Permintaan"), PortType("Catatan"), cap.widget, cap.statuses
            )
        }
        val modules = capabilities.map { cap ->
            ModuleDefinition(
                id = ModuleId("${code}_${cap.suffix}"),
                displayName = cap.name,
                description = "Draf discovery: ${cap.name.lowercase()} untuk $displayName",
                section = section.code,
                kind = ModuleKind.OPERATIONAL,
                iconKey = "clipboard",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA),
                slot = SlotCode("${code}_${cap.suffix}")
            )
        }
        val pack = DomainPack(
            code = DomainPackCode(code),
            displayName = displayName,
            phases = listOf(phase),
            slots = slots,
            portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            sections = listOf(section),
            modules = modules,
            actions = actionsFor(terms[VocabularyKey.DOCUMENT]),
            vocabulary = terms
        )
        val blueprint = Blueprint(
            code = BlueprintCode("${code}_starter"),
            pack = pack.code,
            displayName = "Starter $displayName",
            shortBadge = "Discovery",
            description = "Draf awal dari narasi prospek (agent deterministik).",
            targetClientProfile = request.narrative.take(150),
            modules = modules.map { BlueprintModule(it.id.value, active = true) }
        )
        return DiscoveryDraft(pack = pack, blueprint = blueprint)
    }

    /** Kode pack dari hint, atau kata kunci vertikal pertama di narasi; slug ketat, tanpa jatuh ke 'garment'. */
    private fun packCode(request: DiscoveryRequest): String {
        val source = industryWord(request) ?: "kustom"
        val slug = source.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .replace(Regex("_+"), "_")
            .take(40)
            .trim('_')
        val safe = if (slug.isNotEmpty() && slug.first().isLetter()) slug else "kustom"
        return if (safe == GarmentDomainPack.CODE.value) "bisnis_kustom" else safe
    }

    private fun resolveCapabilities(narrative: String): List<Capability> {
        val text = narrative.lowercase()
        return CATALOG.filter { cap -> cap.keywords.any { text.contains(it) } }
            .ifEmpty { listOf(Capability("operasional", "Operasional Harian", listOf(""))) }
    }

    /** Vertikal yang disebut narasi/hint; `null` = tak dikenali → chrome memakai kata netral platform. */
    private fun industryWord(request: DiscoveryRequest): String? =
        request.industryHint?.takeIf { it.isNotBlank() }
            ?: INDUSTRY_WORDS.firstOrNull { request.narrative.lowercase().contains(it.first) }?.second

    /** Aksi generik bernama dokumen vertikal ("Tambah Kunjungan"), bukan "Tambah Pesanan" milik konveksi. */
    private fun actionsFor(document: String?): List<ModuleAction> {
        val doc = document?.takeIf { it.isNotBlank() } ?: return ModuleActionCode.neutral
        return listOf(
            ModuleAction(ModuleActionCode.ADD, "Tambah $doc"),
            ModuleAction(ModuleActionCode.EDIT, "Ubah $doc"),
            ModuleAction(ModuleActionCode.APPROVE, "Setujui $doc"),
            ModuleAction(ModuleActionCode.DELETE, "Hapus $doc")
        )
    }

    /** [widget]/[statuses] = pemetaan peran → tampilan slot (null = tanpa pendapat, mis. kemampuan cadangan). */
    private data class Capability(
        val suffix: String, val name: String, val keywords: List<String>,
        val widget: WidgetKind? = null, val statuses: List<String> = emptyList()
    )

    companion object {
        private val GARMENT_WORDS = listOf(
            "konveksi", "jahit", "garmen", "garment", "kain", "tekstil", "busana", "pakaian",
            "bordir", "sablon", "makloon", "potong", "spk", "fob", "cmt"
        )
        private val CMT_WORDS = listOf("makloon", "maklon", "cmt", "kain titipan", "bahan dari buyer", "bahan disediakan")
        private val D2C_WORDS = listOf("d2c", "brand sendiri", "distro", "retail", "marketplace", "toko online")
        private val INDUSTRY_WORDS = listOf(
            "klinik" to "klinik", "puskesmas" to "klinik", "pasien" to "klinik", "poli" to "klinik",
            "bengkel" to "bengkel", "servis" to "bengkel", "workshop" to "bengkel",
            "katering" to "katering", "restoran" to "katering", "kulin" to "katering",
            "sekolah" to "sekolah", "kursus" to "sekolah", "siswa" to "sekolah",
            "toko" to "retail", "kasir" to "retail", "gudang" to "gudang", "logistik" to "logistik"
        )

        /**
         * Istilah chrome per vertikal (A4). Hanya kata yang **benar-benar diucapkan** pemilik usaha,
         * dan tidak satu pun boleh berupa kosakata konveksi — pack garment punya tabelnya sendiri.
         */
        private val INDUSTRY_TERMS: Map<String, Map<VocabularyKey, String>> = mapOf(
            "klinik" to mapOf(VocabularyKey.WORKPLACE to "klinik", VocabularyKey.DOCUMENT to "Kunjungan"),
            "bengkel" to mapOf(VocabularyKey.WORKPLACE to "bengkel", VocabularyKey.DOCUMENT to "Servis"),
            "katering" to mapOf(VocabularyKey.WORKPLACE to "dapur produksi", VocabularyKey.DOCUMENT to "Pesanan"),
            "sekolah" to mapOf(VocabularyKey.WORKPLACE to "sekolah", VocabularyKey.DOCUMENT to "Pendaftaran"),
            "retail" to mapOf(VocabularyKey.WORKPLACE to "toko", VocabularyKey.DOCUMENT to "Transaksi"),
            "gudang" to mapOf(VocabularyKey.WORKPLACE to "gudang", VocabularyKey.DOCUMENT to "Barang masuk"),
            "logistik" to mapOf(VocabularyKey.WORKPLACE to "gudang", VocabularyKey.DOCUMENT to "Pengiriman")
        )

        private val CATALOG = listOf(
            Capability("pesanan", "Penerimaan Pesanan", listOf("pesanan", "order", "booking", "reservasi", "pendaftaran"),
                WidgetKind.TABLE, listOf("Baru", "Diproses", "Selesai")),
            Capability("antrean", "Antrean & Penjadwalan", listOf("antrean", "antrian", "jadwal", "poli", "slot waktu"),
                WidgetKind.KANBAN, listOf("Menunggu", "Dikerjakan", "Selesai")),
            Capability("stok", "Persediaan & Gudang", listOf("stok", "persediaan", "gudang", "bahan"),
                WidgetKind.TABLE, listOf("Tersedia", "Menipis", "Habis")),
            Capability("tagihan", "Tagihan & Pembayaran", listOf("tagihan", "invoice", "pembayaran", "kasir", "penjualan"),
                WidgetKind.TABLE, listOf("Belum bayar", "Lunas")),
            Capability("laporan", "Laporan & Pemantauan", listOf("laporan", "monitoring", "dasbor", "dashboard", "rekap"),
                WidgetKind.DASHBOARD)
        )
    }
}
