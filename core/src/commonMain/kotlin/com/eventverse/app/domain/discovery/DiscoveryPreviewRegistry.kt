package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/** Satu sesi pratinjau yang sedang berjalan untuk satu pack draf prospek. */
data class ActiveDiscoveryPreview(
    val packCode: DomainPackCode,
    val sandboxTenantId: TenantId,
    val startedAt: Instant,
    val expiresAt: Instant
)

/**
 * Ledger sesi pratinjau (plan §2 A7, T13): pack **draf** prospek didaftarkan sementara supaya menu & `/m`
 * bisa dilihat tanpa satu baris kode — tanpa menyentuh registry pack bawaan/LOCKED platform.
 *
 * Mekanismenya memakai registry data-pack yang sama dengan B7 (`DomainPackRegistry.register` menulis peta
 * `loaded`, bukan daftar `shipped`), dengan dua pagu dari risiko plan §7:
 *
 * 1. **Terkait waktu** — entri kedaluwarsa dibersihkan (`purgeExpired`) setiap kali ledger disentuh; pack
 *    yang dilepas membuat tenant sandbox ditolak fail-closed (409), tidak jatuh ke garment;
 * 2. **Satu sesi per kode pack** — dua prospek dengan draf kode sama tidak boleh saling menimpa.
 *
 * Object + peta dalam memori disengaja: pratinjau adalah cache sesi, bukan data — restart server
 * mengakhiri semua sesi, dan itu perilaku yang benar.
 */
object DiscoveryPreviewRegistry {

    const val DEFAULT_TTL_MINUTES = 120L

    private val active = LinkedHashMap<DomainPackCode, ActiveDiscoveryPreview>()

    /** Kode pack yang sedang dipratinjau — untuk verifikasi & housekeeping. */
    val activePackCodes: Set<DomainPackCode> get() = active.keys.toSet()

    fun start(
        pack: DomainPack,
        sandboxTenantId: TenantId,
        now: Instant,
        ttlMinutes: Long = DEFAULT_TTL_MINUTES
    ): ActiveDiscoveryPreview {
        require(ttlMinutes in 1..480) { "TTL pratinjau harus 1..480 menit, bukan $ttlMinutes" }
        purgeExpired(now)
        active[pack.code]?.let {
            throw IllegalStateException("Sesi pratinjau ${pack.code.value} sudah berjalan sampai ${it.expiresAt}")
        }
        DomainPackRegistry.register(pack)
        val preview = ActiveDiscoveryPreview(
            packCode = pack.code,
            sandboxTenantId = sandboxTenantId,
            startedAt = now,
            expiresAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + ttlMinutes * 60_000)
        )
        active[pack.code] = preview
        return preview
    }

    fun activeOf(code: DomainPackCode, now: Instant): ActiveDiscoveryPreview? {
        purgeExpired(now)
        return active[code]
    }

    /** Mengakhiri sesi lebih awal; pack dilepas dari registry. `false` bila memang tidak ada sesinya. */
    fun end(code: DomainPackCode): Boolean {
        val removed = active.remove(code) != null
        DomainPackRegistry.unregister(code)
        return removed
    }

    /** Housekeeping: lepaskan semua sesi kedaluwarsa; kembali kode pack yang dilepas. */
    fun purgeExpired(now: Instant): List<DomainPackCode> =
        active.filterValues { it.expiresAt <= now }.keys.toList().also { expired ->
            expired.forEach { end(it) }
        }
}
