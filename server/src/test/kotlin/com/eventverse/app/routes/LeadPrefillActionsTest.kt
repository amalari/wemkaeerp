package com.eventverse.app.routes

import com.eventverse.app.domain.help.HelpAction
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.InMemoryCrmAiSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TRD-HELP-002 5b: aksi "isi form lead" hanya ditawarkan dengan gerbang yang sama dengan `POST /crm/leads/draft`. */
class LeadPrefillActionsTest {

    private val tenantId = TenantId("ten-5b")
    private val command = "catat lead PT Maju, kontak Budi 0812 3456 7890"

    private fun decisions(level: AccessLevel): Map<ModuleId, AccessDecision> =
        ModuleAccessConfig(level = level).let { mapOf(GarmentModules.CRM_SALES to AccessDecision(it, AccessSource.ROLE, it, ModuleAccessConfig())) }

    private fun tenant() = TenantContext(tenantId, TenantSlug("t-5b"), SubscriptionTier.PRO, isAccessible = true)

    @Test
    fun offeredOnlyWithOperateAndOptIn() = runBlocking {
        val settings = InMemoryCrmAiSettingsRepository()
        val actions = leadPrefillActions(settings)
        val t = tenant()

        assertNull(actions(t, decisions(AccessLevel.OPERATE)), "tenant belum opt-in")
        settings.setLeadDraftEnabled(tenantId, true, null)
        assertNull(actions(t, decisions(AccessLevel.VIEW)), "VIEW tidak boleh membuat lead")
        assertNull(actions(t, emptyMap()), "tanpa keputusan = tertutup")

        val resolver = requireNotNull(actions(t, decisions(AccessLevel.OPERATE)))
        assertEquals(HelpAction.PrefillLead(GarmentModules.CRM_SALES, command), resolver.resolve(command))
        assertNull(resolver.resolve("gimana cara bikin lead baru?"), "pertanyaan tetap dijawab tutorial")
    }
}
