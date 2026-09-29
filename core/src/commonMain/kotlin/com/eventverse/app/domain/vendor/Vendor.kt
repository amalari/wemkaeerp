package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Kontak vendor rekanan (makloon/subkon) — data induk milik pabrik, bukan milik CRM.
 *
 * CRM mengurus sisi jual (buyer yang membayar kita); vendor ada di sisi produksi (yang kita
 * bayar untuk mengerjakan). Karena itu wewenangnya ikut `GarmentModules.VENDOR_CONTACTS`,
 * bukan `CRM_SALES` yang ber-scope hierarkis per sales.
 *
 * Vendor dinonaktifkan, tidak pernah dihapus: penugasan dan Surat Jalan lama tetap merujuknya.
 */
data class Vendor(
    val id: VendorId,
    val tenantId: TenantId,
    val name: VendorName,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val rates: List<VendorServiceRate> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun updateProfile(
        name: VendorName,
        phone: String,
        address: String,
        notes: String,
        now: Instant
    ): Vendor = copy(
        name = name,
        phone = phone.trim(),
        address = address.trim(),
        notes = notes.trim(),
        updatedAt = now
    )

    /**
     * Mencatat harga baru tanpa menimpa yang lama.
     *
     * Harga terbuka pada jalur yang sama (layanan + satuan) ditutup tepat di tanggal mulai
     * harga baru, sehingga penugasan yang sudah memakai harga lama tetap bisa ditelusuri.
     * Harga dengan tanggal mulai yang sama menggantikan entri itu (koreksi salah ketik).
     * Tanggal mundur ditolak: ia akan membuat dua harga berlaku di hari yang sama.
     */
    fun setRate(rate: VendorServiceRate, now: Instant): Vendor {
        val normalized = rate.copy(serviceCode = VendorServiceRate.normalizeCode(rate.serviceCode), effectiveTo = null)
        val open = rates.firstOrNull { it.isOpenEnded && it.sameTrackAs(normalized) }

        val updated = when {
            open == null -> rates + normalized
            open.effectiveFrom == normalized.effectiveFrom -> rates.map { if (it === open) normalized else it }
            else -> {
                require(normalized.effectiveFrom > open.effectiveFrom) {
                    "Harga baru ${normalized.serviceName} harus berlaku setelah ${open.effectiveFrom}"
                }
                rates.map { if (it === open) it.closedAt(normalized.effectiveFrom) else it } + normalized
            }
        }
        return copy(rates = updated, updatedAt = now)
    }

    /** Daftar harga yang berlaku pada [date] untuk layanan [serviceCode], satu per satuan. */
    fun ratesFor(serviceCode: String, date: LocalDate): List<VendorServiceRate> =
        rates.filter { it.matchesService(serviceCode) && it.isEffectiveOn(date) }

    fun offersService(serviceCode: String, date: LocalDate): Boolean = ratesFor(serviceCode, date).isNotEmpty()

    /** Harga yang sedang berlaku per layanan — yang ditampilkan di kartu kontak. */
    fun currentRates(date: LocalDate): List<VendorServiceRate> = rates.filter { it.isEffectiveOn(date) }

    fun deactivate(now: Instant): Vendor = copy(isActive = false, updatedAt = now)

    fun activate(now: Instant): Vendor = copy(isActive = true, updatedAt = now)
}
