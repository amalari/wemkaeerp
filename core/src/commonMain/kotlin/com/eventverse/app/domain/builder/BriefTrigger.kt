package com.eventverse.app.domain.builder

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CancellationException

/**
 * Pemicu brief setelah patch diterapkan (FR-1; lihat `ApplyDraftPatchUseCase`). Brief adalah pelengkap: galat apa pun di sini **tidak boleh** menggagalkan
 * balasan chat, jadi ditelan dengan sengaja (pembatalan korutin tetap diteruskan). Pengguna bisa memicunya ulang lewat
 * `POST /api/builder/chat/briefs`; pemanggilan ulang idempoten.
 */
internal suspend fun ModuleBriefService?.triggerQuietly(tenantId: TenantId) {
    if (this == null) return
    try {
        generate(tenantId, null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // sengaja diabaikan: lihat KDoc
    }
}
