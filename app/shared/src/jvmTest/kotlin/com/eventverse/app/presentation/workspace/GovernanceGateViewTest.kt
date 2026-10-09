package com.eventverse.app.presentation.workspace

import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Konten gerbang hanya dipanggil dari SATU cabang (`GateView.Open`). Keputusan belum tiba dan keputusan
 * terbuka harus jatuh ke cabang yang sama, kalau tidak `remember` di dalam konten (ViewModel layar)
 * dibuat ulang saat keputusan tiba (TRD-PLAT-010 §11).
 */
class GovernanceGateViewTest {
    private fun decision(source: AccessSource, level: AccessLevel) = ModuleAccessConfig(level).let {
        AccessDecision(config = it, source = source, fromRole = it, fromDepartment = it)
    }

    @Test
    fun `keputusan belum tiba dan keputusan terbuka sama-sama cabang Open`() {
        assertIs<GateView.Open>(resolveGateView(null))
        assertIs<GateView.Open>(resolveGateView(decision(AccessSource.ROLE, AccessLevel.MANAGE)))
    }

    @Test
    fun `keputusan terbuka membawa konfigurasinya`() {
        val open = resolveGateView(decision(AccessSource.ROLE, AccessLevel.MANAGE)) as GateView.Open
        assertEquals(AccessLevel.MANAGE, open.config.level)
    }

    @Test
    fun `entitlement dan akses tertutup tetap menolak`() {
        assertEquals(GateView.NotEntitled, resolveGateView(decision(AccessSource.NOT_ENTITLED, AccessLevel.NONE)))
        assertEquals(GateView.Denied, resolveGateView(decision(AccessSource.ROLE, AccessLevel.NONE)))
    }
}
