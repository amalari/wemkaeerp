package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BuilderChatRepository
import com.eventverse.app.domain.builder.BuilderConversation
import com.eventverse.app.domain.builder.BuilderConversationId
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.infrastructure.tables.BuilderChatMessagesTable
import com.eventverse.app.infrastructure.tables.BuilderConversationsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Chat Builder (tabel V82). Satu percakapan per tenant — `conversationFor` idempoten
 * (`UNIQUE (tenant_id)` di SQL, kunci in-memory di sini). Pesan tenant lain tidak pernah keluar:
 * semua baca difilter `tenant_id` (RLS lapis kedua di Postgres).
 */
class PostgresBuilderChatRepository(private val clock: Clock = Clock.System) : BuilderChatRepository {

    override suspend fun conversationFor(tenantId: TenantId): BuilderConversation = DatabaseFactory.dbQuery {
        BuilderConversationsTable.selectAll()
            .where { BuilderConversationsTable.tenantId eq tenantId.value }
            .firstOrNull()?.let { row ->
                BuilderConversation(
                    id = BuilderConversationId(row[BuilderConversationsTable.id]),
                    tenantId = tenantId,
                    createdAt = row[BuilderConversationsTable.createdAt]
                )
            } ?: run {
            val id = BuilderConversationId("conv-${tenantId.value}")
            BuilderConversationsTable.insert {
                it[BuilderConversationsTable.id] = id.value
                it[BuilderConversationsTable.tenantId] = tenantId.value
                it[createdAt] = clock.now()
            }
            BuilderConversation(id, tenantId)
        }
    }

    override suspend fun messages(conversationId: BuilderConversationId): List<ChatMessage> = DatabaseFactory.dbQuery {
        BuilderChatMessagesTable.selectAll()
            .where { BuilderChatMessagesTable.conversationId eq conversationId.value }
            .orderBy(BuilderChatMessagesTable.createdAt, order = SortOrder.ASC)
            .map(::toMessage)
    }

    override suspend fun append(message: ChatMessage): ChatMessage = DatabaseFactory.dbQuery {
        BuilderChatMessagesTable.insert {
            it[id] = message.id.value
            it[conversationId] = message.conversationId.value
            it[tenantId] = message.tenantId.value
            it[role] = message.role.name
            it[text] = message.text
            it[proposedDraft] = message.proposedDraftJson
            it[proposedSummary] = jsonArrayOf(message.proposedSummary.map(::jsonOf)).encode()
            it[appliedDraftId] = message.appliedDraftId
            it[createdAt] = message.createdAt ?: clock.now()
        }
        message
    }

    override suspend fun markApplied(messageId: ChatMessageId, draftId: DiscoveryDraftId): ChatMessage? =
        DatabaseFactory.dbQuery {
            BuilderChatMessagesTable.update({ BuilderChatMessagesTable.id eq messageId.value }) {
                it[appliedDraftId] = draftId.value
            }
            BuilderChatMessagesTable.selectAll()
                .where { BuilderChatMessagesTable.id eq messageId.value }
                .firstOrNull()?.let(::toMessage)
        }

    private fun toMessage(row: ResultRow) = ChatMessage(
        id = ChatMessageId(row[BuilderChatMessagesTable.id]),
        conversationId = BuilderConversationId(row[BuilderChatMessagesTable.conversationId]),
        tenantId = TenantId(row[BuilderChatMessagesTable.tenantId]),
        role = ChatRole.valueOf(row[BuilderChatMessagesTable.role]),
        text = row[BuilderChatMessagesTable.text],
        proposedDraftJson = row[BuilderChatMessagesTable.proposedDraft],
        proposedSummary = summaryOf(row[BuilderChatMessagesTable.proposedSummary]),
        appliedDraftId = row[BuilderChatMessagesTable.appliedDraftId],
        createdAt = row[BuilderChatMessagesTable.createdAt]
    )

    private fun summaryOf(raw: String): List<String> =
        (runCatching { JsonParser.parse(raw) }.getOrNull() as? com.eventverse.app.shared.json.JsonValue.Arr)
            ?.items?.mapNotNull { (it as? com.eventverse.app.shared.json.JsonValue.Str)?.value }
            ?: emptyList()
}
