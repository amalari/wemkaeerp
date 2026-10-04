package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.InteractiveScreenFactory

/**
 * `WidgetRegistry` v1 (plan §4): satu-satunya pemetaan kind widget → perilaku prototype. V1
 * menyediakan **sample data berupa data** — [sampleRowsFor] murni & deterministik, sehingga
 * renderer (Fase C UI) hanya menggambar, dan test bisa membandingkan keluarannya persis.
 *
 * Sample TIDAK berusaha realistis: ia penanda struktur (kolom apa, urutan apa) supaya prospek
 * menilai *bentuk* layar, bukan datanya. Label memakai kosakata pack (nama modul/seksi), bukan
 * istilah garment. Untuk `CUSTOM_SCREEN` penanda strukturnya berupa kerangka blok + lebarnya,
 * karena layar rancangan bebas tidak punya kolom baku.
 */
object WidgetRegistry {

    /**
     * Proyeksi deskriptor layar beku ke pack **registri hidup**. Layar ber-id `default-<moduleId>`
     * (dibuat `EnsureTenantWorkingDraftUseCase`) adalah milik pack: judul, widget, dan barisnya
     * mengikuti usulan terkini — begitu pack merevisi watak layar (mis. CRM dari FORM menjadi
     * TABLE), draf lama ikut tanpa dokumennya ditulis ulang. Layar lain (usulan agent LLM /
     * suntingan user) tetap beku apa adanya.
     */
    fun screenFor(screen: PrototypeScreen, pack: DomainPack): PrototypeScreen {
        if (!screen.screenId.startsWith("default-")) return screen
        val suggestion = pack.screenSuggestions.firstOrNull { it.moduleId == screen.moduleId } ?: return screen
        return screen.copy(title = suggestion.title, widget = suggestion.widget.code)
    }

    /** Satu baris contoh: judul kolom → isi. Renderer memutuskan bagaimana menampilkannya. */
    fun sampleRowsFor(screen: PrototypeScreen, pack: DomainPack): List<Map<String, String>> {
        // v2: isi layar bawaan pack dipakai **apa adanya** bila watak widget layar cocok dengan
        // usulannya — ini data vertikal dari pack, bukan karangan mesin. Layar dengan widget lain
        // (mis. usulan agent LLM) jatuh ke penanda struktural generik di bawah, karena baris pack
        // punya bentuk yang dikontrak per widget (lihat KDoc ScreenSuggestion).
        val suggestion = pack.screenSuggestions.firstOrNull { it.moduleId == screen.moduleId }
        if (suggestion != null && suggestion.sampleRows.isNotEmpty() && suggestion.widget == WidgetKind.fromCode(screen.widget)) {
            return suggestion.sampleRows
        }
        val module = pack.modules.firstOrNull { it.id == screen.moduleId } ?: return emptyList()
        val kolom = module.displayName
        val seksi = pack.sections.firstOrNull { it.code == module.section }?.displayName ?: module.section.value
        return when (WidgetKind.fromCode(screen.widget)) {
            WidgetKind.TABLE -> (1..5).map { i ->
                mapOf(kolom to "${module.displayName} contoh $i", "Seksi" to seksi, "Status" to statusOf(i))
            }
            WidgetKind.KANBAN -> listOf("Baru", "Dikerjakan", "Selesai").mapIndexed { i, kolom ->
                mapOf("Kolom" to kolom, kolom.uppercase() to "${module.displayName} contoh ${i + 1}")
            }
            WidgetKind.FORM -> listOf(
                mapOf("Nama" to "Nama ${module.displayName}", "Keterangan" to "Penjelasan singkat", "Simpan" to "Simpan ${module.displayName}")
            )
            WidgetKind.CHECKLIST -> (1..4).map { i ->
                mapOf("Butir $i" to "Periksa ${module.displayName.lowercase()} langkah $i", "Selesai" to if (i % 2 == 0) "ya" else "tidak")
            }
            WidgetKind.DASHBOARD -> listOf(
                "Hari ini" to "${seksi}: 12", "Minggu ini" to "${seksi}: 84",
                "Tertunda" to "${seksi}: 3", "Selesai" to "${seksi}: 81"
            ).map { (k, v) -> mapOf(k to v) }
            WidgetKind.PRINT -> listOf(
                mapOf("Dokumen" to "Cetakan ${module.displayName}", "Nomor" to "0001/${seksi.take(3).uppercase()}/2026")
            )
            // Layar rancangan bebas tidak punya bentuk baku, jadi samplenya adalah **kerangka
            // tata letak**: blok mana yang ada dan selebar apa. Prospek menilai susunannya; isi
            // nyatanya menyusul setelah modul dibangun. Sebelum ini kind ini mengembalikan
            // `emptyList()`, dan renderer menggambar kartu kosong tanpa penjelasan apa pun.
            WidgetKind.CUSTOM_SCREEN -> listOf(
                mapOf("Blok" to "Ringkasan ${module.displayName}", "Lebar" to "penuh"),
                mapOf("Blok" to "Daftar ${module.displayName}", "Lebar" to "separuh"),
                mapOf("Blok" to "Panel aksi", "Lebar" to "separuh")
            )
            null -> emptyList()
        }
    }

    /**
     * Versi **bisa dimainkan** layar kanban/tabel/checklist/dasbor (TRD-PLAT-003): spec + seed dari baris contoh yang sama
     * dengan [sampleRowsFor], dipandu `kanbanHints` pack. Null untuk widget lain atau baris yang tak
     * bisa dibentuk jadi papan — klien lalu menggambar statis.
     */
    fun interactiveFor(screen: PrototypeScreen, pack: DomainPack): InteractiveScreen? {
        val kind = WidgetKind.fromCode(screen.widget)
        val suggestion = pack.screenSuggestions.firstOrNull { it.moduleId == screen.moduleId }?.takeIf { it.widget == kind }
        return when (kind) {
            WidgetKind.KANBAN -> InteractiveScreenFactory.kanban(screen.screenId, screen.title, sampleRowsFor(screen, pack), suggestion?.kanbanHints)
            WidgetKind.TABLE -> InteractiveScreenFactory.table(screen.screenId, screen.title, sampleRowsFor(screen, pack), suggestion?.tableHints)
            WidgetKind.CHECKLIST -> InteractiveScreenFactory.checklist(screen.screenId, screen.title, sampleRowsFor(screen, pack))
            WidgetKind.DASHBOARD -> InteractiveScreenFactory.dashboard(screen.screenId, screen.title, sampleRowsFor(screen, pack), suggestion?.dashboardHints)
            // FORM melengkapi layar sumber satu modul (butir B2): petunjuknya dibaca dari usulan
            // modul yang sama apa pun watak widget sumbernya (lazimnya TABLE) — bukan dari usulan
            // ber-widget FORM. Tanpa petunjuk = layar berbentuk tak cocok → null → digambar statis.
            WidgetKind.FORM -> pack.screenSuggestions.firstOrNull { it.moduleId == screen.moduleId }
                ?.formHints?.let { InteractiveScreenFactory.form(screen.screenId, screen.title, it) }
            else -> null
        }
    }

    private fun statusOf(i: Int): String = listOf("Baru", "Proses", "Selesai", "Proses", "Baru")[(i - 1) % 5]
}
