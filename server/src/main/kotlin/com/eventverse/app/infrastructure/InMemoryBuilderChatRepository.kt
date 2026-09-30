package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BuilderChatRepository
import com.eventverse.app.domain.builder.BuilderConversation
import com.eventverse.app.domain.builder.BuilderConversationId
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Pasangan [BuilderChatRepository] untuk test ktor — satu percakapan per tenant, in-memory penuh. */
class InMemoryBuilderChatRepository(private val clock: Clock = Clock.System) : BuilderChatRepository {

    private val conversations = mutableMapOf<TenantId, BuilderConversation>()
    private val messages = mutableListOf<ChatMessage>()
    private var seq = 0

    override suspend fun conversationFor(tenantId: TenantId): BuilderConversation =
        conversations.getOrPut(tenantId) {
            BuilderConversation(BuilderConversationId("conv-${tenantId.value}"), tenantId)
        }

    override suspend fun messages(conversationId: BuilderConversationId): List<ChatMessage> =
        messages.filter { it.conversationId == conversationId }

    override suspend fun append(message: ChatMessage): ChatMessage {
        seq++
        val stamped = message.copy(
            id = ChatMessageId("${message.id.value}-$seq"),
            createdAt = message.createdAt ?: Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds() + seq)
        )
        messages.add(stamped)
        return stamped
    }

    override suspend fun markApplied(messageId: ChatMessageId, draftId: DiscoveryDraftId): ChatMessage? {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        val updated = messages[index].copy(appliedDraftId = draftId.value)
        messages[index] = updated
        return updated
    }
}
