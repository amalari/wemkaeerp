package com.eventverse.app.domain.vendor

import kotlinx.datetime.LocalDate

/**
 * Satu baris daftar harga vendor: "untuk layanan X, vendor ini memasang harga Y per satuan Z,
 * berlaku mulai tanggal T".
 *
 * [serviceCode] adalah kode proses alur (`TenantOptionalProcess.code`, mis. `SABLON`), bukan
 * teks bebas — itulah yang membuat daftar harga bisa dicocokkan otomatis dengan proses yang
 * sedang menunggu vendor.
 *
 * Interval berlakunya setengah-terbuka `[effectiveFrom, effectiveTo)`, konvensi yang sama
 * dengan harga bahan dan rate card HPP: pada tanggal kenaikan harga, yang berlaku harga baru.
 */
data class VendorServiceRate(
    val serviceCode: String,
    val serviceName: String,
    val priceIdr: Long,
    val unit: VendorPriceUnit = VendorPriceUnit.PER_PIECE,
    val minQuantity: Int = 0,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate? = null
) {
    init {
        require(serviceCode.isNotBlank()) { "Kode layanan tidak boleh kosong" }
        require(serviceName.isNotBlank()) { "Nama layanan tidak boleh kosong" }
        require(priceIdr >= 0L) { "Harga layanan tidak boleh negatif" }
        require(minQuantity >= 0) { "Minimum order tidak boleh negatif" }
        require(effectiveTo == null || effectiveTo > effectiveFrom) {
            "Tanggal akhir berlaku harus setelah tanggal mulai berlaku"
        }
    }

    val isOpenEnded: Boolean get() = effectiveTo == null

    fun isEffectiveOn(date: LocalDate): Boolean =
        effectiveFrom <= date && (effectiveTo == null || date < effectiveTo)

    fun matchesService(code: String): Boolean = serviceCode.equals(code.trim(), ignoreCase = true)

    /** Satu "jalur harga" = layanan yang sama dengan satuan yang sama; kenaikan harga menutup jalurnya. */
    fun sameTrackAs(other: VendorServiceRate): Boolean =
        matchesService(other.serviceCode) && unit == other.unit

    fun closedAt(date: LocalDate): VendorServiceRate = copy(effectiveTo = date)

    companion object {
        fun normalizeCode(raw: String): String = raw.trim().uppercase()
    }
}
