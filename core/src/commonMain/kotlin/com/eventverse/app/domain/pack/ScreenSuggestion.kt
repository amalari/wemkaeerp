package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.TableHints

/**
 * Usulan satu layar prototype bawaan pack (mock `/builder/prototype`). **Isi layar = data pack**:
 * judul layar dan watak widget bawaan adalah kosakata vertikal (tenant-variability-rules), sementara
 * bentuk widgetnya sendiri tetap kosakata sistem [WidgetKind] yang bisa digambar renderer mana pun.
 * Draf kerja tenant yang baru di-bootstrap memakai usulan ini sebelum agent LLM mengusulkan layar
 * miliknya sendiri; draf yang sudah punya layar tidak pernah ditimpa.
 */
data class ScreenSuggestion(
    val moduleId: ModuleId,
    val title: String,
    val widget: WidgetKind,
    /**
     * Baris contoh isi layar (v2 mock `/builder/prototype`): **data vertikal** — kalimat, angka, dan
     * nama yang akan dilihat user garment, bukan karangan mesin renderer. Kosong = pack tidak
     * mengusulkan isi; `WidgetRegistry` lalu memakai penanda strukturnya sendiri (tidak pernah
     * fallback ke kosakata pack lain). Bentuk baris dikonsumsi per widget:
     *  - KANBAN: kunci `Kolom` menamaikan kolom papan; entri lain = kartu (nilai pertama judul, sisanya detail).
     *  - FORM: satu baris berisi label field → contoh isian; kunci `Simpan` digambar sebagai tombol.
     *  - CHECKLIST: kunci `Butir` → teks inspeksi, `Selesai` = "ya"/"tidak".
     *  - TABLE: baris-baris berkunci sama = header + isi tabel.
     *  - DASHBOARD: satu pasang label → angka per baris (satu tile).
     *  - PRINT: pasangan label → isi dokumen cetak.
     */
    val sampleRows: List<Map<String, String>> = emptyList(),
    /**
     * Perilaku papan (hanya bermakna untuk [WidgetKind.KANBAN]): urutan kolom termasuk yang kosong dan
     * transisi yang boleh. Null = kolom diturunkan dari [sampleRows], kartu bebas pindah.
     */
    val kanbanHints: KanbanHints? = null,
    /** Perilaku tabel (hanya bermakna untuk [WidgetKind.TABLE]); null = tabel hanya bisa disortir/difilter. */
    val tableHints: TableHints? = null,
    /** Ubin dasbor yang dihitung dari layar lain (hanya bermakna untuk [WidgetKind.DASHBOARD]). */
    val dashboardHints: DashboardHints? = null,
    /**
     * Petunjuk formulir tambah data (butir B2): pelengkap layar sumber — boleh terpasang pada
     * usulan ber-widget apa pun (lazimnya TABLE/KANBAN), karena form berbagi entitas dengan layar
     * sumbernya, bukan berdiri sendiri. Null = modul tidak mengusulkan form.
     */
    val formHints: FormHints? = null,
    /**
     * Asal data layar (kontrak v2, plan induk §3.4): bawaan [DataBinding.Memory] (demo memori);
     * modul pilot memakai [DataBinding.Api] agar blok memuat/menyimpan ke server. Jenis binding
     * adalah kosakata **sistem** (kode); memilih nilainya adalah keputusan pack.
     */
    val dataBinding: DataBinding = DataBinding.Memory,
    /**
     * Alasan pilihan layar ini dalam bahasa pemilik usaha ("Dipilih karena …"), ditulis manusia — data pack.
     * Dibaca `PackScreenProposer` menjadi `ScreenProposal.rationale`. Null = pack belum menuliskannya; proposer
     * lalu **menolak** (tidak mengarang alasan). Opsional: pack lama tanpa kunci ini tetap terbaca.
     */
    val rationale: String? = null,
    /**
     * Jembatan Strangler Fig TRD-I18N-002 (P4): judul & alasan dua bahasa; [title]/[rationale] = teks `id`.
     * **[sampleRows] tidak dilokalkan**: kuncinya (`Kolom`, `Simpan`, `Butir`) adalah protokol struktur renderer,
     * bukan teks tampilan; menerjemahkannya memutus renderer. Lihat TRD-I18N-002 §4 (tindak lanjut).
     */
    val titleText: LocalizedText = LocalizedText.of(title),
    val rationaleText: LocalizedText? = rationale?.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
) {
    init {
        require(title.isNotBlank()) { "Usulan layar ${moduleId.value} tanpa judul" }
        require(titleText.idText == title) { "Usulan layar ${moduleId.value}: titleText.id harus sama dengan title" }
        require(rationaleText == null || rationaleText.idText == rationale) { "Usulan layar ${moduleId.value}: rationaleText.id harus sama dengan rationale" }
        require(sampleRows.all { row -> row.isNotEmpty() && row.keys.all { it.isNotBlank() } && row.values.all { it.isNotBlank() } }) {
            "Usulan layar ${moduleId.value} punya baris contoh kosong"
        }
    }
}
