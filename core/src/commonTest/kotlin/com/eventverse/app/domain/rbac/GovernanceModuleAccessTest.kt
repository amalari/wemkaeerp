package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Menguji dua hal yang lahir bersamaan ketika Bagan Organisasi, RBAC, dan Alur Pabrik menjadi modul:
 * penyaringan entitlement tenant, dan dua lapis proteksi anti-lockout.
 */
class GovernanceModuleAccessTest {

    private val tenantId = TenantId("ten-demo-001")

    private val ownerRole = CustomRole.createFactoryPresets(tenantId)
        .single { it.id.value.endsWith("owner") }

    private val salesRole = CustomRole.createFactoryPresets(tenantId)
        .single { it.id.value.endsWith("sales") && !it.id.value.contains("head") }

    private fun persona(
        role: CustomRole?,
        isOwner: Boolean = false,
        isSuperAdmin: Boolean = false
    ) = TestingPersona(
        userId = "usr-test",
        name = "Penguji",
        tenantId = tenantId,
        tenantSlug = "wemade-demo",
        departmentId = role?.departmentId,
        departmentName = "Divisi",
        roleId = role?.id,
        roleTitle = role?.name ?: "Tanpa Jabatan",
        isOwnerOrSuperAdmin = isOwner,
        isPlatformSuperAdmin = isSuperAdmin
    )

    // ── Entitlement ──────────────────────────────────────────────────────────────────────────

    @Test
    fun module_notGrantedToTenant_shouldBeReportedAsNotEntitled() {
        val decision = AccessDecisionEngine.explain(
            persona = persona(salesRole),
            module = BusinessModule.FACTORY_FLOW,
            role = salesRole,
            assignments = emptyList(),
            grantedModules = BusinessModule.entries.toSet() - BusinessModule.FACTORY_FLOW
        )

        assertEquals(AccessSource.NOT_ENTITLED, decision.source)
        assertEquals(AccessLevel.NONE, decision.config.level)
        assertTrue(decision.blockedByEntitlement)
    }

    @Test
    fun ownerBypass_shouldNotResurrectAModuleTheTenantDoesNotHave() {
        // Inti aturannya. Modul yang tidak disambungkan ke sebuah pabrik bukan modul yang
        // "Owner-nya berwenang tapi stafnya tidak" — ia tidak ada untuk pabrik itu.
        val decision = AccessDecisionEngine.explain(
            persona = persona(role = null, isOwner = true),
            module = BusinessModule.DYNAMIC_RBAC,
            role = null,
            assignments = emptyList(),
            grantedModules = BusinessModule.entries.toSet() - BusinessModule.DYNAMIC_RBAC
        )

        assertEquals(AccessSource.NOT_ENTITLED, decision.source)
        assertFalse(decision.config.isAccessible)
    }

    @Test
    fun superadminBypass_shouldRetainAccessToModuleEvenWhenNotEntitledToTenant() {
        // Superadmin adalah pengelola SaaS / platform: meskipun modul diputus dari tenant,
        // superadmin harus tetap bisa melihat menu dan mengonfigurasi modul tersebut.
        val decision = AccessDecisionEngine.explain(
            persona = persona(role = null, isSuperAdmin = true),
            module = BusinessModule.DYNAMIC_RBAC,
            role = null,
            assignments = emptyList(),
            grantedModules = BusinessModule.entries.toSet() - BusinessModule.DYNAMIC_RBAC
        )

        assertEquals(AccessSource.SUPERADMIN_BYPASS, decision.source)
        assertEquals(AccessLevel.MANAGE, decision.config.level)
        assertTrue(decision.config.isAccessible)
        assertFalse(decision.blockedByEntitlement)
    }

    @Test
    fun nullGrantedModules_shouldPreserveThePreviousBehaviour() {
        // null berarti "belum diketahui", bukan "tidak ada". Permintaan entitlement yang gagal tidak
        // boleh tampil sebagai langganan yang dicabut.
        val decision = AccessDecisionEngine.explain(
            persona = persona(role = null, isOwner = true),
            module = BusinessModule.DYNAMIC_RBAC,
            role = null,
            assignments = emptyList(),
            grantedModules = null
        )

        assertEquals(AccessSource.OWNER_BYPASS, decision.source)
        assertEquals(AccessLevel.MANAGE, decision.config.level)
    }

