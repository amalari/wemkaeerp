package com.eventverse.app.domain.pack

import com.eventverse.app.domain.tenant.TenantId

/**
 * Status penyimpanan pack data (B7). Enum sah (Uji Variabilitas): siklus hidup dokumen milik **platform**, bukan
 * kosakata tenant — sama untuk setiap vertikal.
 */
enum class DomainPackStatus { DRAFT, LOCKED }

/**
 * Satu versi pack data di tabel `domain_packs`. Versi `LOCKED` membeku (Kontrak 5): revisi berikutnya = versi baru,
 * sehingga tenant yang berjalan di atas versi terkunci tidak pernah berubah kosakata di tengah jalan.
 */
data class StoredDomainPack(
    val pack: DomainPack,
    val version: Int,
    val status: DomainPackStatus,
    /** Tenant pemilik pack hasil discovery (Q1). `null` = pack bersama yang ditetapkan superadmin. */
    val ownerTenantId: TenantId?
) {
    init { require(version > 0) { "Versi pack ${pack.code.value} harus positif" } }
}

/** Penyimpanan pack data. Pack bawaan (garment) tidak pernah lewat sini. */
interface DomainPackRepository {

    /** Versi yang dipakai tenant: LOCKED tertinggi, atau DRAFT tertinggi bila belum ada yang dikunci. */
    suspend fun findEffective(code: DomainPackCode): StoredDomainPack?

    suspend fun findAllEffective(): List<StoredDomainPack>

    /** Versi tertinggi apa pun statusnya — titik tulis draf berikutnya. */
    suspend fun findLatest(code: DomainPackCode): StoredDomainPack?

    /** Satu baris versi persis (pin per tenant, PLAN-builder-console M0). `null` = versi tak dikenal — pemanggil menolak, bukan fallback. */
    suspend fun findVersion(code: DomainPackCode, version: Int): StoredDomainPack?

    /** Menyisipkan atau mengganti **satu baris versi** apa adanya. Aturan versi milik use case, bukan repository. */
    suspend fun save(stored: StoredDomainPack): StoredDomainPack
}

/** Aturan versi efektif, satu tempat untuk semua repository: LOCKED tertinggi, atau DRAFT tertinggi bila belum ada. */
fun effectiveOf(versions: List<StoredDomainPack>): StoredDomainPack? =
    versions.filter { it.status == DomainPackStatus.LOCKED }.maxByOrNull { it.version }
        ?: versions.filter { it.status == DomainPackStatus.DRAFT }.maxByOrNull { it.version }
