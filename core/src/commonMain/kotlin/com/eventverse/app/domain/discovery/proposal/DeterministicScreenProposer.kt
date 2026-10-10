package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TileSpec

/**
 * Pembuat usulan **tanpa jaringan dan tanpa LLM**: menurunkan layar dari pemetaan peran → tampilan yang menjadi data
 * pack ([SlotDefinition.defaultWidget] / [SlotDefinition.defaultStatuses], plan §2.3). Keluarannya deterministik
 * byte-per-byte untuk (pack, modul) yang sama — narasi diabaikan — sehingga demo dan eval tak bergantung kunci API.
 *
 * **Tidak ada tebakan.** Modul tanpa slot, atau slot tanpa `defaultWidget`, **tidak** dibuatkan layar (daftar kosong);
 * pembuat ini tidak pernah meminjam watak dari pack/slot lain. Papan tanpa status gagal bermesej (pack salah isi),
 * bukan menjadi tabel diam-diam.
 *
 * Entitas minimal yang bermakna: judul (wajib), status ENUM bila slot punya `defaultStatuses`, catatan, plus
 * tambahan menurut peran dari [DeterministicScreenRoles]. Istilah vertikal dibaca dari `pack.vocabulary`
 * (dokumen & tempat kerja, mis. "Kunjungan" di "klinik") — tidak ada kosakata satu industri di kode ini.
 */
object DeterministicScreenProposer : ScreenProposer {

    override suspend fun propose(pack: DomainPack, module: ModuleDefinition, narrative: String?): Result<List<ScreenProposal>> =
        proposalsFor(pack, module)

    fun proposalsFor(pack: DomainPack, module: ModuleDefinition): Result<List<ScreenProposal>> = runCatching {
        val slot = module.slot?.let { code -> pack.slots.firstOrNull { it.code == code } }
        val widget = slot?.defaultWidget
        if (slot == null || widget == null) emptyList() else listOf(build(pack, module, slot, widget))
    }

    /** Layar semua modul pack yang slotnya berpendapat, urutan modul dipertahankan; gagal di modul pertama yang salah isi. */
    fun proposalsForAll(pack: DomainPack): Result<List<ScreenProposal>> = runCatching {
        pack.modules.flatMap { proposalsFor(pack, it).getOrThrow() }
    }

    private fun build(pack: DomainPack, module: ModuleDefinition, slot: SlotDefinition, widget: WidgetKind): ScreenProposal {
        val document = pack.vocabulary[VocabularyKey.DOCUMENT]
        val place = pack.vocabulary[VocabularyKey.WORKPLACE]
        val name = module.displayName
        val label = document ?: name
        val statuses = slot.defaultStatuses
        val extras = DeterministicScreenRoles.extrasFor(slot.code.value, pack.defaultCurrencyCode)
        val moduleKey = module.id.value
        val fields = listOf(FieldProposal(TITLE, document ?: "Judul", FieldType.TEXT, required = true)) +
            extras +
            FieldProposal(NOTE, "Catatan", FieldType.TEXT) +
            (if (statuses.isEmpty()) emptyList() else listOf(FieldProposal(STATUS, "Status", FieldType.ENUM, options = statuses)))
        val entity = EntityProposal(moduleKey, label, fields, statusField = STATUS.takeIf { statuses.isNotEmpty() })
        val where = place?.let { " di $it" }.orEmpty()
        fun layar(rationale: String, entity: EntityProposal?, view: ViewProposal, seed: List<Map<String, String>> = emptyList()) =
            ScreenProposal("$moduleKey-layar", module.id, name, widget, rationale.take(ProposalLimits.TEXT), entity, view, seed)

        return when (widget) {
            WidgetKind.KANBAN -> {
                require(statuses.isNotEmpty()) { "Slot ${slot.code.value} berwidget KANBAN tetapi tanpa defaultStatuses; papan butuh kolom" }
                layar(
                    "Dipilih karena ${name.lowercase()}$where bergerak lewat tahap ${stages(statuses)}, jadi paling jelas dilihat sebagai papan.",
                    entity,
                    ViewProposal.Kanban(
                        card = listOf(CardElement(TITLE, CardStyle.TITLE), CardElement(NOTE, CardStyle.TEXT)) + extras.mapNotNull(::cardOf),
                        detailFormFields = fields.map { it.key }
                    ),
                    seedRows(label, statuses, statuses.size)
                )
            }
            WidgetKind.TABLE -> layar(
                "Dipilih karena ${name.lowercase()}$where dibaca berderet dan dibandingkan satu per satu.",
                entity,
                ViewProposal.Table(
                    columns = listOf(TITLE) + extras.map { it.key } + (if (statuses.isEmpty()) emptyList() else listOf(STATUS)),
                    inlineCreate = true, editableFields = listOf(TITLE, NOTE) + extras.map { it.key }
                ),
                seedRows(label, statuses, 3)
            )
            WidgetKind.FORM -> layar(
                "Dipilih karena ${name.lowercase()}$where diisi beberapa data sekaligus dalam satu formulir.",
                entity.copy(fields = fields.filter { it.key != STATUS }, statusField = null),
                ViewProposal.Form(fields.map { it.key }.filter { it != STATUS }, "Simpan $label")
            )
            WidgetKind.CHECKLIST -> layar(
                "Dipilih karena ${name.lowercase()}$where adalah daftar langkah yang dicentang satu per satu.",
                EntityProposal(moduleKey, label, listOf(FieldProposal(ITEM, "Butir", FieldType.TEXT, required = true), FieldProposal(DONE, "Selesai", FieldType.BOOL))),
                ViewProposal.Checklist(ITEM, DONE),
                (1..4).map { mapOf(ITEM to "Langkah $it", DONE to if (it % 2 == 0) "ya" else "tidak") }
            )
            WidgetKind.DASHBOARD -> layar(
                "Dipilih karena pemilik usaha$where butuh angka ringkas tanpa membuka daftar satu per satu.",
                null, ViewProposal.Dashboard(tiles(pack, module))
            )
            WidgetKind.PRINT -> layar(
                "Dipilih karena ${name.lowercase()}$where diserahkan dalam bentuk cetak.",
                entity.copy(fields = fields.filter { it.key != STATUS }, statusField = null),
                ViewProposal.Print(fields.map { it.key }.filter { it != STATUS })
            )
            WidgetKind.CUSTOM_SCREEN -> layar(
                "Dipilih karena tata letak ${name.lowercase()}$where belum punya jenis tampilan baku.", null, ViewProposal.None
            )
        }
    }

