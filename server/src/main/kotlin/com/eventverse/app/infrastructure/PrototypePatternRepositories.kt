package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.PrototypePattern
import com.eventverse.app.domain.discovery.PrototypePatternRepository
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.infrastructure.tables.PrototypePatternsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Pola layout Studio (tabel V79). Baris dengan widget tak dikenal **melempar** (Kontrak 4):
 * kosakata widget tertutup, jadi baris asing berarti kerusakan data, bukan variasi yang ditoleransi.
 */
class PostgresPrototypePatternRepository : PrototypePatternRepository {

    override suspend fun findById(id: String): PrototypePattern? = DatabaseFactory.dbQuery {
        PrototypePatternsTable.selectAll().where { PrototypePatternsTable.id eq id }.firstOrNull()?.let(::toPattern)
    }

    override suspend fun findByName(name: String): PrototypePattern? = DatabaseFactory.dbQuery {
        PrototypePatternsTable.selectAll().where { PrototypePatternsTable.name eq name }.firstOrNull()?.let(::toPattern)
    }

    override suspend fun findAll(): List<PrototypePattern> = DatabaseFactory.dbQuery {
        PrototypePatternsTable.selectAll()
            .orderBy(PrototypePatternsTable.createdAt, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .map(::toPattern)
    }

    override suspend fun save(pattern: PrototypePattern) {
        val now = kotlinx.datetime.Clock.System.now()
        DatabaseFactory.dbQuery {
            val updated = PrototypePatternsTable.update({ PrototypePatternsTable.id eq pattern.id }) {
                it[name] = pattern.name
                it[widget] = pattern.widget.code
                it[packCode] = pattern.packCode
                it[patternJson] = pattern.patternJson
                it[updatedAt] = now
            }
            if (updated == 0) {
                PrototypePatternsTable.insert {
                    it[id] = pattern.id
                    it[name] = pattern.name
                    it[widget] = pattern.widget.code
                    it[packCode] = pattern.packCode
                    it[patternJson] = pattern.patternJson
                    it[createdByUserId] = pattern.createdByUserId.value
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    private fun toPattern(row: ResultRow): PrototypePattern {
        val widgetCode = row[PrototypePatternsTable.widget]
        val widget = WidgetKind.fromCode(widgetCode)
            ?: throw IllegalStateException("Widget '$widgetCode' pada pola ${row[PrototypePatternsTable.id]} tidak dikenal")
        return PrototypePattern(
            id = row[PrototypePatternsTable.id],
            name = row[PrototypePatternsTable.name],
            widget = widget,
            packCode = row[PrototypePatternsTable.packCode],
            patternJson = row[PrototypePatternsTable.patternJson],
            createdByUserId = UserId(row[PrototypePatternsTable.createdByUserId])
        )
    }
}

/** Penyimpan in-memory untuk test & tooling tanpa database. */
class InMemoryPrototypePatternRepository : PrototypePatternRepository {
    private val rows = linkedMapOf<String, PrototypePattern>()

    override suspend fun findById(id: String): PrototypePattern? = rows[id]
    override suspend fun findByName(name: String): PrototypePattern? = rows.values.firstOrNull { it.name == name }
    override suspend fun findAll(): List<PrototypePattern> = rows.values.toList()
    override suspend fun save(pattern: PrototypePattern) { rows[pattern.id] = pattern }

    fun clear() = rows.clear()
}
