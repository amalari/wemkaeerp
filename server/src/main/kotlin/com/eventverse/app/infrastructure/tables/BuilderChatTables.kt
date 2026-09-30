package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Chat Builder (V82, PLAN-builder-console M1). `builder.conversations` satu-per-tenant;
 * `builder.chat_messages` menyimpan patch usulan sebagai teks draf penuh (belum diterapkan).
 */
object BuilderConversationsTable : Table("builder.conversations") {
    val id = varchar("id", 80)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object BuilderChatMessagesTable : Table("builder.chat_messages") {
    val id = varchar("id", 120)
    val conversationId = varchar("conversation_id", 80).references(BuilderConversationsTable.id)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val role = varchar("role", 16)
    val text = text("text")
    val proposedDraft = text("proposed_draft").nullable()

    /** Ringkasan usulan agent — daftar string, disimpan teks JSON array (tanpa plugin serialisasi). */
    val proposedSummary = text("proposed_summary")
    val appliedDraftId = varchar("applied_draft_id", 64).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
