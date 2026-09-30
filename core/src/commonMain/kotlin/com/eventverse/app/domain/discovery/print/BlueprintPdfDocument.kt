package com.eventverse.app.domain.discovery.print

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.pack.DomainPack

/**
 * Satu baris modul di dokumen blueprint.
 *
 * Modul **non-aktif tetap dicetak** (ditandai `[bypass]`), mengikuti keputusan B4/TRD-PLAT-001 FR-2:
 * Gudang yang di-bypass di CMT tetap punya perilaku, dan prospek berhak melihat apa yang dimatikan
 * sebelum ia menandatangani. Menyembunyikannya akan membuat PDF terlihat lebih ramping daripada
 * sistem yang benar-benar akan dibangun.
 */
data class BlueprintModuleLine(
    val moduleCode: String,
    val displayName: String,
    val sectionName: String,
    /** Fase kanvas tempat modul digambar; `null` untuk modul non-operasional (governance/foundation). */
    val phaseName: String?,
    val active: Boolean,
    val parameters: List<Pair<String, String>>
)

/**
 * Isi dokumen blueprint (plan §5, Fase D) — **bukan** geometri: [BlueprintSheetLayout] yang mengurus
 * posisi. Dipisah karena isi diuji sebagai teks ("apakah PDF klinik menyebut kata *pabrik*?") dan
 * geometri diuji sebagai bidang, sehingga kegagalan tata letak tidak menyamar sebagai kegagalan isi.
 *
 * Sumbernya adalah [DiscoveryDraft] yang sudah lolos codec ketat + validator: pack (kosakata vertikal)
 * + blueprint (modul aktif & parameter) + layar prototype. Tidak ada angka harga di sini — harga
 * diputuskan di endpoint `/price` dan **tidak** masuk PDF yang bisa berpindah tangan.
 */
data class BlueprintPdfDocument(
    val title: String,
    val packName: String,
    val packCode: String,
    val blueprintName: String,
    val badge: String,
    val description: String,
    val targetClientProfile: String,
    val generatedAtLabel: String,
    /** Kata yang tercetak diagonal di setiap halaman; sumbernya [watermarkFor]. */
    val watermark: String,
    val footerNote: String,
    /** Pasangan `istilah netral → istilah pack` (A4): "dokumen → Kunjungan". */
    val terms: List<Pair<String, String>>,
    val phases: List<String>,
    val modules: List<BlueprintModuleLine>,
    val screens: List<String>
) {
    val activeModuleCount: Int get() = modules.count { it.active }

    companion object {
        const val DEFAULT_TITLE = "Blueprint Sistem"

        /**
         * Watermark bukan hiasan: PDF ini beredar lewat WhatsApp prospek, dan tanpa penanda ia akan
         * difoto lalu dirujuk sebagai "kesepakatan". Kalimatnya menyebut **apa yang belum berlaku**,
         * bukan sekadar kata "DRAF" yang mudah dilewati mata.
         */
        const val WATERMARK_DRAFT = "DRAF — BUKAN PENAWARAN"
        const val WATERMARK_LOCKED = "TERKUNCI — BELUM DIBANGUN"

        const val DEFAULT_FOOTER =
            "Dokumen ini dihasilkan otomatis dari sesi Discovery dan belum menjadi penawaran harga."

        fun watermarkFor(status: DiscoveryDraftStatus): String = when (status) {
            DiscoveryDraftStatus.DRAFT -> WATERMARK_DRAFT
            DiscoveryDraftStatus.LOCKED -> WATERMARK_LOCKED
        }

        /**
         * Merakit isi dari draf. Urutan modul = urutan pack (bukan urutan blueprint), karena pembaca
         * membandingkan "apa yang ditawarkan" dengan "apa yang dibangun" per seksi menu.
         */
        fun of(
            draft: DiscoveryDraft,
            generatedAtLabel: String,
            watermark: String,
            title: String = DEFAULT_TITLE,
            footerNote: String = DEFAULT_FOOTER
        ): BlueprintPdfDocument {
            val pack = draft.pack
            val blueprint = draft.blueprint
            return BlueprintPdfDocument(
                title = title,
                packName = pack.displayName,
                packCode = pack.code.value,
                blueprintName = blueprint.displayName,
                badge = blueprint.shortBadge,
                description = blueprint.description,
                targetClientProfile = blueprint.targetClientProfile,
                generatedAtLabel = generatedAtLabel,
                watermark = watermark,
                footerNote = footerNote,
                terms = termsOf(pack),
                phases = pack.orderedPhases.map { "${it.displayName} — ${it.subtitle}" },
                modules = pack.modules.map { module ->
                    BlueprintModuleLine(
                        moduleCode = module.id.value,
                        displayName = module.displayName,
                        sectionName = pack.sections.firstOrNull { it.code == module.section }?.displayName
                            ?: module.section.value,
                        phaseName = module.slot?.let { pack.phaseOfSlot(it).displayName },
                        active = blueprint.isActive(module.id.value),
                        parameters = blueprint.parametersOf(module.id.value).entries
                            .sortedBy { it.key }
                            .map { it.key to it.value }
                    )
                },
                screens = draft.screens.map { "${it.title} (${it.widget}) — ${it.moduleId.value}" }
            )
        }

        /**
         * Istilah pack (A4) sebagai pasangan `netral → pack`.
         *
         * Ditulis berpasangan, bukan sebagai daftar kata: pembaca PDF tidak punya chrome aplikasi di
         * sebelahnya, jadi "klinik" saja tidak menjelaskan bahwa kata itu **menggantikan** "perusahaan".
         */
        private fun termsOf(pack: DomainPack): List<Pair<String, String>> =
            pack.vocabulary.entries
                .sortedBy { it.key.name }
                .map { it.key.neutral to it.value }
    }
}
