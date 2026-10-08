package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.FieldType

/**
 * Keluaran kode persistensi dari spec: objek tabel Exposed (cermin migrasi) dan repository Postgres
 * yang mengimplementasikan [PrototypeRowRepository]. Kode yang dihasilkan sengaja **lurus dan
 * eksplisit** (satu baris per kolom) supaya tim bisa membaca dan mengubahnya — bukan runtime generik.
 * Kode hasil generate tidak memakai interpolasi string agar tidak ada `$` yang perlu di-escape.
 */
internal object SpecPostgresWriter {

    fun tableFile(t: SpecTable): String = buildString {
        appendLine("package com.eventverse.app.infrastructure.tables")
        appendLine()
        appendLine("import org.jetbrains.exposed.sql.Table")
        appendLine("import org.jetbrains.exposed.sql.kotlin.datetime.date")
        appendLine("import org.jetbrains.exposed.sql.kotlin.datetime.timestamp")
        appendLine()
        appendLine("// KANDIDAT PR (hasil generator) — cermin migrasi ${t.qualified}. Setelah diterapkan milik tim.")
        appendLine("object ${t.objectName} : Table(\"${t.qualified}\") {")
        appendLine("    val id = varchar(\"id\", 64)")
        appendLine("    val tenantId = varchar(\"tenant_id\", 64).references(TenantsTable.id)")
        t.columns.forEach { c -> appendLine("    val ${c.prop} = ${exposedColumn(c)}") }
        appendLine("    val createdAt = timestamp(\"created_at\")")
        appendLine("    val updatedAt = timestamp(\"updated_at\")")
        appendLine()
        appendLine("    override val primaryKey = PrimaryKey(id)")
        appendLine("}")
    }

