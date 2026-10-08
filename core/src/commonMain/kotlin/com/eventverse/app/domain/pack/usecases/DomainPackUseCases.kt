package com.eventverse.app.domain.pack.usecases

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Penulisan registry diserialkan: dua superadmin menyimpan bersamaan tidak boleh saling menimpa peta pack. */
private val registryWrite = Mutex()

/**
 * Menyimpan draf pack data (B7 FR-5). Draf terakhir diganti di tempat; bila versi tertinggi sudah `LOCKED`, draf menjadi
 * versi baru, jadi tenant di versi terkunci tidak ikut berubah. Pack ditolak **sebelum** disimpan bila melanggar
 * identitas global ([DomainPackRegistry.violations]).
 */
class SaveDomainPackDraftUseCase(private val repository: DomainPackRepository) {
    suspend operator fun invoke(pack: DomainPack, ownerTenantId: TenantId?): Result<StoredDomainPack> = runCatching {
        DomainPackRegistry.violations(pack).takeIf { it.isNotEmpty() }?.let { throw IllegalArgumentException(it.joinToString("; ")) }
        registryWrite.withLock {
            val latest = repository.findLatest(pack.code)
            val version = when (latest?.status) {
                null -> 1
                DomainPackStatus.DRAFT -> latest.version
                DomainPackStatus.LOCKED -> latest.version + 1
            }
            val stored = repository.save(StoredDomainPack(pack, version, DomainPackStatus.DRAFT, ownerTenantId ?: latest?.ownerTenantId))
            // Draf hanya berlaku bila belum ada versi terkunci (lihat DomainPackRepository.findEffective).
            repository.findEffective(pack.code)?.let { DomainPackRegistry.register(it.pack) }
            stored
        }
    }
}

/** Mengunci draf tertinggi: sejak itu tidak berubah di tempat (Kontrak 5) dan menjadi versi yang dipakai tenant. */
class LockDomainPackUseCase(private val repository: DomainPackRepository) {
    suspend operator fun invoke(code: DomainPackCode): Result<StoredDomainPack> = runCatching {
        registryWrite.withLock {
            val latest = repository.findLatest(code) ?: error("Pack ${code.value} tidak ada")
            require(latest.status == DomainPackStatus.DRAFT) { "Pack ${code.value} versi ${latest.version} sudah terkunci" }
            val locked = repository.save(latest.copy(status = DomainPackStatus.LOCKED))
            DomainPackRegistry.register(locked.pack)
            locked
        }
    }
}

/**
 * Resolusi pack tenant (TRD FR-4) dengan registry sebagai cache: pack bawaan & yang sudah dimuat dijawab tanpa I/O,
 * pack data dimuat dari DB **sekali**, saat tenant pemiliknya pertama kali datang. Tidak ada pemuatan blocking saat
 * startup. `null` = tidak dikenal, atau tidak lagi sah (mis. bertabrakan dengan pack bawaan yang dirilis kemudian) —
 * pemanggil menolak (409), tidak jatuh ke garment.
 */
class ResolveDomainPackUseCase(private val repository: DomainPackRepository) {
    suspend operator fun invoke(code: DomainPackCode): DomainPack? = DomainPackRegistry.find(code) ?: registryWrite.withLock {
        DomainPackRegistry.find(code) ?: repository.findEffective(code)?.pack
            ?.takeIf { runCatching { DomainPackRegistry.register(it) }.isSuccess }
    }
}

/**
 * Resolusi pack pada **versi yang di-pin tenant** (PLAN-builder-console M0). Berbeda dari [ResolveDomainPackUseCase]
 * yang menjawab versi effective: di sini versi lain dari yang diminta **ditolak** — `null` berarti versi tak
 * dikenal, dan pemanggil (plugin tenant / deploy M2) menolak request-nya, tidak jatuh ke versi lain
 * (Kontrak 4 tenant-variability-rules: fallback senyap = data berubah).
 */
class ResolveDomainPackVersionUseCase(private val repository: DomainPackRepository) {
    suspend operator fun invoke(code: DomainPackCode, version: Int): DomainPack? {
        require(version > 0) { "Versi pack harus positif: $version" }
        val stored = repository.findVersion(code, version) ?: return null
        require(stored.status == DomainPackStatus.LOCKED) {
            "Pack ${code.value} versi $version belum terkunci (status ${stored.status}) — tidak boleh dipin tenant"
        }
        return stored.pack.takeIf { runCatching { DomainPackRegistry.register(it) }.isSuccess }
    }
}

/** Apakah tenant sudah punya data operasional (SPK, deal, …). Pindah vertikal di atas data itu = data yatim. */
fun interface TenantOperationalDataProbe {
    suspend fun hasOperationalData(tenantId: TenantId): Boolean
}

/**
 * Menetapkan pack sebuah tenant (superadmin). Ditolak bila pack tidak dikenal, bila pack **dimiliki tenant lain**
 * (`StoredDomainPack.ownerTenantId`, TRD-PLAT-005), atau bila tenant sudah punya data operasional di vertikal
 * lamanya (Discovery B7 §5).
 */
class AssignTenantDomainPackUseCase(
    private val tenantRepository: TenantRepository,
    private val probe: TenantOperationalDataProbe,
    private val packRepository: DomainPackRepository
) {
    suspend operator fun invoke(tenantId: TenantId, code: DomainPackCode): Result<Tenant> = runCatching {
        val tenant = tenantRepository.findById(tenantId) ?: error("Tenant tidak ditemukan: ${tenantId.value}")
        requireNotNull(DomainPackRegistry.find(code)) { "Pack ${code.value} tidak dikenal" }
        // TRD-PLAT-005: pack milik satu tenant tidak boleh dipasang pada tenant lain. Dicek SEBELUM jalan pintas
        // "sudah sama", supaya pemasangan yang melanggar (data lama) gagal keras, bukan lolos diam-diam. `null` = pack
        // bersama atau bawaan (tidak ada baris di repository), yang boleh dipakai siapa pun. Pesan tidak menyebut pemiliknya.
        val owner = packRepository.findLatest(code)?.ownerTenantId
        require(owner == null || owner == tenantId) { "Pack ${code.value} tidak tersedia untuk tenant ini." }
        if (tenant.domainPack == code) return@runCatching tenant
        require(!probe.hasOperationalData(tenantId)) {
            "Tenant ${tenant.slug.value} sudah punya data operasional di pack ${tenant.domainPack.value}; pindah vertikal ditolak."
        }
        tenantRepository.save(tenant.copy(domainPack = code)).getOrThrow()
    }
}
