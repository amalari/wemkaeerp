package com.eventverse.app.domain.crm

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Master data pelanggan (buyer) milik satu tenant.
 *
 * Lahir dari kualifikasi [CrmLead]: ketika seorang sales mengangkat lead ke tahap QUALIFIED,
 * sistem otomatis melakukan find-or-create [Contact] dari data kontak lead dan menautkannya
 * ke Deal. Satu Contact boleh dipakai banyak Deal (satu brand, banyak pesanan), sehingga
 * perubahan alamat/telepon di sini berlaku untuk semua transaksi berikutnya — tanpa pernah
 * menyentuh faktur yang sudah terbit (snapshot `BillToParty` milik Invoicing yang menjaga itu).
 *
 * Immutable; mutasi lewat fungsi domain yang menghasilkan salinan baru.
 */
data class Contact(
    val id: ContactId,
    val tenantId: TenantId,
    val name: String = "",
    val brandName: BrandName = BrandName(""),
    val phone: WhatsappNumber? = null,
    val email: String = "",
    val address: String = "",
    val taxId: String = "",
    /** Jejak asal: lead CRM yang menambahkan contact ini. Null bila dibuat manual admin. */
    val sourceLeadId: LeadId? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.length <= 150) { "Contact name must be at most 150 characters" }
        require(email.length <= 100) { "Contact email must be at most 100 characters" }
    }

    /** Nama tampil: kontak personal, lalu brand, lalu nomor telepon, terakhir fallback ID. */
    val displayName: String
        get() = when {
            name.isNotBlank() -> name
            brandName.value.isNotBlank() -> brandName.value
            phone != null -> phone.localDisplay
            else -> "Kontak #${id.value.takeLast(6)}"
        }

    val hasPhone: Boolean get() = phone != null

    fun rename(newName: String, now: Instant): Contact = copy(name = newName.trim(), updatedAt = now)

    fun updateDetails(
        newPhone: WhatsappNumber?,
        newEmail: String,
        newAddress: String,
        newTaxId: String,
        now: Instant
    ): Contact = copy(
        phone = newPhone,
        email = newEmail.trim(),
        address = newAddress.trim(),
        taxId = newTaxId.trim(),
        updatedAt = now
    )
}