    fun repositoryFile(t: SpecTable): String {
        val cls = "Postgres" + SpecNaming.pascal(t.schema) + "Repository"
        val objectName = t.objectName
        val tbl = "T" // alias lokal supaya baris query tetap terbaca
        return buildString {
            appendLine("package com.eventverse.app.infrastructure")
            appendLine()
            appendLine("import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository")
            appendLine("import com.eventverse.app.domain.prototype.PrototypeRow")
            appendLine("import com.eventverse.app.domain.tenant.TenantId")
            appendLine("import com.eventverse.app.infrastructure.tables.$objectName")
            appendLine("import kotlinx.datetime.Clock")
            appendLine("import kotlinx.datetime.LocalDate")
            appendLine("import org.jetbrains.exposed.sql.ResultRow")
            appendLine("import org.jetbrains.exposed.sql.SortOrder")
            appendLine("import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq")
            appendLine("import org.jetbrains.exposed.sql.and")
            appendLine("import org.jetbrains.exposed.sql.deleteWhere")
            appendLine("import org.jetbrains.exposed.sql.insert")
            appendLine("import org.jetbrains.exposed.sql.selectAll")
            appendLine("import org.jetbrains.exposed.sql.update")
            appendLine()
            appendLine("/** KANDIDAT PR (hasil generator) — ${t.qualified}. Setiap query lewat `dbQuery(tenantId)` (RLS aktif). */")
            appendLine("class $cls : PrototypeRowRepository {")
            appendLine()
            appendLine("    private val T = $objectName")
            appendLine()
            appendLine("    override suspend fun list(tenantId: TenantId): List<PrototypeRow> = DatabaseFactory.dbQuery(tenantId) {")
            appendLine("        $tbl.selectAll().where { $tbl.tenantId eq tenantId.value }.orderBy($tbl.createdAt to SortOrder.ASC, $tbl.id to SortOrder.ASC).map(::hydrate)")
            appendLine("    }")
            appendLine()
            appendLine("    override suspend fun find(tenantId: TenantId, id: String): PrototypeRow? = DatabaseFactory.dbQuery(tenantId) {")
            appendLine("        $tbl.selectAll().where { ($tbl.tenantId eq tenantId.value) and ($tbl.id eq id) }.singleOrNull()?.let(::hydrate)")
            appendLine("    }")
            appendLine()
            appendLine("    override suspend fun save(tenantId: TenantId, row: PrototypeRow) {")
            appendLine("        DatabaseFactory.dbQuery(tenantId) {")
            appendLine("            val exists = $tbl.selectAll().where { ($tbl.tenantId eq tenantId.value) and ($tbl.id eq row.id) }.any()")
            appendLine("            val now = Clock.System.now()")
            appendLine("            if (exists) {")
            appendLine("                $tbl.update({ ($tbl.tenantId eq tenantId.value) and ($tbl.id eq row.id) }) {")
            t.columns.forEach { c -> appendLine("                    it[$tbl.${c.prop}] = ${writeExpr(c)}") }
            appendLine("                    it[$tbl.updatedAt] = now")
            appendLine("                }")
            appendLine("            } else {")
            appendLine("                $tbl.insert {")
            appendLine("                    it[$tbl.id] = row.id")
            appendLine("                    it[$tbl.tenantId] = tenantId.value")
            t.columns.forEach { c -> appendLine("                    it[$tbl.${c.prop}] = ${writeExpr(c)}") }
            appendLine("                    it[$tbl.createdAt] = now")
            appendLine("                    it[$tbl.updatedAt] = now")
            appendLine("                }")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    override suspend fun delete(tenantId: TenantId, id: String): Boolean = DatabaseFactory.dbQuery(tenantId) {")
            appendLine("        $tbl.deleteWhere { ($tbl.tenantId eq tenantId.value) and ($tbl.id eq id) } > 0")
            appendLine("    }")
            appendLine()
            appendLine("    private fun hydrate(r: ResultRow): PrototypeRow = PrototypeRow(")
            appendLine("        r[$tbl.id],")
            appendLine("        mapOf(")
            t.columns.forEachIndexed { i, c ->
                appendLine("            ${SpecNaming.kString(c.field.key)} to ${readExpr(c, tbl)}" + if (i < t.columns.lastIndex) "," else "")
            }
            appendLine("        )")
            appendLine("    )")
            appendLine("}")
        }
    }

    private fun exposedColumn(c: SpecColumn): String {
        val n = SpecNaming.kString(c.name)
        val base = when (c.field.type) {
            FieldType.TEXT, FieldType.LONG_TEXT -> "text($n)"
            FieldType.NUMBER -> "decimal($n, 18, 4)"
            FieldType.DATE -> "date($n)"
            FieldType.ENUM -> "varchar($n, 120)"
            FieldType.BOOL -> "bool($n).default(false)"
        }
        return if (c.field.required || c.field.type == FieldType.BOOL) base else "$base.nullable()"
    }

    /** Nilai Kotlin untuk menulis kolom dari `row` (string). Kosong → null pada kolom opsional. */
    private fun writeExpr(c: SpecColumn): String {
        val raw = "row[${SpecNaming.kString(c.field.key)}]"
        val optional = !c.field.required
        return when (c.field.type) {
            FieldType.TEXT, FieldType.LONG_TEXT, FieldType.ENUM -> if (optional) "$raw.ifBlank { null }" else raw
            FieldType.NUMBER -> if (optional) "$raw.takeIf { it.isNotBlank() }?.toBigDecimal()" else "$raw.toBigDecimal()"
            FieldType.DATE -> if (optional) "$raw.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }" else "LocalDate.parse($raw)"
            FieldType.BOOL -> "$raw == \"ya\""
        }
    }

    private fun readExpr(c: SpecColumn, tbl: String): String {
        val cell = "r[$tbl.${c.prop}]"
        return when (c.field.type) {
            FieldType.TEXT, FieldType.LONG_TEXT, FieldType.ENUM -> if (c.field.required) cell else "($cell ?: \"\")"
            FieldType.NUMBER -> if (c.field.required) "$cell.stripTrailingZeros().toPlainString()" else "($cell?.stripTrailingZeros()?.toPlainString() ?: \"\")"
            FieldType.DATE -> if (c.field.required) "$cell.toString()" else "($cell?.toString() ?: \"\")"
            FieldType.BOOL -> "(if ($cell) \"ya\" else \"tidak\")"
        }
    }
}
