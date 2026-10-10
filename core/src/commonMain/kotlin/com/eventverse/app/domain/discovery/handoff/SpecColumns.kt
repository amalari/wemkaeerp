package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType

/** Satu kolom tabel hasil pemetaan [FieldSpec]. [name] = nama SQL, [prop] = nama properti Kotlin. */
internal data class SpecColumn(val field: FieldSpec, val name: String, val prop: String)

/** Rencana tabel satu entitas: nama schema/tabel/objek dan kolom-kolomnya (tervalidasi, tanpa tabrakan). */
internal data class SpecTable(val schema: String, val table: String, val entity: EntitySpec, val columns: List<SpecColumn>) {
    val qualified get() = "$schema.$table"
    val objectName get() = SpecNaming.pascal(schema) + SpecNaming.pascal(table) + "Table"

    companion object {
        /** Gagal keras (fail-closed) bila ada nama tak aman, kata cadangan, atau tabrakan antar kolom. */
        fun of(schema: String, entity: EntitySpec): SpecTable {
            val table = SpecNaming.ident(entity.id, "Entitas").let { if (it.endsWith("s")) it else it + "s" }
            val seen = HashSet<String>()
            val columns = entity.fields.map { f ->
                val name = SpecNaming.ident(f.key, "Field")
                require(name !in SpecNaming.systemColumns) { "Field '${f.key}' bertabrakan dengan kolom bawaan '$name'." }
                require(seen.add(name)) { "Field '${f.key}' menghasilkan nama kolom ganda '$name'." }
                SpecColumn(f, name, SpecNaming.camel(name))
            }
            return SpecTable(schema, table, entity, columns)
        }
    }
}

/** Tipe SQL kolom + batasannya (pola DDL repo ini: NOT NULL untuk wajib, CHECK untuk enum). */
internal fun SpecColumn.sqlDefinition(): String {
    val notNull = if (field.required) " NOT NULL" else ""
    return when (field.type) {
        // C8: FILE menyimpan **ref** (key `fields/...` tervalidasi FileRef) — kolom tetap TEXT, byte di ObjectStorage.
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.FILE -> "TEXT$notNull" + if (field.required) " CHECK (btrim($name) <> '')" else ""
        FieldType.NUMBER -> "NUMERIC(18,4)$notNull"
        // withTime (A0(C6)): waktu dinding tanpa zona = TIMESTAMP (bukan TIMESTAMPTZ), tepat menit di tingkat nilai.
        FieldType.DATE -> (if (field.withTime) "TIMESTAMP" else "DATE") + notNull
        // C6: jam dinding `JJ:MM` tanpa tanggal/zona = kolom TIME bawaan Postgres (pola kolom DATE).
        FieldType.TIME -> "TIME$notNull"
        FieldType.ENUM -> "VARCHAR(120)$notNull CHECK ($name IN (${field.options.joinToString(", ") { SpecNaming.sqlString(it) }}))"
        // A0 (TRD-FIELD-003 R1): kolom larik Postgres; opsi tetap dijaga DB lewat `<@` (subset dari opsi).
        // required menambah cardinality > 0 ("[]" tersimpan tidak sah), maxSelections menambah batas atas.
        FieldType.MULTI_SELECT -> buildString {
            append("TEXT[]").append(notNull)
            append(" CHECK (").append(name).append(" <@ ARRAY[")
            append(field.options.joinToString(", ") { SpecNaming.sqlString(it) })
            append("]::text[])")
            if (field.required) append(" CHECK (cardinality($name) > 0)")
            field.maxSelections?.let { append(" CHECK (cardinality($name) <= $it)") }
        }
        FieldType.BOOL -> "BOOLEAN NOT NULL DEFAULT FALSE"
        // C7 (TRD-FIELD-001 FR-1): rujukan LOGIS — id baris target saja, TANPA `REFERENCES` lintas schema
        // (pagar J3 TRD-PLAT-004; promosi modul = salin + prefiks baru mematahkan FK fisik). Keberadaan
        // target divalidasi saat tulis nilai, bukan oleh DB.
        FieldType.RELATION -> "VARCHAR(64)$notNull"
        // A0 (penyatuan kosakata): id pengguna platform (`users.id`) — VARCHAR(120) TANPA `REFERENCES`
        // (pola RELATION: rujukan logis, pagar J3); keberadaan user divalidasi saat tulis nilai.
        // [FieldSpec.decimals] NUMBER sengaja TIDAK mengubah kolom: NUMERIC(18,4) tetap — decimals adalah
        // petunjuk pembulatan masukan/tampilan, bukan skala kolom.
        FieldType.USER_REF -> "VARCHAR(120)$notNull"
    }
}
