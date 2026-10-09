package com.eventverse.app.domain.prototype

/**
 * Suntingan parameter field yang **mengubah bentuk nilai sah** (`withTime` DATE, `validation` TEXT) — dipisah dari
 * [SpecOpApplier] karena aturannya sendiri: berbeda dari `SetFieldFormat` (hanya tampilan, baris tak tersentuh), di sini
 * baris contoh (seed) bisa menjadi tak sah.
 *
 * Keputusan: operasi **ditolak** (bermesej, layar tak berubah) bila ada nilai tidak kosong di seed yang tak lolos
 * bentuk baru. Tidak ada konversi (mis. `2026-10-08` → `2026-10-08T00:00`) dan tidak ada nilai yang dibuang diam-diam:
 * mengarang jam atau menghapus data pengguna adalah keputusan yang harus dibuat manusia (ubah/hapus barisnya dulu).
 * Nilai kosong selalu sah. Operasi yang tidak mengubah apa pun mengembalikan layar yang sama.
 */
internal object FieldParamOps {

    fun setWithTime(screen: InteractiveScreen, op: SpecOp.SetFieldWithTime): InteractiveScreen {
        val f = fieldOf(screen, op.entityId, op.field)
        require(f.type == FieldType.DATE) { "Field '${f.label}' bertipe ${f.type.name}; waktu (jam) hanya untuk field DATE." }
        if (f.withTime == op.withTime) return screen
        return replace(screen, op.entityId, f.copy(withTime = op.withTime), "mengubah waktu (jam)")
    }

    fun setValidation(screen: InteractiveScreen, op: SpecOp.SetFieldValidation): InteractiveScreen {
        val f = fieldOf(screen, op.entityId, op.field)
        require(f.type == FieldType.TEXT) { "Field '${f.label}' bertipe ${f.type.name}; validasi teks hanya untuk field TEXT." }
        if (f.validation == op.validation) return screen
        return replace(screen, op.entityId, f.copy(validation = op.validation), "mengubah validasi")
    }

    /**
     * A sisa TRD-FIELD-003: batas pilihan `MULTI_SELECT`. Rentang `1..options.size` (atau `null` = tanpa batas)
     * ditegakkan di sini agar pesannya ramah; `FieldSpec` tetap penjaga terakhir. Menyempitkan batas di atas seed
     * yang sudah ada ditolak lewat [replace] — tidak ada pilihan yang dipotong diam-diam.
     */
    fun setMaxSelections(screen: InteractiveScreen, op: SpecOp.SetFieldMaxSelections): InteractiveScreen {
        val f = fieldOf(screen, op.entityId, op.field)
        require(f.type == FieldType.MULTI_SELECT) {
            "Field '${f.label}' bertipe ${f.type.name}; batas pilihan hanya untuk field MULTI_SELECT."
        }
        val max = op.maxSelections
        require(max == null || max in 1..f.options.size) {
            "Batas pilihan '${f.label}' harus 1..${f.options.size}, dapat $max."
        }
        if (f.maxSelections == max) return screen
        return replace(screen, op.entityId, f.copy(maxSelections = max), "mengubah batas pilihan")
    }

    private fun fieldOf(screen: InteractiveScreen, entityId: String, key: String): FieldSpec {
        val e = requireNotNull(screen.spec.entity(entityId)) { "Entitas '$entityId' tidak ada di layar ini." }
        return requireNotNull(e.field(key)) { "Field '$key' tidak ada di '${e.label}'." }
    }

    private fun replace(screen: InteractiveScreen, entityId: String, updated: FieldSpec, action: String): InteractiveScreen {
        val offending = screen.seed[entityId].orEmpty().filter { row ->
            row.values[updated.key].orEmpty().let { it.isNotEmpty() && !updated.accepts(it) }
        }
        require(offending.isEmpty()) {
            "Tidak bisa $action '${updated.label}': ${offending.size} baris punya nilai yang tak sah di bentuk baru " +
                "(mis. '${offending.first().values[updated.key]}'). Ubah atau hapus nilai itu dulu."
        }
        val entities = screen.spec.entities.map { e ->
            if (e.id != entityId) e else e.copy(fields = e.fields.map { if (it.key == updated.key) updated else it })
        }
        return screen.copy(spec = PrototypeSpec(entities, screen.spec.screens))
    }
}
