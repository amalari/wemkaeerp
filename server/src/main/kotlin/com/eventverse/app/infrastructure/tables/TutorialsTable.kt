package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Tutorial storage for RAG (V105). Platform-level table, no RLS. */
object TutorialsTable : Table("tutorials") {
    val id = varchar("id", 64)
    val packCode = varchar("pack_code", 40)
    val scopeType = varchar("scope_type", 20)
    val scopeCode = varchar("scope_code", 40).nullable()
    val title = varchar("title", 200)
    val summary = varchar("summary", 4000)
    val requiredLevel = varchar("required_level", 20)
    val keywords = varchar("keywords", 4000) // stored as comma-separated string
    val steps = varchar("steps", 16000) // serialized as "title:::body:::anchor:::screen:::placement|||..."
    val sampleQuestions = varchar("sample_questions", 4000) // stored as comma-separated string
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
