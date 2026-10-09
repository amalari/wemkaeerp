package com.eventverse.app.infrastructure

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.UnresolvableBlueprintException
import com.eventverse.app.domain.pack.resolveBlueprint

/**
 * Memuat pack tenant lalu me-resolve kode starter-nya (TRD-PLAT-008 K4). Memakai [DomainPackRegistry] hanya sebagai
 * cache cepat; registry diisi malas, jadi pada proses baru pack data dibaca dari `domain_packs` — **tanpa**
 * mendaftarkannya ke registry (membaca tenant tidak boleh punya efek samping global). Versi yang di-pin tenant
 * dihormati; versi tak dikenal = pack `null` (hanya starter platform yang ter-resolve), tidak jatuh ke versi lain.
 *
 * Satu instance per operasi: hasil pemuatan di-cache per (pack, versi) supaya `findAll()` tidak N+1.
 */
class TenantBlueprintResolver(private val packs: DomainPackRepository) {

    private val loaded = HashMap<Pair<DomainPackCode, Int?>, DomainPack?>()

    private suspend fun packOf(code: DomainPackCode, pinnedVersion: Int?): DomainPack? =
        loaded.getOrPut(code to pinnedVersion) {
            when {
                DomainPackRegistry.isShipped(code) -> DomainPackRegistry.find(code)
                pinnedVersion != null -> packs.findVersion(code, pinnedVersion)?.pack
                else -> DomainPackRegistry.find(code) ?: packs.findEffective(code)?.pack
            }
        }

    /** `null` = kode tak ada di pack tenant maupun starter platform; pemanggil menolak (Kontrak 4). */
    suspend fun find(packCode: DomainPackCode, pinnedVersion: Int?, code: BlueprintCode): Blueprint? =
        resolveBlueprint(packOf(packCode, pinnedVersion), code)

    suspend fun require(tenantSlug: String, packCode: DomainPackCode, pinnedVersion: Int?, rawCode: String): Blueprint {
        val code = runCatching { BlueprintCode(rawCode) }.getOrNull()
            ?: throw UnresolvableBlueprintException(tenantSlug, packCode, rawCode)
        return find(packCode, pinnedVersion, code) ?: throw UnresolvableBlueprintException(tenantSlug, packCode, rawCode)
    }
}
