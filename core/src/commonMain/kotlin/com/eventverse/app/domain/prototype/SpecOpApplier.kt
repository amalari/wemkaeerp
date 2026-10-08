package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.brief.CaptureEntry

/** Hasil satu giliran: layar akhir + log tiap operasi (sah maupun ditolak) untuk brief. */
data class AppliedOps(val screen: InteractiveScreen, val log: List<CaptureEntry>)

/**
 * Menerapkan [SpecOp] pada [InteractiveScreen] (kontrak v1). Murni, tanpa jam: waktu dikirim pemanggil.
 * [applyAll] menerapkan operasi satu per satu (yang gagal tidak membatalkan yang sah), membatasi
 * [MAX_OPS_PER_TURN] per giliran, dan mencatat semuanya. Spec hasil selalu dibangun lewat konstruktor
 * [PrototypeSpec], jadi validasinya **tidak pernah dilonggarkan** demi operasi.
 *
 * **Keputusan B3 — `AddField` tidak mengubah layar yang ada:** kolom tabel (`TableConfig.columns`)
 * dan field form (`FormConfig.fields`) adalah daftar eksplisit per layar; tidak ada layar yang
 * "menampilkan semua field". Field baru hanya masuk ke entitasnya; memunculkannya di layar tertentu
 * adalah keputusan lanjutan, bukan efek samping diam-diam. Jalur eksplisitnya adalah operasi
 * [SpecOp.ShowFieldOnCard] (B4) untuk papan kanban. [SpecOp.ChangeWidget] (SP-B5) mengganti jenis tampilan
 * satu layar tanpa menyentuh entitas/seed (lihat `ChangeWidgetOp`).
 */
object SpecOpApplier {
    const val MAX_OPS_PER_TURN = 5

    fun apply(screen: InteractiveScreen, op: SpecOp): Result<InteractiveScreen> = runCatching {
        when (op) {
            is SpecOp.AddEnumOption -> addEnumOption(screen, op)
            is SpecOp.RenameEnumOption -> renameEnumOption(screen, op)
            is SpecOp.AddTransition -> addTransition(screen, op)
            is SpecOp.AddField -> addField(screen, op)
            is SpecOp.RenameFieldLabel -> renameFieldLabel(screen, op)
            // Kontrak v2 (plan induk §3.5), dilaksanakan sejak B4: kartu & kewajiban field.
            is SpecOp.ShowFieldOnCard -> showFieldOnCard(screen, op)
            is SpecOp.SetFieldRequired -> setFieldRequired(screen, op)
            is SpecOp.SetFieldFormat -> setFieldFormat(screen, op)
            // SP-B5: ganti jenis tampilan; aturan kelayakan di ChangeWidgetOp (tolak bermesej, tak menebak).
            is SpecOp.ChangeWidget -> ChangeWidgetOp.apply(screen, op)
        }
    }

    fun applyAll(screen: InteractiveScreen, ops: List<SpecOp>, at: String): AppliedOps {
        var current = screen
        val log = ops.mapIndexed { i, op ->
            if (i >= MAX_OPS_PER_TURN) {
                CaptureEntry(at, op, ok = false, message = "Dibatasi $MAX_OPS_PER_TURN perubahan per permintaan.")
            } else {
                apply(current, op).fold(
                    onSuccess = { next -> current = next; CaptureEntry(at, op, ok = true, message = null) },
                    onFailure = { e -> CaptureEntry(at, op, ok = false, message = e.message ?: "Operasi ditolak.") }
                )
            }
        }
        return AppliedOps(current, log)
    }

    // ---- operasi ---------------------------------------------------------------------------

    private fun addEnumOption(screen: InteractiveScreen, op: SpecOp.AddEnumOption): InteractiveScreen {
        val e = entity(screen, op.entityId)
        val f = enumField(e, op.field)
        require(op.option.isNotBlank()) { "Nama status baru kosong." }
        require(op.option !in f.options) { "Status '${op.option}' sudah ada di '${f.label}'." }
        require(op.after == null || op.after in f.options) { "Status '${op.after ?: ""}' tidak ada di '${f.label}'." }
        val next = withField(screen, e, f.copy(options = insertOption(f.options, op.option, op.after)))
        // Kolom kanban yang dikelompokkan field ini ikut menampilkan status barunya, di posisi sama.
        return withScreens(next) { sc ->
            sc.kanban?.takeIf { sc.entityId == op.entityId && it.groupField == op.field }
                ?.let { sc.copy(kanban = it.copy(columns = insertOption(it.columns, op.option, op.after))) } ?: sc
        }
    }

