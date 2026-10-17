package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.ChatMessageKind
import com.eventverse.app.domain.builder.ChatRole
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pesan BRIEF pada `builder.chat_messages` (V105) terhadap Postgres sungguhan. Hanya jalan bila `DB_NAME` berisi `scratch`
 * (pola `PostgresBuildRequestBriefIntegrationTest`); di luar itu lewat tanpa menguji apa pun.
 */
class PostgresBuilderChatBriefIntegrationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()

    @Test
    fun `BRIEF bulak-balik, pesan lama tetap terbaca persis, constraint menolak BRIEF tanpa modul`() = runBlocking<Unit> {
        if (!System.getenv("DB_NAME").orEmpty().contains("scratch")) return@runBlocking
        DatabaseFactory.init()
        val tenantId = TenantId("ten-chatbrief-$suffix")
        PostgresTenantRepository().save(Tenant(tenantId, TenantSlug("chatbrief-$suffix"), TenantName("Uji Brief Chat"), TenantStatus.ACTIVE, SubscriptionTier.PRO)).getOrThrow()
        val repo = PostgresBuilderChatRepository()
        val conv = repo.conversationFor(tenantId)

        repo.append(ChatMessage(ChatMessageId("lama-$suffix"), conv.id, tenantId, ChatRole.USER, "pesan lama"))
        repo.append(ChatMessage(
            ChatMessageId("brief-$suffix"), conv.id, tenantId, ChatRole.AGENT, "Ringkasan poli",
            moduleId = "poli", kind = ChatMessageKind.BRIEF, features = listOf(BriefFeature("Lampirkan foto")),
            stages = listOf(StageSketch("Intake"), StageSketch("Revisi", optional = true, reworkTo = "Intake")),
            inputFingerprint = "b1:abc", briefSource = BriefSource.AI
        ))

        val all = repo.messages(conv.id).associateBy { it.id.value }
        val old = requireNotNull(all["lama-$suffix"])
        assertEquals(ChatMessageKind.TEXT, old.kind)
        assertEquals(emptyList(), old.features)
        assertEquals(emptyList(), old.stages)
        assertNull(old.inputFingerprint)
        assertNull(old.briefSource)

        val brief = requireNotNull(all["brief-$suffix"])
        assertEquals(ChatMessageKind.BRIEF, brief.kind)
        assertEquals(listOf("Lampirkan foto"), brief.features.map { it.text })
        assertEquals(listOf(StageSketch("Intake"), StageSketch("Revisi", optional = true, reworkTo = "Intake")), brief.stages)
        assertEquals("b1:abc", brief.inputFingerprint)
        assertEquals(BriefSource.AI, brief.briefSource)

        // Constraint di DB (bukan hanya init{} domain): BRIEF tanpa module_id ditolak langsung oleh Postgres.
        val rejected = runCatching {
            DatabaseFactory.dbQuery {
                TransactionManager.current().exec(
                    "INSERT INTO builder.chat_messages (id, conversation_id, tenant_id, role, text, proposed_summary, created_at, kind, brief_source, basis_ref) " +
                        "VALUES ('bad-$suffix', '${conv.id.value}', '${tenantId.value}', 'AGENT', 'x', '[]', now(), 'BRIEF', 'AI', 'b1:x')"
                )
            }
        }
        assertTrue(rejected.isFailure, "BRIEF tanpa module_id harus ditolak constraint")
    }
}