    @Test
    fun grantedModule_shouldStillObeyTheRoleMatrix() {
        // Entitlement memberi izin, bukan wewenang. Modul yang disambungkan tetap tertutup bagi
        // jabatan yang tidak diberi akses.
        val decision = AccessDecisionEngine.explain(
            persona = persona(salesRole),
            module = BusinessModule.DYNAMIC_RBAC,
            role = salesRole,
            assignments = emptyList(),
            grantedModules = BusinessModule.entries.toSet()
        )

        assertEquals(AccessSource.NONE, decision.source)
        assertFalse(decision.config.isAccessible)
    }

    // ── Anti-lockout ─────────────────────────────────────────────────────────────────────────

    @Test
    fun ownerRole_shouldRefuseToLowerItsOwnRbacAccess() {
        val downgraded = ownerRole.updateModuleAccess(BusinessModule.DYNAMIC_RBAC, AccessLevel.NONE)

        assertEquals(
            AccessLevel.MANAGE,
            downgraded.getAccess(BusinessModule.DYNAMIC_RBAC).level,
            "Jabatan Owner tidak boleh kehilangan satu-satunya layar untuk memperbaiki matriks"
        )
    }

    @Test
    fun ownerRole_writtenWholeMatrixWithoutRbacKey_shouldStillKeepManage() {
        // Jalur API mengganti seluruh matriks sekaligus; matriks yang tidak menyebut DYNAMIC_RBAC
        // sama artinya dengan menyetelnya ke NONE bagi Owner.
        val rewritten = ownerRole.withModulePermissions(
            mapOf(BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.VIEW))
        )

        assertEquals(AccessLevel.MANAGE, rewritten.getAccess(BusinessModule.DYNAMIC_RBAC).level)
    }

    @Test
    fun nonOwnerRole_shouldBeFreelyAdjustableOnRbac() {
        val raised = salesRole.updateModuleAccess(BusinessModule.DYNAMIC_RBAC, AccessLevel.MANAGE)
        val lowered = raised.updateModuleAccess(BusinessModule.DYNAMIC_RBAC, AccessLevel.NONE)

        assertEquals(AccessLevel.MANAGE, raised.getAccess(BusinessModule.DYNAMIC_RBAC).level)
        assertEquals(AccessLevel.NONE, lowered.getAccess(BusinessModule.DYNAMIC_RBAC).level)
    }

    @Test
    fun ownerRole_shouldRemainAdjustableOnOtherGovernanceModules() {
        // Hanya modul RBAC yang dikunci. Mengunci lebih dari itu akan menjadi pembatasan yang tidak
        // dibutuhkan oleh alasan anti-lockout mana pun.
        val lowered = ownerRole.updateModuleAccess(BusinessModule.FACTORY_FLOW, AccessLevel.VIEW)

        assertEquals(AccessLevel.VIEW, lowered.getAccess(BusinessModule.FACTORY_FLOW).level)
    }

    // ── Bentuk modul tata kelola ─────────────────────────────────────────────────────────────

    @Test
    fun governanceModules_shouldFillNoCapabilitySlot() {
        BusinessModule.governance.forEach { module ->
            assertEquals(
                null,
                GarmentSlots.forModule(module),
                "${module.code} tidak berdiri di lini produksi dan tidak boleh mengisi slot kapabilitas"
            )
        }
    }

    @Test
    fun operationalModules_shouldAllStillFillACapabilitySlot() {
        BusinessModule.operational.forEach { module ->
            assertNotNull(GarmentSlots.forModule(module))
        }
    }

    @Test
    fun orgChart_shouldBeGlobalOnly() {
        assertTrue(BusinessModule.ORG_CHART.isGlobalOnly)
        assertEquals(setOf(DataScope.ALL_TENANT_DATA), BusinessModule.ORG_CHART.supportedScopes)
    }

    @Test
    fun rbacAndFactoryFlow_shouldBeGlobalOnly() {
        assertTrue(BusinessModule.DYNAMIC_RBAC.isGlobalOnly)
        assertTrue(BusinessModule.FACTORY_FLOW.isGlobalOnly)
    }
}