    private fun renameEnumOption(screen: InteractiveScreen, op: SpecOp.RenameEnumOption): InteractiveScreen {
        val e = entity(screen, op.entityId)
        val f = enumField(e, op.field)
        require(op.to.isNotBlank()) { "Nama status baru kosong." }
        require(op.from in f.options) { "Status '${op.from}' tidak ada di '${f.label}'." }
        require(op.to !in f.options) { "Status '${op.to}' sudah ada di '${f.label}'." }
        val rename: (String) -> String = { if (it == op.from) op.to else it }
        // Tulis ulang: opsi field, kolom kanban, mesin status, dan isi seed yang memakai nama lama.
        // Semuanya dihitung dulu, lalu spec dibangun **sekali** — spesifikasi antara (opsi sudah
        // diganti, kolom belum) memang tidak sah dan tidak boleh pernah terbentuk.
        val newEntity = e.copy(
            fields = e.fields.map { if (it.key == op.field) f.copy(options = f.options.map(rename)) else it },
            stateMachine = when (e.stateMachine) {
                null -> null
                else -> if (e.stateMachine.field == op.field) {
                    val sm = e.stateMachine
                    StateMachine(sm.field, sm.transitions.mapKeys { (k, _) -> rename(k) }.mapValues { (_, v) -> v.map(rename).toSet() })
                } else {
                    e.stateMachine // mesin status field lain — tidak tersentuh
                }
            }
        )
        val newScreens = screen.spec.screens.map { sc ->
            sc.kanban?.takeIf { sc.entityId == op.entityId && it.groupField == op.field }
                ?.let { sc.copy(kanban = it.copy(columns = it.columns.map(rename))) } ?: sc
        }
        val next = screen.copy(
            spec = PrototypeSpec(screen.spec.entities.map { if (it.id == op.entityId) newEntity else it }, newScreens),
            seed = screen.seed.mapValues { (entityId, rows) ->
                if (entityId != op.entityId) rows
                else rows.map { r -> r.copy(values = r.values.mapValues { (k, v) -> if (k == op.field) rename(v) else v }) }
            }
        )
        return next
    }
    private fun addTransition(screen: InteractiveScreen, op: SpecOp.AddTransition): InteractiveScreen {
        val e = entity(screen, op.entityId)
        val f = enumField(e, op.field)
        val sm = e.stateMachine
        require(sm != null && sm.field == op.field) { "'${f.label}' tidak punya aturan transisi yang bisa ditambah." }
        require(op.from in f.options) { "Status '${op.from}' tidak ada di '${f.label}'." }
        require(op.to in f.options) { "Status '${op.to}' tidak ada di '${f.label}'." }
        require(op.from != op.to) { "Perpindahan ke status yang sama memang selalu boleh." }
        return withStateMachine(screen, op.entityId, op.field) {
            StateMachine(sm.field, sm.transitions + (op.from to (sm.transitions[op.from].orEmpty() + op.to)))
        }
    }

    private fun addField(screen: InteractiveScreen, op: SpecOp.AddField): InteractiveScreen {
        val e = entity(screen, op.entityId)
        require(e.field(op.field.key) == null) { "Field '${op.field.key}' sudah ada di '${e.label}'." }
        // Keputusan B3 (KDoc kelas): field baru hanya masuk entitas; layar tidak diubah otomatis.
        return withEntity(screen, e.copy(fields = e.fields + op.field))
    }

    private fun renameFieldLabel(screen: InteractiveScreen, op: SpecOp.RenameFieldLabel): InteractiveScreen {
        val e = entity(screen, op.entityId)
        require(op.label.isNotBlank()) { "Label baru kosong." }
        val f = requireNotNull(e.field(op.key)) { "Field '${op.key}' tidak ada di '${e.label}'." }
        return withField(screen, e, f.copy(label = op.label))
    }

