package com.eventverse.app.domain.builder

import com.eventverse.app.domain.i18n.LanguageCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kontrak Track 0 TRD-PLAT-014: menu v2, peta route lama→baru, dan aturan badge dibekukan di sini.
 * Mengubah salah satu keputusan tanpa mengubah test ini berarti mencairkan kontrak (revisi TRD).
 */
class BuilderConsoleContractTest {

    // ---------------------------------------------------------------- menu v2

    @Test
    fun `menu has exactly five entries with unique keys`() {
        assertEquals(5, BuilderConsoleMenu.entries.size)
        assertEquals(5, BuilderConsoleMenu.entries.map { it.key.value }.toSet().size)
    }

    @Test
    fun `every menu label carries id and en`() {
        BuilderConsoleMenu.entries.forEach { entry ->
            assertTrue(entry.labelText.has(LanguageCode.ID), "${entry.key.value}: label id hilang")
            assertTrue(entry.labelText.has(LanguageCode.EN), "${entry.key.value}: label en hilang (FR-1-3)")
            assertTrue(entry.labelText.idText.isNotBlank(), "${entry.key.value}: label id kosong")
        }
    }

    @Test
    fun `unknown menu key should fail loudly not fall back`() {
        assertFailsWith<IllegalStateException> { BuilderConsoleMenu.label(BuilderMenuKey("overview")) }
    }

    // ---------------------------------------------------------------- peta route lama → baru

    @Test
    fun `legacy route map covers exactly the ten old menu keys`() {
        assertEquals(
            setOf(
                "overview", "newpack", "chat", "modules", "dataflow",
                "prototype", "buildqueue", "deployments", "billing", "settings",
            ),
            BuilderRouteMap.LEGACY.keys,
        )
    }

    @Test
    fun `every legacy target is one of the frozen destinations`() {
        // Enumerasi tujuan dibekukan utuh — penambahan tujuan baru wajib lewat revisi kontrak.
        val frozen = setOf(
            "chat", "live", "handoff", "audit", "setting",
            "live/preview", "live/production", "setting/billing", "newpack",
        )
        BuilderRouteMap.LEGACY.forEach { (old, new) ->
            assertTrue(new in frozen, "Route lama '$old' menunjuk tujuan tak dibekukan: '$new'")
        }
    }

    @Test
    fun `unknown legacy key resolves to null`() {
        assertNull(BuilderRouteMap.resolve("bukan-menu-lama"))
    }

    // ---------------------------------------------------------------- badge

    @Test
    fun `handoff badge counts pending statuses only`() {
        val statuses = listOf(
            BuildRequestStatus.QUEUED,
            BuildRequestStatus.QUOTED,
            BuildRequestStatus.APPROVED,
            BuildRequestStatus.IN_PROGRESS,
            BuildRequestStatus.SHIPPED,
            BuildRequestStatus.REJECTED,
            BuildRequestStatus.SUPERSEDED,
        )
        assertEquals(4, BuilderConsoleBadges.handoffActiveCount(statuses))
        assertEquals(0, BuilderConsoleBadges.handoffActiveCount(emptyList()))
    }

    @Test
    fun `live signal maps every deployment status without else branch`() {
        assertEquals(BuilderLiveSignal.NOT_DEPLOYED, BuilderConsoleBadges.liveSignal(null))
        assertEquals(BuilderLiveSignal.LIVE, BuilderConsoleBadges.liveSignal(DeploymentStatus.ACTIVE))
        assertEquals(BuilderLiveSignal.LIVE, BuilderConsoleBadges.liveSignal(DeploymentStatus.IMPORTED))
        assertEquals(BuilderLiveSignal.VALIDATING, BuilderConsoleBadges.liveSignal(DeploymentStatus.VALIDATING))
        assertEquals(BuilderLiveSignal.BLOCKED_ON_BUILD, BuilderConsoleBadges.liveSignal(DeploymentStatus.BLOCKED_ON_BUILD))
        assertEquals(BuilderLiveSignal.FAILED, BuilderConsoleBadges.liveSignal(DeploymentStatus.FAILED))
        // Bukan status aktif — repository findActive tidak pernah mengembalikannya.
        assertEquals(BuilderLiveSignal.NOT_DEPLOYED, BuilderConsoleBadges.liveSignal(DeploymentStatus.SUPERSEDED))
        assertEquals(BuilderLiveSignal.NOT_DEPLOYED, BuilderConsoleBadges.liveSignal(DeploymentStatus.ROLLED_BACK))
    }
}
