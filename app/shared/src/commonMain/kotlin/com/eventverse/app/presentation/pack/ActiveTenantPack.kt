package com.eventverse.app.presentation.pack

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pack tenant yang sedang login di klien ini (B7, TRD-PLAT-001-tenant-pack FR-3).
 *
 * Satu sesi klien = satu tenant, jadi satu nilai di sini **memang** benar — beda dengan server, yang selalu memakai
 * `TenantContext.pack` per request. Diisi dari `GET /api/tenant/pack` setelah login ([activate]). Sebelum respons tiba
 * nilainya pack bawaan (garment), karena setiap tenant yang ada sebelum B7 adalah garment; menu tetap disaring
 * keputusan `/me/access`, jadi modul asing tidak pernah terbuka karena jeda ini.
 */
object ActiveTenantPack {

    private val state = MutableStateFlow(GarmentDomainPack.pack)

    val flow: StateFlow<DomainPack> = state.asStateFlow()

    val current: DomainPack get() = state.value

    /** Pack data didaftarkan ke registry dulu supaya definisi modulnya bisa dicari (`ModuleId.displayName`). */
    fun activate(pack: DomainPack) {
        if (!DomainPackRegistry.isShipped(pack.code)) DomainPackRegistry.register(pack)
        state.value = pack
    }

    /** Logout: kembali ke pack bawaan. */
    fun reset() {
        state.value = GarmentDomainPack.pack
    }
}