    /**
     * B4 (kontrak v2): menampilkan [SpecOp.ShowFieldOnCard.field] pada kartu papan kanban entitas
     * tersebut. Field yang sudah tampil **diganti gayanya di posisi semula** (urutan kartu terjaga,
     * tidak ada duplikat); yang belum tampil ditambahkan di akhir. Field kelompok (mis. lencana
     * status) juga boleh. Hanya layar kanban milik entitas yang berubah; tabel/form tak tersentuh.
     */
    private fun showFieldOnCard(screen: InteractiveScreen, op: SpecOp.ShowFieldOnCard): InteractiveScreen {
        val e = entity(screen, op.entityId)
        require(e.field(op.field) != null) { "Field '${op.field}' tidak ada di '${e.label}'." }
        require(screen.spec.screens.any { it.entityId == op.entityId && it.kanban != null }) {
            "'${e.label}' tidak punya papan kanban yang bisa diberi elemen kartu."
        }
        return withScreens(screen) { sc ->
            sc.kanban?.takeIf { sc.entityId == op.entityId }?.let { sc.copy(kanban = it.copy(card = it.card.upsertCardElement(op.field, op.style))) } ?: sc
        }
    }

    /** B4 (kontrak v2): kewajiban isi ditegakkan [PrototypeReducer] pada `Create`. */
    private fun setFieldRequired(screen: InteractiveScreen, op: SpecOp.SetFieldRequired): InteractiveScreen {
        val e = entity(screen, op.entityId)
        val f = requireNotNull(e.field(op.field)) { "Field '${op.field}' tidak ada di '${e.label}'." }
        if (f.required == op.required) return screen // sudah seperti itu — tidak ada yang perlu berubah
        return withField(screen, e, f.copy(required = op.required))
    }

    /** C4: varian tampilan angka; invarian (hanya NUMBER, kode mata uang) ditegakkan [FieldSpec] dan dilaporkan bermesej. */
    private fun setFieldFormat(screen: InteractiveScreen, op: SpecOp.SetFieldFormat): InteractiveScreen {
        val e = entity(screen, op.entityId)
        val f = requireNotNull(e.field(op.field)) { "Field '${op.field}' tidak ada di '${e.label}'." }
        require(f.type == FieldType.NUMBER) { "Field '${f.label}' bertipe ${f.type.name}; format angka hanya untuk field NUMBER." }
        if (f.format == op.format && f.currencyCode == op.currencyCode) return screen
        return withField(screen, e, f.copy(format = op.format, currencyCode = op.currencyCode))
    }

    private fun List<CardElement>.upsertCardElement(field: String, style: CardStyle): List<CardElement> =
        if (any { it.field == field }) map { if (it.field == field) CardElement(field, style) else it }
        else this + CardElement(field, style)

    // ---- bantu rekonstruksi ----------------------------------------------------------------

    private fun entity(screen: InteractiveScreen, id: String): EntitySpec =
        requireNotNull(screen.spec.entity(id)) { "Entitas '$id' tidak ada di layar ini." }

    private fun enumField(e: EntitySpec, key: String): FieldSpec {
        val f = requireNotNull(e.field(key)) { "Field '$key' tidak ada di '${e.label}'." }
        require(f.type == FieldType.ENUM) { "Field '${f.label}' bukan pilihan status (ENUM)." }
        return f
    }

    /** Menyisipkan [option] setelah [after], atau di akhir bila [after] null. */
    private fun insertOption(options: List<String>, option: String, after: String?): List<String> {
        if (after == null) return options + option
        val i = options.indexOf(after)
        return options.subList(0, i + 1) + option + options.subList(i + 1, options.size)
    }

    private fun rebuilt(screen: InteractiveScreen, entities: List<EntitySpec>, screens: List<ScreenSpec>) =
        screen.copy(spec = PrototypeSpec(entities, screens))

    private fun withEntity(screen: InteractiveScreen, updated: EntitySpec): InteractiveScreen =
        rebuilt(screen, screen.spec.entities.map { if (it.id == updated.id) updated else it }, screen.spec.screens)

    private fun withField(screen: InteractiveScreen, e: EntitySpec, updated: FieldSpec): InteractiveScreen =
        withEntity(screen, e.copy(fields = e.fields.map { if (it.key == updated.key) updated else it }))

    private fun withScreens(screen: InteractiveScreen, transform: (ScreenSpec) -> ScreenSpec): InteractiveScreen =
        rebuilt(screen, screen.spec.entities, screen.spec.screens.map(transform))

    /** Menulis ulang mesin status [field] pada entitas [entityId]; no-op bila mesinnya field lain. */
    private fun withStateMachine(screen: InteractiveScreen, entityId: String, field: String, transform: (StateMachine) -> StateMachine): InteractiveScreen {
        val e = entity(screen, entityId)
        val sm = e.stateMachine?.takeIf { it.field == field } ?: return screen
        return withEntity(screen, e.copy(stateMachine = transform(sm)))
    }
}