    /** "A, B, lalu C" — tanpa panah: glyph non-ASCII (→) tidak ada di font Nunito dan tampil sebagai kotak. */
    private fun stages(statuses: List<String>): String =
        if (statuses.size < 2) statuses.joinToString() else statuses.dropLast(1).joinToString() + ", lalu " + statuses.last()

    /** Internal (bukan private) supaya test penjaga FILE bisa menegakkan langsung: FILE tidak pernah jadi elemen kartu. */
    internal fun cardOf(f: FieldProposal): CardElement? = when (f.type) {
        FieldType.DATE -> CardElement(f.key, CardStyle.DATE)
        FieldType.NUMBER -> CardElement(f.key, CardStyle.NUMBER)
        FieldType.BOOL -> CardElement(f.key, CardStyle.FLAG)
        // A0 (TRD-FIELD-003): gaya tampil kartu MULTI_SELECT ditetapkan Track C; di sini tidak tampil di kartu.
        // C6: TIME juga belum tampil di kartu (belum ada gaya kartu jam) — sama seperti tipe teks lainnya.
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.TIME, FieldType.ENUM, FieldType.MULTI_SELECT, FieldType.RELATION, FieldType.FILE -> null
    }

    /** Satu ubin per modul lain yang slotnya punya status: jumlah baris berstatus **awal** (antrean yang menunggu). */
    private fun tiles(pack: DomainPack, self: ModuleDefinition): List<TileSpec> {
        val tiles = pack.modules.filter { it.id != self.id }.mapNotNull { m ->
            val statuses = m.slot?.let { code -> pack.slots.firstOrNull { it.code == code } }?.takeIf { it.defaultWidget != null }?.defaultStatuses
            statuses?.takeIf { it.isNotEmpty() }?.let { s ->
                TileSpec("${m.displayName}: ${s.first()}", count = CountSpec(m.id.value, STATUS, equals = s.first()))
            }
        }.take(ProposalLimits.TILES)
        // Tanpa modul bersumber, ubin tak mengarang angka: nilainya penanda kosong yang jujur.
        return tiles.ifEmpty { listOf(TileSpec("Belum ada data untuk diringkas", value = "-")) }
    }

    /** Baris contoh berlabel jelas "Contoh …" — penanda bentuk, bukan data karangan yang tampak nyata. */
    private fun seedRows(label: String, statuses: List<String>, count: Int): List<Map<String, String>> =
        (1..count.coerceAtMost(ProposalLimits.SEED_ROWS)).map { i ->
            buildMap {
                put(TITLE, "Contoh $label $i")
                if (statuses.isNotEmpty()) put(STATUS, statuses[(i - 1) % statuses.size])
            }
        }

    private const val TITLE = "judul"
    private const val NOTE = "catatan"
    private const val STATUS = "status"
    private const val ITEM = "butir"
    private const val DONE = "selesai"
}
