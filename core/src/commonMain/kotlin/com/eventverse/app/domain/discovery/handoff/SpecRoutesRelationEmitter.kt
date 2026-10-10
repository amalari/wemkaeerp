package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.FieldType

/**
 * Emisi validator RELATION untuk rute hasil generate (TRD-FIELD-004 FR-2.1/2.2). Satu tanggung jawab: potongan
 * teks yang disisipkan [SpecRoutesWriter]. Seluruhnya kosong bila entitas tak punya field RELATION, sehingga
 * keluaran spec tanpa RELATION identik byte per byte dengan sebelum B2. Logika keberadaan & normalisasi target
 * **tidak** ditulis ulang di kode yang digenerate: ia memanggil `EntitySpec.relationTargetProblem` (core).
 */
internal class SpecRoutesRelationEmitter(table: SpecTable) {
    val active: Boolean = table.entity.fields.any { it.type == FieldType.RELATION }

    /** Impor tambahan; kosong bila tak aktif. */
    val imports: List<String> = if (!active) emptyList() else listOf(
        "com.eventverse.app.domain.customfield.RelationTargetResolver",
        "com.eventverse.app.domain.prototype.relationTargetProblem",
        "com.eventverse.app.domain.tenant.TenantId"
    )

    /** Parameter fungsi rute: non-null, tanpa default (kompilator memaksa wiring menyuplai resolver). */
    val routeParam: String = if (!active) "" else ",\n    relationResolver: RelationTargetResolver"

    /** Potongan rantai `?:` sebelum reducer; kosong bila tak aktif. */
    val chain: String = if (!active) "" else " ?: relationProblem(tenant.tenantId, values, relationResolver)"

    fun helper(): List<String> = if (!active) emptyList() else listOf(
        "",
        "/** Field RELATION: target harus ada pada tenant penulis (resolver; target tanpa ':' dinormalkan ke modul ini). Tanpa oracle lintas tenant. */",
        "private suspend fun relationProblem(tenantId: TenantId, values: Map<String, String>, resolver: RelationTargetResolver): String? =",
        "    SPEC.entities.single().relationTargetProblem(MODULE.value, tenantId, values, resolver)"
    )
}
